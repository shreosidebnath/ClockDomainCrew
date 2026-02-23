package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class XgmiiDecoderTb(val p: XgmiiDecoderParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val encoded_rx_data       = Input(UInt(p.dataW.W))
    val encoded_rx_data_valid = Input(Bool()) 
    val encoded_rx_hdr        = Input(UInt(p.hdrW.W))
    val encoded_rx_hdr_valid  = Input(Bool())
    val xgmii_rxd      = Output(UInt(p.dataW.W))
    val xgmii_rxc      = Output(UInt(p.ctrlW.W))
    val xgmii_rx_valid = Output(Bool())
    val rx_bad_block      = Output(Bool())
    val rx_sequence_error = Output(Bool())
  })

  // Fixed: Correct named parameters matching class definition
  val dut = Module(new XgmiiDecoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.encoded_rx_data := io.encoded_rx_data
  dut.io.encoded_rx_data_valid := io.encoded_rx_data_valid
  dut.io.encoded_rx_hdr := io.encoded_rx_hdr
  dut.io.encoded_rx_hdr_valid := io.encoded_rx_hdr_valid

  io.xgmii_rxd := dut.io.xgmii_rxd
  io.xgmii_rxc := dut.io.xgmii_rxc
  io.xgmii_rx_valid := dut.io.xgmii_rx_valid
  io.rx_bad_block := dut.io.rx_bad_block
  io.rx_sequence_error := dut.io.rx_sequence_error
}