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
  val simConfigMap = LinkedHashMap[String, PcsTxParams](
    "config" -> PcsTxParams()
  )

  val synConfigMap = LinkedHashMap[String, PcsTxParams](
    "pcs_tx_inst" -> PcsTxParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/PcsTx.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}
