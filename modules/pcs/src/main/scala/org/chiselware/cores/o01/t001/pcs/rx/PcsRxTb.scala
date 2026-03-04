package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

class PcsRxTb(val p: PcsRxParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val xgmiiRxd = Output(UInt(p.dataW.W))
    val xgmiiRxc = Output(UInt(p.ctrlW.W))
    val xgmiiRxValid = Output(Bool())
    val serdesRxData = Input(UInt(p.dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())
    val rxErrorCount = Output(UInt(7.W))
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())
    val cfgRxPrbs31Enable = Input(Bool())
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
  io.xgmiiRxd := dut.io.xgmiiRxd
  io.xgmiiRxc := dut.io.xgmiiRxc
  io.xgmiiRxValid := dut.io.xgmiiRxValid
  dut.io.serdesRxData := io.serdesRxData
  dut.io.serdesRxDataValid := io.serdesRxDataValid
  dut.io.serdesRxHdr := io.serdesRxHdr
  dut.io.serdesRxHdrValid := io.serdesRxHdrValid
  io.serdesRxBitslip := dut.io.serdesRxBitslip
  io.serdesRxResetReq := dut.io.serdesRxResetReq
  io.rxErrorCount := dut.io.rxErrorCount
  io.rxBadBlock := dut.io.rxBadBlock
  io.rxSequenceError := dut.io.rxSequenceError
  io.rxBlockLock := dut.io.rxBlockLock
  io.rxHighBer := dut.io.rxHighBer
  io.rxStatus := dut.io.rxStatus
  dut.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable
}
