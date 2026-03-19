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

/** Configuration parameters for the 10GBASE-R PCS core.
  *
  * @constructor create a new PCS configuration
  * @param dataW width of the internal data bus (typically 32 or 64)
  * @param ctrlW width of the control/keep bus (typically dataW / 8)
  * @param hdrW width of the 64b/66b sync header (2 bits)
  * @param txGbxIfEn enable gearbox interface for the TX path
  * @param rxGbxIfEn enable gearbox interface for the RX path
  * @param bitReverse if true, reverses bit order for SERDES compatibility
  * @param scramblerDisable if true, disables the polynomial scrambler (for debugging)
  * @param prbs31En enable hardware generation/checking of PRBS31 test patterns
  * @param txSerdesPipeline number of pipeline stages in the TX SERDES interface
  * @param rxSerdesPipeline number of pipeline stages in the RX SERDES interface
  * @param bitslipHighCycles number of cycles to hold the bitslip pulse high
  * @param bitslipLowCycles dead-time (cooldown) between bitslip attempts
  * @param count125Us constant used to calculate the 125us window for BER monitoring
  *
  * @author ClockDomainCrew
  */
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
) {
  // Validation checks for bus widths
  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  require(ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")
  require(hdrW == 2, "Error: Interface requires 2-bit sync header")
}

object PcsParams {
  val simConfigMap = LinkedHashMap[String, PcsParams](
    "pcs_default" -> PcsParams(),
  )

  val synConfigMap = LinkedHashMap[String, PcsParams](
    "pcs_default" -> PcsParams(),
  )

  val synConfigs = synConfigMap.keys.mkString(" ")

  // Backward-compatible aliases for existing code
  val SimConfigMap: LinkedHashMap[String, PcsParams] = simConfigMap
  val SynConfigMap: LinkedHashMap[String, PcsParams] = synConfigMap
  val SynConfigs: String = synConfigs

}

object SdcFile {
  def create(
      p: PcsParams,
      sdcFilePath: String
    ): Unit = {
    val period = 6.400 // ns (Standard for 156.25 MHz)
    val dutyCycle = 0.50
    val inputDelayPct = 0.20
    val outputDelayPct = 0.20

    val inputDelay = period * inputDelayPct   // 1.28ns
    val outputDelay = period * outputDelayPct // 1.28ns
    val fallingEdge = period * dutyCycle      // 3.2ns

    val sdcFileData =
      s"""
      |# --- 10GBASE-R PCS Timing Constraints ---
      |create_clock -name tx_pcs_clk -period $period -waveform {0 $fallingEdge} [get_ports {io_txClk}]
      |create_clock -name rx_pcs_clk -period $period -waveform {0 $fallingEdge} [get_ports {io_rxClk}]
      |
      |# --- Asynchronous Clock Groups ---
      |set_clock_groups -asynchronous \\
      |    -group [get_clocks {tx_pcs_clk}] \\
      |    -group [get_clocks {rx_pcs_clk}]
      |
      |# --- IO Delays ---
      |
      |# XGMII Side (Interfacing with MAC)
      |set_input_delay -clock [get_clocks {tx_pcs_clk}] $inputDelay [get_ports {io_xgmiiTxd[*] io_xgmiiTxc[*] io_xgmiiTxValid}]
      |set_output_delay -clock [get_clocks {rx_pcs_clk}] $outputDelay [get_ports {io_xgmiiRxd[*] io_xgmiiRxc[*] io_xgmiiRxValid}]
      |
      |# SERDES Side (Interfacing with Transceiver/PMA)
      |set_input_delay -clock [get_clocks {rx_pcs_clk}] $inputDelay [get_ports {io_serdesRxData[*] io_serdesRxHdr[*] io_serdesRxDataValid io_serdesRxHdrValid}]
      |set_output_delay -clock [get_clocks {tx_pcs_clk}] $outputDelay [get_ports {io_serdesTxData[*] io_serdesTxHdr[*] io_serdesTxDataValid io_serdesTxHdrValid}]
      |
      |# --- False Paths ---
      |set_false_path -from [get_ports {io_txRst io_rxRst}]
      |set_false_path -through [get_ports {io_rxStatus io_rxBlockLock io_rxHighBer io_rxErrorCount[*]}]
      """.stripMargin.trim

    println(s"Writing PCS SDC file to $sdcFilePath")
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/Pcs.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    try {
      sdcFile.write(sdcFileData)
    } finally {
      sdcFile.close()
    }
  }
}