package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._

class PcsTxTb(val p: PcsTxParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())

    val xgmii_txd = Input(UInt(p.dataW.W))
    val xgmii_txc = Input(UInt(p.ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val tx_gbx_req_sync = Output(Bool())
    val tx_gbx_req_stall = Output(Bool())
    val tx_gbx_sync = Input(Bool())

    val serdes_tx_data = Output(UInt(p.dataW.W))
    val serdes_tx_data_valid = Output(Bool())
    val serdes_tx_hdr = Output(UInt(p.hdrW.W))
    val serdes_tx_hdr_valid = Output(Bool())
    val serdes_tx_gbx_req_sync = Input(Bool())
    val serdes_tx_gbx_req_stall = Input(Bool())
    val serdes_tx_gbx_sync = Output(Bool())

    val tx_bad_block = Output(Bool()) // Status
    val cfg_tx_prbs31_enable = Input(Bool()) // Configuration
  })

  val dut = Module(new PcsTx(
    dataW = p.dataW, 
    ctrlW = p.ctrlW,
    hdrW = p.hdrW, 
    gbxIfEn = p.gbxIfEn, 
    bitReverse = p.bitReverse,
    scramblerDisable = p.scramblerDisable, 
    prbs31En = p.prbs31En, 
    serdesPipeline = p.serdesPipeline
  ))

    dut.clock := io.clk
    dut.reset := io.rst
    
    dut.io.xgmii_txd := io.xgmii_txd
    dut.io.xgmii_txc := io.xgmii_txc
    dut.io.xgmii_tx_valid := io.xgmii_tx_valid
    io.tx_gbx_req_sync := dut.io.tx_gbx_req_sync
    io.tx_gbx_req_stall := dut.io.tx_gbx_req_stall
    dut.io.tx_gbx_sync := io.tx_gbx_sync
    io.serdes_tx_data := dut.io.serdes_tx_data
    io.serdes_tx_data_valid := dut.io.serdes_tx_data_valid
    io.serdes_tx_hdr := dut.io.serdes_tx_hdr
    io.serdes_tx_hdr_valid := dut.io.serdes_tx_hdr_valid
    dut.io.serdes_tx_gbx_req_sync := io.serdes_tx_gbx_req_sync
    dut.io.serdes_tx_gbx_req_stall := io.serdes_tx_gbx_req_stall
    io.serdes_tx_gbx_sync := dut.io.serdes_tx_gbx_sync
    io.tx_bad_block := dut.io.tx_bad_block
    dut.io.cfg_tx_prbs31_enable := io.cfg_tx_prbs31_enable
}