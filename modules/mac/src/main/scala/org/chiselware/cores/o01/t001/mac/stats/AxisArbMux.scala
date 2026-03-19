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
import scala.collection.mutable.LinkedHashMap
import org.chiselware.cores.o01.t001.mac.{AxisInterface, AxisInterfaceParams}

class AxisArbMux(val p: AxisArbMuxParams) extends Module {
  val io = IO(new Bundle {
    val s_axis = Vec(p.sCount, Flipped(new AxisInterface(
      AxisInterfaceParams(
        dataW = p.dataW,
        keepW = p.keepW,
        idW = p.idW,
        destW = p.destW,
        userW = p.userW
      )
    )))
    val m_axis = new AxisInterface(
      AxisInterfaceParams(
        dataW = p.dataW,
        keepW = p.keepW,
        idW = p.idW,
        destW = p.destW,
        userW = p.userW
      )
    )
  })

  val arbiter = Module(new CustomArbiter(p.sCount, p.arbRoundRobin, p.arbBlock, p.arbBlockAck, p.arbLsbHighPrio))
  
  // Pipeline Registers
  val s_axis_tdata_reg  = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.dataW.W))))
  val s_axis_tkeep_reg  = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.keepW.W))))
  val s_axis_tstrb_reg  = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.keepW.W))))
  val s_axis_tvalid_reg = RegInit(0.U(p.sCount.W))
  val s_axis_tlast_reg  = RegInit(0.U(p.sCount.W))
  val s_axis_tid_reg    = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.idW.W))))
  val s_axis_tdest_reg  = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.destW.W))))
  val s_axis_tuser_reg  = RegInit(VecInit(Seq.fill(p.sCount)(0.U(p.userW.W))))

  val m_axis_tready_int_reg = RegInit(false.B)

  // Handshake wiring
  val s_axis_tready = Wire(Vec(p.sCount, Bool()))
  for (i <- 0 until p.sCount) {
    io.s_axis(i).tready := s_axis_tready(i)
    s_axis_tready(i) := !s_axis_tvalid_reg(i) || (m_axis_tready_int_reg && arbiter.io.grant(i))
  }

  // Arbiter Inputs
  val s_axis_tvalid_vec = VecInit(io.s_axis.map(_.tvalid)).asUInt
  arbiter.io.req := s_axis_tvalid_vec | (s_axis_tvalid_reg & ~arbiter.io.grant)
  
  val ackMask = Wire(Vec(p.sCount, Bool()))
  for (i <- 0 until p.sCount) {
    ackMask(i) := s_axis_tlast_reg(i)
  }
  arbiter.io.ack := arbiter.io.grant & s_axis_tvalid_reg & Fill(p.sCount, m_axis_tready_int_reg) & ackMask.asUInt

  // Mux logic
  val grant_idx = arbiter.io.grantIndex
  val current_s_tdata  = s_axis_tdata_reg(grant_idx)
  val current_s_tkeep  = s_axis_tkeep_reg(grant_idx)
  val current_s_tstrb  = s_axis_tstrb_reg(grant_idx)
  val current_s_tvalid = s_axis_tvalid_reg(grant_idx)
  val current_s_tlast  = s_axis_tlast_reg(grant_idx)
  val current_s_tid    = s_axis_tid_reg(grant_idx)
  val current_s_tdest  = s_axis_tdest_reg(grant_idx)
  val current_s_tuser  = s_axis_tuser_reg(grant_idx)

  val m_axis_tvalid_int = current_s_tvalid && m_axis_tready_int_reg && arbiter.io.grantValid
  
  // Register inputs
  for (i <- 0 until p.sCount) {
    when(s_axis_tready(i)) {
      s_axis_tdata_reg(i)  := io.s_axis(i).tdata
      s_axis_tkeep_reg(i)  := io.s_axis(i).tkeep
      s_axis_tstrb_reg(i)  := io.s_axis(i).tstrb
      s_axis_tvalid_reg    := s_axis_tvalid_reg.bitSet(i.U, io.s_axis(i).tvalid)
      s_axis_tlast_reg     := s_axis_tlast_reg.bitSet(i.U, io.s_axis(i).tlast)
      s_axis_tid_reg(i)    := io.s_axis(i).tid
      s_axis_tdest_reg(i)  := io.s_axis(i).tdest
      s_axis_tuser_reg(i)  := io.s_axis(i).tuser
    }
  }

  // Skid Buffer (Output Datapath)
  val m_axis_tdata_reg  = RegInit(0.U(p.dataW.W))
  val m_axis_tkeep_reg  = RegInit(0.U(p.keepW.W))
  val m_axis_tstrb_reg  = RegInit(0.U(p.keepW.W))
  val m_axis_tvalid_reg = RegInit(false.B)
  val m_axis_tlast_reg  = RegInit(false.B)
  val m_axis_tid_reg    = RegInit(0.U(p.idW.W))
  val m_axis_tdest_reg  = RegInit(0.U(p.destW.W))
  val m_axis_tuser_reg  = RegInit(0.U(p.userW.W))

  val temp_m_axis_tdata_reg  = RegInit(0.U(p.dataW.W))
  val temp_m_axis_tkeep_reg  = RegInit(0.U(p.keepW.W))
  val temp_m_axis_tstrb_reg  = RegInit(0.U(p.keepW.W))
  val temp_m_axis_tvalid_reg = RegInit(false.B)
  val temp_m_axis_tlast_reg  = RegInit(false.B)
  val temp_m_axis_tid_reg    = RegInit(0.U(p.idW.W))
  val temp_m_axis_tdest_reg  = RegInit(0.U(p.destW.W))
  val temp_m_axis_tuser_reg  = RegInit(0.U(p.userW.W))

  io.m_axis.tdata  := m_axis_tdata_reg
  io.m_axis.tkeep  := m_axis_tkeep_reg
  io.m_axis.tstrb  := m_axis_tstrb_reg
  io.m_axis.tvalid := m_axis_tvalid_reg
  io.m_axis.tlast  := m_axis_tlast_reg
  io.m_axis.tid    := m_axis_tid_reg
  io.m_axis.tdest  := m_axis_tdest_reg
  io.m_axis.tuser  := m_axis_tuser_reg

  val m_axis_tready_int_early = io.m_axis.tready || (!temp_m_axis_tvalid_reg && (!m_axis_tvalid_reg || !m_axis_tvalid_int))

  val store_axis_int_to_output = WireDefault(false.B)
  val store_axis_int_to_temp = WireDefault(false.B)
  val store_axis_temp_to_output = WireDefault(false.B)
  
  val m_axis_tvalid_next = WireDefault(m_axis_tvalid_reg)
  val temp_m_axis_tvalid_next = WireDefault(temp_m_axis_tvalid_reg)

  when(m_axis_tready_int_reg) {
    when(io.m_axis.tready || !m_axis_tvalid_reg) {
      m_axis_tvalid_next := m_axis_tvalid_int
      store_axis_int_to_output := true.B
    }.otherwise {
      temp_m_axis_tvalid_next := m_axis_tvalid_int
      store_axis_int_to_temp := true.B
    }
  }.elsewhen(io.m_axis.tready) {
    m_axis_tvalid_next := temp_m_axis_tvalid_reg
    temp_m_axis_tvalid_next := false.B
    store_axis_temp_to_output := true.B
  }

  m_axis_tvalid_reg := m_axis_tvalid_next
  m_axis_tready_int_reg := m_axis_tready_int_early
  temp_m_axis_tvalid_reg := temp_m_axis_tvalid_next

  when(store_axis_int_to_output) {
    m_axis_tdata_reg  := current_s_tdata
    m_axis_tkeep_reg  := current_s_tkeep
    m_axis_tstrb_reg  := current_s_tstrb
    m_axis_tlast_reg  := current_s_tlast
    m_axis_tid_reg    := current_s_tid
    m_axis_tdest_reg  := current_s_tdest
    m_axis_tuser_reg  := current_s_tuser
  }.elsewhen(store_axis_temp_to_output) {
    m_axis_tdata_reg  := temp_m_axis_tdata_reg
    m_axis_tkeep_reg  := temp_m_axis_tkeep_reg
    m_axis_tstrb_reg  := temp_m_axis_tstrb_reg
    m_axis_tlast_reg  := temp_m_axis_tlast_reg
    m_axis_tid_reg    := temp_m_axis_tid_reg
    m_axis_tdest_reg  := temp_m_axis_tdest_reg
    m_axis_tuser_reg  := temp_m_axis_tuser_reg
  }

  when(store_axis_int_to_temp) {
    temp_m_axis_tdata_reg  := current_s_tdata
    temp_m_axis_tkeep_reg  := current_s_tkeep
    temp_m_axis_tstrb_reg  := current_s_tstrb
    temp_m_axis_tlast_reg  := current_s_tlast
    temp_m_axis_tid_reg    := current_s_tid
    temp_m_axis_tdest_reg  := current_s_tdest
    temp_m_axis_tuser_reg  := current_s_tuser
  }
}