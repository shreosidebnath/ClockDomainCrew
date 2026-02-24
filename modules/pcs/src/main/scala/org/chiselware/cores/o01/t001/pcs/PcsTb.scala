package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._


class PcsTb(val p: PcsParams) extends Module {
    val io = IO(new Bundle {
        // Clocks and Resets
        val rx_clk = Input(Clock())
        val rx_rst = Input(Bool())
        val tx_clk = Input(Clock())
        val tx_rst = Input(Bool())

        // XGMII Interface
        val xgmii_txd        = Input(UInt(p.dataW.W))
        val xgmii_txc        = Input(UInt(p.ctrlW.W))
        val xgmii_tx_valid   = Input(Bool())
        val xgmii_rxd        = Output(UInt(p.dataW.W))
        val xgmii_rxc        = Output(UInt(p.ctrlW.W))
        val xgmii_rx_valid   = Output(Bool())
        
        // TX Gearbox Handshake
        val tx_gbx_req_sync  = Output(Bool())
        val tx_gbx_req_stall = Output(Bool())
        val tx_gbx_sync      = Input(Bool())

        // SERDES Interface (TX)
        val serdes_tx_data          = Output(UInt(p.dataW.W))
        val serdes_tx_data_valid    = Output(Bool())
        val serdes_tx_hdr           = Output(UInt(p.hdrW.W))
        val serdes_tx_hdr_valid     = Output(Bool())
        val serdes_tx_gbx_req_sync  = Input(Bool())
        val serdes_tx_gbx_req_stall = Input(Bool())
        val serdes_tx_gbx_sync      = Output(Bool())

        // SERDES Interface (RX)
        val serdes_rx_data        = Input(UInt(p.dataW.W))
        val serdes_rx_data_valid  = Input(Bool())
        val serdes_rx_hdr         = Input(UInt(p.hdrW.W))
        val serdes_rx_hdr_valid   = Input(Bool())
        val serdes_rx_bitslip     = Output(Bool())
        val serdes_rx_reset_req   = Output(Bool())

        // Status
        val tx_bad_block       = Output(Bool())
        val rx_error_count     = Output(UInt(7.W))
        val rx_bad_block       = Output(Bool())
        val rx_sequence_error  = Output(Bool())
        val rx_block_lock      = Output(Bool())
        val rx_high_ber        = Output(Bool())
        val rx_status          = Output(Bool())

        // Configuration
        val cfg_tx_prbs31_enable = Input(Bool())
        val cfg_rx_prbs31_enable = Input(Bool())
    })

    val dut = Module(new Pcs(
        dataW             = p.dataW,
        ctrlW             = p.ctrlW,
        hdrW              = p.hdrW,
        txGbxIfEn         = p.txGbxIfEn,
        rxGbxIfEn         = p.rxGbxIfEn,
        bitReverse        = p.bitReverse,
        scramblerDisable  = p.scramblerDisable,
        prbs31En          = p.prbs31En,
        txSerdesPipeline  = p.txSerdesPipeline,
        rxSerdesPipeline  = p.rxSerdesPipeline,
        bitslipHighCycles = p.bitslipHighCycles,
        bitslipLowCycles  = p.bitslipLowCycles,
        count125Us        = p.count125Us
    ))

    // Clock & Reset Wiring
    dut.io.rx_clk := io.rx_clk
    dut.io.rx_rst := io.rx_rst
    dut.io.tx_clk := io.tx_clk
    dut.io.tx_rst := io.tx_rst

    // XGMII Wiring
    dut.io.xgmii_txd      := io.xgmii_txd
    dut.io.xgmii_txc      := io.xgmii_txc
    dut.io.xgmii_tx_valid := io.xgmii_tx_valid
    io.xgmii_rxd          := dut.io.xgmii_rxd
    io.xgmii_rxc          := dut.io.xgmii_rxc
    io.xgmii_rx_valid     := dut.io.xgmii_rx_valid

    // TX Gearbox Wiring
    io.tx_gbx_req_sync    := dut.io.tx_gbx_req_sync
    io.tx_gbx_req_stall   := dut.io.tx_gbx_req_stall
    dut.io.tx_gbx_sync    := io.tx_gbx_sync

    // SERDES TX Wiring
    io.serdes_tx_data              := dut.io.serdes_tx_data
    io.serdes_tx_data_valid        := dut.io.serdes_tx_data_valid
    io.serdes_tx_hdr               := dut.io.serdes_tx_hdr
    io.serdes_tx_hdr_valid         := dut.io.serdes_tx_hdr_valid
    io.serdes_tx_gbx_sync          := dut.io.serdes_tx_gbx_sync
    dut.io.serdes_tx_gbx_req_sync  := io.serdes_tx_gbx_req_sync
    dut.io.serdes_tx_gbx_req_stall := io.serdes_tx_gbx_req_stall

    // SERDES RX Wiring
    dut.io.serdes_rx_data       := io.serdes_rx_data
    dut.io.serdes_rx_data_valid := io.serdes_rx_data_valid
    dut.io.serdes_rx_hdr        := io.serdes_rx_hdr
    dut.io.serdes_rx_hdr_valid  := io.serdes_rx_hdr_valid
    io.serdes_rx_bitslip        := dut.io.serdes_rx_bitslip
    io.serdes_rx_reset_req      := dut.io.serdes_rx_reset_req

    // Status Wiring
    io.tx_bad_block      := dut.io.tx_bad_block
    io.rx_error_count    := dut.io.rx_error_count
    io.rx_bad_block      := dut.io.rx_bad_block
    io.rx_sequence_error := dut.io.rx_sequence_error
    io.rx_block_lock     := dut.io.rx_block_lock
    io.rx_high_ber       := dut.io.rx_high_ber
    io.rx_status         := dut.io.rx_status

    // Configuration Wiring
    dut.io.cfg_tx_prbs31_enable := io.cfg_tx_prbs31_enable
    dut.io.cfg_rx_prbs31_enable := io.cfg_rx_prbs31_enable
}