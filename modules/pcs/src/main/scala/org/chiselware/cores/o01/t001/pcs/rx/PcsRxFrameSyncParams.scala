package org.chiselware.cores.o01.t001.pcs.rx
import scala.collection.mutable.LinkedHashMap

case class PcsRxFrameSyncParams(
    hdrW: Int = 2,
    bitslipHighCycles: Int = 0,
    bitslipLowCycles: Int = 7)

object PcsRxFrameSyncParams {
  val SimConfigMap = LinkedHashMap[String, PcsRxFrameSyncParams](
    "config" -> PcsRxFrameSyncParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsRxFrameSyncParams](
    "pcs_rx_frame_sync_inst" -> PcsRxFrameSyncParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxFrameSync.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
