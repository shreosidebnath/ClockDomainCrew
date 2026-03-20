package org.chiselware.cores.o01.t001.mac

import chisel3._

class DualWrapperMac extends Module {
  private val dataW = 64
  private val ctrlW = dataW / 8
  private val userW = 1
  private val idW = 8

  val io = IO(new Bundle {
    // Drive XGMII RX into both DUTs
    val xgmiiRxd = Input(UInt(dataW.W))
    val xgmiiRxc = Input(UInt(ctrlW.W))
    val xgmiiRxValid = Input(Bool())

    // Ready for both RX outputs
    val rxReady = Input(Bool())

    // For oversize test
    val cfgRxMaxPktLen = Input(UInt(16.W))

    // Compare outputs: Chisel vs Verilog (golden)
    val chiselRxTdata = Output(UInt(dataW.W))
    val chiselRxTkeep = Output(UInt(ctrlW.W))
    val chiselRxTvalid = Output(Bool())
    val chiselRxTlast = Output(Bool())
    val chiselRxTuser = Output(UInt(userW.W))
    val chiselRxTid = Output(UInt(idW.W))

    val chiselStatRxPktGood = Output(Bool())
    val chiselStatRxPktBad = Output(Bool())
    val chiselStatRxErrBadFcs = Output(Bool())
    val chiselStatRxErrPreamble = Output(Bool())
    val chiselStatRxErrFraming = Output(Bool())
    val chiselStatRxErrOversize = Output(Bool())
    val chiselStatRxPktFragment = Output(Bool())

    val chiselStatTxPktGood = Output(Bool())
    val chiselStatTxPktBad = Output(Bool())
    val chiselStatTxErrOversize = Output(Bool())
    val chiselStatTxErrUser = Output(Bool())
    val chiselStatTxErrUnderflow = Output(Bool())

    val verilogRxTdata = Output(UInt(dataW.W))
    val verilogRxTkeep = Output(UInt(ctrlW.W))
    val verilogRxTvalid = Output(Bool())
    val verilogRxTlast = Output(Bool())
    val verilogRxTuser = Output(UInt(userW.W))
    val verilogRxTid = Output(UInt(idW.W))

    val verilogStatRxPktGood = Output(Bool())
    val verilogStatRxPktBad = Output(Bool())
    val verilogStatRxErrBadFcs = Output(Bool())
    val verilogStatRxErrPreamble = Output(Bool())
    val verilogStatRxErrFraming = Output(Bool())
    val verilogStatRxErrOversize = Output(Bool())
    val verilogStatRxPktFragment = Output(Bool())

    val verilogStatTxPktGood = Output(Bool())
    val verilogStatTxPktBad = Output(Bool())
    val verilogStatTxErrOversize = Output(Bool())
    val verilogStatTxErrUser = Output(Bool())
    val verilogStatTxErrUnderflow = Output(Bool())

    // TX IOs
    val txTdata = Input(UInt(dataW.W))
    val txTkeep = Input(UInt(ctrlW.W))
    val txTvalid = Input(Bool())
    val txTlast = Input(Bool())
    val txTuser = Input(UInt(userW.W))
    val txTid = Input(UInt(idW.W))
    val txTready = Output(Bool())

    // Export XGMII TX from both DUTs
    val chiselXgmiiTxd = Output(UInt(dataW.W))
    val chiselXgmiiTxc = Output(UInt(ctrlW.W))
    val chiselXgmiiTxValid = Output(Bool())

    val verilogXgmiiTxd = Output(UInt(dataW.W))
    val verilogXgmiiTxc = Output(UInt(ctrlW.W))
    val verilogXgmiiTxValid = Output(Bool())
  })

  private val bbParams = MacBbParams()

  private val chiselParams = MacParams(
    dataW = dataW,
    ctrlW = ctrlW,
    txGbxIfEn = true,
    rxGbxIfEn = true,
    gbxCnt = 1,
    paddingEn = true,
    dicEn = true,
    minFrameLen = 64,
    ptpTsEn = false,
    ptpTsFmtTod = true,
    ptpTsW = 96
  )

  private val chiselDut = Module(new MacTb(chiselParams))
  private val origDut = Module(new MacBb(bbParams))

  // =========================
  // Chisel DUT (MacTb)
  // =========================
  chiselDut.io.rxClk := clock
  chiselDut.io.txClk := clock
  chiselDut.io.statClk := clock
  chiselDut.io.rxRst := reset.asBool
  chiselDut.io.txRst := reset.asBool
  chiselDut.io.statRst := reset.asBool

  // AXIS TX input
  chiselDut.io.sAxisTx.tdata := io.txTdata
  chiselDut.io.sAxisTx.tkeep := io.txTkeep
  chiselDut.io.sAxisTx.tstrb := io.txTkeep
  chiselDut.io.sAxisTx.tvalid := io.txTvalid
  chiselDut.io.sAxisTx.tlast := io.txTlast
  chiselDut.io.sAxisTx.tuser := io.txTuser
  chiselDut.io.sAxisTx.tid := io.txTid
  chiselDut.io.sAxisTx.tdest := 0.U

  // AXIS RX output ready
  chiselDut.io.mAxisRx.tready := io.rxReady

  // TX completion sink always ready
  chiselDut.io.mAxisTxCpl.tready := true.B

  // Stats sink always ready
  chiselDut.io.mAxisStat.tready := true.B

  // No RX FIFO drop indication in this wrapper
  chiselDut.io.statRxFifoDrop := false.B

  // XGMII RX input
  chiselDut.io.xgmiiRxd := io.xgmiiRxd
  chiselDut.io.xgmiiRxc := io.xgmiiRxc
  chiselDut.io.xgmiiRxValid := io.xgmiiRxValid

  // Minimal required inputs
  chiselDut.io.txGbxReqSync := 0.U
  chiselDut.io.txGbxReqStall := false.B
  chiselDut.io.txPtpTs := 0.U
  chiselDut.io.rxPtpTs := 0.U

  chiselDut.io.txLfcReq := false.B
  chiselDut.io.txLfcResend := false.B
  chiselDut.io.rxLfcEn := false.B
  chiselDut.io.rxLfcAck := false.B

  chiselDut.io.txPfcReq := 0.U
  chiselDut.io.txPfcResend := false.B
  chiselDut.io.rxPfcEn := 0.U
  chiselDut.io.rxPfcAck := 0.U

  chiselDut.io.txLfcPauseEn := false.B
  chiselDut.io.txPauseReq := false.B

  // Config
  chiselDut.io.cfgTxMaxPktLen := 1518.U
  chiselDut.io.cfgTxIfg := 12.U
  chiselDut.io.cfgTxEnable := true.B
  chiselDut.io.cfgRxMaxPktLen := io.cfgRxMaxPktLen
  chiselDut.io.cfgRxEnable := true.B

  chiselDut.io.cfgMcfRxEthDstMcast := 0.U
  chiselDut.io.cfgMcfRxCheckEthDstMcast := false.B
  chiselDut.io.cfgMcfRxEthDstUcast := 0.U
  chiselDut.io.cfgMcfRxCheckEthDstUcast := false.B
  chiselDut.io.cfgMcfRxEthSrc := 0.U
  chiselDut.io.cfgMcfRxCheckEthSrc := false.B
  chiselDut.io.cfgMcfRxEthType := 0.U
  chiselDut.io.cfgMcfRxOpcodeLfc := 0.U
  chiselDut.io.cfgMcfRxCheckOpcodeLfc := false.B
  chiselDut.io.cfgMcfRxOpcodePfc := 0.U
  chiselDut.io.cfgMcfRxCheckOpcodePfc := false.B
  chiselDut.io.cfgMcfRxForward := false.B
  chiselDut.io.cfgMcfRxEnable := false.B

  chiselDut.io.cfgTxLfcEthDst := 0.U
  chiselDut.io.cfgTxLfcEthSrc := 0.U
  chiselDut.io.cfgTxLfcEthType := 0.U
  chiselDut.io.cfgTxLfcOpcode := 0.U
  chiselDut.io.cfgTxLfcEn := false.B
  chiselDut.io.cfgTxLfcQuanta := 0.U
  chiselDut.io.cfgTxLfcRefresh := 0.U

  chiselDut.io.cfgTxPfcEthDst := 0.U
  chiselDut.io.cfgTxPfcEthSrc := 0.U
  chiselDut.io.cfgTxPfcEthType := 0.U
  chiselDut.io.cfgTxPfcOpcode := 0.U
  chiselDut.io.cfgTxPfcEn := false.B
  chiselDut.io.cfgTxPfcQuanta.foreach(_ := 0.U)
  chiselDut.io.cfgTxPfcRefresh.foreach(_ := 0.U)

  chiselDut.io.cfgRxLfcOpcode := 0.U
  chiselDut.io.cfgRxLfcEn := false.B
  chiselDut.io.cfgRxPfcOpcode := 0.U
  chiselDut.io.cfgRxPfcEn := false.B

  // =========================
  // Verilog DUT (MacBb)
  // =========================
  origDut.io.rx_clk := clock
  origDut.io.tx_clk := clock
  origDut.io.rx_rst := reset.asBool
  origDut.io.tx_rst := reset.asBool

  origDut.io.s_axis_tx_tdata := io.txTdata
  origDut.io.s_axis_tx_tkeep := io.txTkeep
  origDut.io.s_axis_tx_tvalid := io.txTvalid
  origDut.io.s_axis_tx_tlast := io.txTlast
  origDut.io.s_axis_tx_tuser := io.txTuser
  origDut.io.s_axis_tx_tid := io.txTid

  origDut.io.m_axis_tx_cpl_tready := true.B
  origDut.io.m_axis_rx_tready := io.rxReady

  origDut.io.xgmii_rxd := io.xgmiiRxd
  origDut.io.xgmii_rxc := io.xgmiiRxc
  origDut.io.xgmii_rx_valid := io.xgmiiRxValid

  origDut.io.tx_gbx_req_sync := 0.U
  origDut.io.tx_gbx_req_stall := false.B
  origDut.io.tx_ptp_ts := 0.U
  origDut.io.rx_ptp_ts := 0.U

  origDut.io.cfg_tx_max_pkt_len := 1518.U
  origDut.io.cfg_tx_ifg := 12.U
  origDut.io.cfg_tx_enable := true.B
  origDut.io.cfg_rx_max_pkt_len := io.cfgRxMaxPktLen
  origDut.io.cfg_rx_enable := true.B

  // Shared TX ready
  io.txTready := chiselDut.io.sAxisTx.tready
  assert(chiselDut.io.sAxisTx.tready === origDut.io.s_axis_tx_tready)

  // =========================
  // Export RX outputs
  // =========================
  io.chiselRxTdata := chiselDut.io.mAxisRx.tdata
  io.chiselRxTkeep := chiselDut.io.mAxisRx.tkeep
  io.chiselRxTvalid := chiselDut.io.mAxisRx.tvalid
  io.chiselRxTlast := chiselDut.io.mAxisRx.tlast
  io.chiselRxTuser := chiselDut.io.mAxisRx.tuser
  io.chiselRxTid := 0.U

  io.verilogRxTdata := origDut.io.m_axis_rx_tdata
  io.verilogRxTkeep := origDut.io.m_axis_rx_tkeep
  io.verilogRxTvalid := origDut.io.m_axis_rx_tvalid
  io.verilogRxTlast := origDut.io.m_axis_rx_tlast
  io.verilogRxTuser := origDut.io.m_axis_rx_tuser
  io.verilogRxTid := origDut.io.m_axis_rx_tid

  // RX status
  io.chiselStatRxPktGood := chiselDut.io.statRxPktGood
  io.chiselStatRxPktBad := chiselDut.io.statRxPktBad
  io.chiselStatRxErrBadFcs := chiselDut.io.statRxErrBadFcs
  io.chiselStatRxErrPreamble := chiselDut.io.statRxErrPreamble
  io.chiselStatRxErrFraming := chiselDut.io.statRxErrFraming
  io.chiselStatRxErrOversize := chiselDut.io.statRxErrOversize
  io.chiselStatRxPktFragment := chiselDut.io.statRxPktFragment

  io.verilogStatRxPktGood := origDut.io.stat_rx_pkt_good
  io.verilogStatRxPktBad := origDut.io.stat_rx_pkt_bad
  io.verilogStatRxErrBadFcs := origDut.io.stat_rx_err_bad_fcs
  io.verilogStatRxErrPreamble := origDut.io.stat_rx_err_preamble
  io.verilogStatRxErrFraming := origDut.io.stat_rx_err_framing
  io.verilogStatRxErrOversize := origDut.io.stat_rx_err_oversize
  io.verilogStatRxPktFragment := origDut.io.stat_rx_pkt_fragment

  // TX status
  io.chiselStatTxPktGood := chiselDut.io.statTxPktGood
  io.chiselStatTxPktBad := chiselDut.io.statTxPktBad
  io.chiselStatTxErrOversize := chiselDut.io.statTxErrOversize
  io.chiselStatTxErrUser := chiselDut.io.statTxErrUser
  io.chiselStatTxErrUnderflow := chiselDut.io.statTxErrUnderflow

  io.verilogStatTxPktGood := origDut.io.stat_tx_pkt_good
  io.verilogStatTxPktBad := origDut.io.stat_tx_pkt_bad
  io.verilogStatTxErrOversize := origDut.io.stat_tx_err_oversize
  io.verilogStatTxErrUser := origDut.io.stat_tx_err_user
  io.verilogStatTxErrUnderflow := origDut.io.stat_tx_err_underflow

  // XGMII TX
  io.chiselXgmiiTxd := chiselDut.io.xgmiiTxd
  io.chiselXgmiiTxc := chiselDut.io.xgmiiTxc
  io.chiselXgmiiTxValid := chiselDut.io.xgmiiTxValid

  io.verilogXgmiiTxd := origDut.io.xgmii_txd
  io.verilogXgmiiTxc := origDut.io.xgmii_txc
  io.verilogXgmiiTxValid := origDut.io.xgmii_tx_valid
}