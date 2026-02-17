package org.chiselware.cores.o01.t001.mac.tx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class Axis2Xgmii32Params(
  val dataW: Int = 32,
  val ctrlW: Int = 4,
  val gbxIfEn: Boolean = false,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsW: Int = 96,
  val txCplCtrlInTuser: Boolean = true,
  val idW: Int = 8 // Matches s_axis_tx.ID_W
)

object Axis2Xgmii32Params {
  val simConfigMap = LinkedHashMap[String, Axis2Xgmii32Params](
    "config" -> Axis2Xgmii32Params()
  )

  val synConfigMap = LinkedHashMap[String, Axis2Xgmii32Params](
    "mac_axis_2_xgmii_32_inst" -> Axis2Xgmii32Params()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Axis2Xgmii32.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}