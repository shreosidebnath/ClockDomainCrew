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

import org.chiselware.cores.o01.t001.mac._

class MacStats(val p: MacStatsParams) extends RawModule {
  val io = IO(new Bundle {
    // Clocks and Resets
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())
    val statClk = Input(Clock())
    val statRst = Input(Bool())

    // Output Statistics AXI Stream (USING EDWARD'S INTERFACE)
    val mAxisStat = new AxisInterface(AxisInterfaceParams(
        dataW = 16,
        keepW = 1,
        keepEn = false,
        lastEn = false,
        userEn = true,
        userW = 1,
        idEn = true,
        idW = 8
      ))

    // TX Status Inputs
    val txStartPacket     = Input(Bool())
    val statTxByte        = Input(UInt(p.incW.W))
    val statTxPktLen      = Input(UInt(16.W))
    val statTxPktUcast    = Input(Bool())
    val statTxPktMcast    = Input(Bool())
    val statTxPktBcast    = Input(Bool())
    val statTxPktGood     = Input(Bool())
    val statTxErrUser     = Input(Bool())
    val statTxErrUnderflow= Input(Bool())
    val statTxErrOversize = Input(Bool())
    val statTxMcf         = Input(Bool())

    // RX Status Inputs
    val rxStartPacket     = Input(Bool())
    val statRxByte        = Input(UInt(p.incW.W))
    val statRxPktLen      = Input(UInt(16.W))
    val statRxErrBadFcs   = Input(Bool())
    val statRxFifoDrop    = Input(Bool())
    val statRxErrOversize = Input(Bool())
    val statRxMcf         = Input(Bool())
    val statRxErrBadBlock = Input(Bool())
    val statRxErrFraming  = Input(Bool())
    val statRxPktGood     = Input(Bool())
    val statRxPktBad      = Input(Bool())
    val statRxPktUcast    = Input(Bool())
    val statRxPktMcast    = Input(Bool())
    val statRxPktBcast    = Input(Bool())
  })


  val axisStatTx = new AxisInterface(AxisInterfaceParams(
        dataW = 16,
        keepW = 1,
        keepEn = false,
        lastEn = false,
        userEn = true,
        userW = 1,
        idEn = true,
        idW = 8
  ))

  val axisStatRx = new AxisInterface(AxisInterfaceParams(
        dataW = 16,
        keepW = 1,
        keepEn = false,
        lastEn = false,
        userEn = true,
        userW = 1,
        idEn = true,
        idW = 8
  ))

  val axisStatInt = Vec(2, new AxisInterface(AxisInterfaceParams(
        dataW = 16,
        keepW = 1,
        keepEn = false,
        lastEn = false,
        userEn = true,
        userW = 1,
        idEn = true,
        idW = 8
  )))


  // --------------------------------------------------------
  // TX DOMAIN LOGIC
  // --------------------------------------------------------
  val histTxPktSmall  = (io.statTxPktLen =/= 0.U) && (io.statTxPktLen(15, 6) === 0.U)
  val histTxPkt64     = io.statTxPktLen === 64.U
  val histTxPkt65_127 = (io.statTxPktLen(15, 6) === 1.U) && (io.statTxPktLen =/= 64.U)
  val histTxPkt128_255= io.statTxPktLen(15, 7) === 1.U

  val txInc = Wire(Vec(p.txCnt, UInt(p.incW.W)))
  txInc(0)  := io.statTxByte
  txInc(1)  := io.txStartPacket
  txInc(2)  := io.statTxErrUser
  txInc(3)  := io.statTxErrUnderflow
  txInc(4)  := io.statTxErrOversize
  txInc(5)  := io.statTxMcf
  txInc(6)  := 0.U
  txInc(7)  := 0.U
  txInc(8)  := histTxPktSmall
  txInc(9)  := histTxPkt64
  txInc(10) := histTxPkt65_127
  txInc(11) := histTxPkt128_255
  txInc(12) := io.statTxPktUcast
  txInc(13) := io.statTxPktMcast
  txInc(14) := io.statTxPktBcast
  txInc(15) := io.statTxPktGood

 val txStats = withClockAndReset(io.txClk, io.txRst) { 
    Module(new StatsCollect(StatsCollectParams(cnt = p.txCnt))) 
  }

  for (i <- 0 until p.txCnt) {
    txStats.io.stat_inc(i) := txInc(i)
    txStats.io.stat_valid(i) := true.B
    txStats.io.stat_str(i) := 0.U
  }
  txStats.io.gate := true.B
  txStats.io.update := false.B

  val txFifo = Module(new AsyncFifo(p.fifoParams))
  txFifo.io.sClk := io.txClk
  txFifo.io.sRst := io.txRst
  txFifo.io.mClk := io.statClk
  txFifo.io.mRst := io.statRst

  txFifo.io.sAxisTdata  := txStats.io.m_axis_stat_tdata
  txFifo.io.sAxisTkeep  := txStats.io.m_axis_stat_tkeep
  txFifo.io.sAxisTstrb  := txStats.io.m_axis_stat_tkeep // mirror keep
  txFifo.io.sAxisTvalid := txStats.io.m_axis_stat_tvalid
  txStats.io.m_axis_stat_tready := txFifo.io.sAxisTready
  txFifo.io.sAxisTlast  := txStats.io.m_axis_stat_tlast
  txFifo.io.sAxisTid    := txStats.io.m_axis_stat_tid
  txFifo.io.sAxisTdest  := txStats.io.m_axis_stat_tdest
  txFifo.io.sAxisTuser  := txStats.io.m_axis_stat_tuser

  // --------------------------------------------------------
  // RX DOMAIN LOGIC
  // --------------------------------------------------------
  val histRxPktSmall = (io.statRxPktLen =/= 0.U) && (io.statRxPktLen(15, 6) === 0.U)

  val rxInc = Wire(Vec(p.rxCnt, UInt(p.incW.W)))
  rxInc(0)  := io.statRxByte
  rxInc(1)  := io.rxStartPacket
  rxInc(2)  := io.statRxErrBadFcs
  rxInc(3)  := io.statRxFifoDrop
  rxInc(4)  := io.statRxErrOversize
  rxInc(5)  := io.statRxMcf
  rxInc(6)  := io.statRxErrBadBlock
  rxInc(7)  := io.statRxErrFraming
  rxInc(8)  := histRxPktSmall
  rxInc(9)  := io.statRxPktGood
  rxInc(10) := io.statRxPktBad
  rxInc(11) := io.statRxPktUcast
  rxInc(12) := io.statRxPktMcast
  rxInc(13) := io.statRxPktBcast
  rxInc(14) := 0.U
  rxInc(15) := 0.U

 val rxStats = withClockAndReset(io.rxClk, io.rxRst) { 
    Module(new StatsCollect(StatsCollectParams(cnt = p.rxCnt))) 
  }

  for (i <- 0 until p.rxCnt) {
    rxStats.io.stat_inc(i) := rxInc(i)
    rxStats.io.stat_valid(i) := true.B
    rxStats.io.stat_str(i) := 0.U
  }
  rxStats.io.gate := true.B
  rxStats.io.update := false.B

  val rxFifo = Module(new AsyncFifo(p.fifoParams))
  rxFifo.io.sClk := io.rxClk
  rxFifo.io.sRst := io.rxRst
  rxFifo.io.mClk := io.statClk
  rxFifo.io.mRst := io.statRst

  rxFifo.io.sAxisTdata  := rxStats.io.m_axis_stat_tdata
  rxFifo.io.sAxisTkeep  := rxStats.io.m_axis_stat_tkeep
  rxFifo.io.sAxisTstrb  := rxStats.io.m_axis_stat_tkeep 
  rxFifo.io.sAxisTvalid := rxStats.io.m_axis_stat_tvalid
  rxStats.io.m_axis_stat_tready := rxFifo.io.sAxisTready
  rxFifo.io.sAxisTlast  := rxStats.io.m_axis_stat_tlast
  rxFifo.io.sAxisTid    := rxStats.io.m_axis_stat_tid
  rxFifo.io.sAxisTdest  := rxStats.io.m_axis_stat_tdest
  rxFifo.io.sAxisTuser  := rxStats.io.m_axis_stat_tuser

  // --------------------------------------------------------
  // ARBITER MUX (STAT CLK DOMAIN)
  // --------------------------------------------------------
  val mux = withClockAndReset(io.statClk, io.statRst) { Module(new AxisArbMux(p.muxParams)) }

  // Connect TX FIFO (Port 0) to MUX
  mux.io.s_axis(0).tdata  := txFifo.io.mAxisTdata
  mux.io.s_axis(0).tkeep  := txFifo.io.mAxisTkeep
  mux.io.s_axis(0).tstrb  := txFifo.io.mAxisTstrb
  mux.io.s_axis(0).tvalid := txFifo.io.mAxisTvalid
  txFifo.io.mAxisTready   := mux.io.s_axis(0).tready
  mux.io.s_axis(0).tlast  := txFifo.io.mAxisTlast
  mux.io.s_axis(0).tid    := txFifo.io.mAxisTid
  mux.io.s_axis(0).tdest  := txFifo.io.mAxisTdest
  mux.io.s_axis(0).tuser  := txFifo.io.mAxisTuser

  // Connect RX FIFO (Port 1) to MUX
  mux.io.s_axis(1).tdata  := rxFifo.io.mAxisTdata
  mux.io.s_axis(1).tkeep  := rxFifo.io.mAxisTkeep
  mux.io.s_axis(1).tstrb  := rxFifo.io.mAxisTstrb
  mux.io.s_axis(1).tvalid := rxFifo.io.mAxisTvalid
  rxFifo.io.mAxisTready   := mux.io.s_axis(1).tready
  mux.io.s_axis(1).tlast  := rxFifo.io.mAxisTlast
  mux.io.s_axis(1).tid    := rxFifo.io.mAxisTid
  mux.io.s_axis(1).tdest  := rxFifo.io.mAxisTdest
  mux.io.s_axis(1).tuser  := rxFifo.io.mAxisTuser

  // Connect MUX to Top Level IO
  io.mAxisStat.tdata  := mux.io.m_axis.tdata
  io.mAxisStat.tkeep  := mux.io.m_axis.tkeep
  io.mAxisStat.tstrb  := mux.io.m_axis.tstrb
  io.mAxisStat.tvalid := mux.io.m_axis.tvalid
  mux.io.m_axis.tready := io.mAxisStat.tready
  io.mAxisStat.tlast  := mux.io.m_axis.tlast
  io.mAxisStat.tid    := mux.io.m_axis.tid
  io.mAxisStat.tdest  := mux.io.m_axis.tdest
  io.mAxisStat.tuser  := mux.io.m_axis.tuser
}