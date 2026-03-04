package org.chiselware.cores.o01.t001.pcs.rx
import _root_.circt.stage.ChiselStage
import chisel3._
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

class PcsRx(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = false,
    val bitReverse: Boolean = false,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 0,
    val bitslipHighCycles: Int = 1,
    val bitslipLowCycles: Int = 7,
    val count125Us: Double = 125000.0 / 6.4) extends Module {
  val io = IO(new Bundle {
    // XGMII interface
    val xgmiiRxd = Output(UInt(dataW.W))
    val xgmiiRxc = Output(UInt(ctrlW.W))
    val xgmiiRxValid = Output(Bool())

    // SERDES interface
    val serdesRxData = Input(UInt(dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())

    // Status
    val rxErrorCount = Output(UInt(7.W))
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())

    // Configuration
    val cfgRxPrbs31Enable = Input(Bool())
  })

  // -------------------------------------------------------------------------
  // 1. Instantiation of RX Interface (Physical Coding Sublayer)
  // -------------------------------------------------------------------------
  val rxIf = Module(new PcsRxInterface(
    dataW = dataW,
    hdrW = hdrW,
    gbxIfEn = gbxIfEn,
    bitReverse = bitReverse,
    scramblerDisable = scramblerDisable,
    prbs31En = prbs31En,
    serdesPipeline = serdesPipeline,
    bitslipHighCycles = bitslipHighCycles,
    bitslipLowCycles = bitslipLowCycles,
    count125Us = count125Us
  ))

  // Connect SERDES inputs
  rxIf.io.serdesRxData := io.serdesRxData
  rxIf.io.serdesRxDataValid := io.serdesRxDataValid
  rxIf.io.serdesRxHdr := io.serdesRxHdr
  rxIf.io.serdesRxHdrValid := io.serdesRxHdrValid

  // Connect Physical Status/Config
  io.serdesRxBitslip := rxIf.io.serdesRxBitslip
  io.serdesRxResetReq := rxIf.io.serdesRxResetReq
  io.rxErrorCount := rxIf.io.rxErrorCount
  io.rxBlockLock := rxIf.io.rxBlockLock
  io.rxHighBer := rxIf.io.rxHighBer
  io.rxStatus := rxIf.io.rxStatus
  rxIf.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable

  // -------------------------------------------------------------------------
  // 2. Instantiation of XGMII Decoder (Base-R to XGMII Translation)
  // -------------------------------------------------------------------------
  val decoder = Module(new XgmiiDecoder(
    dataW = dataW,
    ctrlW = ctrlW,
    hdrW = hdrW,
    gbxIfEn = gbxIfEn
  ))

  // Connection between RX IF and Decoder
  decoder.io.encodedRxData := rxIf.io.encodedRxData
  decoder.io.encodedRxDataValid := rxIf.io.encodedRxDataValid
  decoder.io.encodedRxHdr := rxIf.io.encodedRxHdr
  decoder.io.encodedRxHdrValid := rxIf.io.encodedRxHdrValid

  // -------------------------------------------------------------------------
  // 3. Feedback Loop & Final Outputs
  // -------------------------------------------------------------------------

  // Connect XGMII Data/Valid outputs to top level
  io.xgmiiRxd := decoder.io.xgmiiRxd
  io.xgmiiRxc := decoder.io.xgmiiRxc
  io.xgmiiRxValid := decoder.io.xgmiiRxValid

  // Feed decoder status back to the RX Interface watchdog
  rxIf.io.rxBadBlock := decoder.io.rxBadBlock
  rxIf.io.rxSequenceError := decoder.io.rxSequenceError

  // Also expose these status signals to the top level
  io.rxBadBlock := decoder.io.rxBadBlock
  io.rxSequenceError := decoder.io.rxSequenceError
}

object PcsRx {
  def apply(p: PcsRxParams): PcsRx = Module(new PcsRx(
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
}

object Main extends App {
  val MainClassName = "Pcs"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  PcsRxParams.SynConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new PcsRx(
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
      PcsRxParams.SynConfigs,
      s"${coreDir}/generated/synTestCases"
    )
  }
}
