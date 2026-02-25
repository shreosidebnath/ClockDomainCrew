package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

class PcsRxBerMonTb(val p: PcsRxBerMonParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val rxHighBer = Output(Bool())
  })

  val dut = Module(new PcsRxBerMon(
    hdrW = p.hdrW,
    count125Us = p.count125Us
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.serdesRxHdr := io.serdesRxHdr
  dut.io.serdesRxHdrValid := io.serdesRxHdrValid
  io.rxHighBer := dut.io.rxHighBer
}
