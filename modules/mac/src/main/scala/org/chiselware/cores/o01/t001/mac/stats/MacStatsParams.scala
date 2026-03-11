package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class MacStatsParams(
  incW: Int = 1,
  dataW: Int = 64,
  keepW: Int = 8,
  idW: Int = 8,
  destW: Int = 8,
  userW: Int = 1,
  txCnt: Int = 16,
  rxCnt: Int = 16
) {
  // Pass these parameters to the sub-modules to guarantee perfect matching
  val fifoParams = AsyncFifoParams(
    depth = 16384, ramPipeline = 2, frameFifo = true, 
    dropBadFrame = true, dropWhenFull = true, dropOversizeFrame = true,
    dataW = dataW, keepW = keepW, idW = idW, destW = destW, userW = userW
  )
  val muxParams = AxisArbMuxParams(
    sCount = 2, dataW = dataW, keepW = keepW, idW = idW, destW = destW, userW = userW
  )
}

object MacStatsParams {
  val synConfigMap = LinkedHashMap[String, MacStatsParams](
    "taxi_eth_mac_stats_inst" -> MacStatsParams()
  )

  val simConfigMap = synConfigMap
  val synConfigs = synConfigMap.keys.mkString(" ")
}

object MacStatsSdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/MacStats.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}