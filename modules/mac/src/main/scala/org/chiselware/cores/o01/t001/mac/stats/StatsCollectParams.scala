// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

case class StatsCollectParams(
  cnt: Int = 8,
  incW: Int = 4,
  idBase: Int = 0,
  updatePeriod: Int = 1024,
  strEn: Boolean = false,
  prefixStr: String = "MAC",
  // AXI Stream widths
  dataW: Int = 64,
  idW: Int = 8,
  userW: Int = 1
)

object StatsCollectParams {
  // These perfectly match the tx_stats_inst and rx_stats_inst in the Verilog wrapper
  val synConfigMap = LinkedHashMap[String, StatsCollectParams](
    "tx_stats_inst" -> StatsCollectParams(
      cnt = 16,
      incW = 4,
      idBase = 0,
      updatePeriod = 1024,
      strEn = false,
      prefixStr = "MAC"
    ),
    "rx_stats_inst" -> StatsCollectParams(
      cnt = 16,
      incW = 4,
      idBase = 16,
      updatePeriod = 1024,
      strEn = false,
      prefixStr = "MAC"
    )
  )

  val simConfigMap = synConfigMap
  val synConfigs = synConfigMap.keys.mkString(" ")
}