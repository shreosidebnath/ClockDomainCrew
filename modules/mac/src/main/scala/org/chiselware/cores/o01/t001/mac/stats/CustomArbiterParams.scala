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