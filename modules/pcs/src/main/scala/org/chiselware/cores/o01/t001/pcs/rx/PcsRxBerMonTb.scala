package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxBerMonTb(val p: PcsRxBerMonParams) extends Module {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val serdes_rx_hdr       = Input(UInt(p.hdrW.W))
        val serdes_rx_hdr_valid = Input(Bool())
        val rx_high_ber         = Output(Bool())
    })

    val dut = Module(new PcsRxBerMon(
        hdrW = p.hdrW,
        count125Us = p.count125Us
    ))

    dut.clock := io.clk
    dut.reset := io.rst
    dut.io.serdes_rx_hdr := io.serdes_rx_hdr
    dut.io.serdes_rx_hdr_valid := io.serdes_rx_hdr_valid
    io.rx_high_ber := dut.io.rx_high_ber
}