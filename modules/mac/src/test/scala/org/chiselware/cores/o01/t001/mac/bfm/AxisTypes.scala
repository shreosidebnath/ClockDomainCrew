package org.chiselware.cores.o01.t001.mac.bfm

// One transfer on AXI-Stream (one cycle handshake)
case class AxisBeat(
    data: BigInt,
    keep: Int,
    last: Boolean,
    user: BigInt = 0)

// A full frame is multiple beats, ending with last=true
case class AxisFrame(beats: Seq[AxisBeat]) {
  require(beats.nonEmpty, "AxisFrame must contain at least 1 beat")
  require(beats.last.last, "AxisFrame must end with last=true on final beat")
}

import chisel3._

class AxiStreamIn(
    dataW: Int,
    keepW: Int,
    userW: Int,
    idW: Int) extends Bundle {
  val tdata = Input(UInt(dataW.W))
  val tkeep = Input(UInt(keepW.W))
  val tvalid = Input(Bool())
  val tready = Output(Bool())
  val tlast = Input(Bool())
  val tuser = Input(UInt(userW.W))
  val tid = Input(UInt(idW.W))
}

class AxiStreamOut(
    dataW: Int,
    keepW: Int,
    userW: Int,
    idW: Int) extends Bundle {
  val tdata = Output(UInt(dataW.W))
  val tkeep = Output(UInt(keepW.W))
  val tvalid = Output(Bool())
  val tready = Input(Bool())
  val tlast = Output(Bool())
  val tuser = Output(UInt(userW.W))
  val tid = Output(UInt(idW.W))
}
