package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxWatchdogTb(val p: PcsRxWatchdogParams) extends Module {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val serdes_rx_hdr = Input(UInt(p.hdrW.W))
        val serdes_rx_hdr_valid = Input(Bool())
        val serdes_rx_reset_req = Output(Bool())
        val rx_bad_block = Input(Bool())
        val rx_sequence_error = Input(Bool())
        val rx_block_lock = Input(Bool())
        val rx_high_ber = Input(Bool())
        val rx_status = Output(Bool())
    })

    val dut = Module(new PcsRxWatchdog(
        hdrW = p.hdrW,
        count125us = p.count125us,
    ))

    dut.clock := io.clk
    dut.reset := io.rst
    dut.io.serdes_rx_hdr := io.serdes_rx_hdr
    dut.io.serdes_rx_hdr_valid := io.serdes_rx_hdr_valid    
    io.serdes_rx_reset_req := dut.io.serdes_rx_reset_req
    dut.io.rx_bad_block := io.rx_bad_block
    dut.io.rx_sequence_error := io.rx_sequence_error
    dut.io.rx_block_lock := io.rx_block_lock
    dut.io.rx_high_ber := io.rx_high_ber
    io.rx_status := dut.io.rx_status
}