package org.chiselware.cores.o01.t001.mac.rx
import java.io.{ File, PrintWriter }
import scala.collection.mutable.LinkedHashMap

case class Xgmii2Axis64Params(
    dataW: Int = 64,
    ctrlW: Int = 8,
    gbxIfEn: Boolean = true,
    ptpTsEn: Boolean = false,
    ptpTsFmtTod: Boolean = true,
    ptpTsW: Int = 96)

object Xgmii2Axis64Params {
  val SimConfigMap = LinkedHashMap[String, Xgmii2Axis64Params](
    "config" -> Xgmii2Axis64Params()
  )

  val SynConfigMap = LinkedHashMap[String, Xgmii2Axis64Params](
    "mac_xgmii_2_axis_64_inst" -> Xgmii2Axis64Params()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Xgmii2Axis64.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    sdcFileWriter.write(sdcFileData)
    sdcFileWriter.close()
  }
}