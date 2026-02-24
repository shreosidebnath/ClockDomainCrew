package org.chiselware.cores.o01.t001.mac

import chisel3._

class DualWrapperMac extends Module {
  val DATA_W = 64
  val CTRL_W = DATA_W / 8
  val USER_W = 1
  val ID_W = 8

  val io = IO(new Bundle {
    // Drive XGMII RX into both DUTs
    val xgmii_rxd = Input(UInt(DATA_W.W))
    val xgmii_rxc = Input(UInt(CTRL_W.W))
    val xgmii_rx_valid = Input(Bool())

    // Ready for both RX outputs
    val rx_ready = Input(Bool())

    // For oversize test
    val cfg_rx_max_pkt_len = Input(UInt(16.W))

    // Compare outputs: CHISEL vs VERILOG (golden)
    val chisel_rx_tdata = Output(UInt(DATA_W.W))
    val chisel_rx_tkeep = Output(UInt(CTRL_W.W))
    val chisel_rx_tvalid = Output(Bool())
    val chisel_rx_tlast = Output(Bool())
    val chisel_rx_tuser = Output(UInt(USER_W.W))
    val chisel_rx_tid = Output(UInt(ID_W.W))
    // for negative testing
    val chisel_stat_rx_pkt_good = Output(Bool())
    val chisel_stat_rx_pkt_bad = Output(Bool())
    val chisel_stat_rx_err_bad_fcs = Output(Bool())
    val chisel_stat_rx_err_preamble = Output(Bool())
    val chisel_stat_rx_err_framing = Output(Bool())
    val chisel_stat_rx_err_oversize = Output(Bool())
    val chisel_stat_rx_pkt_fragment = Output(Bool())

    val verilog_rx_tdata = Output(UInt(DATA_W.W))
    val verilog_rx_tkeep = Output(UInt(CTRL_W.W))
    val verilog_rx_tvalid = Output(Bool())
    val verilog_rx_tlast = Output(Bool())
    val verilog_rx_tuser = Output(UInt(USER_W.W))
    val verilog_rx_tid = Output(UInt(ID_W.W))
    // for negative testing
    val verilog_stat_rx_pkt_good = Output(Bool())
    val verilog_stat_rx_pkt_bad = Output(Bool())
    val verilog_stat_rx_err_bad_fcs = Output(Bool())
    val verilog_stat_rx_err_preamble = Output(Bool())
    val verilog_stat_rx_err_framing = Output(Bool())
    val verilog_stat_rx_err_oversize = Output(Bool())
    val verilog_stat_rx_pkt_fragment = Output(Bool())
  })

  // Instantiate both versions (for now both are the BB)
  val bbParams = MacBbParams()
  val chiselDut = Module(new MacBb(bbParams))
  val origDut = Module(new MacBb(bbParams))

  // Common clocks/resets
  for (d <- Seq(chiselDut, origDut)) {
    d.io.rx_clk := clock
    d.io.tx_clk := clock
    d.io.rx_rst := reset.asBool
    d.io.tx_rst := reset.asBool

    // Minimal configs
    d.io.cfg_tx_max_pkt_len := 1518.U
    d.io.cfg_tx_ifg := 12.U
    d.io.cfg_tx_enable := true.B
    d.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
    d.io.cfg_rx_enable := true.B

    // Tie TX side off (RX-only test)
    d.io.s_axis_tx_tdata := 0.U
    d.io.s_axis_tx_tkeep := 0.U
    d.io.s_axis_tx_tvalid := false.B
    d.io.s_axis_tx_tlast := false.B
    d.io.s_axis_tx_tuser := 0.U
    d.io.s_axis_tx_tid := 0.U

    // Always accept TX completion (even though we’re not driving TX)
    d.io.m_axis_tx_cpl_tready := true.B

    // Misc inputs
    d.io.tx_gbx_req_sync := 0.U
    d.io.tx_gbx_req_stall := false.B

    d.io.tx_ptp_ts := 0.U
    d.io.rx_ptp_ts := 0.U
  }

  // Drive XGMII RX into both
  chiselDut.io.xgmii_rxd := io.xgmii_rxd
  chiselDut.io.xgmii_rxc := io.xgmii_rxc
  chiselDut.io.xgmii_rx_valid := io.xgmii_rx_valid

  origDut.io.xgmii_rxd := io.xgmii_rxd
  origDut.io.xgmii_rxc := io.xgmii_rxc
  origDut.io.xgmii_rx_valid := io.xgmii_rx_valid

  // Ready for RX stream
  chiselDut.io.m_axis_rx_tready := io.rx_ready
  origDut.io.m_axis_rx_tready := io.rx_ready

  // Export both RX outputs
  io.chisel_rx_tdata := chiselDut.io.m_axis_rx_tdata
  io.chisel_rx_tkeep := chiselDut.io.m_axis_rx_tkeep
  io.chisel_rx_tvalid := chiselDut.io.m_axis_rx_tvalid
  io.chisel_rx_tlast := chiselDut.io.m_axis_rx_tlast
  io.chisel_rx_tuser := chiselDut.io.m_axis_rx_tuser
  io.chisel_rx_tid := chiselDut.io.m_axis_rx_tid

  io.verilog_rx_tdata := origDut.io.m_axis_rx_tdata
  io.verilog_rx_tkeep := origDut.io.m_axis_rx_tkeep
  io.verilog_rx_tvalid := origDut.io.m_axis_rx_tvalid
  io.verilog_rx_tlast := origDut.io.m_axis_rx_tlast
  io.verilog_rx_tuser := origDut.io.m_axis_rx_tuser
  io.verilog_rx_tid := origDut.io.m_axis_rx_tid

  // Export RX status (Chisel DUT)
  io.chisel_stat_rx_pkt_good := chiselDut.io.stat_rx_pkt_good
  io.chisel_stat_rx_pkt_fragment := chiselDut.io.stat_rx_pkt_fragment
  io.chisel_stat_rx_pkt_bad := chiselDut.io.stat_rx_pkt_bad
  io.chisel_stat_rx_err_bad_fcs := chiselDut.io.stat_rx_err_bad_fcs
  io.chisel_stat_rx_err_preamble := chiselDut.io.stat_rx_err_preamble
  io.chisel_stat_rx_err_framing := chiselDut.io.stat_rx_err_framing
  io.chisel_stat_rx_err_oversize := chiselDut.io.stat_rx_err_oversize

  // Export RX status (Golden / Verilog DUT)
  io.verilog_stat_rx_pkt_good := origDut.io.stat_rx_pkt_good
  io.verilog_stat_rx_pkt_fragment := origDut.io.stat_rx_pkt_fragment
  io.verilog_stat_rx_pkt_bad := origDut.io.stat_rx_pkt_bad
  io.verilog_stat_rx_err_bad_fcs := origDut.io.stat_rx_err_bad_fcs
  io.verilog_stat_rx_err_preamble := origDut.io.stat_rx_err_preamble
  io.verilog_stat_rx_err_framing := origDut.io.stat_rx_err_framing
  io.verilog_stat_rx_err_oversize := origDut.io.stat_rx_err_oversize
}
