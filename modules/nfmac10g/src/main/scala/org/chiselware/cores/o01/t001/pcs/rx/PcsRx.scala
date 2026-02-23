package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


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
  val count125Us: Double = 125000.0/6.4
) extends Module {
  val io = IO(new Bundle {
    // XGMII interface
    val xgmii_rxd          = Output(UInt(dataW.W))
    val xgmii_rxc          = Output(UInt(ctrlW.W))
    val xgmii_rx_valid     = Output(Bool())

    // SERDES interface
    val serdes_rx_data        = Input(UInt(dataW.W))
    val serdes_rx_data_valid  = Input(Bool())
    val serdes_rx_hdr         = Input(UInt(hdrW.W))
    val serdes_rx_hdr_valid   = Input(Bool())
    val serdes_rx_bitslip     = Output(Bool())
    val serdes_rx_reset_req   = Output(Bool())

    // Status
    val rx_error_count     = Output(UInt(7.W))
    val rx_bad_block       = Output(Bool())
    val rx_sequence_error  = Output(Bool())
    val rx_block_lock      = Output(Bool())
    val rx_high_ber        = Output(Bool())
    val rx_status          = Output(Bool())

    // Configuration
    val cfg_rx_prbs31_enable = Input(Bool())
  })

  // -------------------------------------------------------------------------
  // 1. Instantiation of RX Interface (Physical Coding Sublayer)
  // -------------------------------------------------------------------------
  val rx_if = Module(new PcsRxInterface(
    dataW             = dataW,
    hdrW              = hdrW,
    gbxIfEn           = gbxIfEn,
    bitReverse        = bitReverse,
    scramblerDisable  = scramblerDisable,
    prbs31En          = prbs31En,
    serdesPipeline    = serdesPipeline,
    bitslipHighCycles = bitslipHighCycles,
    bitslipLowCycles  = bitslipLowCycles,
    count125Us        = count125Us
  ))

  // Connect SERDES inputs
  rx_if.io.serdes_rx_data        := io.serdes_rx_data
  rx_if.io.serdes_rx_data_valid  := io.serdes_rx_data_valid
  rx_if.io.serdes_rx_hdr         := io.serdes_rx_hdr
  rx_if.io.serdes_rx_hdr_valid   := io.serdes_rx_hdr_valid
  
  // Connect Physical Status/Config
  io.serdes_rx_bitslip           := rx_if.io.serdes_rx_bitslip
  io.serdes_rx_reset_req         := rx_if.io.serdes_rx_reset_req
  io.rx_error_count              := rx_if.io.rx_error_count
  io.rx_block_lock               := rx_if.io.rx_block_lock
  io.rx_high_ber                 := rx_if.io.rx_high_ber
  io.rx_status                   := rx_if.io.rx_status
  rx_if.io.cfg_rx_prbs31_enable  := io.cfg_rx_prbs31_enable

  // -------------------------------------------------------------------------
  // 2. Instantiation of XGMII Decoder (Base-R to XGMII Translation)
  // -------------------------------------------------------------------------
  val decoder = Module(new XgmiiDecoder(
    dataW   = dataW,
    ctrlW   = ctrlW,
    hdrW    = hdrW,
    gbxIfEn = gbxIfEn
  ))

  // Connection between RX IF and Decoder
  decoder.io.encoded_rx_data       := rx_if.io.encoded_rx_data
  decoder.io.encoded_rx_data_valid := rx_if.io.encoded_rx_data_valid
  decoder.io.encoded_rx_hdr        := rx_if.io.encoded_rx_hdr
  decoder.io.encoded_rx_hdr_valid  := rx_if.io.encoded_rx_hdr_valid

  // -------------------------------------------------------------------------
  // 3. Feedback Loop & Final Outputs
  // -------------------------------------------------------------------------
  
  // Connect XGMII Data/Valid outputs to top level
  io.xgmii_rxd      := decoder.io.xgmii_rxd
  io.xgmii_rxc      := decoder.io.xgmii_rxc
  io.xgmii_rx_valid := decoder.io.xgmii_rx_valid

  // Feed decoder status back to the RX Interface watchdog
  rx_if.io.rx_bad_block      := decoder.io.rx_bad_block
  rx_if.io.rx_sequence_error := decoder.io.rx_sequence_error

  // Also expose these status signals to the top level
  io.rx_bad_block      := decoder.io.rx_bad_block
  io.rx_sequence_error := decoder.io.rx_sequence_error
}


object PcsRx {
  def apply(p: PcsRxParams): PcsRx = Module(new PcsRx(
    dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse, scramblerDisable = p.scramblerDisable,
    prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline, bitslipHighCycles = p.bitslipHighCycles,
    bitslipLowCycles = p.bitslipLowCycles, count125Us = p.count125Us
  ))
}

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  PcsRxParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new PcsRx(
    dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse, scramblerDisable = p.scramblerDisable,
    prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline, bitslipHighCycles = p.bitslipHighCycles,
    bitslipLowCycles = p.bitslipLowCycles, count125Us = p.count125Us
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    // Synthesis collateral generation
    sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    RunScriptFile.create(mainClassName, PcsRxParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}