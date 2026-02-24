package org.chiselware.cores.o01.t001.mac.rx
import chisel3._
import org.chiselware.cores.o01.t001.mac.{ AxisInterface, AxisInterfaceParams }

class Xgmii2Axis64Tb(val p: Xgmii2Axis64Params) extends Module {
  val userW =
    (if (p.ptpTsEn)
       p.ptpTsW
     else
       0) + 1
  val keepW = p.dataW / 8

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmiiRxd = Input(UInt(p.dataW.W))
    val xgmiiRxc = Input(UInt(p.ctrlW.W))
    val xgmiiRxValid = Input(Bool())
    val mAxisRx =
      new AxisInterface(AxisInterfaceParams(
        dataW = p.dataW,
        keepW = keepW,
        userEn = true,
        userW = userW
      ))
    val ptpTs = Input(UInt(p.ptpTsW.W))
    val cfgRxMaxPktLen = Input(UInt(16.W))
    val cfgRxEnable = Input(Bool())
    val rxStartPacket = Output(UInt(2.W))
    val statRxByte = Output(UInt(4.W))
    val statRxPktLen = Output(UInt(16.W))
    val statRxPktFragment = Output(Bool())
    val statRxPktJabber = Output(Bool())
    val statRxPktUcast = Output(Bool())
    val statRxPktMcast = Output(Bool())
    val statRxPktBcast = Output(Bool())
    val statRxPktVlan = Output(Bool())
    val statRxPktGood = Output(Bool())
    val statRxPktBad = Output(Bool())
    val statRxErrOversize = Output(Bool())
    val statRxErrBadFcs = Output(Bool())
    val statRxErrBadBlock = Output(Bool())
    val statRxErrFraming = Output(Bool())
    val statRxErrPreamble = Output(Bool())
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
  dut.io.xgmiiRxd := io.xgmiiRxd
  dut.io.xgmiiRxc := io.xgmiiRxc
  dut.io.xgmiiRxValid := io.xgmiiRxValid
  io.mAxisRx <> dut.io.mAxisRx
  dut.io.ptpTs := io.ptpTs
  dut.io.cfgRxMaxPktLen := io.cfgRxMaxPktLen
  dut.io.cfgRxEnable := io.cfgRxEnable
  io.rxStartPacket := dut.io.rxStartPacket
  io.statRxByte := dut.io.statRxByte
  io.statRxPktLen := dut.io.statRxPktLen
  io.statRxPktFragment := dut.io.statRxPktFragment
  io.statRxPktJabber := dut.io.statRxPktJabber
  io.statRxPktUcast := dut.io.statRxPktUcast
  io.statRxPktMcast := dut.io.statRxPktMcast
  io.statRxPktBcast := dut.io.statRxPktBcast
  io.statRxPktVlan := dut.io.statRxPktVlan
  io.statRxPktGood := dut.io.statRxPktGood
  io.statRxPktBad := dut.io.statRxPktBad
  io.statRxErrOversize := dut.io.statRxErrOversize
  io.statRxErrBadFcs := dut.io.statRxErrBadFcs
  io.statRxErrBadBlock := dut.io.statRxErrBadBlock
  io.statRxErrFraming := dut.io.statRxErrFraming
  io.statRxErrPreamble := dut.io.statRxErrPreamble
}