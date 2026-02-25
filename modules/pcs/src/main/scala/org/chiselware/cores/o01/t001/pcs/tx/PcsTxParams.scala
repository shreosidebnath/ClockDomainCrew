package org.chiselware.cores.o01.t001.pcs.tx
import java.io.{ File, PrintWriter }
import scala.collection.mutable.LinkedHashMap

case class PcsTxParams(
    dataW: Int = 64,
    ctrlW: Int = 8,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    bitReverse: Boolean = true,
    scramblerDisable: Boolean = false,
    prbs31En: Boolean = false,
    serdesPipeline: Int = 1)

object PcsTxParams {
  val SimConfigMap = LinkedHashMap[String, PcsTxParams](
    "config" -> PcsTxParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsTxParams](
    "pcs_tx_inst" -> PcsTxParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/PcsTx.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    sdcFileWriter.write(sdcFileData)
    sdcFileWriter.close()
  }
}
