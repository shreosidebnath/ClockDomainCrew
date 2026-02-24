package org.chiselware.cores.o01.t001.pcs
import _root_.circt.stage.ChiselStage
import chisel3._
import org.chiselware.cores.o01.t001.pcs.rx.PcsRx
import org.chiselware.cores.o01.t001.pcs.tx.PcsTx
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

class Pcs(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val txGbxIfEn: Boolean = false,
    val rxGbxIfEn: Boolean = false,
    val bitReverse: Boolean = false,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val txSerdesPipeline: Int = 0,
    val rxSerdesPipeline: Int = 0,
    val bitslipHighCycles: Int = 1,
    val bitslipLowCycles: Int = 7,
    val count125Us: Double = 125000.0 / 6.4) extends RawModule {
  val io = IO(new Bundle {
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())

    val xgmiiTxd = Input(UInt(dataW.W))
    val xgmiiTxc = Input(UInt(ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val xgmiiRxd = Output(UInt(dataW.W))
    val xgmiiRxc = Output(UInt(ctrlW.W))
    val xgmiiRxValid = Output(Bool())
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())

    // SERDES interface
    val serdesTxData = Output(UInt(dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())
    val serdesRxData = Input(UInt(dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(hdrW.W))
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

  // -------------------------------------------------------------------------
  // 1. RX Path Instantiation
  // -------------------------------------------------------------------------
  withClockAndReset(clock = io.rxClk, reset = io.rxRst) {
    val rx = Module(new PcsRx(
      dataW = dataW,
      ctrlW = ctrlW,
      hdrW = hdrW,
      gbxIfEn = rxGbxIfEn,
      bitReverse = bitReverse,
      scramblerDisable = scramblerDisable,
      prbs31En = prbs31En,
      serdesPipeline = rxSerdesPipeline,
      bitslipHighCycles = bitslipHighCycles,
      bitslipLowCycles = bitslipLowCycles,
      count125Us = count125Us
    ))

    // XGMII Output
    io.xgmiiRxd := rx.io.xgmiiRxd
    io.xgmiiRxc := rx.io.xgmiiRxc
    io.xgmiiRxValid := rx.io.xgmiiRxValid

    // SERDES Input
    rx.io.serdesRxData := io.serdesRxData
    rx.io.serdesRxDataValid := io.serdesRxDataValid
    rx.io.serdesRxHdr := io.serdesRxHdr
    rx.io.serdesRxHdrValid := io.serdesRxHdrValid

    // SERDES Control Output
    io.serdesRxBitslip := rx.io.serdesRxBitslip
    io.serdesRxResetReq := rx.io.serdesRxResetReq

    // Status
    io.rxErrorCount := rx.io.rxErrorCount
    io.rxBadBlock := rx.io.rxBadBlock
    io.rxSequenceError := rx.io.rxSequenceError
    io.rxBlockLock := rx.io.rxBlockLock
    io.rxHighBer := rx.io.rxHighBer
    io.rxStatus := rx.io.rxStatus

    // Configuration
    rx.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable
  }

  // -------------------------------------------------------------------------
  // 2. TX Path Instantiation
  // -------------------------------------------------------------------------
  withClockAndReset(clock = io.txClk, reset = io.txRst) {
    val tx = Module(new PcsTx(
      dataW = dataW,
      ctrlW = ctrlW,
      hdrW = hdrW,
      gbxIfEn = txGbxIfEn,
      bitReverse = bitReverse,
      scramblerDisable = scramblerDisable,
      prbs31En = prbs31En,
      serdesPipeline = txSerdesPipeline
    ))

    // XGMII Input
    tx.io.xgmiiTxd := io.xgmiiTxd
    tx.io.xgmiiTxc := io.xgmiiTxc
    tx.io.xgmiiTxValid := io.xgmiiTxValid

    // Gearbox Handshaking (TX Side)
    io.txGbxReqSync := tx.io.txGbxReqSync
    io.txGbxReqStall := tx.io.txGbxReqStall
    tx.io.txGbxSync := io.txGbxSync

    // SERDES Output
    io.serdesTxData := tx.io.serdesTxData
    io.serdesTxDataValid := tx.io.serdesTxDataValid
    io.serdesTxHdr := tx.io.serdesTxHdr
    io.serdesTxHdrValid := tx.io.serdesTxHdrValid
    io.serdesTxGbxSync := tx.io.serdesTxGbxSync

    // SERDES Handshaking
    tx.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
    tx.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall

    // Status
    io.txBadBlock := tx.io.txBadBlock

    // Configuration
    tx.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
  }
}

object Pcs {
  def apply(p: PcsParams): Pcs = Module(new Pcs(
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
}

object Main extends App {
  val MainClassName = "Pcs"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  PcsParams.SynConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Pcs(
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
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    SdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(
      mainClassName = MainClassName,
      outputDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      mainClassName = MainClassName,
      outputDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      mainClassName = MainClassName,
      synConfigs = PcsParams.SynConfigs,
      outputDir = s"${coreDir}/generated/synTestCases"
    )
  }
}