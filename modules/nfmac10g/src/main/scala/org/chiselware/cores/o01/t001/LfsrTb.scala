package org.chiselware.cores.o01.t001
import chisel3._
import chisel3.util._

class LfsrTb(val p: LfsrParams) extends Module {
  val io = IO(new Bundle {
    val data_in   = Input(UInt(p.dataW.W))
    val state_in  = Input(UInt(p.lfsrW.W))
    val data_out  = Output(UInt(p.dataW.W))
    val state_out = Output(UInt(p.lfsrW.W))
  })

  val dut = Module(new Lfsr(
    lfsrW = p.lfsrW,
    lfsrPoly = p.lfsrPoly,
    lfsrGalois = p.lfsrGalois,
    lfsrFeedForward = p.lfsrFeedForward,
    reverse = p.reverse,
    dataW = p.dataW,
    dataInEn = p.dataInEn,
    dataOutEn = p.dataOutEn
  ))

  dut.io.data_in   := io.data_in
  dut.io.state_in  := io.state_in
  io.data_out   := dut.io.data_out
  io.state_out  := dut.io.state_out
}