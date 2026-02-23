package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxInterfaceTb(val p: PcsRxInterfaceParams) extends Module {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val encoded_rx_data       = Output(UInt(p.dataW.W))
        val encoded_rx_data_valid = Output(Bool())
        val encoded_rx_hdr        = Output(UInt(p.hdrW.W))
        val encoded_rx_hdr_valid  = Output(Bool())
        val serdes_rx_data        = Input(UInt(p.dataW.W))
        val serdes_rx_data_valid  = Input(Bool())
        val serdes_rx_hdr         = Input(UInt(p.hdrW.W))
        val serdes_rx_hdr_valid   = Input(Bool())
        val serdes_rx_bitslip     = Output(Bool())
        val serdes_rx_reset_req   = Output(Bool())
        val rx_bad_block          = Input(Bool())
        val rx_sequence_error     = Input(Bool())
        val rx_error_count        = Output(UInt(7.W))
        val rx_block_lock         = Output(Bool())
        val rx_high_ber           = Output(Bool())
        val rx_status             = Output(Bool())
        val cfg_rx_prbs31_enable  = Input(Bool())
    })

    val dut = Module(new PcsRxInterface(
        dataW = p.dataW,
        hdrW = p.hdrW,
        gbxIfEn = p.gbxIfEn,
        bitReverse = p.bitReverse,
        scramblerDisable = p.scramblerDisable,
        prbs31En = p.prbs31En,
        serdesPipeline = p.serdesPipeline,
        bitslipHighCycles = p.bitslipHighCycles,
        bitslipLowCycles = p.bitslipLowCycles,
        count125Us = p.count125Us
    ))

    dut.clock := io.clk
    dut.reset := io.rst
    io.encoded_rx_data := dut.io.encoded_rx_data
    io.encoded_rx_data_valid := dut.io.encoded_rx_data_valid
    io.encoded_rx_hdr := dut.io.encoded_rx_hdr
    io.encoded_rx_hdr_valid := dut.io.encoded_rx_hdr_valid
    dut.io.serdes_rx_data := io.serdes_rx_data
    dut.io.serdes_rx_data_valid := io.serdes_rx_data_valid
    dut.io.serdes_rx_hdr := io.serdes_rx_hdr
    dut.io.serdes_rx_hdr_valid := io.serdes_rx_hdr_valid
    io.serdes_rx_bitslip := dut.io.serdes_rx_bitslip
    io.serdes_rx_reset_req := dut.io.serdes_rx_reset_req
    dut.io.rx_bad_block := io.rx_bad_block
    dut.io.rx_sequence_error := io.rx_sequence_error
    io.rx_error_count := dut.io.rx_error_count
    io.rx_block_lock := dut.io.rx_block_lock
    io.rx_high_ber := dut.io.rx_high_ber
    io.rx_status := dut.io.rx_status
    dut.io.cfg_rx_prbs31_enable := io.cfg_rx_prbs31_enable
}