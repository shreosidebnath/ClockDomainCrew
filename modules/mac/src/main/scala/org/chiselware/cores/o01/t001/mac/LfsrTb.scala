package org.chiselware.cores.o01.t001.mac
import chisel3._

class LfsrTb(val p: LfsrParams) extends Module {
  val io = IO(new Bundle {
    val dataIn  = Input(UInt(p.dataW.W))
    val stateIn = Input(UInt(p.lfsrW.W))
    val dataOut  = Output(UInt(p.dataW.W))
    val stateOut = Output(UInt(p.lfsrW.W))
  })

  val dut = Module(new Lfsr(
    lfsrW           = p.lfsrW,
    lfsrPoly        = p.lfsrPoly,
    lfsrGalois      = p.lfsrGalois,
    lfsrFeedForward = p.lfsrFeedForward,
    reverse         = p.reverse,
    dataW           = p.dataW,
    dataInEn        = p.dataInEn,
    dataOutEn       = p.dataOutEn
  ))

  dut.io.dataIn  := io.dataIn
  dut.io.stateIn := io.stateIn
  io.dataOut  := dut.io.dataOut
  io.stateOut := dut.io.stateOut
}