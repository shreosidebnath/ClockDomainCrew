package org.chiselware.cores.o01.t001.mac

import chisel3._

class DualWrapperMac extends Module {
  val DATA_W = 64
  val CTRL_W = DATA_W / 8
  val USER_W = 1
  val ID_W   = 8

  val io = IO(new Bundle {
    // Drive XGMII RX into both DUTs
    val xgmii_rxd      = Input(UInt(DATA_W.W))
    val xgmii_rxc      = Input(UInt(CTRL_W.W))
    val xgmii_rx_valid = Input(Bool())

    // Ready for both RX outputs
    val rx_ready = Input(Bool())

    // For oversize test
    val cfg_rx_max_pkt_len = Input(UInt(16.W))

    // Compare outputs: CHISEL vs VERILOG (golden)
    val chisel_rx_tdata  = Output(UInt(DATA_W.W))
    val chisel_rx_tkeep  = Output(UInt(CTRL_W.W))
    val chisel_rx_tvalid = Output(Bool())
    val chisel_rx_tlast  = Output(Bool())
    val chisel_rx_tuser  = Output(UInt(USER_W.W))
    val chisel_rx_tid    = Output(UInt(ID_W.W))

    val chisel_stat_rx_pkt_good     = Output(Bool())
    val chisel_stat_rx_pkt_bad      = Output(Bool())
    val chisel_stat_rx_err_bad_fcs  = Output(Bool())
    val chisel_stat_rx_err_preamble = Output(Bool())
    val chisel_stat_rx_err_framing  = Output(Bool())
    val chisel_stat_rx_err_oversize = Output(Bool())
    val chisel_stat_rx_pkt_fragment = Output(Bool())

    val chisel_stat_tx_pkt_good      = Output(Bool())
    val chisel_stat_tx_pkt_bad       = Output(Bool())
    val chisel_stat_tx_err_oversize  = Output(Bool())
    val chisel_stat_tx_err_user      = Output(Bool())
    val chisel_stat_tx_err_underflow = Output(Bool())

    val verilog_rx_tdata  = Output(UInt(DATA_W.W))
    val verilog_rx_tkeep  = Output(UInt(CTRL_W.W))
    val verilog_rx_tvalid = Output(Bool())
    val verilog_rx_tlast  = Output(Bool())
    val verilog_rx_tuser  = Output(UInt(USER_W.W))
    val verilog_rx_tid    = Output(UInt(ID_W.W))

    val verilog_stat_rx_pkt_good     = Output(Bool())
    val verilog_stat_rx_pkt_bad      = Output(Bool())
    val verilog_stat_rx_err_bad_fcs  = Output(Bool())
    val verilog_stat_rx_err_preamble = Output(Bool())
    val verilog_stat_rx_err_framing  = Output(Bool())
    val verilog_stat_rx_err_oversize = Output(Bool())
    val verilog_stat_rx_pkt_fragment = Output(Bool())

    val verilog_stat_tx_pkt_good      = Output(Bool())
    val verilog_stat_tx_pkt_bad       = Output(Bool())
    val verilog_stat_tx_err_oversize  = Output(Bool())
    val verilog_stat_tx_err_user      = Output(Bool())
    val verilog_stat_tx_err_underflow = Output(Bool())

    // TX IOs
    val tx_tdata  = Input(UInt(DATA_W.W))
    val tx_tkeep  = Input(UInt(CTRL_W.W))
    val tx_tvalid = Input(Bool())
    val tx_tlast  = Input(Bool())
    val tx_tuser  = Input(UInt(USER_W.W))
    val tx_tid    = Input(UInt(ID_W.W))
    val tx_tready = Output(Bool())

    // Export XGMII TX from both DUTs
    val chisel_xgmii_txd      = Output(UInt(DATA_W.W))
    val chisel_xgmii_txc      = Output(UInt(CTRL_W.W))
    val chisel_xgmii_tx_valid = Output(Bool())

    val verilog_xgmii_txd      = Output(UInt(DATA_W.W))
    val verilog_xgmii_txc      = Output(UInt(CTRL_W.W))
    val verilog_xgmii_tx_valid = Output(Bool())
  })

  val bbParams = MacBbParams()

  val chiselParams = MacParams(
    dataW = DATA_W,
    ctrlW = CTRL_W,
    txGbxIfEn = true,
    rxGbxIfEn = true,
    gbxCnt = 1,
    paddingEn = true,
    dicEn = true,
    minFrameLen = 64,
    ptpTsEn = false,
    ptpTsFmtTod = true,
    ptpTsW = 96,
    pfcEn = false,
    pauseEn = false
  )

  val chiselDut = Module(new MacTb(chiselParams))
  val origDut   = Module(new MacBb(bbParams))

  // =========================
  // Chisel DUT (MacTb)
  // =========================
  chiselDut.io.rxClk := clock
  chiselDut.io.txClk := clock
  chiselDut.io.rxRst := reset.asBool
  chiselDut.io.txRst := reset.asBool

  // AXIS TX input
  chiselDut.io.sAxisTx.tdata  := io.tx_tdata
  chiselDut.io.sAxisTx.tkeep  := io.tx_tkeep
  chiselDut.io.sAxisTx.tstrb  := io.tx_tkeep
  chiselDut.io.sAxisTx.tvalid := io.tx_tvalid
  chiselDut.io.sAxisTx.tlast  := io.tx_tlast
  chiselDut.io.sAxisTx.tuser  := io.tx_tuser
  chiselDut.io.sAxisTx.tid    := io.tx_tid
  chiselDut.io.sAxisTx.tdest  := 0.U

  // AXIS RX output ready
  chiselDut.io.mAxisRx.tready := io.rx_ready

  // TX completion sink always ready
  chiselDut.io.mAxisTxCpl.tready := true.B

  // XGMII RX input
  chiselDut.io.xgmiiRxd     := io.xgmii_rxd
  chiselDut.io.xgmiiRxc     := io.xgmii_rxc
  chiselDut.io.xgmiiRxValid := io.xgmii_rx_valid

  // Minimal required inputs
  chiselDut.io.txGbxReqSync  := 0.U
  chiselDut.io.txGbxReqStall := false.B
  chiselDut.io.txPtpTs       := 0.U
  chiselDut.io.rxPtpTs       := 0.U

  chiselDut.io.txLfcReq      := false.B
  chiselDut.io.txLfcResend   := false.B
  chiselDut.io.rxLfcEn       := false.B
  chiselDut.io.rxLfcAck      := false.B

  chiselDut.io.txPfcReq      := 0.U
  chiselDut.io.txPfcResend   := false.B
  chiselDut.io.rxPfcEn       := 0.U
  chiselDut.io.rxPfcAck      := 0.U

  chiselDut.io.txLfcPauseEn  := false.B
  chiselDut.io.txPauseReq    := false.B

  // Config
  chiselDut.io.cfgTxMaxPktLen := 1518.U
  chiselDut.io.cfgTxIfg       := 12.U
  chiselDut.io.cfgTxEnable    := true.B
  chiselDut.io.cfgRxMaxPktLen := io.cfg_rx_max_pkt_len
  chiselDut.io.cfgRxEnable    := true.B

  chiselDut.io.cfgMcfRxEthDstMcast      := 0.U
  chiselDut.io.cfgMcfRxCheckEthDstMcast := false.B
  chiselDut.io.cfgMcfRxEthDstUcast      := 0.U
  chiselDut.io.cfgMcfRxCheckEthDstUcast := false.B
  chiselDut.io.cfgMcfRxEthSrc           := 0.U
  chiselDut.io.cfgMcfRxCheckEthSrc      := false.B
  chiselDut.io.cfgMcfRxEthType          := 0.U
  chiselDut.io.cfgMcfRxOpcodeLfc        := 0.U
  chiselDut.io.cfgMcfRxCheckOpcodeLfc   := false.B
  chiselDut.io.cfgMcfRxOpcodePfc        := 0.U
  chiselDut.io.cfgMcfRxCheckOpcodePfc   := false.B
  chiselDut.io.cfgMcfRxForward          := false.B
  chiselDut.io.cfgMcfRxEnable           := false.B

  chiselDut.io.cfgTxLfcEthDst  := 0.U
  chiselDut.io.cfgTxLfcEthSrc  := 0.U
  chiselDut.io.cfgTxLfcEthType := 0.U
  chiselDut.io.cfgTxLfcOpcode  := 0.U
  chiselDut.io.cfgTxLfcEn      := false.B
  chiselDut.io.cfgTxLfcQuanta  := 0.U
  chiselDut.io.cfgTxLfcRefresh := 0.U

  chiselDut.io.cfgTxPfcEthDst  := 0.U
  chiselDut.io.cfgTxPfcEthSrc  := 0.U
  chiselDut.io.cfgTxPfcEthType := 0.U
  chiselDut.io.cfgTxPfcOpcode  := 0.U
  chiselDut.io.cfgTxPfcEn      := false.B
  chiselDut.io.cfgTxPfcQuanta.foreach(_ := 0.U)
  chiselDut.io.cfgTxPfcRefresh.foreach(_ := 0.U)

  chiselDut.io.cfgRxLfcOpcode := 0.U
  chiselDut.io.cfgRxLfcEn     := false.B
  chiselDut.io.cfgRxPfcOpcode := 0.U
  chiselDut.io.cfgRxPfcEn     := false.B

  // =========================
  // Verilog DUT (MacBb)
  // =========================
  origDut.io.rx_clk := clock
  origDut.io.tx_clk := clock
  origDut.io.rx_rst := reset.asBool
  origDut.io.tx_rst := reset.asBool

  origDut.io.s_axis_tx_tdata  := io.tx_tdata
  origDut.io.s_axis_tx_tkeep  := io.tx_tkeep
  origDut.io.s_axis_tx_tvalid := io.tx_tvalid
  origDut.io.s_axis_tx_tlast  := io.tx_tlast
  origDut.io.s_axis_tx_tuser  := io.tx_tuser
  origDut.io.s_axis_tx_tid    := io.tx_tid

  origDut.io.m_axis_tx_cpl_tready := true.B
  origDut.io.m_axis_rx_tready     := io.rx_ready

  origDut.io.xgmii_rxd      := io.xgmii_rxd
  origDut.io.xgmii_rxc      := io.xgmii_rxc
  origDut.io.xgmii_rx_valid := io.xgmii_rx_valid

  origDut.io.tx_gbx_req_sync  := 0.U
  origDut.io.tx_gbx_req_stall := false.B
  origDut.io.tx_ptp_ts        := 0.U
  origDut.io.rx_ptp_ts        := 0.U

  origDut.io.cfg_tx_max_pkt_len := 1518.U
  origDut.io.cfg_tx_ifg         := 12.U
  origDut.io.cfg_tx_enable      := true.B
  origDut.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
  origDut.io.cfg_rx_enable      := true.B

  // Shared TX ready
  io.tx_tready := chiselDut.io.sAxisTx.tready
  assert(chiselDut.io.sAxisTx.tready === origDut.io.s_axis_tx_tready)

  // =========================
  // Export RX outputs
  // =========================
  io.chisel_rx_tdata  := chiselDut.io.mAxisRx.tdata
  io.chisel_rx_tkeep  := chiselDut.io.mAxisRx.tkeep
  io.chisel_rx_tvalid := chiselDut.io.mAxisRx.tvalid
  io.chisel_rx_tlast  := chiselDut.io.mAxisRx.tlast
  io.chisel_rx_tuser  := chiselDut.io.mAxisRx.tuser
  io.chisel_rx_tid    := 0.U

  io.verilog_rx_tdata  := origDut.io.m_axis_rx_tdata
  io.verilog_rx_tkeep  := origDut.io.m_axis_rx_tkeep
  io.verilog_rx_tvalid := origDut.io.m_axis_rx_tvalid
  io.verilog_rx_tlast  := origDut.io.m_axis_rx_tlast
  io.verilog_rx_tuser  := origDut.io.m_axis_rx_tuser
  io.verilog_rx_tid    := origDut.io.m_axis_rx_tid

  // RX status
  io.chisel_stat_rx_pkt_good     := chiselDut.io.statRxPktGood
  io.chisel_stat_rx_pkt_bad      := chiselDut.io.statRxPktBad
  io.chisel_stat_rx_err_bad_fcs  := chiselDut.io.statRxErrBadFcs
  io.chisel_stat_rx_err_preamble := chiselDut.io.statRxErrPreamble
  io.chisel_stat_rx_err_framing  := chiselDut.io.statRxErrFraming
  io.chisel_stat_rx_err_oversize := chiselDut.io.statRxErrOversize
  io.chisel_stat_rx_pkt_fragment := chiselDut.io.statRxPktFragment

  io.verilog_stat_rx_pkt_good     := origDut.io.stat_rx_pkt_good
  io.verilog_stat_rx_pkt_bad      := origDut.io.stat_rx_pkt_bad
  io.verilog_stat_rx_err_bad_fcs  := origDut.io.stat_rx_err_bad_fcs
  io.verilog_stat_rx_err_preamble := origDut.io.stat_rx_err_preamble
  io.verilog_stat_rx_err_framing  := origDut.io.stat_rx_err_framing
  io.verilog_stat_rx_err_oversize := origDut.io.stat_rx_err_oversize
  io.verilog_stat_rx_pkt_fragment := origDut.io.stat_rx_pkt_fragment

  // TX status
  io.chisel_stat_tx_pkt_good      := chiselDut.io.statTxPktGood
  io.chisel_stat_tx_pkt_bad       := chiselDut.io.statTxPktBad
  io.chisel_stat_tx_err_oversize  := chiselDut.io.statTxErrOversize
  io.chisel_stat_tx_err_user      := chiselDut.io.statTxErrUser
  io.chisel_stat_tx_err_underflow := chiselDut.io.statTxErrUnderflow

  io.verilog_stat_tx_pkt_good      := origDut.io.stat_tx_pkt_good
  io.verilog_stat_tx_pkt_bad       := origDut.io.stat_tx_pkt_bad
  io.verilog_stat_tx_err_oversize  := origDut.io.stat_tx_err_oversize
  io.verilog_stat_tx_err_user      := origDut.io.stat_tx_err_user
  io.verilog_stat_tx_err_underflow := origDut.io.stat_tx_err_underflow

  // XGMII TX
  io.chisel_xgmii_txd      := chiselDut.io.xgmiiTxd
  io.chisel_xgmii_txc      := chiselDut.io.xgmiiTxc
  io.chisel_xgmii_tx_valid := chiselDut.io.xgmiiTxValid

  io.verilog_xgmii_txd      := origDut.io.xgmii_txd
  io.verilog_xgmii_txc      := origDut.io.xgmii_txc
  io.verilog_xgmii_tx_valid := origDut.io.xgmii_tx_valid
}