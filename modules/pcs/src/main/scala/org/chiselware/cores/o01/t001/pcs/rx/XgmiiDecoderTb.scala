package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

class XgmiiDecoderTb(val p: XgmiiDecoderParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val encodedRxData = Input(UInt(p.dataW.W))
    val encodedRxDataValid = Input(Bool())
    val encodedRxHdr = Input(UInt(p.hdrW.W))
    val encodedRxHdrValid = Input(Bool())
    val xgmiiRxd = Output(UInt(p.dataW.W))
    val xgmiiRxc = Output(UInt(p.ctrlW.W))
    val xgmiiRxValid = Output(Bool())
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
  })

  val dut = Module(new XgmiiDecoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.encodedRxData := io.encodedRxData
  dut.io.encodedRxDataValid := io.encodedRxDataValid
  dut.io.encodedRxHdr := io.encodedRxHdr
  dut.io.encodedRxHdrValid := io.encodedRxHdrValid

  io.xgmiiRxd := dut.io.xgmiiRxd
  io.xgmiiRxc := dut.io.xgmiiRxc
  io.xgmiiRxValid := dut.io.xgmiiRxValid
  io.rxBadBlock := dut.io.rxBadBlock
  io.rxSequenceError := dut.io.rxSequenceError
}