package org.chiselware.cores.o01.t001.mac.tx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class Axis2Xgmii64Params(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val gbxIfEn: Boolean = true,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96,
  val txCplCtrlInTuser: Boolean = false
)

object Axis2Xgmii64Params {
  val simConfigMap = LinkedHashMap[String, Axis2Xgmii64Params](
    "config" -> Axis2Xgmii64Params()
  )

  val synConfigMap = LinkedHashMap[String, Axis2Xgmii64Params](
    "mac_axis_2_xgmii_64_inst" -> Axis2Xgmii64Params()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Axis2Xgmii64.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}