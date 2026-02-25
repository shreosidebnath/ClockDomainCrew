package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._

class PcsTxInterfaceTb(val p: PcsTxInterfaceParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val encodedTxData = Input(UInt(p.dataW.W))
    val encodedTxDataValid = Input(Bool())
    val encodedTxHdr = Input(UInt(p.hdrW.W))
    val encodedTxHdrValid = Input(Bool())
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())
    val serdesTxData = Output(UInt(p.dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(p.hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())
    val cfgTxPrbs31Enable = Input(Bool())
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
  dut.io.encodedTxData := io.encodedTxData
  dut.io.encodedTxDataValid := io.encodedTxDataValid
  dut.io.encodedTxHdr := io.encodedTxHdr
  dut.io.encodedTxHdrValid := io.encodedTxHdrValid
  io.txGbxReqSync := dut.io.txGbxReqSync
  io.txGbxReqStall := dut.io.txGbxReqStall
  dut.io.txGbxSync := io.txGbxSync
  io.serdesTxData := dut.io.serdesTxData
  io.serdesTxDataValid := dut.io.serdesTxDataValid
  io.serdesTxHdr := dut.io.serdesTxHdr
  io.serdesTxHdrValid := dut.io.serdesTxHdrValid
  dut.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
  dut.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall
  io.serdesTxGbxSync := dut.io.serdesTxGbxSync
  dut.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
}
