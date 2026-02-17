package ethernet

import chisel3._
import chisel3.util._

/**
  * AXI4-Stream Interface Bundle
  * @param dataWidth Total bits in the data bus (usually 64 for 10GbE)
  * @param hasKeep   Enable tkeep signal (byte qualifiers)
  * @param userWidth Bits for the tuser sideband signal
  */
class AXIStream(val dataWidth: Int, val hasKeep: Boolean = true, val userWidth: Int = 0) extends Bundle {
  val tdata  = UInt(dataWidth.W)
  val tvalid = Bool()
  val tready = Input(Bool()) // Flipped relative to the others
  val tlast  = Bool()
  
  // Optional signals using Option/Some/None
  val tkeep  = if (hasKeep) Some(UInt((dataWidth / 8).W)) else None
  val tuser  = if (userWidth > 0) Some(UInt(userWidth.W)) else None
}

/**
  * AXI4-Stream Null Source
  * Effectively "ties off" an output to a safe, idle state.
  */
class AxisNullSrc(dataWidth: Int, hasKeep: Boolean = true, userWidth: Int = 0) extends Module {
  val m_axis = IO(Output(new AXIStream(dataWidth, hasKeep, userWidth)))

  m_axis.tvalid := false.B
  m_axis.tdata  := 0.U
  m_axis.tlast  := true.B
  
  // Handle optional signals safely
  m_axis.tkeep.foreach(k => k := Fill(dataWidth / 8, 1.U))
  m_axis.tuser.foreach(u => u := 0.U)
}

/**
  * AXI4-Stream Tie
  * Connects a sink to a source while handling parameter normalization.
  */
class AxisTie(dataWidth: Int, hasKeep: Boolean = true, userWidth: Int = 0) extends Module {
  val io = IO(new Bundle {
    val s_axis = Flipped(new AXIStream(dataWidth, hasKeep, userWidth))
    val m_axis = new AXIStream(dataWidth, hasKeep, userWidth)
  })

  // Mandatory Signal Passthrough
  io.m_axis.tdata  := io.s_axis.tdata
  io.m_axis.tvalid := io.s_axis.tvalid
  io.s_axis.tready := io.m_axis.tready
  io.m_axis.tlast  := io.s_axis.tlast

  // Optional Signal Passthrough
  // These 'zip' operations safely connect signals only if they exist in both
  (io.m_axis.tkeep, io.s_axis.tkeep) match {
    case (Some(m), Some(s)) => m := s
    case (Some(m), None)    => m := Fill(dataWidth / 8, 1.U)
    case _ => // Do nothing
  }

  (io.m_axis.tuser, io.s_axis.tuser) match {
    case (Some(m), Some(s)) => m := s
    case (Some(m), None)    => m := 0.U
    case _ => // Do nothing
  }
}