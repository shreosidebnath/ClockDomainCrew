package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class AxisTie(
  val dataW: Int, 
  val userW: Int, 
  val idW: Int = 8, 
  val destW: Int = 8,
  // AXI4-Stream feature enable flags to match SystemVerilog interface parameters
  val keepEn: Boolean = true,
  val strbEn: Boolean = true,
  val lastEn: Boolean = true,
  val idEn:   Boolean = true,
  val destEn: Boolean = true,
  val userEn: Boolean = true
) extends RawModule {
  
  val io = IO(new Bundle {
    val s_axis = Flipped(new AxisInterface(dataW, userW, idW, destW))
    val m_axis = new AxisInterface(dataW, userW, idW, destW)
  })

  // SystemVerilog $fatal checks translate perfectly to Chisel's require() statements.
  // We check this at elaboration time.
  require(io.m_axis.dataW == dataW, "Error: Interface DATA_W parameter mismatch")
  
  val keepW = dataW / 8
  if (keepEn) {
    require((io.m_axis.dataW / 8) == keepW, "Error: Interface KEEP_W parameter mismatch")
  }

  // AXI4-Stream tie logic
  io.m_axis.tdata  := io.s_axis.tdata
  
  if (keepEn) {
    io.m_axis.tkeep := io.s_axis.tkeep
  } else {
    io.m_axis.tkeep := ((BigInt(1) << keepW) - 1).U
  }

  if (strbEn) {
    io.m_axis.tstrb := io.s_axis.tstrb
  } else {
    io.m_axis.tstrb := io.m_axis.tkeep
  }

  io.m_axis.tvalid := io.s_axis.tvalid

  if (lastEn) {
    io.m_axis.tlast := io.s_axis.tlast
  } else {
    io.m_axis.tlast := true.B
  }

  if (idEn) {
    io.m_axis.tid := io.s_axis.tid
  } else {
    io.m_axis.tid := 0.U
  }

  if (destEn) {
    io.m_axis.tdest := io.s_axis.tdest
  } else {
    io.m_axis.tdest := 0.U
  }

  if (userEn) {
    io.m_axis.tuser := io.s_axis.tuser
  } else {
    io.m_axis.tuser := 0.U
  }

  io.s_axis.tready := io.m_axis.tready
}