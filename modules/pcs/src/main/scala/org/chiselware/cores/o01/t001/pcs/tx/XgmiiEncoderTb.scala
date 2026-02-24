package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._

class XgmiiEncoderTb(val p: XgmiiEncoderParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmii_txd = Input(UInt(p.dataW.W))
    val xgmii_txc = Input(UInt(p.ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val tx_gbx_sync_in = Input(UInt(p.gbxCnt.W))
    val encoded_tx_data = Output(UInt(p.dataW.W))
    val encoded_tx_data_valid = Output(Bool())
    val encoded_tx_hdr = Output(UInt(p.hdrW.W))
    val encoded_tx_hdr_valid = Output(Bool())
    val tx_gbx_sync_out = Output(UInt(p.gbxCnt.W))
    val tx_bad_block = Output(Bool())
  })

  // Fixed: Correct named parameters matching class definition
  val dut = Module(new XgmiiEncoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.xgmii_txd := io.xgmii_txd
  dut.io.xgmii_txc := io.xgmii_txc
  dut.io.xgmii_tx_valid := io.xgmii_tx_valid
  dut.io.tx_gbx_sync_in := io.tx_gbx_sync_in

  io.encoded_tx_data := dut.io.encoded_tx_data
  io.encoded_tx_data_valid := dut.io.encoded_tx_data_valid
  io.encoded_tx_hdr := dut.io.encoded_tx_hdr
  io.encoded_tx_hdr_valid := dut.io.encoded_tx_hdr_valid
  io.tx_gbx_sync_out := dut.io.tx_gbx_sync_out
  io.tx_bad_block := dut.io.tx_bad_block
}
