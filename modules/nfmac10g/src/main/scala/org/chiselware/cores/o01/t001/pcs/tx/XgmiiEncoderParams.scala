package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class XgmiiEncoderParams(
    dataW: Int = 32,
    ctrlW: Int = 4,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    gbxCnt: Int = 1
)

object XgmiiEncoderParams {
  val simConfigMap = LinkedHashMap[String, XgmiiEncoderParams](
    "config" -> XgmiiEncoderParams()
  )

  val synConfigMap = LinkedHashMap[String, XgmiiEncoderParams](
    "xgmii_encoder_inst" -> XgmiiEncoderParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/XgmiiEncoder.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }