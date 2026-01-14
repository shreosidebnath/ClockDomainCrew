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

class Xgmii2axisTb() extends Module {
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
    
    // AXIS output
    val aresetn = Input(Bool())
    val tdata = Output(UInt(64.W))
    val tkeep = Output(UInt(8.W))
    val tvalid = Output(Bool())
    val tlast = Output(Bool())
    val tuser = Output(UInt(1.W))
  })

  val dut = Module(new Xgmii2axis())
    io.rst := dut.io.rst

    dut.io.good_frames := io.good_frames
    dut.io.bad_frames := io.bad_frames

    dut.io.configuration_vector := io.configuration_vector
    dut.io.rx_statistics_vector := io.rx_statistics_vector
    dut.io.rx_statistics_valid := io.rx_statistics_valid

    dut.io.xgmii_d := io.xgmii_d    // RXD
    dut.io.xgmii_c := io.xgmii_c    // RXC

    dut.io.aresetn := io.aresetn
    dut.io.tdata := io.tdata
    dut.io.tkeep := io.tkeep
    dut.io.tvalid := io.tvalid
    dut.io.tlast := io.tlast
    dut.io.tuser := io.tuser     // ERROR FLAG
}
