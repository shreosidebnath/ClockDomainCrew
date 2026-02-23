package org.chiselware.cores.o01.t001.mac.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class Xgmii2Axis64Params(
    dataW: Int = 64,
    ctrlW: Int = 8,
    gbxIfEn: Boolean = true,
    ptpTsEn: Boolean = false,
    ptpTsFmtTod: Boolean = true,
    ptpTsW: Int = 96
)

object Xgmii2Axis64Params {
  val simConfigMap = LinkedHashMap[String, Xgmii2Axis64Params](
    "config" -> Xgmii2Axis64Params()
  )

  val synConfigMap = LinkedHashMap[String, Xgmii2Axis64Params](
    "mac_xgmii_2_axis_64_inst" -> Xgmii2Axis64Params()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Xgmii2Axis64.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}