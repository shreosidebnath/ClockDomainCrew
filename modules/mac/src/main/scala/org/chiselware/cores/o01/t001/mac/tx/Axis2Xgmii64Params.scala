package org.chiselware.cores.o01.t001.mac.tx
import java.io.{ File, PrintWriter }
import scala.collection.mutable.LinkedHashMap

case class Axis2Xgmii64Params(
    dataW: Int = 64,
    ctrlW: Int = 8,
    gbxIfEn: Boolean = true,
    gbxCnt: Int = 1,
    paddingEn: Boolean = true,
    dicEn: Boolean = true,
    minFrameLen: Int = 64,
    ptpTsEn: Boolean = false,
    ptpTsFmtTod: Boolean = true,
    ptpTsW: Int = 96,
    txCplCtrlInTuser: Boolean = false)

object Axis2Xgmii64Params {
  val simConfigMap = LinkedHashMap[String, Axis2Xgmii64Params](
    "config" -> Axis2Xgmii64Params()
  )

  val synConfigMap = LinkedHashMap[String, Axis2Xgmii64Params](
    "mac_axis_2_xgmii_64_inst" -> Axis2Xgmii64Params()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Axis2Xgmii64.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    sdcFileWriter.write(sdcFileData)
    sdcFileWriter.close()
  }
}