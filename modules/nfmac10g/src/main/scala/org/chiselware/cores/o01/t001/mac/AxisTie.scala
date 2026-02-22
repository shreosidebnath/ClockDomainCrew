package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class AxisTie(sParams: AxisInterfaceParams, mParams: AxisInterfaceParams) extends RawModule {
  val io = IO(new Bundle {
    // Flipped() creates the sink (input) interface
    val s_axis = Flipped(new AxisInterface(sParams))
    // Default creates the source (output) interface
    val m_axis = new AxisInterface(mParams)
  })

  // Extract and combine parameters (Equivalent to SV localparams)
  val dataW  = sParams.dataW
  val keepEn = sParams.keepEn && mParams.keepEn
  val keepW  = sParams.keepW
  val strbEn = sParams.strbEn && mParams.strbEn
  val lastEn = sParams.lastEn && mParams.lastEn
  val idEn   = sParams.idEn && mParams.idEn
  val destEn = sParams.destEn && mParams.destEn
  val userEn = sParams.userEn && mParams.userEn

  // Check configuration (Equivalent to SV $fatal checks)
  require(
    mParams.dataW == dataW, 
    s"Error: Interface DATA_W parameter mismatch (m_axis: ${mParams.dataW}, s_axis: $dataW)"
  )
  
  require(
    !(keepEn && (mParams.keepW != keepW)), 
    s"Error: Interface KEEP_W parameter mismatch (m_axis: ${mParams.keepW}, s_axis: $keepW)"
  )

  // Direct Assignments
  io.m_axis.tdata  := io.s_axis.tdata
  io.m_axis.tvalid := io.s_axis.tvalid
  io.s_axis.tready := io.m_axis.tready

  // Conditional Assignments based on the combined enable flags
  
  if (keepEn) {
    io.m_axis.tkeep := io.s_axis.tkeep
  } else {
    // Equivalent to '1 in SystemVerilog
    io.m_axis.tkeep := Fill(mParams.keepW, 1.U(1.W)) 
  }

  if (strbEn) {
    io.m_axis.tstrb := io.s_axis.tstrb
  } else {
    io.m_axis.tstrb := io.m_axis.tkeep
  }

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
}


// 2. The Companion Object (The Magic Factory)
object AxisTie {
  // This takes the ACTUAL interface objects you already created in your parent module
  def apply(s_axis_in: AxisInterface, m_axis_out: AxisInterface): Unit = {
    
    // Extract the parameters directly from the passed objects!
    val s_params = s_axis_in.p
    val m_params = m_axis_out.p
    
    // Instantiate the underlying module
    val tie_inst = Module(new AxisTie(s_params, m_params))
    
    tie_inst.io.s_axis <> s_axis_in
    m_axis_out <> tie_inst.io.m_axis
  }
}