package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class AxisArbMuxParams(
  sCount: Int = 2, // 2 ports for TX and RX
  updateTid: Boolean = false,
  arbRoundRobin: Boolean = true,
  arbBlock: Boolean = true,
  arbBlockAck: Boolean = true,
  arbLsbHighPrio: Boolean = false,
  
  dataW: Int = 64,
  keepW: Int = 8,
  idW: Int = 8,
  destW: Int = 8,
  userW: Int = 1
)

object AxisArbMuxParams {
  val synConfigMap = LinkedHashMap[String, AxisArbMuxParams](
    "stat_mux_inst" -> AxisArbMuxParams(sCount = 2)
  )

  val simConfigMap = synConfigMap
  val synConfigs = synConfigMap.keys.mkString(" ")
}

object AxisArbMuxSdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/AxisArbMux.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}