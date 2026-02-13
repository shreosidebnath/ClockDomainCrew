package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsRxFrameSyncParams(
  hdrW: Int = 2,
  bitslipHighCycles: Int = 0,
  bitslipLowCycles: Int = 7
)

object PcsRxFrameSyncParams {
    val simConfigMap = LinkedHashMap[String, PcsRxFrameSyncParams](
        "config" -> PcsRxFrameSyncParams()
    )

    val synConfigMap = LinkedHashMap[String, PcsRxFrameSyncParams](
        "pcs_rx_frame_sync_inst" -> PcsRxFrameSyncParams()
    )

    val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxFrameSync.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }