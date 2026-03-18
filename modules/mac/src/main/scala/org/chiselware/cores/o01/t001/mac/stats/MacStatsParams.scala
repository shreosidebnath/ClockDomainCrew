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

case class MacStatsParams(
  incW: Int = 4, // Changed default based on your terminal error
  dataW: Int = 16, // CHANGED FROM 64 TO 16
  keepW: Int = 2,  // CHANGED FROM 8 to 2
  idW: Int = 8,
  destW: Int = 8,
  userW: Int = 1,
  txCnt: Int = 16,
  rxCnt: Int = 16,
  // ADDED MISSING LEGACY PARAMETERS
  statTxLevel: Int = 0,
  statRxLevel: Int = 0,
  statIdBase: Int = 0,
  statUpdatePeriod: Int = 0,
  statStrEn: Boolean = false,
  statPrefixStr: String = ""
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