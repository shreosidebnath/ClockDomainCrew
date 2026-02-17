package org.chiselware.cores.o01.t001.mac.tx
import org.chiselware.cores.o01.t001.mac.AxisInterface
import chisel3._
import chisel3.util._

class Axis2Xgmii32Tb(val p: Axis2Xgmii32Params) extends Module {
  val userW = (if (p.ptpTsEn) p.ptpTsW else 0) + 1
  val cplDataW = if (p.ptpTsEn) p.ptpTsW else p.dataW

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val s_axis_tx = Flipped(new AxisInterface(p.dataW, userW, p.idW))
    val m_axis_tx_cpl = new AxisInterface(cplDataW, 1, p.idW)
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
    val tx_start_packet = Output(Bool())
    val stat_tx_byte = Output(UInt(3.W))
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

  val dut = Module(new Axis2Xgmii32(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsW = p.ptpTsW,
    txCplCtrlInTuser = p.txCplCtrlInTuser,
    idW = p.idW
  ))

    dut.clock := io.clk
    dut.reset := io.rst
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