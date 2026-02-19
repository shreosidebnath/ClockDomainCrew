package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class Mac(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val txGbxIfEn: Boolean = false,
  val rxGbxIfEn: Boolean = false,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96,
  val pfcEn: Boolean = false,
  val pauseEn: Boolean = false,
  val statEn: Boolean = false,
  val statTxLevel: Int = 1,
  val statRxLevel: Int = 1,
  val statIdBase: Int = 0,
  val statUpdatePeriod: Int = 1024,
  val statStrEn: Boolean = false,
  val statPrefixStr: String = "MAC" 
) extends RawModule {
    val io = IO(new Bundle{
        


    })
}


object Mac {
  def apply(p: MacParams): Mac = Module(new Mac(
  dataW = p.dataW,
  ctrlW = p.ctrlW,
  txGbxIfEn = p.txGbxIfEn,
  rxGbxIfEn = p.rxGbxIfEn,
  gbxCnt = p.gbxCnt,
  paddingEn = p.paddingEn,
  dicEn = p.dicEn,
  minFrameLen = p.minFrameLen,
  ptpTsEn = p.ptpTsEn,
  ptpTsFmtTod = p.ptpTsFmtTod,
  ptpTsW = p.ptpTsW,
  pfcEn = p.pfcEn,
  pauseEn = p.pauseEn,
  statEn = p.statEn,
  statTxLevel = p.statTxLevel,
  statRxLevel = p.statRxLevel,
  statIdBase  = p.statIdBase,
  statUpdatePeriod = p.statUpdatePeriod,
  statStrEn = p.statStrEn,
  statPrefixStr = p.statPrefixStr
  ))
}


object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  MacParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Mac(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        txGbxIfEn = p.txGbxIfEn,
        rxGbxIfEn = p.rxGbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        pfcEn = p.pfcEn,
        pauseEn = p.pauseEn,
        statEn = p.statEn,
        statTxLevel = p.statTxLevel,
        statRxLevel = p.statRxLevel,
        statIdBase  = p.statIdBase,
        statUpdatePeriod = p.statUpdatePeriod,
        statStrEn = p.statStrEn,
        statPrefixStr = p.statPrefixStr
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
    RunScriptFile.create(mainClassName, MacParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}