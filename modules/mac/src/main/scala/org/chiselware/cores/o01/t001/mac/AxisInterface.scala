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
import chisel3._

/** A standard AXI-Stream Bundle definition */
class AxisInterface(val p: AxisInterfaceParams) extends Bundle {
  // By default, this interface is for outputs (src)
  // Flipped() can be used to create a sink (snk).
  val tdata = Output(UInt(p.dataW.W))
  val tkeep = Output(UInt(p.keepW.W))
  val tstrb = Output(UInt(p.keepW.W))
  val tid = Output(UInt(p.idW.W))
  val tdest = Output(UInt(p.destW.W))
  val tuser = Output(UInt(p.userW.W))
  val tlast = Output(Bool())
  val tvalid = Output(Bool())
  val tready = Input(Bool()) // tready flows in the opposite direction
}
