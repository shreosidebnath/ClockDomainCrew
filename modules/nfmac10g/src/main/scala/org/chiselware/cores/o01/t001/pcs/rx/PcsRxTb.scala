package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxTb(val p: PcsRxParams) extends Module {
    val io = IO(new Bundle {
        val clk = Input(Clock())
        val rst = Input(Bool())
        val xgmii_rxd          = Output(UInt(p.dataW.W))
        val xgmii_rxc          = Output(UInt(p.ctrlW.W))
        val xgmii_rx_valid     = Output(Bool())
        val serdes_rx_data        = Input(UInt(p.dataW.W))
        val serdes_rx_data_valid  = Input(Bool())
        val serdes_rx_hdr         = Input(UInt(p.hdrW.W))
        val serdes_rx_hdr_valid   = Input(Bool())
        val serdes_rx_bitslip     = Output(Bool())
        val serdes_rx_reset_req   = Output(Bool())
        val rx_error_count     = Output(UInt(7.W))
        val rx_bad_block       = Output(Bool())
        val rx_sequence_error  = Output(Bool())
        val rx_block_lock      = Output(Bool())
        val rx_high_ber        = Output(Bool())
        val rx_status          = Output(Bool())
        val cfg_rx_prbs31_enable = Input(Bool())
    })

    val dut = Module(new PcsRx(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
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
    io.xgmii_rxd := dut.io.xgmii_rxd
    io.xgmii_rxc := dut.io.xgmii_rxc
    io.xgmii_rx_valid := dut.io.xgmii_rx_valid
    dut.io.serdes_rx_data := io.serdes_rx_data
    dut.io.serdes_rx_data_valid := io.serdes_rx_data_valid
    dut.io.serdes_rx_hdr := io.serdes_rx_hdr
    dut.io.serdes_rx_hdr_valid := io.serdes_rx_hdr_valid
    io.serdes_rx_bitslip := dut.io.serdes_rx_bitslip
    io.serdes_rx_reset_req := dut.io.serdes_rx_reset_req
    io.rx_error_count := dut.io.rx_error_count
    io.rx_bad_block := dut.io.rx_bad_block
    io.rx_sequence_error := dut.io.rx_sequence_error
    io.rx_block_lock := dut.io.rx_block_lock
    io.rx_high_ber := dut.io.rx_high_ber
    io.rx_status := dut.io.rx_status
    dut.io.cfg_rx_prbs31_enable := io.cfg_rx_prbs31_enable
}