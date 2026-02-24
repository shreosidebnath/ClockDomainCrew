package org.chiselware.cores.o01.t001.pcs
import chisel3._

class PcsTb(val p: PcsParams) extends Module {
  val io = IO(new Bundle {
    // Clocks and Resets
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())

    // XGMII Interface
    val xgmiiTxd = Input(UInt(p.dataW.W))
    val xgmiiTxc = Input(UInt(p.ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val xgmiiRxd = Output(UInt(p.dataW.W))
    val xgmiiRxc = Output(UInt(p.ctrlW.W))
    val xgmiiRxValid = Output(Bool())

    // TX Gearbox Handshake
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())

    // SERDES Interface (TX)
    val serdesTxData = Output(UInt(p.dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(p.hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())

    // SERDES Interface (RX)
    val serdesRxData = Input(UInt(p.dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())

    // Status
    val txBadBlock = Output(Bool())
    val rxErrorCount = Output(UInt(7.W))
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())

    // Configuration
    val cfgTxPrbs31Enable = Input(Bool())
    val cfgRxPrbs31Enable = Input(Bool())
  })

  val dut = Module(new Pcs(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    txGbxIfEn = p.txGbxIfEn,
    rxGbxIfEn = p.rxGbxIfEn,
    bitReverse = p.bitReverse,
    scramblerDisable = p.scramblerDisable,
    prbs31En = p.prbs31En,
    txSerdesPipeline = p.txSerdesPipeline,
    rxSerdesPipeline = p.rxSerdesPipeline,
    bitslipHighCycles = p.bitslipHighCycles,
    bitslipLowCycles = p.bitslipLowCycles,
    count125Us = p.count125Us
  ))

  // Clock & Reset Wiring
  dut.io.rxClk := io.rxClk
  dut.io.rxRst := io.rxRst
  dut.io.txClk := io.txClk
  dut.io.txRst := io.txRst

  // XGMII Wiring
  dut.io.xgmiiTxd := io.xgmiiTxd
  dut.io.xgmiiTxc := io.xgmiiTxc
  dut.io.xgmiiTxValid := io.xgmiiTxValid
  io.xgmiiRxd := dut.io.xgmiiRxd
  io.xgmiiRxc := dut.io.xgmiiRxc
  io.xgmiiRxValid := dut.io.xgmiiRxValid

  // TX Gearbox Wiring
  io.txGbxReqSync := dut.io.txGbxReqSync
  io.txGbxReqStall := dut.io.txGbxReqStall
  dut.io.txGbxSync := io.txGbxSync

  // SERDES TX Wiring
  io.serdesTxData := dut.io.serdesTxData
  io.serdesTxDataValid := dut.io.serdesTxDataValid
  io.serdesTxHdr := dut.io.serdesTxHdr
  io.serdesTxHdrValid := dut.io.serdesTxHdrValid
  io.serdesTxGbxSync := dut.io.serdesTxGbxSync
  dut.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
  dut.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall

  // SERDES RX Wiring
  dut.io.serdesRxData := io.serdesRxData
  dut.io.serdesRxDataValid := io.serdesRxDataValid
  dut.io.serdesRxHdr := io.serdesRxHdr
  dut.io.serdesRxHdrValid := io.serdesRxHdrValid
  io.serdesRxBitslip := dut.io.serdesRxBitslip
  io.serdesRxResetReq := dut.io.serdesRxResetReq

  // Status Wiring
  io.txBadBlock := dut.io.txBadBlock
  io.rxErrorCount := dut.io.rxErrorCount
  io.rxBadBlock := dut.io.rxBadBlock
  io.rxSequenceError := dut.io.rxSequenceError
  io.rxBlockLock := dut.io.rxBlockLock
  io.rxHighBer := dut.io.rxHighBer
  io.rxStatus := dut.io.rxStatus

  // Configuration Wiring
  dut.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
  dut.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable
}