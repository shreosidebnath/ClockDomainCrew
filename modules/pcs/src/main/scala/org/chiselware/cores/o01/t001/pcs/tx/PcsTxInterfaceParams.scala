package org.chiselware.cores.o01.t001.pcs.tx
import scala.collection.mutable.LinkedHashMap

case class PcsTxInterfaceParams(
    dataW: Int = 64,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    bitReverse: Boolean = true,
    scramblerDisable: Boolean = false,
    prbs31En: Boolean = false,
    serdesPipeline: Int = 1)

object PcsTxInterfaceParams {
  val SimConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "config" -> PcsTxInterfaceParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsTxInterfaceParams](
    "pcs_tx_interface_inst" -> PcsTxInterfaceParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

// object SdcFile {
//   def create(sdcFilePath: String): Unit = {
//     val sdcFileData = ""
//     val sdcFileDir = new File(sdcFilePath)
//     sdcFileDir.mkdirs()
//     val sdcFileName = new File(s"$sdcFilePath/PcsTxInterface.sdc")
//     val sdcFileWriter = new PrintWriter(sdcFileName)
//     sdcFileWriter.write(sdcFileData)
//     sdcFileWriter.close()
//   }
// }
