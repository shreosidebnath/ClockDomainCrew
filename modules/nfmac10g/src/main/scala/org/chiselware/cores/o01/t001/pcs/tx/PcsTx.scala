package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class PcsTx(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val hdrW: Int = 2,
  val gbxIfEn: Boolean = false,
  val bitReverse: Boolean = false,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val serdesPipeline: Int = 0
) extends Module {
  val io = IO(new Bundle {
    val xgmii_txd = Input(UInt(dataW.W))
    val xgmii_txc = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val tx_gbx_req_sync = Output(Bool())
    val tx_gbx_req_stall = Output(Bool())
    val tx_gbx_sync = Input(Bool())

    val serdes_tx_data = Output(UInt(dataW.W))
    val serdes_tx_data_valid = Output(Bool())
    val serdes_tx_hdr = Output(UInt(hdrW.W))
    val serdes_tx_hdr_valid = Output(Bool())
    val serdes_tx_gbx_req_sync = Input(Bool())
    val serdes_tx_gbx_req_stall = Input(Bool())
    val serdes_tx_gbx_sync = Output(Bool())

    val tx_bad_block = Output(Bool()) // Status
    val cfg_tx_prbs31_enable = Input(Bool()) // Configuration
  })

  val encoded_tx_data = Wire(UInt(dataW.W))
  val encoded_tx_data_valid = Wire(Bool())
  val encoded_tx_hdr = Wire(UInt(hdrW.W))
  val encoded_tx_hdr_valid = Wire(Bool())
  val tx_gbx_sync_int = Wire(Bool())

  // Encoder
  val encoder = Module(new XgmiiEncoder(dataW=dataW, ctrlW=ctrlW, hdrW=hdrW, gbxIfEn=gbxIfEn, gbxCnt=1))
  encoder.clock := clock
  encoder.reset := reset
  encoder.io.xgmii_txd := io.xgmii_txd
  encoder.io.xgmii_txc := io.xgmii_txc
  encoder.io.xgmii_tx_valid := io.xgmii_tx_valid
  encoder.io.tx_gbx_sync_in := io.tx_gbx_sync

  encoded_tx_data := encoder.io.encoded_tx_data
  encoded_tx_data_valid := encoder.io.encoded_tx_data_valid
  encoded_tx_hdr := encoder.io.encoded_tx_hdr
  encoded_tx_hdr_valid := encoder.io.encoded_tx_hdr_valid 
  tx_gbx_sync_int := encoder.io.tx_gbx_sync_out
  
  io.tx_bad_block := encoder.io.tx_bad_block

  // TX Interface
  val tx_if = Module(new PcsTxInterface(
    dataW=dataW, hdrW=hdrW, gbxIfEn=gbxIfEn, bitReverse=bitReverse, scramblerDisable=scramblerDisable, prbs31En=prbs31En, serdesPipeline=serdesPipeline
  ))
  
  tx_if.clock := clock
  tx_if.reset := reset
  tx_if.io.encoded_tx_data := encoded_tx_data 
  tx_if.io.encoded_tx_data_valid := encoded_tx_data_valid 
  tx_if.io.encoded_tx_hdr := encoded_tx_hdr 
  tx_if.io.encoded_tx_hdr_valid := encoded_tx_hdr_valid
  
  io.tx_gbx_req_sync := tx_if.io.tx_gbx_req_sync
  io.tx_gbx_req_stall := tx_if.io.tx_gbx_req_stall
  tx_if.io.tx_gbx_sync := tx_gbx_sync_int

  io.serdes_tx_data := tx_if.io.serdes_tx_data
  io.serdes_tx_data_valid := tx_if.io.serdes_tx_data_valid
  io.serdes_tx_hdr := tx_if.io.serdes_tx_hdr
  io.serdes_tx_hdr_valid := tx_if.io.serdes_tx_hdr_valid
  
  tx_if.io.serdes_tx_gbx_req_sync := io.serdes_tx_gbx_req_sync
  tx_if.io.serdes_tx_gbx_req_stall := io.serdes_tx_gbx_req_stall
  io.serdes_tx_gbx_sync := tx_if.io.serdes_tx_gbx_sync
  
  tx_if.io.cfg_tx_prbs31_enable := io.cfg_tx_prbs31_enable
}



object PcsTx {
  def apply(p: PcsTxParams): PcsTx = Module(new PcsTx(
    dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse, scramblerDisable = p.scramblerDisable,
    prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline
  ))
}

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  PcsTxParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new PcsTx(
        dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW,
        gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse, scramblerDisable = p.scramblerDisable,
        prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline
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
    RunScriptFile.create(mainClassName, PcsTxParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}