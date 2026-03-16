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

class AxisArbMuxTb(val p: AxisArbMuxParams) extends Module {
  val io = IO(new Bundle {
    // Array of input streams
    val s_axis = Vec(p.sCount, Flipped(new AxisStream(p)))
    // Single output stream
    val m_axis = new AxisStream(p)
  })

  // Instantiate the multiplexer
  val dut = Module(new AxisArbMux(p))
  
  // Wire up the testbench IOs directly to the DUT
  dut.io.s_axis <> io.s_axis
  io.m_axis <> dut.io.m_axis
}