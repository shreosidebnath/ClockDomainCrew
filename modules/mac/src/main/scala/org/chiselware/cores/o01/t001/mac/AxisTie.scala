package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._

class AxisTie(
    sParams: AxisInterfaceParams,
    mParams: AxisInterfaceParams) extends RawModule {
  val io = IO(new Bundle {
    // Flipped() creates the sink (input) interface
    val sAxis = Flipped(new AxisInterface(sParams))
    // Default creates the source (output) interface
    val mAxis = new AxisInterface(mParams)
  })

  // Extract and combine parameters (Equivalent to SV localparams)
  val dataW = sParams.dataW
  val keepEn = sParams.keepEn && mParams.keepEn
  val keepW = sParams.keepW
  val strbEn = sParams.strbEn && mParams.strbEn
  val lastEn = sParams.lastEn && mParams.lastEn
  val idEn = sParams.idEn && mParams.idEn
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
  io.mAxis.tdata := io.sAxis.tdata
  io.mAxis.tvalid := io.sAxis.tvalid
  io.sAxis.tready := io.mAxis.tready

  // Conditional Assignments based on the combined enable flags

  if (keepEn) {
    io.mAxis.tkeep := io.sAxis.tkeep
  } else {
    // Equivalent to '1 in SystemVerilog
    io.mAxis.tkeep := Fill(mParams.keepW, 1.U(1.W))
  }

  if (strbEn) {
    io.mAxis.tstrb := io.sAxis.tstrb
  } else {
    io.mAxis.tstrb := io.mAxis.tkeep
  }

  if (lastEn) {
    io.mAxis.tlast := io.sAxis.tlast
  } else {
    io.mAxis.tlast := true.B
  }

  if (idEn) {
    io.mAxis.tid := io.sAxis.tid
  } else {
    io.mAxis.tid := 0.U
  }

  if (destEn) {
    io.mAxis.tdest := io.sAxis.tdest
  } else {
    io.mAxis.tdest := 0.U
  }

  if (userEn) {
    io.mAxis.tuser := io.sAxis.tuser
  } else {
    io.mAxis.tuser := 0.U
  }
}

// 2. The Companion Object (The Magic Factory)
object AxisTie {
  // This takes the ACTUAL interface objects you already created in your parent module
  def apply(
      sAxisIn: AxisInterface,
      mAxisOut: AxisInterface
    ): Unit = {

    // Extract the parameters directly from the passed objects!
    val sParams = sAxisIn.p
    val mParams = mAxisOut.p

    // Instantiate the underlying module
    val tieInst = Module(new AxisTie(sParams = sParams, mParams = mParams))

    tieInst.io.sAxis <> sAxisIn
    mAxisOut <> tieInst.io.mAxis
  }
}
