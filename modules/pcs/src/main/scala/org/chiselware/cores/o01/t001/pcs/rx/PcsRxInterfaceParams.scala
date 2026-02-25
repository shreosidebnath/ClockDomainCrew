package org.chiselware.cores.o01.t001.pcs.rx
import scala.collection.mutable.LinkedHashMap

case class PcsRxInterfaceParams(
    dataW: Int = 64,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    bitReverse: Boolean = true,
    scramblerDisable: Boolean = false,
    prbs31En: Boolean = false,
    serdesPipeline: Int = 1,
    bitslipHighCycles: Int = 0,
    bitslipLowCycles: Int = 7,
    count125Us: Double = 125000.0 / 6.4)

object PcsRxInterfaceParams {

  val SimConfigMap = LinkedHashMap[String, PcsRxInterfaceParams](
    "config" -> PcsRxInterfaceParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsRxInterfaceParams](
    "pcs_rx_interface_inst" -> PcsRxInterfaceParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsRxInterface.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
