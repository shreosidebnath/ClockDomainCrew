package org.chiselware.cores.o01.t001.mac.rx
import org.chiselware.cores.o01.t001.mac.AxisInterface
import org.chiselware.cores.o01.t001.mac.AxisInterfaceParams
import chisel3._
import chisel3.util._

class Xgmii2Axis64Tb(val p: Xgmii2Axis64Params) extends Module {
  val userW = (if (p.ptpTsEn) p.ptpTsW else 0) + 1
  val keepW = p.dataW / 8

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmii_rxd = Input(UInt(p.dataW.W))
    val xgmii_rxc = Input(UInt(p.ctrlW.W))
    val xgmii_rx_valid = Input(Bool())
    val m_axis_rx = new AxisInterface(AxisInterfaceParams(
      dataW = p.dataW, keepW = keepW, userEn = true, userW = userW
    ))
    val ptp_ts = Input(UInt(p.ptpTsW.W))
    val cfg_rx_max_pkt_len = Input(UInt(16.W))
    val cfg_rx_enable = Input(Bool())
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
  })

  val dut = Module(new Xgmii2Axis64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW
  ))

    dut.clock := io.clk
    dut.reset := io.rst
    dut.io.xgmii_rxd      := io.xgmii_rxd
    dut.io.xgmii_rxc      := io.xgmii_rxc
    dut.io.xgmii_rx_valid := io.xgmii_rx_valid
    io.m_axis_rx            <> dut.io.m_axis_rx
    dut.io.ptp_ts         := io.ptp_ts
    dut.io.cfg_rx_max_pkt_len := io.cfg_rx_max_pkt_len
    dut.io.cfg_rx_enable      := io.cfg_rx_enable
    io.rx_start_packet      := dut.io.rx_start_packet
    io.stat_rx_byte         := dut.io.stat_rx_byte
    io.stat_rx_pkt_len      := dut.io.stat_rx_pkt_len
    io.stat_rx_pkt_fragment := dut.io.stat_rx_pkt_fragment
    io.stat_rx_pkt_jabber   := dut.io.stat_rx_pkt_jabber
    io.stat_rx_pkt_ucast    := dut.io.stat_rx_pkt_ucast
    io.stat_rx_pkt_mcast    := dut.io.stat_rx_pkt_mcast
    io.stat_rx_pkt_bcast    := dut.io.stat_rx_pkt_bcast
    io.stat_rx_pkt_vlan     := dut.io.stat_rx_pkt_vlan
    io.stat_rx_pkt_good     := dut.io.stat_rx_pkt_good
    io.stat_rx_pkt_bad      := dut.io.stat_rx_pkt_bad
    io.stat_rx_err_oversize := dut.io.stat_rx_err_oversize
    io.stat_rx_err_bad_fcs  := dut.io.stat_rx_err_bad_fcs
    io.stat_rx_err_bad_block:= dut.io.stat_rx_err_bad_block
    io.stat_rx_err_framing  := dut.io.stat_rx_err_framing
    io.stat_rx_err_preamble := dut.io.stat_rx_err_preamble
}