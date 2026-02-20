package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class AxisNullSrc(val dataW: Int, val userW: Int, val idW: Int = 8, val destW: Int = 8) extends RawModule {
  val io = IO(new Bundle {
    val m_axis = new AxisInterface(dataW, userW, idW, destW)
  })

  // AXI4-Stream null source tie-offs
  io.m_axis.tdata  := 0.U
  
  // '1 in SystemVerilog sets all bits to 1. In Chisel, we calculate the max value for the byte-width.
  io.m_axis.tkeep  := ((BigInt(1) << (dataW / 8)) - 1).U 
  
  io.m_axis.tstrb  := io.m_axis.tkeep
  io.m_axis.tvalid := false.B
  io.m_axis.tlast  := true.B
  io.m_axis.tid    := 0.U
  io.m_axis.tdest  := 0.U
  io.m_axis.tuser  := 0.U
}