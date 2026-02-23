package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsTxInterfaceParams(
  dataW: Int = 32,
  hdrW: Int = 2,
  gbxIfEn: Boolean = true,
  bitReverse: Boolean = true,
  scramblerDisable: Boolean = false,
  prbs31En: Boolean = false,
  serdesPipeline: Int = 1
)

object PcsTxInterfaceParams {
  val simConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "config" -> PcsTxInterfaceParams()
  )

  val synConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "pcs_tx_interface_inst" -> PcsTxInterfaceParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsTxInterface.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }