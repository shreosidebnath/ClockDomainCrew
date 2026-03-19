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

/** Configuration parameters for the Ethernet MAC core.
  *
  * @constructor create a new Mac configuration
  * @param dataW width of the XGMII data bus (fixed to 64 for this implementation)
  * @param ctrlW width of the XGMII control bus (fixed to 8 for 64-bit data)
  * @param txGbxIfEn enable gearbox interface for the transmit path
  * @param rxGbxIfEn enable gearbox interface for the receive path
  * @param gbxCnt number of gearbox synchronization signals
  * @param paddingEn enable automatic padding of short frames to minFrameLen
  * @param dicEn enable Deficit Idle Count (DIC) for precise Inter-Frame Gap (IFG) management
  * @param minFrameLen minimum Ethernet frame length in bytes (usually 64)
  * @param ptpTsEn enable PTP timestamping support
  * @param ptpTsFmtTod select PTP timestamp format (True: TOD, False: Raw Counter)
  * @param ptpTsW bit width of the PTP timestamp
  * @param statEn enable the internal statistics aggregator module
  * @param statTxLevel granularity of transmit statistics (0: basic, 1: standard, >1: verbose)
  * @param statRxLevel granularity of receive statistics (0: basic, 1: standard, >1: verbose)
  * @param statIdBase the base ID for the statistics bus to uniquely identify this MAC instance
  * @param statUpdatePeriod period (in clock cycles) for statistics updates
  * @param statStrEn if true, enables human-readable string metadata for status reporting
  * @param statPrefixStr prefix prepended to all statistics names for identification (e.g., "MAC")
  * @author ClockDomainCrew
  */
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
  // Validation rules for 10G/XGMII compatibility
  val dataWMsg = s"Error: Interface width must be 64 (instance dataW=$dataW)"
  require(dataW == 64, dataWMsg)
  val ctrlWMsg = s"Error: Interface requires byte (8-bit) granularity (instance ctrlW=$ctrlW, dataW=$dataW)"
  require(ctrlW * 8 == dataW, ctrlWMsg)
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

/** Utility to generate Synopsys Design Constraints (SDC) for the MAC core.
  *
  * This object creates the timing constraint files required for 
  * synthesis tools to properly time the asynchronous clock domains 
  * (RX, TX, and STAT clocks).
  */
object SdcFile {
  def create(sdcFilePath: String): Unit = {
    val sdcFileData = """|# Clocks: 156.25 MHz for 10GbE
         |create_clock -name tx_clk -period 6.4 [get_ports {txClk}]
         |create_clock -name rx_clk -period 6.4 [get_ports {rxClk}]
         |create_clock -name stat_clk -period 10.0 [get_ports {statClk}]
         |
         |# Define asynchronous relationships
         |set_clock_groups -asynchronous \
         |    -group [get_clocks {tx_clk}] \
         |    -group [get_clocks {rx_clk}] \
         |    -group [get_clocks {stat_clk}]
         |
         |# False paths for resets
         |set_false_path -from [get_ports {txRst rxRst statRst}]
         |""".stripMargin
         
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