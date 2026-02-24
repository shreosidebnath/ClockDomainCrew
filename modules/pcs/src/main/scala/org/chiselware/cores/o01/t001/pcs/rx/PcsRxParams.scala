package org.chiselware.cores.o01.t001.pcs.rx
import java.io.{ File, PrintWriter }
import scala.collection.mutable.LinkedHashMap

case class PcsRxParams(
    dataW: Int = 64,
    ctrlW: Int = 8,
    hdrW: Int = 2,
    gbxIfEn: Boolean = true,
    bitReverse: Boolean = true,
    scramblerDisable: Boolean = false,
    prbs31En: Boolean = false,
    serdesPipeline: Int = 1,
    bitslipHighCycles: Int = 0,
    bitslipLowCycles: Int = 7,
    count125Us: Double = 125000.0 / 6.4)

object PcsRxParams {

  val SimConfigMap = LinkedHashMap[String, PcsRxParams](
    "config" -> PcsRxParams()
  )

  val SynConfigMap = LinkedHashMap[String, PcsRxParams](
    "pcs_rx_inst" -> PcsRxParams()
  )

  val SynConfigs = SynConfigMap.keys.mkString(" ")
}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/PcsRx.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    sdcFileWriter.write(sdcFileData)
    sdcFileWriter.close()
  }
}