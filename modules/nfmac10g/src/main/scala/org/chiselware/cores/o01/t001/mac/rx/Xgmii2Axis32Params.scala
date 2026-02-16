package org.chiselware.cores.o01.t001.mac.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class Xgmii2Axis32Params(
    dataW: Int = 32,
    ctrlW: Int = 4,
    gbxIfEn: Boolean = false,
    ptpTsEn: Boolean = false,
    ptpTsW: Int = 96
)

object Xgmii2Axis32Params {
  val simConfigMap = LinkedHashMap[String, Xgmii2Axis32Params](
    "config" -> Xgmii2Axis32Params()
  )

  val synConfigMap = LinkedHashMap[String, Xgmii2Axis32Params](
    "mac_xgmii_2_axis_32_inst" -> Xgmii2Axis32Params()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Xgmii2Axis32.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}