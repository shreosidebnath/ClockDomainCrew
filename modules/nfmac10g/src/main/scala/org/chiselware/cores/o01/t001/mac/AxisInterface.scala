package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

// --- Interface Definition ---
// Matches taxi_axis_if.src usage
class AxisInterface(val dataW: Int, val userW: Int, val idW: Int = 8, val destW: Int = 8) extends Bundle {
  val tdata  = Output(UInt(dataW.W))
  val tkeep  = Output(UInt((dataW/8).W))
  val tstrb  = Output(UInt((dataW/8).W))
  val tvalid = Output(Bool())
  val tlast  = Output(Bool())
  val tuser  = Output(UInt(userW.W))
  val tid    = Output(UInt(idW.W))
  val tdest  = Output(UInt(destW.W))
  val tready = Input(Bool()) // Present in interface, but ignored by RX MAC (push only)
}
