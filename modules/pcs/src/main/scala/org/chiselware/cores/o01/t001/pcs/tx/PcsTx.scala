package org.chiselware.cores.o01.t001.pcs.tx
import _root_.circt.stage.ChiselStage
import chisel3._
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

class PcsTx(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = false,
    val bitReverse: Boolean = false,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 0) extends Module {
  val io = IO(new Bundle {
    val xgmiiTxd = Input(UInt(dataW.W))
    val xgmiiTxc = Input(UInt(ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())

    val serdesTxData = Output(UInt(dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())

    val txBadBlock = Output(Bool())
    val cfgTxPrbs31Enable = Input(Bool())
  })

  val encodedTxData = Wire(UInt(dataW.W))
  val encodedTxDataValid = Wire(Bool())
  val encodedTxHdr = Wire(UInt(hdrW.W))
  val encodedTxHdrValid = Wire(Bool())
  val txGbxSyncInt = Wire(Bool())

  // Encoder
  val encoderInst = Module(new XgmiiEncoder(
    dataW = dataW,
    ctrlW = ctrlW,
    hdrW = hdrW,
    gbxIfEn = gbxIfEn,
    gbxCnt = 1
  ))
  encoderInst.io.xgmiiTxd := io.xgmiiTxd
  encoderInst.io.xgmiiTxc := io.xgmiiTxc
  encoderInst.io.xgmiiTxValid := io.xgmiiTxValid
  encoderInst.io.txGbxSyncIn := io.txGbxSync

  encodedTxData := encoderInst.io.encodedTxData
  encodedTxDataValid := encoderInst.io.encodedTxDataValid
  encodedTxHdr := encoderInst.io.encodedTxHdr
  encodedTxHdrValid := encoderInst.io.encodedTxHdrValid
  txGbxSyncInt := encoderInst.io.txGbxSyncOut

  io.txBadBlock := encoderInst.io.txBadBlock

  // TX Interface
  val txIfInst = Module(new PcsTxInterface(
    dataW = dataW,
    hdrW = hdrW,
    gbxIfEn = gbxIfEn,
    bitReverse = bitReverse,
    scramblerDisable = scramblerDisable,
    prbs31En = prbs31En,
    serdesPipeline = serdesPipeline
  ))

  txIfInst.io.encodedTxData := encodedTxData
  txIfInst.io.encodedTxDataValid := encodedTxDataValid
  txIfInst.io.encodedTxHdr := encodedTxHdr
  txIfInst.io.encodedTxHdrValid := encodedTxHdrValid

  io.txGbxReqSync := txIfInst.io.txGbxReqSync
  io.txGbxReqStall := txIfInst.io.txGbxReqStall
  txIfInst.io.txGbxSync := txGbxSyncInt

  io.serdesTxData := txIfInst.io.serdesTxData
  io.serdesTxDataValid := txIfInst.io.serdesTxDataValid
  io.serdesTxHdr := txIfInst.io.serdesTxHdr
  io.serdesTxHdrValid := txIfInst.io.serdesTxHdrValid

  txIfInst.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
  txIfInst.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall
  io.serdesTxGbxSync := txIfInst.io.serdesTxGbxSync

  txIfInst.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
}

object PcsTx {
  def apply(p: PcsTxParams): PcsTx = Module(new PcsTx(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn,
    bitReverse = p.bitReverse,
    scramblerDisable = p.scramblerDisable,
    prbs31En = p.prbs31En,
    serdesPipeline = p.serdesPipeline
  ))
}

object Main extends App {
  val MainClassName = "Pcs"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  PcsTxParams.SynConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new PcsTx(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        hdrW = p.hdrW,
        gbxIfEn = p.gbxIfEn,
        bitReverse = p.bitReverse,
        scramblerDisable = p.scramblerDisable,
        prbs31En = p.prbs31En,
        serdesPipeline = p.serdesPipeline
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
      MainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      MainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      MainClassName,
      PcsTxParams.SynConfigs,
      s"${coreDir}/generated/synTestCases"
    )
  }
}
