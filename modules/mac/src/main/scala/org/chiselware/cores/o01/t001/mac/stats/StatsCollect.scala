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
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class StatsCollect(val p: StatsCollectParams) extends Module {
  val io = IO(new Bundle {
    val stat_inc = Input(Vec(p.cnt, UInt(p.incW.W)))
    val stat_valid = Input(Vec(p.cnt, Bool()))
    val stat_str = Input(Vec(p.cnt, UInt(64.W))) // Matches SV [8*8-1:0] stat_str[CNT]
    
    // AXI Stream Source
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

  // Width calculations
  val cntW = if (p.cnt > 1) log2Ceil(p.cnt) else 1
  val periodCntW = log2Ceil(p.updatePeriod + 1)
  val accW = p.incW + cntW + 1

  // FSM States
  val sRead :: sWrite :: Nil = Enum(2)
  val stateReg = RegInit(sRead)

  // AXI Stream Registers
  val tdataReg = RegInit(0.U(p.dataW.W))
  val tidReg = RegInit(0.U(p.idW.W))
  val tvalidReg = RegInit(false.B)
  val tuserReg = RegInit(0.U(p.userW.W))

  // Internal Logic Registers
  val countReg = RegInit(0.U(cntW.W))
  val updatePeriodReg = RegInit(p.updatePeriod.U(periodCntW.W))
  val zeroReg = RegInit(true.B)
  val updateReqReg = RegInit(false.B)
  val updateReg = RegInit(false.B)
  val updateShiftReg = RegInit(0.U(p.cnt.W))
  val chReg = RegInit(0.U(accW.W))

  // Memory Array
  val mem = Reg(Vec(p.cnt, UInt(p.dataW.W)))
  val memRdDataReg = RegInit(0.U(p.dataW.W))

  // Accumulators
  val accRegs = RegInit(VecInit(Seq.fill(p.cnt)(0.U(accW.W))))
  val accClear = WireDefault(VecInit(Seq.fill(p.cnt)(false.B)))

  for (n <- 0 until p.cnt) {
    when(accClear(n)) {
      when(io.stat_valid(n) && io.gate) {
        accRegs(n) := io.stat_inc(n)
      }.otherwise {
        accRegs(n) := 0.U
      }
    }.otherwise {
      when(io.stat_valid(n) && io.gate) {
        accRegs(n) := accRegs(n) + io.stat_inc(n)
      }
    }
  }

  // Next State Wires
  val nextState = WireDefault(sRead)
  val nextTdata = WireDefault(tdataReg)
  val nextTid = WireDefault(tidReg)
  val nextTvalid = WireDefault(tvalidReg && !io.m_axis_stat_tready)
  val nextTuser = WireDefault(tuserReg)
  
  val countNext = WireDefault(countReg)
  val zeroNext = WireDefault(zeroReg)
  val updateReqNext = WireDefault(updateReqReg)
  val updateNext = WireDefault(updateReg)
  val chNext = WireDefault(chReg)

  val memWrEn = WireDefault(false.B)
  val memRdEn = WireDefault(false.B)
  val memWrData = WireDefault(memRdDataReg + chReg)

  when(!tvalidReg) {
    nextTdata := memRdDataReg + chReg
    nextTid := countReg + p.idBase.U
  }

  val shiftIn = updateReg || updateShiftReg(0)
  val shiftNext = WireDefault(Cat(shiftIn, updateShiftReg(p.cnt - 1, 1)))

  // FSM Control Logic
  switch(stateReg) {
    is(sRead) {
      accClear(countReg) := true.B
      chNext := accRegs(countReg)
      memRdEn := true.B
      nextState := sWrite
    }
    is(sWrite) {
      memWrEn := true.B
      
      when(zeroReg) {
        memWrData := chReg
      }.elsewhen(!tvalidReg && shiftIn) {
        shiftNext := Cat(false.B, updateShiftReg(p.cnt - 1, 1)) // clear MSB
        memWrData := 0.U
        nextTdata := memRdDataReg + chReg
        nextTid := countReg + p.idBase.U
        nextTvalid := (memRdDataReg =/= 0.U) || (chReg =/= 0.U)
        nextTuser := 0.U
      }.otherwise {
        memWrData := memRdDataReg + chReg
      }

      when(countReg === (p.cnt - 1).U) {
        zeroNext := false.B
        updateReqNext := false.B
        updateNext := updateReqReg
        countNext := 0.U
      }.otherwise {
        countNext := countReg + 1.U
      }
      nextState := sRead
    }
  }

  // Update Timer Logic
  when(updatePeriodReg === 0.U || io.update) {
    updateReqNext := true.B
    updatePeriodReg := p.updatePeriod.U
  }.otherwise {
    updatePeriodReg := updatePeriodReg - 1.U
  }

  // Memory Access
  when(memWrEn) { mem(countReg) := memWrData }
  when(memRdEn) { memRdDataReg := mem(countReg) }

  // Assign Next States to Registers
  stateReg := nextState
  tdataReg := nextTdata
  tidReg := nextTid
  tvalidReg := nextTvalid
  tuserReg := nextTuser
  countReg := countNext
  zeroReg := zeroNext
  updateReqReg := updateReqNext
  updateReg := updateNext
  updateShiftReg := shiftNext
  chReg := chNext

  // Output Assignments
  io.m_axis_stat_tdata := tdataReg
  io.m_axis_stat_tkeep := ((1 << (p.dataW / 8)) - 1).U
  io.m_axis_stat_tvalid := tvalidReg
  io.m_axis_stat_tlast := true.B
  io.m_axis_stat_tid := tidReg
  io.m_axis_stat_tdest := 0.U
  io.m_axis_stat_tuser := Mux(p.strEn.B, tuserReg, 0.U)
}