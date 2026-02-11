// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/** Xgmii2axisTb is a simple test bench wrapper to hold a single instance of RstMod.
  *
  * @param p
  *   A customization of default parameters contained in xgmii2axisParams case class.
  */

class Xgmii2AxisTb() extends Module {
  val io = IO(new Bundle {
    // Clock and Reset (implicit in Module)
    val rst = Input(Bool())
    
    // Stats
    val good_frames = Output(UInt(32.W))
    val bad_frames = Output(UInt(32.W))
    
    // Configuration vectors
    val configuration_vector = Input(UInt(80.W))
    val rx_statistics_vector = Output(UInt(30.W))
    val rx_statistics_valid = Output(Bool())
    
    // XGMII input
    val xgmii_d = Input(UInt(64.W)) // RXD
    val xgmii_c = Input(UInt(8.W))  // RXC
    val tready = Input(Bool())
    val aresetn = Input(Bool())
    
    // AXIS output
    val tdata = Output(UInt(64.W))
    val tkeep = Output(UInt(8.W))
    val tvalid = Output(Bool())
    val tlast = Output(Bool())
    val tuser = Output(UInt(1.W))
  })

  val dut = Module(new Xgmii2Axis())
    // Inputs into DUT
    dut.io.rst                  := io.rst
    dut.io.configuration_vector := io.configuration_vector
    dut.io.xgmii_d              := io.xgmii_d
    dut.io.xgmii_c              := io.xgmii_c
    dut.io.aresetn              := io.aresetn
    dut.io.tready               := io.tready

    // Outputs from DUT
    io.good_frames        := dut.io.good_frames
    io.bad_frames         := dut.io.bad_frames
    io.rx_statistics_vector := dut.io.rx_statistics_vector
    io.rx_statistics_valid  := dut.io.rx_statistics_valid

    io.tdata  := dut.io.tdata
    io.tkeep  := dut.io.tkeep
    io.tvalid := dut.io.tvalid
    io.tlast  := dut.io.tlast
    io.tuser  := dut.io.tuser
}
