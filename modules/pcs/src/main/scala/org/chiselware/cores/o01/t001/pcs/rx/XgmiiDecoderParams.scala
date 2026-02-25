package org.chiselware.cores.o01.t001.pcs.rx
import scala.collection.mutable.LinkedHashMap

case class XgmiiDecoderParams(
    dataW: Int = 64,
    ctrlW: Int = 8,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true)

object XgmiiDecoderParams {
  val SimConfigMap = LinkedHashMap[String, XgmiiDecoderParams](
    "config" -> XgmiiDecoderParams()
  )

  val SynConfigMap = LinkedHashMap[String, XgmiiDecoderParams](
    "xgmii_decoder_inst" -> XgmiiDecoderParams(
      dataW = 32,
      ctrlW = 4,
      hdrW = 2,
      gbxIfEn = true
    )
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/XgmiiDecoder.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
