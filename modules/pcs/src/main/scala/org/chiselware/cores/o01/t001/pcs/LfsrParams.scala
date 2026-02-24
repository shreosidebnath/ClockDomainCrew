package org.chiselware.cores.o01.t001.pcs
import scala.collection.mutable.LinkedHashMap

case class LfsrParams(
    lfsrW: Int = 31,
    lfsrPoly: BigInt = 0x10000001L,
    lfsrGalois: Boolean = false,
    lfsrFeedForward: Boolean = false,
    reverse: Boolean = false,
    dataW: Int = 8,
    dataInEn: Boolean = true,
    dataOutEn: Boolean = true)

object LfsrParams {
  val SynConfigMap = LinkedHashMap[String, LfsrParams](
    "descrambler_inst" -> LfsrParams(
      lfsrW = 58,
      lfsrPoly = BigInt("8000000001", 16),
      lfsrGalois = false,
      lfsrFeedForward = true,
      reverse = true,
      dataW = 64,
      dataInEn = true,
      dataOutEn = true
    ),
    "prbs31_check_inst" -> LfsrParams(
      lfsrW = 31,
      lfsrPoly = BigInt("10000001", 16),
      lfsrGalois = false,
      lfsrFeedForward = true,
      reverse = true,
      dataW = 66,
      dataInEn = true,
      dataOutEn = true
    ),
    "scrambler_inst" -> LfsrParams(
      lfsrW = 58,
      lfsrPoly = BigInt("8000000001", 16),
      lfsrGalois = false,
      lfsrFeedForward = false,
      reverse = true,
      dataW = 64,
      dataInEn = true,
      dataOutEn = true
    ),
    "prbs31_gen_inst" -> LfsrParams(
      lfsrW = 31,
      lfsrPoly = BigInt("10000001", 16),
      lfsrGalois = false,
      lfsrFeedForward = false,
      reverse = true,
      dataW = 66,
      dataInEn = false,
      dataOutEn = true
    )
  )

  val SimConfigMap = SynConfigMap

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/Lfsr.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }