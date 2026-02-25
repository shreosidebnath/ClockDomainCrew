package org.chiselware.cores.o01.t001.pcs.rx
import scala.collection.mutable.LinkedHashMap

case class PcsRxWatchdogParams(
    hdrW: Int = 2,
    count125us: Double = 125000.0 / 6.4)

object PcsRxWatchdogParams {
  val SimConfigMap = LinkedHashMap[String, PcsRxWatchdogParams](
    "config" -> PcsRxWatchdogParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsRxWatchdogParams](
    "pcs_rx_watchdog_inst" -> PcsRxWatchdogParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxWatchdog.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
