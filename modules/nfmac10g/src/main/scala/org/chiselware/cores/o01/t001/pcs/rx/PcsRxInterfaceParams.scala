package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsRxInterfaceParams(
  val dataW: Int = 32,
  val hdrW: Int = 2,
  val gbxIfEn: Boolean = true,
  val bitReverse: Boolean = true,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val serdesPipeline: Int = 1,
  val bitslipHighCycles: Int = 0,
  val bitslipLowCycles: Int = 7,
  val count125Us: Double = 125000.0 / 6.4
)

object PcsRxInterfaceParams {

    val simConfigMap = LinkedHashMap[String, PcsRxInterfaceParams](
        "config" -> PcsRxInterfaceParams()
    )

    val synConfigMap = LinkedHashMap[String, PcsRxInterfaceParams](
        "pcs_rx_interface_inst" -> PcsRxInterfaceParams()
    )

    val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxInterface.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }