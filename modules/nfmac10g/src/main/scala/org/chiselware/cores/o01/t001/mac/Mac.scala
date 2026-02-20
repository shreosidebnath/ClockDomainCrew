package org.chiselware.cores.o01.t001.mac
import org.chiselware.cores.o01.t001.mac.tx.Axis2Xgmii32
import org.chiselware.cores.o01.t001.mac.rx.Xgmii2Axis32
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
  val pauseEn: Boolean = false,
  val statEn: Boolean = false,
  val statTxLevel: Int = 1,
  val statRxLevel: Int = 1,
  val statIdBase: Int = 0,
  val statUpdatePeriod: Int = 1024,
  val statStrEn: Boolean = false,
  val statPrefixStr: String = "MAC" 
) extends RawModule {
  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  require(ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")

  val keepW = dataW / 8
  val txUserW = 1
  val rxUserW = (if (ptpTsEn) ptpTsW else 0) + 1
  val txTagW = 8 // Defaulting ID_W to 8 based on standard usage
  val macCtrlEn = pauseEn || pfcEn
  val txUserWInt = (if (macCtrlEn) 1 else 0) + txUserW

  val io = IO(new Bundle {
    // Clocks and Resets
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())

    // AXI Stream Interfaces
    val s_axis_tx     = Flipped(new AxisInterface(dataW, txUserWInt, txTagW))
    val m_axis_tx_cpl = new AxisInterface(dataW, txUserWInt, txTagW)
    val m_axis_rx     = new AxisInterface(dataW, rxUserW)

    // XGMII Interface
    val xgmii_rxd      = Input(UInt(dataW.W))
    val xgmii_rxc      = Input(UInt(ctrlW.W))
    val xgmii_rx_valid = Input(Bool())
    val xgmii_txd      = Output(UInt(dataW.W))
    val xgmii_txc      = Output(UInt(ctrlW.W))
    val xgmii_tx_valid = Output(Bool())
    val tx_gbx_req_sync  = Input(UInt(gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync      = Output(UInt(gbxCnt.W))

    // PTP
    val tx_ptp_ts = Input(UInt(ptpTsW.W))
    val rx_ptp_ts = Input(UInt(ptpTsW.W))

    // Link-level Flow Control (LFC)
    val tx_lfc_req    = Input(Bool())
    val tx_lfc_resend = Input(Bool())
    val rx_lfc_en     = Input(Bool())
    val rx_lfc_req    = Output(Bool())
    val rx_lfc_ack    = Input(Bool())

    // Priority Flow Control (PFC)
    val tx_pfc_req    = Input(UInt(8.W))
    val tx_pfc_resend = Input(Bool())
    val rx_pfc_en     = Input(UInt(8.W))
    val rx_pfc_req    = Output(UInt(8.W))
    val rx_pfc_ack    = Input(UInt(8.W))

    // Pause interface
    val tx_lfc_pause_en = Input(Bool())
    val tx_pause_req    = Input(Bool())
    val tx_pause_ack    = Output(Bool())

    // Statistics Clocks
    val stat_clk = Input(Clock())
    val stat_rst = Input(Bool())
    val m_axis_stat = new AxisInterface(dataW, txUserWInt, txTagW)

    // Status
    val tx_start_packet       = Output(UInt(2.W))
    val stat_tx_byte          = Output(UInt(4.W))
    val stat_tx_pkt_len       = Output(UInt(16.W))
    val stat_tx_pkt_ucast     = Output(Bool())
    val stat_tx_pkt_mcast     = Output(Bool())
    val stat_tx_pkt_bcast     = Output(Bool())
    val stat_tx_pkt_vlan      = Output(Bool())
    val stat_tx_pkt_good      = Output(Bool())
    val stat_tx_pkt_bad       = Output(Bool())
    val stat_tx_err_oversize  = Output(Bool())
    val stat_tx_err_user      = Output(Bool())
    val stat_tx_err_underflow = Output(Bool())

    val rx_start_packet       = Output(UInt(2.W))
    val stat_rx_byte          = Output(UInt(4.W))
    val stat_rx_pkt_len       = Output(UInt(16.W))
    val stat_rx_pkt_fragment  = Output(Bool())
    val stat_rx_pkt_jabber    = Output(Bool())
    val stat_rx_pkt_ucast     = Output(Bool())
    val stat_rx_pkt_mcast     = Output(Bool())
    val stat_rx_pkt_bcast     = Output(Bool())
    val stat_rx_pkt_vlan      = Output(Bool())
    val stat_rx_pkt_good      = Output(Bool())
    val stat_rx_pkt_bad       = Output(Bool())
    val stat_rx_err_oversize  = Output(Bool())
    val stat_rx_err_bad_fcs   = Output(Bool())
    val stat_rx_err_bad_block = Output(Bool())
    val stat_rx_err_framing   = Output(Bool())
    val stat_rx_err_preamble  = Output(Bool())
    val stat_rx_fifo_drop     = Input(Bool())

    val stat_tx_mcf        = Output(Bool())
    val stat_rx_mcf        = Output(Bool())
    val stat_tx_lfc_pkt    = Output(Bool())
    val stat_tx_lfc_xon    = Output(Bool())
    val stat_tx_lfc_xoff   = Output(Bool())
    val stat_tx_lfc_paused = Output(Bool())
    val stat_tx_pfc_pkt    = Output(Bool())
    val stat_tx_pfc_xon    = Output(UInt(8.W))
    val stat_tx_pfc_xoff   = Output(UInt(8.W))
    val stat_tx_pfc_paused = Output(UInt(8.W))
    val stat_rx_lfc_pkt    = Output(Bool())
    val stat_rx_lfc_xon    = Output(Bool())
    val stat_rx_lfc_xoff   = Output(Bool())
    val stat_rx_lfc_paused = Output(Bool())
    val stat_rx_pfc_pkt    = Output(Bool())
    val stat_rx_pfc_xon    = Output(UInt(8.W))
    val stat_rx_pfc_xoff   = Output(UInt(8.W))
    val stat_rx_pfc_paused = Output(UInt(8.W))

    // Configuration
    val cfg_tx_max_pkt_len             = Input(UInt(16.W))
    val cfg_tx_ifg                     = Input(UInt(8.W))
    val cfg_tx_enable                  = Input(Bool())
    val cfg_rx_max_pkt_len             = Input(UInt(16.W))
    val cfg_rx_enable                  = Input(Bool())
    val cfg_mcf_rx_eth_dst_mcast       = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_dst_mcast = Input(Bool())
    val cfg_mcf_rx_eth_dst_ucast       = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_dst_ucast = Input(Bool())
    val cfg_mcf_rx_eth_src             = Input(UInt(48.W))
    val cfg_mcf_rx_check_eth_src       = Input(Bool())
    val cfg_mcf_rx_eth_type            = Input(UInt(16.W))
    val cfg_mcf_rx_opcode_lfc          = Input(UInt(16.W))
    val cfg_mcf_rx_check_opcode_lfc    = Input(Bool())
    val cfg_mcf_rx_opcode_pfc          = Input(UInt(16.W))
    val cfg_mcf_rx_check_opcode_pfc    = Input(Bool())
    val cfg_mcf_rx_forward             = Input(Bool())
    val cfg_mcf_rx_enable              = Input(Bool())
    val cfg_tx_lfc_eth_dst             = Input(UInt(48.W))
    val cfg_tx_lfc_eth_src             = Input(UInt(48.W))
    val cfg_tx_lfc_eth_type            = Input(UInt(16.W))
    val cfg_tx_lfc_opcode              = Input(UInt(16.W))
    val cfg_tx_lfc_en                  = Input(Bool())
    val cfg_tx_lfc_quanta              = Input(UInt(16.W))
    val cfg_tx_lfc_refresh             = Input(UInt(16.W))
    val cfg_tx_pfc_eth_dst             = Input(UInt(48.W))
    val cfg_tx_pfc_eth_src             = Input(UInt(48.W))
    val cfg_tx_pfc_eth_type            = Input(UInt(16.W))
    val cfg_tx_pfc_opcode              = Input(UInt(16.W))
    val cfg_tx_pfc_en                  = Input(Bool())
    val cfg_tx_pfc_quanta              = Input(Vec(8, UInt(16.W)))
    val cfg_tx_pfc_refresh             = Input(Vec(8, UInt(16.W)))
    val cfg_rx_lfc_opcode              = Input(UInt(16.W))
    val cfg_rx_lfc_en                  = Input(Bool())
    val cfg_rx_pfc_opcode              = Input(UInt(16.W))
    val cfg_rx_pfc_en                  = Input(Bool())
  })

  // Internal Wires for AXIS interfaces
  val axis_tx_int = Wire(new AxisInterface(dataW, txUserWInt, txTagW))
  val axis_rx_int = Wire(new AxisInterface(dataW, rxUserW))

  // ---------------------------------------------------------------------------
  // XGMII RX/TX Datapath Setup
  // ---------------------------------------------------------------------------
  if (dataW == 64) {
    // withClockAndReset(io.rx_clk, io.rx_rst) {
        // val axis_xgmii_rx_inst = Module(new Xgmii2Axis64(dataW, ctrlW, rxGbxIfEn, ptpTsEn, ptpTsFmtTod, ptpTsW))
        // axis_xgmii_rx_inst.io.xgmii_rxd      := io.xgmii_rxd
        // axis_xgmii_rx_inst.io.xgmii_rxc      := io.xgmii_rxc
        // axis_xgmii_rx_inst.io.xgmii_rx_valid := io.xgmii_rx_valid
        // axis_rx_int                          <> axis_xgmii_rx_inst.io.m_axis_rx
        // axis_xgmii_rx_inst.io.ptp_ts         := io.rx_ptp_ts
        
        // axis_xgmii_rx_inst.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
        // axis_xgmii_rx_inst.io.cfg_rx_enable      := io.cfg_rx_enable

        // io.rx_start_packet       := axis_xgmii_rx_inst.io.rx_start_packet
        // io.stat_rx_byte          := axis_xgmii_rx_inst.io.stat_rx_byte
        // io.stat_rx_pkt_len       := axis_xgmii_rx_inst.io.stat_rx_pkt_len
        // io.stat_rx_pkt_fragment  := axis_xgmii_rx_inst.io.stat_rx_pkt_fragment
        // io.stat_rx_pkt_jabber    := axis_xgmii_rx_inst.io.stat_rx_pkt_jabber
        // io.stat_rx_pkt_ucast     := axis_xgmii_rx_inst.io.stat_rx_pkt_ucast
        // io.stat_rx_pkt_mcast     := axis_xgmii_rx_inst.io.stat_rx_pkt_mcast
        // io.stat_rx_pkt_bcast     := axis_xgmii_rx_inst.io.stat_rx_pkt_bcast
        // io.stat_rx_pkt_vlan      := axis_xgmii_rx_inst.io.stat_rx_pkt_vlan
        // io.stat_rx_pkt_good      := axis_xgmii_rx_inst.io.stat_rx_pkt_good
        // io.stat_rx_pkt_bad       := axis_xgmii_rx_inst.io.stat_rx_pkt_bad
        // io.stat_rx_err_oversize  := axis_xgmii_rx_inst.io.stat_rx_err_oversize
        // io.stat_rx_err_bad_fcs   := axis_xgmii_rx_inst.io.stat_rx_err_bad_fcs
        // io.stat_rx_err_bad_block := axis_xgmii_rx_inst.io.stat_rx_err_bad_block
        // io.stat_rx_err_framing   := axis_xgmii_rx_inst.io.stat_rx_err_framing
        // io.stat_rx_err_preamble  := axis_xgmii_rx_inst.io.stat_rx_err_preamble
    // }

    // withClockAndReset(io.tx_clk, io.tx_rst) {
        // val axis_xgmii_tx_inst = Module(new Axis2Xgmii64(dataW, ctrlW, txGbxIfEn, gbxCnt, paddingEn, dicEn, minFrameLen, ptpTsEn, ptpTsFmtTod, ptpTsW, macCtrlEn))
        // axis_xgmii_tx_inst.io.s_axis_tx        <> axis_tx_int
        // io.m_axis_tx_cpl                       <> axis_xgmii_tx_inst.io.m_axis_tx_cpl
        
        // io.xgmii_txd                           := axis_xgmii_tx_inst.io.xgmii_txd
        // io.xgmii_txc                           := axis_xgmii_tx_inst.io.xgmii_txc
        // io.xgmii_tx_valid                      := axis_xgmii_tx_inst.io.xgmii_tx_valid
        // axis_xgmii_tx_inst.io.tx_gbx_req_sync  := io.tx_gbx_req_sync
        // axis_xgmii_tx_inst.io.tx_gbx_req_stall := io.tx_gbx_req_stall
        // io.tx_gbx_sync                         := axis_xgmii_tx_inst.io.tx_gbx_sync
        
        // axis_xgmii_tx_inst.io.ptp_ts           := io.tx_ptp_ts
        // axis_xgmii_tx_inst.io.cfg_tx_max_pkt_len := io.cfg_tx_max_pkt_len
        // axis_xgmii_tx_inst.io.cfg_tx_ifg       := io.cfg_tx_ifg
        // axis_xgmii_tx_inst.io.cfg_tx_enable    := io.cfg_tx_enable

        // io.tx_start_packet       := axis_xgmii_tx_inst.io.tx_start_packet
        // io.stat_tx_byte          := axis_xgmii_tx_inst.io.stat_tx_byte
        // io.stat_tx_pkt_len       := axis_xgmii_tx_inst.io.stat_tx_pkt_len
        // io.stat_tx_pkt_ucast     := axis_xgmii_tx_inst.io.stat_tx_pkt_ucast
        // io.stat_tx_pkt_mcast     := axis_xgmii_tx_inst.io.stat_tx_pkt_mcast
        // io.stat_tx_pkt_bcast     := axis_xgmii_tx_inst.io.stat_tx_pkt_bcast
        // io.stat_tx_pkt_vlan      := axis_xgmii_tx_inst.io.stat_tx_pkt_vlan
        // io.stat_tx_pkt_good      := axis_xgmii_tx_inst.io.stat_tx_pkt_good
        // io.stat_tx_pkt_bad       := axis_xgmii_tx_inst.io.stat_tx_pkt_bad
        // io.stat_tx_err_oversize  := axis_xgmii_tx_inst.io.stat_tx_err_oversize
        // io.stat_tx_err_user      := axis_xgmii_tx_inst.io.stat_tx_err_user
        // io.stat_tx_err_underflow := axis_xgmii_tx_inst.io.stat_tx_err_underflow
    // }
    } else {
        // 32-bit Instantiation
    withClockAndReset(io.rx_clk, io.rx_rst) {
        val axis_xgmii_rx_inst = Module(new Xgmii2Axis32(dataW, ctrlW, rxGbxIfEn, ptpTsEn, ptpTsW))
        axis_xgmii_rx_inst.io.xgmii_rxd      := io.xgmii_rxd
        axis_xgmii_rx_inst.io.xgmii_rxc      := io.xgmii_rxc
        axis_xgmii_rx_inst.io.xgmii_rx_valid := io.xgmii_rx_valid
        axis_rx_int                          <> axis_xgmii_rx_inst.io.m_axis_rx
        axis_xgmii_rx_inst.io.ptp_ts         := io.rx_ptp_ts
        
        axis_xgmii_rx_inst.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
        axis_xgmii_rx_inst.io.cfg_rx_enable      := io.cfg_rx_enable

        io.rx_start_packet       := Cat(0.U(1.W), axis_xgmii_rx_inst.io.rx_start_packet)
        io.stat_rx_byte          := Cat(0.U(1.W), axis_xgmii_rx_inst.io.stat_rx_byte)
        io.stat_rx_pkt_len       := axis_xgmii_rx_inst.io.stat_rx_pkt_len
        io.stat_rx_pkt_fragment  := axis_xgmii_rx_inst.io.stat_rx_pkt_fragment
        io.stat_rx_pkt_jabber    := axis_xgmii_rx_inst.io.stat_rx_pkt_jabber
        io.stat_rx_pkt_ucast     := axis_xgmii_rx_inst.io.stat_rx_pkt_ucast
        io.stat_rx_pkt_mcast     := axis_xgmii_rx_inst.io.stat_rx_pkt_mcast
        io.stat_rx_pkt_bcast     := axis_xgmii_rx_inst.io.stat_rx_pkt_bcast
        io.stat_rx_pkt_vlan      := axis_xgmii_rx_inst.io.stat_rx_pkt_vlan
        io.stat_rx_pkt_good      := axis_xgmii_rx_inst.io.stat_rx_pkt_good
        io.stat_rx_pkt_bad       := axis_xgmii_rx_inst.io.stat_rx_pkt_bad
        io.stat_rx_err_oversize  := axis_xgmii_rx_inst.io.stat_rx_err_oversize
        io.stat_rx_err_bad_fcs   := axis_xgmii_rx_inst.io.stat_rx_err_bad_fcs
        io.stat_rx_err_bad_block := axis_xgmii_rx_inst.io.stat_rx_err_bad_block
        io.stat_rx_err_framing   := axis_xgmii_rx_inst.io.stat_rx_err_framing
        io.stat_rx_err_preamble  := axis_xgmii_rx_inst.io.stat_rx_err_preamble
    }

    withClockAndReset(io.tx_clk, io.tx_rst) {
        val axis_xgmii_tx_inst = Module(new Axis2Xgmii32(dataW, ctrlW, txGbxIfEn, gbxCnt, paddingEn, dicEn, minFrameLen, ptpTsEn, ptpTsW, macCtrlEn))
        axis_xgmii_tx_inst.io.s_axis_tx        <> axis_tx_int
        io.m_axis_tx_cpl                       <> axis_xgmii_tx_inst.io.m_axis_tx_cpl
        
        io.xgmii_txd                           := axis_xgmii_tx_inst.io.xgmii_txd
        io.xgmii_txc                           := axis_xgmii_tx_inst.io.xgmii_txc
        io.xgmii_tx_valid                      := axis_xgmii_tx_inst.io.xgmii_tx_valid
        axis_xgmii_tx_inst.io.tx_gbx_req_sync  := io.tx_gbx_req_sync
        axis_xgmii_tx_inst.io.tx_gbx_req_stall := io.tx_gbx_req_stall
        io.tx_gbx_sync                         := axis_xgmii_tx_inst.io.tx_gbx_sync
        
        axis_xgmii_tx_inst.io.ptp_ts           := io.tx_ptp_ts
        axis_xgmii_tx_inst.io.cfg_tx_max_pkt_len := io.cfg_tx_max_pkt_len
        axis_xgmii_tx_inst.io.cfg_tx_ifg       := io.cfg_tx_ifg
        axis_xgmii_tx_inst.io.cfg_tx_enable    := io.cfg_tx_enable

        io.tx_start_packet       := Cat(0.U(1.W), axis_xgmii_tx_inst.io.tx_start_packet)
        io.stat_tx_byte          := Cat(0.U(1.W), axis_xgmii_tx_inst.io.stat_tx_byte)
        io.stat_tx_pkt_len       := axis_xgmii_tx_inst.io.stat_tx_pkt_len
        io.stat_tx_pkt_ucast     := axis_xgmii_tx_inst.io.stat_tx_pkt_ucast
        io.stat_tx_pkt_mcast     := axis_xgmii_tx_inst.io.stat_tx_pkt_mcast
        io.stat_tx_pkt_bcast     := axis_xgmii_tx_inst.io.stat_tx_pkt_bcast
        io.stat_tx_pkt_vlan      := axis_xgmii_tx_inst.io.stat_tx_pkt_vlan
        io.stat_tx_pkt_good      := axis_xgmii_tx_inst.io.stat_tx_pkt_good
        io.stat_tx_pkt_bad       := axis_xgmii_tx_inst.io.stat_tx_pkt_bad
        io.stat_tx_err_oversize  := axis_xgmii_tx_inst.io.stat_tx_err_oversize
        io.stat_tx_err_user      := axis_xgmii_tx_inst.io.stat_tx_err_user
        io.stat_tx_err_underflow := axis_xgmii_tx_inst.io.stat_tx_err_underflow
    }
  }

  // ---------------------------------------------------------------------------
  // Statistics Block Selection
  // ---------------------------------------------------------------------------
  if (statEn) {
    // val mac_stats_inst = Module(new EthMacStats(statTxLevel, statRxLevel, statIdBase, statUpdatePeriod, statStrEn, statPrefixStr, 4))
    // mac_stats_inst.io.rx_clk := io.rx_clk
    // mac_stats_inst.io.rx_rst := io.rx_rst
    // mac_stats_inst.io.tx_clk := io.tx_clk
    // mac_stats_inst.io.tx_rst := io.tx_rst

    // mac_stats_inst.io.stat_clk := io.stat_clk
    // mac_stats_inst.io.stat_rst := io.stat_rst
    // io.m_axis_stat <> mac_stats_inst.io.m_axis_stat

    // mac_stats_inst.io.tx_start_packet       := io.tx_start_packet.orR
    // mac_stats_inst.io.stat_tx_byte          := io.stat_tx_byte
    // mac_stats_inst.io.stat_tx_pkt_len       := io.stat_tx_pkt_len
    // mac_stats_inst.io.stat_tx_pkt_ucast     := io.stat_tx_pkt_ucast
    // mac_stats_inst.io.stat_tx_pkt_mcast     := io.stat_tx_pkt_mcast
    // mac_stats_inst.io.stat_tx_pkt_bcast     := io.stat_tx_pkt_bcast
    // mac_stats_inst.io.stat_tx_pkt_vlan      := io.stat_tx_pkt_vlan
    // mac_stats_inst.io.stat_tx_pkt_good      := io.stat_tx_pkt_good
    // mac_stats_inst.io.stat_tx_pkt_bad       := io.stat_tx_pkt_bad
    // mac_stats_inst.io.stat_tx_err_oversize  := io.stat_tx_err_oversize
    // mac_stats_inst.io.stat_tx_err_user      := io.stat_tx_err_user
    // mac_stats_inst.io.stat_tx_err_underflow := io.stat_tx_err_underflow
    
    // mac_stats_inst.io.rx_start_packet       := io.rx_start_packet.orR
    // mac_stats_inst.io.stat_rx_byte          := io.stat_rx_byte
    // mac_stats_inst.io.stat_rx_pkt_len       := io.stat_rx_pkt_len
    // mac_stats_inst.io.stat_rx_pkt_fragment  := io.stat_rx_pkt_fragment
    // mac_stats_inst.io.stat_rx_pkt_jabber    := io.stat_rx_pkt_jabber
    // mac_stats_inst.io.stat_rx_pkt_ucast     := io.stat_rx_pkt_ucast
    // mac_stats_inst.io.stat_rx_pkt_mcast     := io.stat_rx_pkt_mcast
    // mac_stats_inst.io.stat_rx_pkt_bcast     := io.stat_rx_pkt_bcast
    // mac_stats_inst.io.stat_rx_pkt_vlan      := io.stat_rx_pkt_vlan
    // mac_stats_inst.io.stat_rx_pkt_good      := io.stat_rx_pkt_good
    // mac_stats_inst.io.stat_rx_pkt_bad       := io.stat_rx_pkt_bad
    // mac_stats_inst.io.stat_rx_err_oversize  := io.stat_rx_err_oversize
    // mac_stats_inst.io.stat_rx_err_bad_fcs   := io.stat_rx_err_bad_fcs
    // mac_stats_inst.io.stat_rx_err_bad_block := io.stat_rx_err_bad_block
    // mac_stats_inst.io.stat_rx_err_framing   := io.stat_rx_err_framing
    // mac_stats_inst.io.stat_rx_err_preamble  := io.stat_rx_err_preamble
    // mac_stats_inst.io.stat_rx_fifo_drop     := io.stat_rx_fifo_drop

    // mac_stats_inst.io.stat_tx_mcf        := io.stat_tx_mcf
    // mac_stats_inst.io.stat_rx_mcf        := io.stat_rx_mcf
    // mac_stats_inst.io.stat_tx_lfc_pkt    := io.stat_tx_lfc_pkt
    // mac_stats_inst.io.stat_tx_lfc_xon    := io.stat_tx_lfc_xon
    // mac_stats_inst.io.stat_tx_lfc_xoff   := io.stat_tx_lfc_xoff
    // mac_stats_inst.io.stat_tx_lfc_paused := io.stat_tx_lfc_paused
    // mac_stats_inst.io.stat_tx_pfc_pkt    := io.stat_tx_pfc_pkt
    // mac_stats_inst.io.stat_tx_pfc_xon    := io.stat_tx_pfc_xon
    // mac_stats_inst.io.stat_tx_pfc_xoff   := io.stat_tx_pfc_xoff
    // mac_stats_inst.io.stat_tx_pfc_paused := io.stat_tx_pfc_paused
    // mac_stats_inst.io.stat_rx_lfc_pkt    := io.stat_rx_lfc_pkt
    // mac_stats_inst.io.stat_rx_lfc_xon    := io.stat_rx_lfc_xon
    // mac_stats_inst.io.stat_rx_lfc_xoff   := io.stat_rx_lfc_xoff
    // mac_stats_inst.io.stat_rx_lfc_paused := io.stat_rx_lfc_paused
    // mac_stats_inst.io.stat_rx_pfc_pkt    := io.stat_rx_pfc_pkt
    // mac_stats_inst.io.stat_rx_pfc_xon    := io.stat_rx_pfc_xon
    // mac_stats_inst.io.stat_rx_pfc_xoff   := io.stat_rx_pfc_xoff
    // mac_stats_inst.io.stat_rx_pfc_paused := io.stat_rx_pfc_paused
  } else {
    val null_src_inst = Module(new AxisNullSrc(dataW, txUserWInt, txTagW))
    io.m_axis_stat <> null_src_inst.io.m_axis
  }

  // ---------------------------------------------------------------------------
  // MAC Control Frame logic (LFC & PFC)
  // ---------------------------------------------------------------------------
  if (macCtrlEn) {
    // val mcfParamsSize = if (pfcEn) 18 else 2

    // // Internal Wires
    // val tx_mcf_valid    = Wire(Bool())
    // val tx_mcf_ready    = Wire(Bool())
    // val tx_mcf_eth_dst  = Wire(UInt(48.W))
    // val tx_mcf_eth_src  = Wire(UInt(48.W))
    // val tx_mcf_eth_type = Wire(UInt(16.W))
    // val tx_mcf_opcode   = Wire(UInt(16.W))
    // val tx_mcf_params   = Wire(UInt((mcfParamsSize * 8).W))

    // val rx_mcf_valid    = Wire(Bool())
    // val rx_mcf_eth_dst  = Wire(UInt(48.W))
    // val rx_mcf_eth_src  = Wire(UInt(48.W))
    // val rx_mcf_eth_type = Wire(UInt(16.W))
    // val rx_mcf_opcode   = Wire(UInt(16.W))
    // val rx_mcf_params   = Wire(UInt((mcfParamsSize * 8).W))

    // // CDC Syncing LFC requests
    // val rx_lfc_req_sync_inst = Module(new SyncSignal(1, 2))
    // rx_lfc_req_sync_inst.io.clk := io.tx_clk
    // rx_lfc_req_sync_inst.io.in  := io.rx_lfc_req
    // val rx_lfc_req_sync = rx_lfc_req_sync_inst.io.out(0)

    // val tx_pause_ack_sync_inst = Module(new SyncSignal(1, 2))
    // tx_pause_ack_sync_inst.io.clk := io.rx_clk
    // tx_pause_ack_sync_inst.io.in  := io.tx_lfc_pause_en && io.tx_pause_ack
    // val tx_pause_ack_sync = tx_pause_ack_sync_inst.io.out(0)

    // val tx_pause_req_int = io.tx_pause_req || (io.tx_lfc_pause_en && rx_lfc_req_sync)
    // val rx_lfc_ack_int   = io.rx_lfc_ack || tx_pause_ack_sync

    // // Transmit MAC Ctrl
    // val mac_ctrl_tx_inst = Module(new MacCtrlTx(txTagW, 8, txUserWInt, mcfParamsSize))
    // mac_ctrl_tx_inst.io.clk := io.tx_clk
    // mac_ctrl_tx_inst.io.rst := io.tx_rst
    // mac_ctrl_tx_inst.io.s_axis <> io.s_axis_tx
    // axis_tx_int <> mac_ctrl_tx_inst.io.m_axis

    // mac_ctrl_tx_inst.io.mcf_valid    := tx_mcf_valid
    // tx_mcf_ready                     := mac_ctrl_tx_inst.io.mcf_ready
    // mac_ctrl_tx_inst.io.mcf_eth_dst  := tx_mcf_eth_dst
    // mac_ctrl_tx_inst.io.mcf_eth_src  := tx_mcf_eth_src
    // mac_ctrl_tx_inst.io.mcf_eth_type := tx_mcf_eth_type
    // mac_ctrl_tx_inst.io.mcf_opcode   := tx_mcf_opcode
    // mac_ctrl_tx_inst.io.mcf_params   := tx_mcf_params
    // mac_ctrl_tx_inst.io.mcf_id       := 0.U
    // mac_ctrl_tx_inst.io.mcf_dest     := 0.U
    // mac_ctrl_tx_inst.io.mcf_user     := 2.U(2.W) // 2'b10

    // mac_ctrl_tx_inst.io.tx_pause_req := tx_pause_req_int
    // io.tx_pause_ack                  := mac_ctrl_tx_inst.io.tx_pause_ack
    // io.stat_tx_mcf                   := mac_ctrl_tx_inst.io.stat_tx_mcf

    // // Receive MAC Ctrl
    // val mac_ctrl_rx_inst = Module(new MacCtrlRx(rxUserW, 0, mcfParamsSize))
    // mac_ctrl_rx_inst.io.clk := io.rx_clk
    // mac_ctrl_rx_inst.io.rst := io.rx_rst
    // mac_ctrl_rx_inst.io.s_axis <> axis_rx_int
    // io.m_axis_rx <> mac_ctrl_rx_inst.io.m_axis

    // rx_mcf_valid    := mac_ctrl_rx_inst.io.mcf_valid
    // rx_mcf_eth_dst  := mac_ctrl_rx_inst.io.mcf_eth_dst
    // rx_mcf_eth_src  := mac_ctrl_rx_inst.io.mcf_eth_src
    // rx_mcf_eth_type := mac_ctrl_rx_inst.io.mcf_eth_type
    // rx_mcf_opcode   := mac_ctrl_rx_inst.io.mcf_opcode
    // rx_mcf_params   := mac_ctrl_rx_inst.io.mcf_params

    // mac_ctrl_rx_inst.io.cfg_mcf_rx_eth_dst_mcast       := io.cfg_mcf_rx_eth_dst_mcast
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_check_eth_dst_mcast := io.cfg_mcf_rx_check_eth_dst_mcast
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_eth_dst_ucast       := io.cfg_mcf_rx_eth_dst_ucast
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_check_eth_dst_ucast := io.cfg_mcf_rx_check_eth_dst_ucast
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_eth_src             := io.cfg_mcf_rx_eth_src
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_check_eth_src       := io.cfg_mcf_rx_check_eth_src
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_eth_type            := io.cfg_mcf_rx_eth_type
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_opcode_lfc          := io.cfg_mcf_rx_opcode_lfc
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_check_opcode_lfc    := io.cfg_mcf_rx_check_opcode_lfc
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_opcode_pfc          := io.cfg_mcf_rx_opcode_pfc
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_check_opcode_pfc    := io.cfg_mcf_rx_check_opcode_pfc && pfcEn.B
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_forward             := io.cfg_mcf_rx_forward
    // mac_ctrl_rx_inst.io.cfg_mcf_rx_enable              := io.cfg_mcf_rx_enable

    // io.stat_rx_mcf := mac_ctrl_rx_inst.io.stat_rx_mcf

    // // Pause Control TX
    // val mac_pause_ctrl_tx_inst = Module(new MacPauseCtrlTx(mcfParamsSize, pfcEn))
    // mac_pause_ctrl_tx_inst.io.clk := io.tx_clk
    // mac_pause_ctrl_tx_inst.io.rst := io.tx_rst
    
    // tx_mcf_valid := mac_pause_ctrl_tx_inst.io.mcf_valid
    // mac_pause_ctrl_tx_inst.io.mcf_ready := tx_mcf_ready
    // tx_mcf_eth_dst := mac_pause_ctrl_tx_inst.io.mcf_eth_dst
    // tx_mcf_eth_src := mac_pause_ctrl_tx_inst.io.mcf_eth_src
    // tx_mcf_eth_type := mac_pause_ctrl_tx_inst.io.mcf_eth_type
    // tx_mcf_opcode := mac_pause_ctrl_tx_inst.io.mcf_opcode
    // tx_mcf_params := mac_pause_ctrl_tx_inst.io.mcf_params

    // mac_pause_ctrl_tx_inst.io.tx_lfc_req := io.tx_lfc_req
    // mac_pause_ctrl_tx_inst.io.tx_lfc_resend := io.tx_lfc_resend
    // mac_pause_ctrl_tx_inst.io.tx_pfc_req := io.tx_pfc_req
    // mac_pause_ctrl_tx_inst.io.tx_pfc_resend := io.tx_pfc_resend

    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_eth_dst := io.cfg_tx_lfc_eth_dst
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_eth_src := io.cfg_tx_lfc_eth_src
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_eth_type := io.cfg_tx_lfc_eth_type
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_opcode := io.cfg_tx_lfc_opcode
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_en := io.cfg_tx_lfc_en
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_quanta := io.cfg_tx_lfc_quanta
    // mac_pause_ctrl_tx_inst.io.cfg_tx_lfc_refresh := io.cfg_tx_lfc_refresh

    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_eth_dst := io.cfg_tx_pfc_eth_dst
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_eth_src := io.cfg_tx_pfc_eth_src
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_eth_type := io.cfg_tx_pfc_eth_type
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_opcode := io.cfg_tx_pfc_opcode
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_en := io.cfg_tx_pfc_en
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_quanta := io.cfg_tx_pfc_quanta
    // mac_pause_ctrl_tx_inst.io.cfg_tx_pfc_refresh := io.cfg_tx_pfc_refresh
    
    // mac_pause_ctrl_tx_inst.io.cfg_quanta_step := ((dataW * 256) / 512).U(10.W)
    // mac_pause_ctrl_tx_inst.io.cfg_quanta_clk_en := (!txGbxIfEn.B) || io.xgmii_tx_valid

    // io.stat_tx_lfc_pkt    := mac_pause_ctrl_tx_inst.io.stat_tx_lfc_pkt
    // io.stat_tx_lfc_xon    := mac_pause_ctrl_tx_inst.io.stat_tx_lfc_xon
    // io.stat_tx_lfc_xoff   := mac_pause_ctrl_tx_inst.io.stat_tx_lfc_xoff
    // io.stat_tx_lfc_paused := mac_pause_ctrl_tx_inst.io.stat_tx_lfc_paused
    // io.stat_tx_pfc_pkt    := mac_pause_ctrl_tx_inst.io.stat_tx_pfc_pkt
    // io.stat_tx_pfc_xon    := mac_pause_ctrl_tx_inst.io.stat_tx_pfc_xon
    // io.stat_tx_pfc_xoff   := mac_pause_ctrl_tx_inst.io.stat_tx_pfc_xoff
    // io.stat_tx_pfc_paused := mac_pause_ctrl_tx_inst.io.stat_tx_pfc_paused

    // // Pause Control RX
    // val mac_pause_ctrl_rx_inst = Module(new MacPauseCtrlRx(18, pfcEn))
    // mac_pause_ctrl_rx_inst.io.clk := io.rx_clk
    // mac_pause_ctrl_rx_inst.io.rst := io.rx_rst
    // mac_pause_ctrl_rx_inst.io.mcf_valid := rx_mcf_valid
    // mac_pause_ctrl_rx_inst.io.mcf_eth_dst := rx_mcf_eth_dst
    // mac_pause_ctrl_rx_inst.io.mcf_eth_src := rx_mcf_eth_src
    // mac_pause_ctrl_rx_inst.io.mcf_eth_type := rx_mcf_eth_type
    // mac_pause_ctrl_rx_inst.io.mcf_opcode := rx_mcf_opcode
    // mac_pause_ctrl_rx_inst.io.mcf_params := rx_mcf_params

    // mac_pause_ctrl_rx_inst.io.rx_lfc_en := io.rx_lfc_en
    // io.rx_lfc_req := mac_pause_ctrl_rx_inst.io.rx_lfc_req
    // mac_pause_ctrl_rx_inst.io.rx_lfc_ack := rx_lfc_ack_int

    // mac_pause_ctrl_rx_inst.io.rx_pfc_en := io.rx_pfc_en
    // io.rx_pfc_req := mac_pause_ctrl_rx_inst.io.rx_pfc_req
    // mac_pause_ctrl_rx_inst.io.rx_pfc_ack := io.rx_pfc_ack

    // mac_pause_ctrl_rx_inst.io.cfg_rx_lfc_opcode := io.cfg_rx_lfc_opcode
    // mac_pause_ctrl_rx_inst.io.cfg_rx_lfc_en := io.cfg_rx_lfc_en
    // mac_pause_ctrl_rx_inst.io.cfg_rx_pfc_opcode := io.cfg_rx_pfc_opcode
    // mac_pause_ctrl_rx_inst.io.cfg_rx_pfc_en := io.cfg_rx_pfc_en
    // mac_pause_ctrl_rx_inst.io.cfg_quanta_step := ((dataW * 256) / 512).U(10.W)
    // mac_pause_ctrl_rx_inst.io.cfg_quanta_clk_en := (!rxGbxIfEn.B) || io.xgmii_rx_valid

    // io.stat_rx_lfc_pkt    := mac_pause_ctrl_rx_inst.io.stat_rx_lfc_pkt
    // io.stat_rx_lfc_xon    := mac_pause_ctrl_rx_inst.io.stat_rx_lfc_xon
    // io.stat_rx_lfc_xoff   := mac_pause_ctrl_rx_inst.io.stat_rx_lfc_xoff
    // io.stat_rx_lfc_paused := mac_pause_ctrl_rx_inst.io.stat_rx_lfc_paused
    // io.stat_rx_pfc_pkt    := mac_pause_ctrl_rx_inst.io.stat_rx_pfc_pkt
    // io.stat_rx_pfc_xon    := mac_pause_ctrl_rx_inst.io.stat_rx_pfc_xon
    // io.stat_rx_pfc_xoff   := mac_pause_ctrl_rx_inst.io.stat_rx_pfc_xoff
    // io.stat_rx_pfc_paused := mac_pause_ctrl_rx_inst.io.stat_rx_pfc_paused

  } else {
    // Tie off everything if MAC controls disabled
    val tx_tie_inst = Module(new AxisTie(dataW, txUserWInt, txTagW))
    tx_tie_inst.io.s_axis <> io.s_axis_tx
    axis_tx_int <> tx_tie_inst.io.m_axis

    val rx_tie_inst = Module(new AxisTie(dataW, rxUserW))
    rx_tie_inst.io.s_axis <> axis_rx_int
    io.m_axis_rx <> rx_tie_inst.io.m_axis

    io.rx_lfc_req         := false.B
    io.rx_pfc_req         := 0.U
    io.tx_pause_ack       := false.B

    io.stat_tx_mcf        := false.B
    io.stat_rx_mcf        := false.B
    io.stat_tx_lfc_pkt    := false.B
    io.stat_tx_lfc_xon    := false.B
    io.stat_tx_lfc_xoff   := false.B
    io.stat_tx_lfc_paused := false.B
    io.stat_tx_pfc_pkt    := false.B
    io.stat_tx_pfc_xon    := 0.U
    io.stat_tx_pfc_xoff   := 0.U
    io.stat_tx_pfc_paused := 0.U
    io.stat_rx_lfc_pkt    := false.B
    io.stat_rx_lfc_xon    := false.B
    io.stat_rx_lfc_xoff   := false.B
    io.stat_rx_lfc_paused := false.B
    io.stat_rx_pfc_pkt    := false.B
    io.stat_rx_pfc_xon    := 0.U
    io.stat_rx_pfc_xoff   := 0.U
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
  pauseEn = p.pauseEn,
  statEn = p.statEn,
  statTxLevel = p.statTxLevel,
  statRxLevel = p.statRxLevel,
  statIdBase  = p.statIdBase,
  statUpdatePeriod = p.statUpdatePeriod,
  statStrEn = p.statStrEn,
  statPrefixStr = p.statPrefixStr
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
        pauseEn = p.pauseEn,
        statEn = p.statEn,
        statTxLevel = p.statTxLevel,
        statRxLevel = p.statRxLevel,
        statIdBase  = p.statIdBase,
        statUpdatePeriod = p.statUpdatePeriod,
        statStrEn = p.statStrEn,
        statPrefixStr = p.statPrefixStr
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