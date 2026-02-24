package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class PcsRxBerMonParams(
  hdrW: Int = 2,
  count125Us: Double = 125000.0 / 6.4
)

object PcsRxBerMonParams {
    val simConfigMap = LinkedHashMap[String, PcsRxBerMonParams](
        "config" -> PcsRxBerMonParams()
    )

    val synConfigMap = LinkedHashMap[String, PcsRxBerMonParams](
        "pcs_rx_ber_mon_inst" -> PcsRxBerMonParams()
    )

    val synConfigs = synConfigMap.keys.mkString(" ")
}

// object sdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxBerMon.sdc")
//     val sdcFile = new PrintWriter(sdcFileName)
//     sdcFile.write(sdcFileData)
//     sdcFile.close()
//   }
// }