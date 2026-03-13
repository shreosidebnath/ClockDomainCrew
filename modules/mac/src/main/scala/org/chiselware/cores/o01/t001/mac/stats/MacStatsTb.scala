package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chisel3.util._

class MacStatsTb(val p: MacStatsParams) extends Module {
  val io = IO(new Bundle {
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())
    val statClk = Input(Clock())
    val statRst = Input(Bool())
    // For a minimal TB, we just expose the master AXI stream
    val mAxisStatTdata = Output(UInt(p.dataW.W))
    val mAxisStatTvalid = Output(Bool())
    val mAxisStatTready = Input(Bool())
  })

  val dut = Module(new MacStats(p))
  
  dut.io.rxClk := io.rxClk
  dut.io.rxRst := io.rxRst
  dut.io.txClk := io.txClk
  dut.io.txRst := io.txRst
  dut.io.statClk := io.statClk
  dut.io.statRst := io.statRst

  // io.mAxisStatTdata := dut.io.mAxisStatTdata
  // io.mAxisStatTvalid := dut.io.mAxisStatTvalid
  // dut.io.mAxisStatTready := io.mAxisStatTready

  io.mAxisStatTdata := dut.io.mAxisStat.tdata
  io.mAxisStatTvalid := dut.io.mAxisStat.tvalid
  dut.io.mAxisStat.tready := io.mAxisStatTready

  // Tie off required inputs for the TB wrapper to compile cleanly
  dut.io.txStartPacket := false.B
  dut.io.statTxByte := 0.U
  dut.io.statTxPktLen := 0.U
  dut.io.statTxPktUcast := false.B
  dut.io.statTxPktMcast := false.B
  dut.io.statTxPktBcast := false.B
  dut.io.statTxPktGood := false.B
  dut.io.statTxErrUser := false.B
  dut.io.statTxErrUnderflow := false.B
  dut.io.statTxErrOversize := false.B
  dut.io.statTxMcf := false.B

  dut.io.rxStartPacket := false.B
  dut.io.statRxByte := 0.U
  dut.io.statRxPktLen := 0.U
  dut.io.statRxErrBadFcs := false.B
  dut.io.statRxFifoDrop := false.B
  dut.io.statRxErrOversize := false.B
  dut.io.statRxMcf := false.B
  dut.io.statRxErrBadBlock := false.B
  dut.io.statRxErrFraming := false.B
  dut.io.statRxPktGood := false.B
  dut.io.statRxPktBad := false.B
  dut.io.statRxPktUcast := false.B
  dut.io.statRxPktMcast := false.B
  dut.io.statRxPktBcast := false.B
}