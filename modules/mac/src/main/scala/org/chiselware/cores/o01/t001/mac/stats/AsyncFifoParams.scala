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

case class AsyncFifoParams(
  depth: Int = 4096,
  fifoRamstyle: String = "auto",
  ramPipeline: Int = 1,
  outputFifoEn: Boolean = false,
  outputFifoRamstyle: String = "distributed",
  frameFifo: Boolean = false,
  userBadFrameValue: Int = 1,
  userBadFrameMask: Int = 1,
  dropOversizeFrame: Boolean = false,
  dropBadFrame: Boolean = false,
  dropWhenFull: Boolean = false,
  markWhenFull: Boolean = false,
  pauseEn: Boolean = false,
  framePause: Boolean = false,
  
  // AXI Stream interface parameters
  dataW: Int = 64,
  keepEn: Boolean = true,
  keepW: Int = 8,
  strbEn: Boolean = false,
  lastEn: Boolean = true,
  idEn: Boolean = true,
  idW: Int = 8,
  destEn: Boolean = false,
  destW: Int = 8,
  userEn: Boolean = true,
  userW: Int = 1
)

object AsyncFifoParams {
  val synConfigMap = LinkedHashMap[String, AsyncFifoParams](
    "tx_stat_fifo_inst" -> AsyncFifoParams(
      depth = 16384,
      ramPipeline = 2,
      frameFifo = true,
      dropBadFrame = true,
      dropWhenFull = true,
      dropOversizeFrame = true
    ),
    "rx_stat_fifo_inst" -> AsyncFifoParams(
      depth = 16384,
      ramPipeline = 2,
      frameFifo = true,
      dropBadFrame = true,
      dropWhenFull = true,
      dropOversizeFrame = true
    )
  )

  val simConfigMap = synConfigMap
  val synConfigs = synConfigMap.keys.mkString(" ")
}