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
      // "config_default_stats" -> MacParams(statEn = true),
      "config_default_no_stats" -> MacParams(statEn = false)
    )

  val synConfigMap: LinkedHashMap[String, MacParams] = 
    LinkedHashMap(
      // "mac_default_stats" -> MacParams(statEn = true),
      "mac_default_no_stats" -> MacParams(statEn = false)
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
  def create(
      p: MacParams,
      sdcFilePath: String
    ): Unit = {
    val period = 6.400      // ns (Standard for 156.25 MHz)
    val statPeriod = 10.000 // ns
    val dutyCycle = 0.50
    val inputDelayPct = 0.20
    val outputDelayPct = 0.20

    val inputDelay = period * inputDelayPct   // 1.28ns
    val outputDelay = period * outputDelayPct // 1.28ns
    val fallingEdge = period * dutyCycle      // 3.2ns
    val statFallingEdge = statPeriod * dutyCycle

    val sdcFileData =
      s"""
      |# --- Clock Definitions ---
      |create_clock -name tx_clk -period $period -waveform {0 $fallingEdge} [get_ports {txClk}]
      |create_clock -name rx_clk -period $period -waveform {0 $fallingEdge} [get_ports {rxClk}]
      |create_clock -name stat_clk -period $statPeriod -waveform {0 $statFallingEdge} [get_ports {statClk}]
      |
      |# --- Asynchronous Clock Groups ---
      |# This prevents the tool from timing paths between unrelated clock domains
      |set_clock_groups -asynchronous \\
      |    -group [get_clocks {tx_clk}] \\
      |    -group [get_clocks {rx_clk}] \\
      |    -group [get_clocks {stat_clk}]
      |
      |# --- IO Delays (Calculated at ${inputDelayPct * 100}% of period) ---
      |# TX Path
      |set_input_delay -clock [get_clocks {tx_clk}] $inputDelay [get_ports {sAxisTx_tdata[*] sAxisTx_tkeep[*] sAxisTx_tlast sAxisTx_tvalid}]
      |set_output_delay -clock [get_clocks {tx_clk}] $outputDelay [get_ports {xgmiiTxd[*] xgmiiTxc[*] xgmiiTxValid}]
      |
      |# RX Path
      |set_input_delay -clock [get_clocks {rx_clk}] $inputDelay [get_ports {xgmiiRxd[*] xgmiiRxc[*] xgmiiRxValid}]
      |set_output_delay -clock [get_clocks {rx_clk}] $outputDelay [get_ports {mAxisRx_tdata[*] mAxisRx_tkeep[*] mAxisRx_tlast mAxisRx_tvalid}]
      |
      |# --- False Paths ---
      |set_false_path -from [get_ports {txRst rxRst statRst}]
      |# Status signals crossing to CPUs/Stats are usually quasi-static
      |set_false_path -through [get_ports {statTxPktLen[*] statRxPktLen[*]}]
      """.stripMargin.trim

    println(s"Writing MAC/PCS SDC file to $sdcFilePath")
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Mac.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(sdcFileData)
    sdcFile.close()
  }
}