package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class XgmiiDecoderParams(
  dataW: Int = 64,
  ctrlW: Int = 8,
  hdrW: Int = 2,
  gbxIfEn: Boolean = false,
)

object XgmiiDecoderParams {
  val simConfigMap = LinkedHashMap[String, XgmiiDecoderParams](
    "config" -> XgmiiDecoderParams()
  )

  val synConfigMap = LinkedHashMap[String, XgmiiDecoderParams](
    "xgmii_decoder_inst" -> XgmiiDecoderParams(
        dataW = 32,
        ctrlW = 4,
        hdrW = 2,
        gbxIfEn = true,
    )
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/XgmiiDecoder.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}