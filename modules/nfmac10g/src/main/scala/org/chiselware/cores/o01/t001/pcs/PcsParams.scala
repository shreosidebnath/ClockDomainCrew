package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsParams(
  val dataW: Int = 32,
  val ctrlW: Int = 4,
  val hdrW: Int = 2,
  val txGbxIfEn: Boolean = true,
  val rxGbxIfEn: Boolean = true,
  val bitReverse: Boolean = true,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val txSerdesPipeline: Int = 1,
  val rxSerdesPipeline: Int = 1,
  val bitslipHighCycles: Int = 0,
  val bitslipLowCycles: Int = 7,
  val count125Us: Double = 125000.0/6.4
)

object PcsParams {

    val simConfigMap = LinkedHashMap[String, PcsParams](
        "config" -> PcsParams()
    )

    val synConfigMap = LinkedHashMap[String, PcsParams](
        "pcs_inst" -> PcsParams()
    )

    val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Pcs.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}