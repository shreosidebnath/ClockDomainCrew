package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class LFSRParams(
  lfsrW: Int = 31,
  lfsrPoly: BigInt = 0x10000001L,
  lfsrGalois: Boolean = false,
  lfsrFeedForward: Boolean = false,
  reverse: Boolean = false,
  dataW: Int = 8,
  dataInEn: Boolean = true,
  dataOutEn: Boolean = true
)

object LFSRParams {
  val simConfigMap = LinkedHashMap[String, LFSRParams](
    "config" -> LFSRParams()
  )

  val synConfigMap = LinkedHashMap[String, LFSRParams](
    "descrambler_inst" -> LFSRParams(
      lfsrW            = 58,
      lfsrPoly         = BigInt("8000000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = true,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "prbs31_check_inst" -> LFSRParams(
      lfsrW            = 31,
      lfsrPoly         = BigInt("10000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = true,
      reverse           = true,
      dataW            = 34,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "scrambler_inst" -> LFSRParams(
      lfsrW            = 58,
      lfsrPoly         = BigInt("8000000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 32,
      dataInEn        = true,
      dataOutEn       = true
    ),
    "prbs31_gen_inst" -> LFSRParams(
      lfsrW            = 31,
      lfsrPoly         = BigInt("10000001", 16),
      lfsrGalois       = false,
      lfsrFeedForward = false,
      reverse           = true,
      dataW            = 34,
      dataInEn        = false,
      dataOutEn       = true
    )
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/LFSR.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}