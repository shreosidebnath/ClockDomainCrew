package org.chiselware.cores.o01.t001.mac.stats
import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}


case class CustomArbiterParams(
  val ports: Int = 2,
  val arbRoundRobin: Boolean = true,
  val arbBlock: Boolean = true,
  val arbBlockAck: Boolean = true,
  val lsbHighPrio: Boolean = false
)

object CustomArbiterParams {
  val simConfigMap = LinkedHashMap[String, CustomArbiterParams](
    "config" -> CustomArbiterParams()
  )

  val synConfigMap = LinkedHashMap[String, CustomArbiterParams](
    "custom_arbiter_inst" -> CustomArbiterParams()
  )

  val synConfigs = synConfigMap.keys.mkString(" ")
}

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/CustomArbiter.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}