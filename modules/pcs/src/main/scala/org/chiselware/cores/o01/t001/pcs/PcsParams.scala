// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.pcs

import java.io.{File, PrintWriter}
import scala.collection.mutable.LinkedHashMap

case class PcsParams(
  dataW: Int = 64,
  ctrlW: Int = 8,
  hdrW: Int = 2,
  txGbxIfEn: Boolean = true,
  rxGbxIfEn: Boolean = true,
  bitReverse: Boolean = true,
  scramblerDisable: Boolean = false,
  prbs31En: Boolean = false,
  txSerdesPipeline: Int = 1,
  rxSerdesPipeline: Int = 1,
  bitslipHighCycles: Int = 0,
  bitslipLowCycles: Int = 7,
  count125Us: Double = 125000.0 / 6.4,
)

object PcsParams {

  val simConfigMap = LinkedHashMap[String, PcsParams](
    "config" -> PcsParams(),
  )

  val synConfigMap = LinkedHashMap[String, PcsParams](
    "pcs_inst" -> PcsParams(),
  )

  val synConfigs = synConfigMap.keys.mkString(" ")

  // Backward-compatible aliases for existing code
  val SimConfigMap: LinkedHashMap[String, PcsParams] = simConfigMap
  val SynConfigMap: LinkedHashMap[String, PcsParams] = synConfigMap
  val SynConfigs: String = synConfigs

}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Pcs.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    sdcFileWriter.write(sdcFileData)
    sdcFileWriter.close()
  }
}
