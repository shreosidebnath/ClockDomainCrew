package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxFrameSyncTb(val p: PcsRxFrameSyncParams) extends Module {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val serdesRxHdr       = Input(UInt(p.hdrW.W))
        val serdesRxHdrValid  = Input(Bool())
        val serdesRxBitslip   = Output(Bool())
        val rxBlockLock       = Output(Bool())
    })

    val dut = Module(new PcsRxFrameSync(
        hdrW = p.hdrW,
        bitslipHighCycles = p.bitslipHighCycles,
        bitslipLowCycles = p.bitslipLowCycles
    ))

    dut.clock := io.clk
    dut.reset := io.rst
    dut.io.serdesRxHdr := io.serdesRxHdr
    dut.io.serdesRxHdrValid := io.serdesRxHdrValid
    io.serdesRxBitslip := dut.io.serdesRxBitslip
    io.rxBlockLock := dut.io.rxBlockLock
}