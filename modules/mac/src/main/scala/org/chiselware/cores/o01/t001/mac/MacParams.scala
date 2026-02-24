package org.chiselware.cores.o01.t001.mac
import java.io.{ File, PrintWriter }
import scala.collection.mutable.LinkedHashMap

case class MacParams(
    dataW: Int = 64,
    ctrlW: Int = 8,
    txGbxIfEn: Boolean = true,
    rxGbxIfEn: Boolean = true,
    gbxCnt: Int = 1,
    paddingEn: Boolean = true,
    dicEn: Boolean = true,
    minFrameLen: Int = 64,
    ptpTsEn: Boolean = false,
    ptpTsFmtTod: Boolean = true,
    ptpTsW: Int = 96,
    pfcEn: Boolean = false,
    pauseEn: Boolean = false)

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