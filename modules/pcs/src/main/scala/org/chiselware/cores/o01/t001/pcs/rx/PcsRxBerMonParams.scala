package org.chiselware.cores.o01.t001.pcs.rx
import scala.collection.mutable.LinkedHashMap

case class PcsRxBerMonParams(
    hdrW: Int = 2,
    count125Us: Double = 125000.0 / 6.4)

object PcsRxBerMonParams {
  val SimConfigMap = LinkedHashMap[String, PcsRxBerMonParams](
    "config" -> PcsRxBerMonParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsRxBerMonParams](
    "pcs_rx_ber_mon_inst" -> PcsRxBerMonParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxBerMon.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
