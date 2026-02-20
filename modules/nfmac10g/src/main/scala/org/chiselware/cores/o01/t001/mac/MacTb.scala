package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._

class MacTb(val p: MacParams) extends Module {
    val txUserW = 1
    val rxUserW = (if (p.ptpTsEn) p.ptpTsW else 0) + 1
    val txTagW = 8 // Defaulting ID_W to 8 based on standard usage
    val macCtrlEn = p.pauseEn || p.pfcEn
    val txUserWInt = (if (macCtrlEn) 1 else 0) + txUserW

  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())
    val s_axis_tx     = Flipped(new AxisInterface(p.dataW, txUserWInt, txTagW))
    val m_axis_tx_cpl = new AxisInterface(p.dataW, txUserWInt, txTagW)
    val m_axis_rx     = new AxisInterface(p.dataW, rxUserW)
    val xgmii_rxd      = Input(UInt(p.dataW.W))
    val xgmii_rxc      = Input(UInt(p.ctrlW.W))
    val xgmii_rx_valid = Input(Bool())
    val xgmii_txd      = Output(UInt(p.dataW.W))
    val xgmii_txc      = Output(UInt(p.ctrlW.W))
    val xgmii_tx_valid = Output(Bool())
    val tx_gbx_req_sync  = Input(UInt(p.gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync      = Output(UInt(p.gbxCnt.W))
    val tx_ptp_ts = Input(UInt(p.ptpTsW.W))
    val rx_ptp_ts = Input(UInt(p.ptpTsW.W))
    val tx_lfc_req    = Input(Bool())
    val tx_lfc_resend = Input(Bool())
    val rx_lfc_en     = Input(Bool())
    val rx_lfc_req    = Output(Bool())
    val rx_lfc_ack    = Input(Bool())
    val tx_pfc_req    = Input(UInt(8.W))
    val tx_pfc_resend = Input(Bool())
    val rx_pfc_en     = Input(UInt(8.W))
    val rx_pfc_req    = Output(UInt(8.W))
    val rx_pfc_ack    = Input(UInt(8.W))
    val tx_lfc_pause_en = Input(Bool())
    val tx_pause_req    = Input(Bool())
    val tx_pause_ack    = Output(Bool())
    val stat_clk = Input(Clock())
    val stat_rst = Input(Bool())
    val m_axis_stat = new AxisInterface(p.dataW, txUserWInt, txTagW)
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

  val dut = Module(new Mac(
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

// Clocks & Resets
  dut.io.rx_clk := io.rx_clk
  dut.io.rx_rst := io.rx_rst
  dut.io.tx_clk := io.tx_clk
  dut.io.tx_rst := io.tx_rst

  // AXI Stream Interfaces (Bulk Connections)
  dut.io.s_axis_tx     <> io.s_axis_tx
  io.m_axis_tx_cpl     <> dut.io.m_axis_tx_cpl
  io.m_axis_rx         <> dut.io.m_axis_rx

  // XGMII
  dut.io.xgmii_rxd         := io.xgmii_rxd
  dut.io.xgmii_rxc         := io.xgmii_rxc
  dut.io.xgmii_rx_valid    := io.xgmii_rx_valid
  io.xgmii_txd             := dut.io.xgmii_txd
  io.xgmii_txc             := dut.io.xgmii_txc
  io.xgmii_tx_valid        := dut.io.xgmii_tx_valid
  dut.io.tx_gbx_req_sync   := io.tx_gbx_req_sync
  dut.io.tx_gbx_req_stall  := io.tx_gbx_req_stall
  io.tx_gbx_sync           := dut.io.tx_gbx_sync

  // PTP
  dut.io.tx_ptp_ts := io.tx_ptp_ts
  dut.io.rx_ptp_ts := io.rx_ptp_ts

  // Link-level Flow Control (LFC)
  dut.io.tx_lfc_req    := io.tx_lfc_req
  dut.io.tx_lfc_resend := io.tx_lfc_resend
  dut.io.rx_lfc_en     := io.rx_lfc_en
  io.rx_lfc_req        := dut.io.rx_lfc_req
  dut.io.rx_lfc_ack    := io.rx_lfc_ack

  // Priority Flow Control (PFC)
  dut.io.tx_pfc_req    := io.tx_pfc_req
  dut.io.tx_pfc_resend := io.tx_pfc_resend
  dut.io.rx_pfc_en     := io.rx_pfc_en
  io.rx_pfc_req        := dut.io.rx_pfc_req
  dut.io.rx_pfc_ack    := io.rx_pfc_ack

  // Pause Interface
  dut.io.tx_lfc_pause_en := io.tx_lfc_pause_en
  dut.io.tx_pause_req    := io.tx_pause_req
  io.tx_pause_ack        := dut.io.tx_pause_ack

  // Statistics
  dut.io.stat_clk := io.stat_clk
  dut.io.stat_rst := io.stat_rst
  io.m_axis_stat  <> dut.io.m_axis_stat

  // Status Output Routing
  io.tx_start_packet       := dut.io.tx_start_packet
  io.stat_tx_byte          := dut.io.stat_tx_byte
  io.stat_tx_pkt_len       := dut.io.stat_tx_pkt_len
  io.stat_tx_pkt_ucast     := dut.io.stat_tx_pkt_ucast
  io.stat_tx_pkt_mcast     := dut.io.stat_tx_pkt_mcast
  io.stat_tx_pkt_bcast     := dut.io.stat_tx_pkt_bcast
  io.stat_tx_pkt_vlan      := dut.io.stat_tx_pkt_vlan
  io.stat_tx_pkt_good      := dut.io.stat_tx_pkt_good
  io.stat_tx_pkt_bad       := dut.io.stat_tx_pkt_bad
  io.stat_tx_err_oversize  := dut.io.stat_tx_err_oversize
  io.stat_tx_err_user      := dut.io.stat_tx_err_user
  io.stat_tx_err_underflow := dut.io.stat_tx_err_underflow

  io.rx_start_packet       := dut.io.rx_start_packet
  io.stat_rx_byte          := dut.io.stat_rx_byte
  io.stat_rx_pkt_len       := dut.io.stat_rx_pkt_len
  io.stat_rx_pkt_fragment  := dut.io.stat_rx_pkt_fragment
  io.stat_rx_pkt_jabber    := dut.io.stat_rx_pkt_jabber
  io.stat_rx_pkt_ucast     := dut.io.stat_rx_pkt_ucast
  io.stat_rx_pkt_mcast     := dut.io.stat_rx_pkt_mcast
  io.stat_rx_pkt_bcast     := dut.io.stat_rx_pkt_bcast
  io.stat_rx_pkt_vlan      := dut.io.stat_rx_pkt_vlan
  io.stat_rx_pkt_good      := dut.io.stat_rx_pkt_good
  io.stat_rx_pkt_bad       := dut.io.stat_rx_pkt_bad
  io.stat_rx_err_oversize  := dut.io.stat_rx_err_oversize
  io.stat_rx_err_bad_fcs   := dut.io.stat_rx_err_bad_fcs
  io.stat_rx_err_bad_block := dut.io.stat_rx_err_bad_block
  io.stat_rx_err_framing   := dut.io.stat_rx_err_framing
  io.stat_rx_err_preamble  := dut.io.stat_rx_err_preamble
  
  dut.io.stat_rx_fifo_drop := io.stat_rx_fifo_drop

  io.stat_tx_mcf           := dut.io.stat_tx_mcf
  io.stat_rx_mcf           := dut.io.stat_rx_mcf
  io.stat_tx_lfc_pkt       := dut.io.stat_tx_lfc_pkt
  io.stat_tx_lfc_xon       := dut.io.stat_tx_lfc_xon
  io.stat_tx_lfc_xoff      := dut.io.stat_tx_lfc_xoff
  io.stat_tx_lfc_paused    := dut.io.stat_tx_lfc_paused
  io.stat_tx_pfc_pkt       := dut.io.stat_tx_pfc_pkt
  io.stat_tx_pfc_xon       := dut.io.stat_tx_pfc_xon
  io.stat_tx_pfc_xoff      := dut.io.stat_tx_pfc_xoff
  io.stat_tx_pfc_paused    := dut.io.stat_tx_pfc_paused
  
  io.stat_rx_lfc_pkt       := dut.io.stat_rx_lfc_pkt
  io.stat_rx_lfc_xon       := dut.io.stat_rx_lfc_xon
  io.stat_rx_lfc_xoff      := dut.io.stat_rx_lfc_xoff
  io.stat_rx_lfc_paused    := dut.io.stat_rx_lfc_paused
  io.stat_rx_pfc_pkt       := dut.io.stat_rx_pfc_pkt
  io.stat_rx_pfc_xon       := dut.io.stat_rx_pfc_xon
  io.stat_rx_pfc_xoff      := dut.io.stat_rx_pfc_xoff
  io.stat_rx_pfc_paused    := dut.io.stat_rx_pfc_paused

  // Configuration Routing
  dut.io.cfg_tx_max_pkt_len             := io.cfg_tx_max_pkt_len
  dut.io.cfg_tx_ifg                     := io.cfg_tx_ifg
  dut.io.cfg_tx_enable                  := io.cfg_tx_enable
  dut.io.cfg_rx_max_pkt_len             := io.cfg_rx_max_pkt_len
  dut.io.cfg_rx_enable                  := io.cfg_rx_enable
  dut.io.cfg_mcf_rx_eth_dst_mcast       := io.cfg_mcf_rx_eth_dst_mcast
  dut.io.cfg_mcf_rx_check_eth_dst_mcast := io.cfg_mcf_rx_check_eth_dst_mcast
  dut.io.cfg_mcf_rx_eth_dst_ucast       := io.cfg_mcf_rx_eth_dst_ucast
  dut.io.cfg_mcf_rx_check_eth_dst_ucast := io.cfg_mcf_rx_check_eth_dst_ucast
  dut.io.cfg_mcf_rx_eth_src             := io.cfg_mcf_rx_eth_src
  dut.io.cfg_mcf_rx_check_eth_src       := io.cfg_mcf_rx_check_eth_src
  dut.io.cfg_mcf_rx_eth_type            := io.cfg_mcf_rx_eth_type
  dut.io.cfg_mcf_rx_opcode_lfc          := io.cfg_mcf_rx_opcode_lfc
  dut.io.cfg_mcf_rx_check_opcode_lfc    := io.cfg_mcf_rx_check_opcode_lfc
  dut.io.cfg_mcf_rx_opcode_pfc          := io.cfg_mcf_rx_opcode_pfc
  dut.io.cfg_mcf_rx_check_opcode_pfc    := io.cfg_mcf_rx_check_opcode_pfc
  dut.io.cfg_mcf_rx_forward             := io.cfg_mcf_rx_forward
  dut.io.cfg_mcf_rx_enable              := io.cfg_mcf_rx_enable
  dut.io.cfg_tx_lfc_eth_dst             := io.cfg_tx_lfc_eth_dst
  dut.io.cfg_tx_lfc_eth_src             := io.cfg_tx_lfc_eth_src
  dut.io.cfg_tx_lfc_eth_type            := io.cfg_tx_lfc_eth_type
  dut.io.cfg_tx_lfc_opcode              := io.cfg_tx_lfc_opcode
  dut.io.cfg_tx_lfc_en                  := io.cfg_tx_lfc_en
  dut.io.cfg_tx_lfc_quanta              := io.cfg_tx_lfc_quanta
  dut.io.cfg_tx_lfc_refresh             := io.cfg_tx_lfc_refresh
  dut.io.cfg_tx_pfc_eth_dst             := io.cfg_tx_pfc_eth_dst
  dut.io.cfg_tx_pfc_eth_src             := io.cfg_tx_pfc_eth_src
  dut.io.cfg_tx_pfc_eth_type            := io.cfg_tx_pfc_eth_type
  dut.io.cfg_tx_pfc_opcode              := io.cfg_tx_pfc_opcode
  dut.io.cfg_tx_pfc_en                  := io.cfg_tx_pfc_en
  dut.io.cfg_tx_pfc_quanta              := io.cfg_tx_pfc_quanta
  dut.io.cfg_tx_pfc_refresh             := io.cfg_tx_pfc_refresh
  dut.io.cfg_rx_lfc_opcode              := io.cfg_rx_lfc_opcode
  dut.io.cfg_rx_lfc_en                  := io.cfg_rx_lfc_en
  dut.io.cfg_rx_pfc_opcode              := io.cfg_rx_pfc_opcode
  dut.io.cfg_rx_pfc_en                  := io.cfg_rx_pfc_en
}