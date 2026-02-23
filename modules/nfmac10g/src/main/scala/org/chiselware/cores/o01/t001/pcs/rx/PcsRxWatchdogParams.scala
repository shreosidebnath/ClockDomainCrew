package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsRxWatchdogParams(
  hdrW: Int = 2,
  count125us: Double = 125000.0 / 6.4
)

object PcsRxWatchdogParams {
    val simConfigMap = LinkedHashMap[String, PcsRxWatchdogParams](
        "config" -> PcsRxWatchdogParams()
    )

    val synConfigMap = LinkedHashMap[String, PcsRxWatchdogParams](
        "pcs_rx_watchdog_inst" -> PcsRxWatchdogParams()
    )

    val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxWatchdog.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }