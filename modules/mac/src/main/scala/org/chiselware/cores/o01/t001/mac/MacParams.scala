// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.mac
import java.io.{File, PrintWriter}
import scala.collection.mutable.LinkedHashMap

case class MacParams(
  dataW: Int = 64,
  ctrlW: Int = 8,
  txGbxIfEn: Boolean = true,
  rxGbxIfEn: Boolean = true,
  gbxCnt: Int = 1,
  paddingEn: Boolean = true,
  dicEn: Boolean = true,
  minFrameLen: Int = 64,
  ptpTsEn: Boolean = false,
  ptpTsFmtTod: Boolean = true,
  ptpTsW: Int = 96,
  statEn: Boolean = true,
  statTxLevel: Int = 1,
  statRxLevel: Int = 1,
  statIdBase: Int = 0,
  statUpdatePeriod: Int = 1024,
  statStrEn: Boolean = false,
  statPrefixStr: String = "MAC"
) {
  require(dataW == 64, s"Error: Interface width must be 64 (instance dataW=$dataW)")
  require(ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")
}

object MacParams {
  val simConfigMap: LinkedHashMap[String, MacParams] = 
    LinkedHashMap(
      "config" -> MacParams()
    )

  val synConfigMap: LinkedHashMap[String, MacParams] = 
    LinkedHashMap(
      "mac_inst" -> MacParams()
    )

  val synConfigs: String = synConfigMap.keys.mkString(" ")
}

object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = ""
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    
    val sdcFileName = new File(sdcFilePath, "Mac.sdc")
    val sdcFileWriter = new PrintWriter(sdcFileName)
    
    try {
      sdcFileWriter.write(sdcFileData)
    } finally {
      sdcFileWriter.close()
    }
  }
}