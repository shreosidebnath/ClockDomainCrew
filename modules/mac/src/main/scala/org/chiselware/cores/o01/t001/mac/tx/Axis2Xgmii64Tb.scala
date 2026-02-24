package org.chiselware.cores.o01.t001.mac.tx
import chisel3._
import org.chiselware.cores.o01.t001.mac.{ AxisInterface, AxisInterfaceParams }

class Axis2Xgmii64Tb(val p: Axis2Xgmii64Params) extends Module {
  val userW =
    (if (p.ptpTsEn)
       p.ptpTsW
     else
       0) + 1
  val keepW = p.dataW / 8
  val txTagW = 8

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val sAxisTx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = p.dataW,
      keepW = keepW,
      idEn = true,
      idW = txTagW,
      userEn = true,
      userW = userW
    )))
    val mAxisTxCpl =
      new AxisInterface(AxisInterfaceParams(
        dataW = 96,
        keepW = 1,
        idW = 8
      ))
    val xgmiiTxd = Output(UInt(p.dataW.W))
    val xgmiiTxc = Output(UInt(p.ctrlW.W))
    val xgmiiTxValid = Output(Bool())
    val txGbxReqSync = Input(UInt(p.gbxCnt.W))
    val txGbxReqStall = Input(Bool())
    val txGbxSync = Output(UInt(p.gbxCnt.W))
    val ptpTs = Input(UInt(p.ptpTsW.W))
    val cfgTxMaxPktLen = Input(UInt(16.W))
    val cfgTxIfg = Input(UInt(8.W))
    val cfgTxEnable = Input(Bool())
    val txStartPacket = Output(UInt(2.W))
    val statTxByte = Output(UInt(4.W))
    val statTxPktLen = Output(UInt(16.W))
    val statTxPktUcast = Output(Bool())
    val statTxPktMcast = Output(Bool())
    val statTxPktBcast = Output(Bool())
    val statTxPktVlan = Output(Bool())
    val statTxPktGood = Output(Bool())
    val statTxPktBad = Output(Bool())
    val statTxErrOversize = Output(Bool())
    val statTxErrUser = Output(Bool())
    val statTxErrUnderflow = Output(Bool())
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
  io.sAxisTx <> dut.io.sAxisTx
  dut.io.mAxisTxCpl <> io.mAxisTxCpl
  io.xgmiiTxd := dut.io.xgmiiTxd
  io.xgmiiTxc := dut.io.xgmiiTxc
  io.xgmiiTxValid := dut.io.xgmiiTxValid
  dut.io.txGbxReqSync := io.txGbxReqSync
  dut.io.txGbxReqStall := io.txGbxReqStall
  io.txGbxSync := dut.io.txGbxSync
  dut.io.ptpTs := io.ptpTs
  dut.io.cfgTxMaxPktLen := io.cfgTxMaxPktLen
  dut.io.cfgTxIfg := io.cfgTxIfg
  dut.io.cfgTxEnable := io.cfgTxEnable
  io.txStartPacket := dut.io.txStartPacket
  io.statTxByte := dut.io.statTxByte
  io.statTxPktLen := dut.io.statTxPktLen
  io.statTxPktUcast := dut.io.statTxPktUcast
  io.statTxPktMcast := dut.io.statTxPktMcast
  io.statTxPktBcast := dut.io.statTxPktBcast
  io.statTxPktVlan := dut.io.statTxPktVlan
  io.statTxPktGood := dut.io.statTxPktGood
  io.statTxPktBad := dut.io.statTxPktBad
  io.statTxErrOversize := dut.io.statTxErrOversize
  io.statTxErrUser := dut.io.statTxErrUser
  io.statTxErrUnderflow := dut.io.statTxErrUnderflow
}