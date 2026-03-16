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

class AsyncFifoTb(val p: AsyncFifoParams) extends Module {
  val io = IO(new Bundle {
    val sClk = Input(Clock())
    val sRst = Input(Bool())
    val mClk = Input(Clock())
    val mRst = Input(Bool())
    
    val sAxis = Flipped(new Bundle {
        val tdata = UInt(p.dataW.W)
        val tvalid = Bool()
        val tready = Output(Bool())
    })
    val mAxis = new Bundle {
        val tdata = Output(UInt(p.dataW.W))
        val tvalid = Output(Bool())
        val tready = Input(Bool())
    }
  })

  val dut = Module(new AsyncFifo(p))
  dut.io.sClk := io.sClk
  dut.io.sRst := io.sRst
  dut.io.mClk := io.mClk
  dut.io.mRst := io.mRst
  
  // Minimal wiring for TB
  dut.io.sAxisTdata := io.sAxis.tdata
  dut.io.sAxisTvalid := io.sAxis.tvalid
  io.sAxis.tready := dut.io.sAxisTready
  
  io.mAxis.tdata := dut.io.mAxisTdata
  io.mAxis.tvalid := dut.io.mAxisTvalid
  dut.io.mAxisTready := io.mAxis.tready
  
  // Tie off unused
  dut.io.sAxisTkeep := 0.U
  dut.io.sAxisTstrb := 0.U
  dut.io.sAxisTlast := false.B
  dut.io.sAxisTid := 0.U
  dut.io.sAxisTdest := 0.U
  dut.io.sAxisTuser := 0.U
}