package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._

class PcsTxInterfaceTb(val p: PcsTxInterfaceParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val encoded_tx_data = Input(UInt(p.dataW.W))
    val encoded_tx_data_valid = Input(Bool())
    val encoded_tx_hdr = Input(UInt(p.hdrW.W))
    val encoded_tx_hdr_valid = Input(Bool())
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
    val cfg_tx_prbs31_enable = Input(Bool())
  })

  val dut = Module(new PcsTxInterface(
    dataW = p.dataW, 
    hdrW = p.hdrW, 
    gbxIfEn = p.gbxIfEn, 
    bitReverse = p.bitReverse,
    scramblerDisable = p.scramblerDisable, 
    prbs31En = p.prbs31En, 
    serdesPipeline = p.serdesPipeline
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.encoded_tx_data := io.encoded_tx_data
  dut.io.encoded_tx_data_valid := io.encoded_tx_data_valid
  dut.io.encoded_tx_hdr := io.encoded_tx_hdr
  dut.io.encoded_tx_hdr_valid := io.encoded_tx_hdr_valid
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
  dut.io.cfg_tx_prbs31_enable := io.cfg_tx_prbs31_enable
}