package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

class PcsRxWatchdogTb(val p: PcsRxWatchdogParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxResetReq = Output(Bool())
    val rxBadBlock = Input(Bool())
    val rxSequenceError = Input(Bool())
    val rxBlockLock = Input(Bool())
    val rxHighBer = Input(Bool())
    val rxStatus = Output(Bool())
  })

  val dut = Module(new PcsRxWatchdog(
    hdrW = p.hdrW,
    count125us = p.count125us
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.serdesRxHdr := io.serdesRxHdr
  dut.io.serdesRxHdrValid := io.serdesRxHdrValid
  io.serdesRxResetReq := dut.io.serdesRxResetReq
  dut.io.rxBadBlock := io.rxBadBlock
  dut.io.rxSequenceError := io.rxSequenceError
  dut.io.rxBlockLock := io.rxBlockLock
  dut.io.rxHighBer := io.rxHighBer
  io.rxStatus := dut.io.rxStatus
}
