package org.chiselware.cores.o01.t001
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class LfsrParams(
  lfsrW: Int = 31,
  lfsrPoly: BigInt = 0x10000001L,
  lfsrGalois: Boolean = false,
  lfsrFeedForward: Boolean = false,
  reverse: Boolean = false,
  dataW: Int = 8,
  dataInEn: Boolean = true,
  dataOutEn: Boolean = true
)

object LfsrParams {
  val synConfigMap = LinkedHashMap[String, LfsrParams](
    "descrambler_inst" -> LfsrParams(
      lfsrW            = 58,
      lfsrPoly         = BigInt("8000000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = true,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "prbs31_check_inst" -> LfsrParams(
      lfsrW            = 31,
      lfsrPoly         = BigInt("10000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = true,
      reverse           = true,
      dataW            = 34,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "scrambler_inst" -> LfsrParams(
      lfsrW            = 58,
      lfsrPoly         = BigInt("8000000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "prbs31_gen_inst" -> LfsrParams(
      lfsrW            = 31,
      lfsrPoly         = BigInt("10000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 34,
      dataInEn        = false,
      dataOutEn       = true
    ),
    "eth_crc_inst" -> LfsrParams(
      lfsrW            = 32,
      lfsrPoly         = BigInt("4c11db7", 16),
      lfsrGalois       = true,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = false
    ),
    "eth_crc_1_inst" -> LfsrParams(
      lfsrW            = 32,
      lfsrPoly         = BigInt("4c11db7", 16),
      lfsrGalois       = true,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 8,
      dataInEn        = true,
      dataOutEn       = false
    ),
    "eth_crc_2_inst" -> LfsrParams(
      lfsrW            = 32,
      lfsrPoly         = BigInt("4c11db7", 16),
      lfsrGalois       = true,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 16,
      dataInEn        = true,
      dataOutEn       = false
    ),
    "eth_crc_3_inst" -> LfsrParams(
      lfsrW            = 32,
      lfsrPoly         = BigInt("4c11db7", 16),
      lfsrGalois       = true,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 24,
      dataInEn        = true,
      dataOutEn       = false
    ),
    "eth_crc_4_inst" -> LfsrParams(
      lfsrW            = 32,
      lfsrPoly         = BigInt("4c11db7", 16),
      lfsrGalois       = true,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = false
    )
  )

  val simConfigMap = synConfigMap

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Lfsr.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}