package org.chiselware.cores.o01.t001.mac.tx
import org.chiselware.cores.o01.t001.mac.AxisInterface
import org.chiselware.cores.o01.t001.mac.AxisInterfaceParams
import chisel3._
import chisel3.util._

class Axis2Xgmii64Tb(val p: Axis2Xgmii64Params) extends Module {
  val userW = (if (p.ptpTsEn) p.ptpTsW else 0) + 1
  val keepW = p.dataW / 8
  val txTagW = 8 

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val s_axis_tx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = p.dataW, keepW = keepW, idEn = true, idW = txTagW, userEn = true, userW = userW
    )))
    val m_axis_tx_cpl = new AxisInterface(AxisInterfaceParams(
      dataW = 96, keepW = 1, idW = 8
    ))
    val xgmii_txd = Output(UInt(p.dataW.W))
    val xgmii_txc = Output(UInt(p.ctrlW.W))
    val xgmii_tx_valid = Output(Bool())
    val tx_gbx_req_sync = Input(UInt(p.gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync = Output(UInt(p.gbxCnt.W))
    val ptp_ts = Input(UInt(p.ptpTsW.W))
    val cfg_tx_max_pkt_len = Input(UInt(16.W))
    val cfg_tx_ifg = Input(UInt(8.W))
    val cfg_tx_enable = Input(Bool())
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
  })

  val dut = Module(new Axis2Xgmii64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW,
    txCplCtrlInTuser = p.txCplCtrlInTuser
  ))

    dut.clock := io.clk
    dut.reset := io.rst
    io.s_axis_tx <> dut.io.s_axis_tx
    dut.io.m_axis_tx_cpl <> io.m_axis_tx_cpl
    io.xgmii_txd := dut.io.xgmii_txd
    io.xgmii_txc := dut.io.xgmii_txc
    io.xgmii_tx_valid := dut.io.xgmii_tx_valid
    dut.io.tx_gbx_req_sync := io.tx_gbx_req_sync
    dut.io.tx_gbx_req_stall := io.tx_gbx_req_stall
    io.tx_gbx_sync := dut.io.tx_gbx_sync
    dut.io.ptp_ts := io.ptp_ts
    dut.io.cfg_tx_max_pkt_len := io.cfg_tx_max_pkt_len
    dut.io.cfg_tx_ifg := io.cfg_tx_ifg
    dut.io.cfg_tx_enable := io.cfg_tx_enable
    io.tx_start_packet := dut.io.tx_start_packet
    io.stat_tx_byte := dut.io.stat_tx_byte
    io.stat_tx_pkt_len := dut.io.stat_tx_pkt_len
    io.stat_tx_pkt_ucast := dut.io.stat_tx_pkt_ucast
    io.stat_tx_pkt_mcast := dut.io.stat_tx_pkt_mcast
    io.stat_tx_pkt_bcast := dut.io.stat_tx_pkt_bcast
    io.stat_tx_pkt_vlan := dut.io.stat_tx_pkt_vlan
    io.stat_tx_pkt_good := dut.io.stat_tx_pkt_good
    io.stat_tx_pkt_bad := dut.io.stat_tx_pkt_bad
    io.stat_tx_err_oversize := dut.io.stat_tx_err_oversize
    io.stat_tx_err_user := dut.io.stat_tx_err_user
    io.stat_tx_err_underflow := dut.io.stat_tx_err_underflow
}