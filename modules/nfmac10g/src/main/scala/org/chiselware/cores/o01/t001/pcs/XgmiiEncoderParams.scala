package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class XgmiiEncoderParams(
    val DATA_W: Int = 64,
    val GBX_IF_EN: Boolean = false,
    val GBX_CNT: Int = 1
)

object XgmiiEncoderParams {
  val simConfigMap = LinkedHashMap[String, XgmiiEncoderParams](
    "config" -> XgmiiEncoderParams()
  )

  val synConfigMap = LinkedHashMap[String, XgmiiEncoderParams](
    "xgmii_encoder_inst" -> XgmiiEncoderParams(
        DATA_W = 32,
        GBX_IF_EN = true,
        GBX_CNT = 1
    )
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/XgmiiEncoder.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}