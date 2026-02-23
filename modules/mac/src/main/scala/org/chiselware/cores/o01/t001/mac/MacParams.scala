package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class MacParams(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val txGbxIfEn: Boolean = true,
  val rxGbxIfEn: Boolean = true,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96,
  val pfcEn: Boolean = false,
  val pauseEn: Boolean = false
)

object MacParams {
  val simConfigMap = LinkedHashMap[String, MacParams](
    "config" -> MacParams()
  )

  val synConfigMap = LinkedHashMap[String, MacParams](
    "mac_inst" -> MacParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Mac.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}