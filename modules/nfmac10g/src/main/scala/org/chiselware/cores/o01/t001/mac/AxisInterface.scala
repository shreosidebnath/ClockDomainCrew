package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class AxisInterface(val p: AxisInterfaceParams) extends Bundle {
  // By default, this interface is for outputs (src) 
  // Flipped() can be used to create a sink (snk).
  val tdata  = Output(UInt(p.dataW.W))
  val tkeep  = Output(UInt(p.keepW.W))
  val tstrb  = Output(UInt(p.keepW.W))
  val tid    = Output(UInt(p.idW.W))
  val tdest  = Output(UInt(p.destW.W))
  val tuser  = Output(UInt(p.userW.W))
  val tlast  = Output(Bool())
  val tvalid = Output(Bool())
  val tready = Input(Bool()) // tready flows in the opposite direction
}