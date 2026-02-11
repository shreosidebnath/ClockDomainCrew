package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsTxInterfaceParams(
  dataW: Int = 64,
  hdrW: Int = 2,
  gbxIfEn: Boolean = false,
  bitReverse: Boolean = false,
  scramblerDisable: Boolean = false,
  prbs31En: Boolean = false,
  serdesPipeline: Int = 0
)

object PcsTxInterfaceParams {
  val simConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "config" -> PcsTxInterfaceParams()
  )

  val synConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "pcs_tx_interface_inst" -> PcsTxInterfaceParams(
        dataW = 32,
        hdrW = 2,
        gbxIfEn = true,
        bitReverse = true,
        scramblerDisable = false,
        prbs31En = false,
        serdesPipeline = 1
    )
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/PcsTxInterface.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}