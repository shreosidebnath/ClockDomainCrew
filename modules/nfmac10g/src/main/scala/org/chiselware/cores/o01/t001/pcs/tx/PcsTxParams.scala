package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsTxParams(
    dataW: Int = 32,
    ctrlW: Int = 4,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    bitReverse: Boolean = true,
    scramblerDisable: Boolean = false,
    prbs31En: Boolean = false,
    serdesPipeline: Int = 1
)

object PcsTxParams {
  val simConfigMap = LinkedHashMap[String, PcsTxParams](
    "config" -> PcsTxParams()
  )

  val synConfigMap = LinkedHashMap[String, PcsTxParams](
    "pcs_tx_inst" -> PcsTxParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/PcsTx.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}