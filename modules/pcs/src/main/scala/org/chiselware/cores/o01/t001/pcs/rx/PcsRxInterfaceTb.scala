package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

class PcsRxInterfaceTb(val p: PcsRxInterfaceParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val encodedRxData = Output(UInt(p.dataW.W))
    val encodedRxDataValid = Output(Bool())
    val encodedRxHdr = Output(UInt(p.hdrW.W))
    val encodedRxHdrValid = Output(Bool())
    val serdesRxData = Input(UInt(p.dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())
    val rxBadBlock = Input(Bool())
    val rxSequenceError = Input(Bool())
    val rxErrorCount = Output(UInt(7.W))
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())
    val cfgRxPrbs31Enable = Input(Bool())
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
  io.encodedRxData := dut.io.encodedRxData
  io.encodedRxDataValid := dut.io.encodedRxDataValid
  io.encodedRxHdr := dut.io.encodedRxHdr
  io.encodedRxHdrValid := dut.io.encodedRxHdrValid
  dut.io.serdesRxData := io.serdesRxData
  dut.io.serdesRxDataValid := io.serdesRxDataValid
  dut.io.serdesRxHdr := io.serdesRxHdr
  dut.io.serdesRxHdrValid := io.serdesRxHdrValid
  io.serdesRxBitslip := dut.io.serdesRxBitslip
  io.serdesRxResetReq := dut.io.serdesRxResetReq
  dut.io.rxBadBlock := io.rxBadBlock
  dut.io.rxSequenceError := io.rxSequenceError
  io.rxErrorCount := dut.io.rxErrorCount
  io.rxBlockLock := dut.io.rxBlockLock
  io.rxHighBer := dut.io.rxHighBer
  io.rxStatus := dut.io.rxStatus
  dut.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable
}
