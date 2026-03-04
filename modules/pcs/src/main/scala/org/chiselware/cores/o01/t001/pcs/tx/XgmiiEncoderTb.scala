package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._

class XgmiiEncoderTb(val p: XgmiiEncoderParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmiiTxd = Input(UInt(p.dataW.W))
    val xgmiiTxc = Input(UInt(p.ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val txGbxSyncIn = Input(UInt(p.gbxCnt.W))
    val encodedTxData = Output(UInt(p.dataW.W))
    val encodedTxDataValid = Output(Bool())
    val encodedTxHdr = Output(UInt(p.hdrW.W))
    val encodedTxHdrValid = Output(Bool())
    val txGbxSyncOut = Output(UInt(p.gbxCnt.W))
    val txBadBlock = Output(Bool())
  })

  val dut = Module(new XgmiiEncoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt
  ))

  dut.clock := io.clk
  dut.reset := io.rst
  dut.io.xgmiiTxd := io.xgmiiTxd
  dut.io.xgmiiTxc := io.xgmiiTxc
  dut.io.xgmiiTxValid := io.xgmiiTxValid
  dut.io.txGbxSyncIn := io.txGbxSyncIn

  io.encodedTxData := dut.io.encodedTxData
  io.encodedTxDataValid := dut.io.encodedTxDataValid
  io.encodedTxHdr := dut.io.encodedTxHdr
  io.encodedTxHdrValid := dut.io.encodedTxHdrValid
  io.txGbxSyncOut := dut.io.txGbxSyncOut
  io.txBadBlock := dut.io.txBadBlock
}
