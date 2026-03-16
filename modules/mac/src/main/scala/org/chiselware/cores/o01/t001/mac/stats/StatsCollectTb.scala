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

class StatsCollectTb(val p: StatsCollectParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())

    val stat_inc = Input(Vec(p.cnt, UInt(p.incW.W)))
    val stat_valid = Input(Vec(p.cnt, Bool()))
    val stat_str = Input(Vec(p.cnt, UInt(64.W))) // Matches main module
    
    val m_axis_stat_tdata = Output(UInt(p.dataW.W))
    val m_axis_stat_tkeep = Output(UInt((p.dataW/8).W))
    val m_axis_stat_tvalid = Output(Bool())
    val m_axis_stat_tready = Input(Bool())
    val m_axis_stat_tlast = Output(Bool())
    val m_axis_stat_tid = Output(UInt(p.idW.W))
    val m_axis_stat_tdest = Output(UInt(8.W))
    val m_axis_stat_tuser = Output(UInt(p.userW.W))

    val gate = Input(Bool())
    val update = Input(Bool())
  })

  val dut = Module(new StatsCollect(p))

  // Explicit Clock and Reset wiring
  dut.clock := io.clk
  dut.reset := io.rst

  // Input wiring
  dut.io.stat_inc := io.stat_inc
  dut.io.stat_valid := io.stat_valid
  dut.io.stat_str := io.stat_str
  dut.io.gate := io.gate
  dut.io.update := io.update
  dut.io.m_axis_stat_tready := io.m_axis_stat_tready

  // Output wiring
  io.m_axis_stat_tdata := dut.io.m_axis_stat_tdata
  io.m_axis_stat_tkeep := dut.io.m_axis_stat_tkeep
  io.m_axis_stat_tvalid := dut.io.m_axis_stat_tvalid
  io.m_axis_stat_tlast := dut.io.m_axis_stat_tlast
  io.m_axis_stat_tid := dut.io.m_axis_stat_tid
  io.m_axis_stat_tdest := dut.io.m_axis_stat_tdest
  io.m_axis_stat_tuser := dut.io.m_axis_stat_tuser
}