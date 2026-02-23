package org.chiselware.cores.o01.t001.mac
import org.chiselware.cores.o01.t001.mac.tx.Axis2Xgmii64
import org.chiselware.cores.o01.t001.mac.rx.Xgmii2Axis64
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class Mac(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val txGbxIfEn: Boolean = false,
  val rxGbxIfEn: Boolean = false,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96,
  val pfcEn: Boolean = false,
  val pauseEn: Boolean = false
) extends RawModule { 

  val keepW = dataW / 8
  val macCtrlEn = pauseEn || pfcEn
  val txUserW = 1
  val rxUserW = (if (ptpTsEn) ptpTsW else 0) + 1
  val txUserWInt = (if (macCtrlEn) 1 else 0) + txUserW
  val txTagW = 8 // Extracted from s_axis_tx.ID_W

  // Check configuration
  require(dataW == 64, s"Error: Interface width must be 64 (instance dataW=$dataW)")
  require(keepW * 8 == dataW && ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")

  val io = IO(new Bundle {
    // Explicit Clocks and Resets
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())

    // Transmit interface (AXI stream)
    val s_axis_tx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = dataW, keepW = keepW, idEn = true, idW = txTagW, userEn = true, userW = txUserW
    )))
    val m_axis_tx_cpl = new AxisInterface(AxisInterfaceParams( // Defined in fpga_core wrapper
      dataW = 96, keepW = 1, idW = 8
    ))

    // Receive interface (AXI stream)
    val m_axis_rx = new AxisInterface(AxisInterfaceParams(
      dataW = dataW, keepW = keepW, userEn = true, userW = rxUserW
    ))

    // XGMII interface
    val xgmii_rxd = Input(UInt(dataW.W))
    val xgmii_rxc = Input(UInt(ctrlW.W))
    val xgmii_rx_valid = Input(Bool())
    val xgmii_txd = Output(UInt(dataW.W))
    val xgmii_txc = Output(UInt(ctrlW.W))
    val xgmii_tx_valid = Output(Bool())
    
    val tx_gbx_req_sync = Input(UInt(gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync = Output(UInt(gbxCnt.W))

    // PTP
    val tx_ptp_ts = Input(UInt(ptpTsW.W))
    val rx_ptp_ts = Input(UInt(ptpTsW.W))

    // Link-level Flow Control (LFC)
    val tx_lfc_req = Input(Bool())
    val tx_lfc_resend = Input(Bool())
    val rx_lfc_en = Input(Bool())
    val rx_lfc_req = Output(Bool())
    val rx_lfc_ack = Input(Bool())

    // Priority Flow Control (PFC)
    val tx_pfc_req = Input(UInt(8.W))
    val tx_pfc_resend = Input(Bool())
    val rx_pfc_en = Input(UInt(8.W))
    val rx_pfc_req = Output(UInt(8.W))
    val rx_pfc_ack = Input(UInt(8.W))

    // Pause interface
    val tx_lfc_pause_en = Input(Bool())
    val tx_pause_req = Input(Bool())
    val tx_pause_ack = Output(Bool())

    // Status
    val tx_start_packet = Output(UInt(2.W))
    val stat_tx_byte = Output(UInt(4.W))
    val stat_tx_pkt_len = Output(UInt(16.W))
    val stat_tx_pkt_ucast = Output(Bool())
    val stat_tx_pkt_mcast = Output(Bool())
    val stat_tx_pkt_bcast = Output(Bool())
    val stat_tx_pkt_vlan = Output(Bool())
    val stat_tx_pkt_good = Output(Bool())
    val stat_tx_pkt_bad = Output(Bool())
    val stat_tx_err_oversize = Output(Bool())
    val stat_tx_err_user = Output(Bool())
    val stat_tx_err_underflow = Output(Bool())
    
    val rx_start_packet = Output(UInt(2.W))
    val stat_rx_byte = Output(UInt(4.W))
    val stat_rx_pkt_len = Output(UInt(16.W))
    val stat_rx_pkt_fragment = Output(Bool())
    val stat_rx_pkt_jabber = Output(Bool())
    val stat_rx_pkt_ucast = Output(Bool())
    val stat_rx_pkt_mcast = Output(Bool())
    val stat_rx_pkt_bcast = Output(Bool())
    val stat_rx_pkt_vlan = Output(Bool())
    val stat_rx_pkt_good = Output(Bool())
    val stat_rx_pkt_bad = Output(Bool())
    val stat_rx_err_oversize = Output(Bool())
    val stat_rx_err_bad_fcs = Output(Bool())
    val stat_rx_err_bad_block = Output(Bool())
    val stat_rx_err_framing = Output(Bool())
    val stat_rx_err_preamble = Output(Bool())
    
    val stat_tx_mcf = Output(Bool())
    val stat_rx_mcf = Output(Bool())
    val stat_tx_lfc_pkt = Output(Bool())
    val stat_tx_lfc_xon = Output(Bool())
    val stat_tx_lfc_xoff = Output(Bool())
    val stat_tx_lfc_paused = Output(Bool())
    val stat_tx_pfc_pkt = Output(Bool())
    val stat_tx_pfc_xon = Output(UInt(8.W))
    val stat_tx_pfc_xoff = Output(UInt(8.W))
    val stat_tx_pfc_paused = Output(UInt(8.W))
    val stat_rx_lfc_pkt = Output(Bool())
    val stat_rx_lfc_xon = Output(Bool())
    val stat_rx_lfc_xoff = Output(Bool())
    val stat_rx_lfc_paused = Output(Bool())
    val stat_rx_pfc_pkt = Output(Bool())
    val stat_rx_pfc_xon = Output(UInt(8.W))
    val stat_rx_pfc_xoff = Output(UInt(8.W))
    val stat_rx_pfc_paused = Output(UInt(8.W))

    // Configuration
    val cfg_tx_max_pkt_len = Input(UInt(16.W))
    val cfg_tx_ifg = Input(UInt(8.W))
    val cfg_tx_enable = Input(Bool())
    val cfg_rx_max_pkt_len = Input(UInt(16.W))
    val cfg_rx_enable = Input(Bool())
    
    val cfg_mcf_rx_eth_dst_mcast = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_dst_mcast = Input(Bool())
    val cfg_mcf_rx_eth_dst_ucast = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_dst_ucast = Input(Bool())
    val cfg_mcf_rx_eth_src = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_src = Input(Bool())
    val cfg_mcf_rx_eth_type = Input(UInt(16.W))
    val cfg_mcf_rx_opcode_lfc = Input(UInt(16.W))
    val cfg_mcf_rx_check_opcode_lfc = Input(Bool())
    val cfg_mcf_rx_opcode_pfc = Input(UInt(16.W))
    val cfg_mcf_rx_check_opcode_pfc = Input(Bool())
    val cfg_mcf_rx_forward = Input(Bool())
    val cfg_mcf_rx_enable = Input(Bool())
    
    val cfg_tx_lfc_eth_dst = Input(UInt(48.W))
    val cfg_tx_lfc_eth_src = Input(UInt(48.W))
    val cfg_tx_lfc_eth_type = Input(UInt(16.W))
    val cfg_tx_lfc_opcode = Input(UInt(16.W))
    val cfg_tx_lfc_en = Input(Bool())
    val cfg_tx_lfc_quanta = Input(UInt(16.W))
    val cfg_tx_lfc_refresh = Input(UInt(16.W))
    
    val cfg_tx_pfc_eth_dst = Input(UInt(48.W))
    val cfg_tx_pfc_eth_src = Input(UInt(48.W))
    val cfg_tx_pfc_eth_type = Input(UInt(16.W))
    val cfg_tx_pfc_opcode = Input(UInt(16.W))
    val cfg_tx_pfc_en = Input(Bool())
    val cfg_tx_pfc_quanta = Input(Vec(8, UInt(16.W)))
    val cfg_tx_pfc_refresh = Input(Vec(8, UInt(16.W)))
    
    val cfg_rx_lfc_opcode = Input(UInt(16.W))
    val cfg_rx_lfc_en = Input(Bool())
    val cfg_rx_pfc_opcode = Input(UInt(16.W))
    val cfg_rx_pfc_en = Input(Bool())
  })

  // Internal Interface declarations
  val axis_tx_int = Wire(new AxisInterface(AxisInterfaceParams(
    dataW = dataW, keepW = keepW, idEn = true, idW = txTagW, userEn = true, userW = txUserWInt
  )))
  val axis_rx_int = Wire(new AxisInterface(AxisInterfaceParams(
    dataW = dataW, keepW = keepW, userEn = true, userW = rxUserW
  )))

  // -------------------------------------------------------------
  // RX MAC Submodule (Mapped to rx_clk domain)
  // -------------------------------------------------------------
  withClockAndReset(io.rx_clk, io.rx_rst) {
    val axis_xgmii_rx_inst = Module(new Xgmii2Axis64(
      dataW = dataW,
      ctrlW = ctrlW,
      gbxIfEn = rxGbxIfEn,
      ptpTsEn = ptpTsEn,
      ptpTsFmtTod = ptpTsFmtTod,
      ptpTsW = ptpTsW
    ))

    // Inputs
    axis_xgmii_rx_inst.io.xgmii_rxd := io.xgmii_rxd
    axis_xgmii_rx_inst.io.xgmii_rxc := io.xgmii_rxc
    axis_xgmii_rx_inst.io.xgmii_rx_valid := io.xgmii_rx_valid
    axis_xgmii_rx_inst.io.ptp_ts := io.rx_ptp_ts
    axis_xgmii_rx_inst.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
    axis_xgmii_rx_inst.io.cfg_rx_enable := io.cfg_rx_enable

    // Outputs
    axis_rx_int <> axis_xgmii_rx_inst.io.m_axis_rx // Connect interface directly

    io.rx_start_packet := axis_xgmii_rx_inst.io.rx_start_packet
    io.stat_rx_byte := axis_xgmii_rx_inst.io.stat_rx_byte
    io.stat_rx_pkt_len := axis_xgmii_rx_inst.io.stat_rx_pkt_len
    io.stat_rx_pkt_fragment := axis_xgmii_rx_inst.io.stat_rx_pkt_fragment
    io.stat_rx_pkt_jabber := axis_xgmii_rx_inst.io.stat_rx_pkt_jabber
    io.stat_rx_pkt_ucast := axis_xgmii_rx_inst.io.stat_rx_pkt_ucast
    io.stat_rx_pkt_mcast := axis_xgmii_rx_inst.io.stat_rx_pkt_mcast
    io.stat_rx_pkt_bcast := axis_xgmii_rx_inst.io.stat_rx_pkt_bcast
    io.stat_rx_pkt_vlan := axis_xgmii_rx_inst.io.stat_rx_pkt_vlan
    io.stat_rx_pkt_good := axis_xgmii_rx_inst.io.stat_rx_pkt_good
    io.stat_rx_pkt_bad := axis_xgmii_rx_inst.io.stat_rx_pkt_bad
    io.stat_rx_err_oversize := axis_xgmii_rx_inst.io.stat_rx_err_oversize
    io.stat_rx_err_bad_fcs := axis_xgmii_rx_inst.io.stat_rx_err_bad_fcs
    io.stat_rx_err_bad_block := axis_xgmii_rx_inst.io.stat_rx_err_bad_block
    io.stat_rx_err_framing := axis_xgmii_rx_inst.io.stat_rx_err_framing
    io.stat_rx_err_preamble := axis_xgmii_rx_inst.io.stat_rx_err_preamble
  }

  // -------------------------------------------------------------
  // TX MAC Submodule (Mapped to tx_clk domain)
  // -------------------------------------------------------------
  withClockAndReset(io.tx_clk, io.tx_rst) {
    val axis_xgmii_tx_inst = Module(new Axis2Xgmii64(
      dataW = dataW,
      ctrlW = ctrlW,
      gbxIfEn = txGbxIfEn,
      gbxCnt = gbxCnt,
      paddingEn = paddingEn,
      dicEn = dicEn,
      minFrameLen = minFrameLen,
      ptpTsEn = ptpTsEn,
      ptpTsFmtTod = ptpTsFmtTod,
      ptpTsW = ptpTsW,
      txCplCtrlInTuser = macCtrlEn
    ))

    // Inputs
    axis_xgmii_tx_inst.io.s_axis_tx <> axis_tx_int // Receive from tie/mac_ctrl
    
    io.tx_gbx_sync := axis_xgmii_tx_inst.io.tx_gbx_sync
    axis_xgmii_tx_inst.io.tx_gbx_req_sync := io.tx_gbx_req_sync
    axis_xgmii_tx_inst.io.tx_gbx_req_stall := io.tx_gbx_req_stall
    axis_xgmii_tx_inst.io.ptp_ts := io.tx_ptp_ts
    axis_xgmii_tx_inst.io.cfg_tx_max_pkt_len := io.cfg_tx_max_pkt_len
    axis_xgmii_tx_inst.io.cfg_tx_ifg := io.cfg_tx_ifg
    axis_xgmii_tx_inst.io.cfg_tx_enable := io.cfg_tx_enable

    // Outputs
    io.m_axis_tx_cpl <> axis_xgmii_tx_inst.io.m_axis_tx_cpl
    io.xgmii_txd := axis_xgmii_tx_inst.io.xgmii_txd
    io.xgmii_txc := axis_xgmii_tx_inst.io.xgmii_txc
    io.xgmii_tx_valid := axis_xgmii_tx_inst.io.xgmii_tx_valid

    io.tx_start_packet := axis_xgmii_tx_inst.io.tx_start_packet
    io.stat_tx_byte := axis_xgmii_tx_inst.io.stat_tx_byte
    io.stat_tx_pkt_len := axis_xgmii_tx_inst.io.stat_tx_pkt_len
    io.stat_tx_pkt_ucast := axis_xgmii_tx_inst.io.stat_tx_pkt_ucast
    io.stat_tx_pkt_mcast := axis_xgmii_tx_inst.io.stat_tx_pkt_mcast
    io.stat_tx_pkt_bcast := axis_xgmii_tx_inst.io.stat_tx_pkt_bcast
    io.stat_tx_pkt_vlan := axis_xgmii_tx_inst.io.stat_tx_pkt_vlan
    io.stat_tx_pkt_good := axis_xgmii_tx_inst.io.stat_tx_pkt_good
    io.stat_tx_pkt_bad := axis_xgmii_tx_inst.io.stat_tx_pkt_bad
    io.stat_tx_err_oversize := axis_xgmii_tx_inst.io.stat_tx_err_oversize
    io.stat_tx_err_user := axis_xgmii_tx_inst.io.stat_tx_err_user
    io.stat_tx_err_underflow := axis_xgmii_tx_inst.io.stat_tx_err_underflow
  }

  // -------------------------------------------------------------
  // MAC Control / Bridging Logic
  // -------------------------------------------------------------
  if (macCtrlEn) {
    // Implement control logic here
    } else {
    
    // Connect external TX input to internal TX MAC
    AxisTie(io.s_axis_tx, axis_tx_int)

    // Connect internal RX MAC to external RX output
    AxisTie(axis_rx_int, io.m_axis_rx)

    // Tie off unused flow-control outputs securely
    io.rx_lfc_req := false.B
    io.rx_pfc_req := 0.U
    io.tx_pause_ack := false.B

    io.stat_tx_mcf := false.B
    io.stat_rx_mcf := false.B
    io.stat_tx_lfc_pkt := false.B
    io.stat_tx_lfc_xon := false.B
    io.stat_tx_lfc_xoff := false.B
    io.stat_tx_lfc_paused := false.B
    io.stat_tx_pfc_pkt := false.B
    io.stat_tx_pfc_xon := 0.U
    io.stat_tx_pfc_xoff := 0.U
    io.stat_tx_pfc_paused := 0.U
    io.stat_rx_lfc_pkt := false.B
    io.stat_rx_lfc_xon := false.B
    io.stat_rx_lfc_xoff := false.B
    io.stat_rx_lfc_paused := false.B
    io.stat_rx_pfc_pkt := false.B
    io.stat_rx_pfc_xon := 0.U
    io.stat_rx_pfc_xoff := 0.U
    io.stat_rx_pfc_paused := 0.U
  }
}



object Mac {
  def apply(p: MacParams): Mac = Module(new Mac(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    txGbxIfEn = p.txGbxIfEn,
    rxGbxIfEn = p.rxGbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW,
    pfcEn = p.pfcEn,
    pauseEn = p.pauseEn
  ))
}


object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  MacParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Mac(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        txGbxIfEn = p.txGbxIfEn,
        rxGbxIfEn = p.rxGbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        pfcEn = p.pfcEn,
        pauseEn = p.pauseEn
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    // Synthesis collateral generation
    sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    RunScriptFile.create(mainClassName, MacParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}
