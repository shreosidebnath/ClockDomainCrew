package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._

class XgmiiEncoderTb(val p: XgmiiEncoderParams) extends Module {
  // Helper vals because Params struct only holds base config
  val CTRL_W = p.DATA_W / 8
  val HDR_W = 2

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmii_txd = Input(UInt(p.DATA_W.W))
    val xgmii_txc = Input(UInt(CTRL_W.W))
    val xgmii_tx_valid = Input(Bool())
    val tx_gbx_sync_in = Input(UInt(p.GBX_CNT.W))
    val encoded_tx_data = Output(UInt(p.DATA_W.W))
    val encoded_tx_data_valid = Output(Bool())
    val encoded_tx_hdr = Output(UInt(HDR_W.W))
    val encoded_tx_hdr_valid = Output(Bool())
    val tx_gbx_sync_out = Output(UInt(p.GBX_CNT.W))
    val tx_bad_block = Output(Bool())
  })

  // Fixed: Correct named parameters matching class definition
  val dut = Module(new XgmiiEncoder(
    DATA_W = p.DATA_W,
    GBX_IF_EN = p.GBX_IF_EN,
    GBX_CNT = p.GBX_CNT
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