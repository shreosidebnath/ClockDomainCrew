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

class RxTb() extends Module {
  val io = IO(new Bundle {
    // Reset
    val rst = Input(Bool())

    // Configuration
    val configuration_vector = Input(UInt(80.W))
    val cfg_rx_pause_enable  = Input(Bool())
    val cfg_sub_quanta_count = Input(UInt(8.W))

    // XGMII input
    val xgmii_rxd = Input(UInt(64.W))
    val xgmii_rxc = Input(UInt(8.W))

    // AXIS reset input
    val axis_aresetn = Input(Bool())

    // Outputs / Observability
    val good_frames = Output(UInt(32.W))
    val bad_frames  = Output(UInt(32.W))
    val rx_pause_active = Output(Bool())

    val rx_statistics_vector = Output(UInt(30.W))
    val rx_statistics_valid  = Output(Bool())

    // AXIS output
    val axis_tdata  = Output(UInt(64.W))
    val axis_tkeep  = Output(UInt(8.W))
    val axis_tvalid = Output(Bool())
    val axis_tlast  = Output(Bool())
    val axis_tuser  = Output(UInt(1.W))
  })

  val dut = Module(new Rx)
    // Drive DUT inputs
    dut.io.rst                  := io.rst
    dut.io.configuration_vector := io.configuration_vector
    dut.io.cfg_rx_pause_enable  := io.cfg_rx_pause_enable
    dut.io.cfg_sub_quanta_count := io.cfg_sub_quanta_count
    dut.io.xgmii_rxd            := io.xgmii_rxd
    dut.io.xgmii_rxc            := io.xgmii_rxc
    dut.io.axis_aresetn         := io.axis_aresetn

    // Observe DUT outputs
    io.good_frames          := dut.io.good_frames
    io.bad_frames           := dut.io.bad_frames
    io.rx_pause_active      := dut.io.rx_pause_active
    io.rx_statistics_vector := dut.io.rx_statistics_vector
    io.rx_statistics_valid  := dut.io.rx_statistics_valid

    io.axis_tdata  := dut.io.axis_tdata
    io.axis_tkeep  := dut.io.axis_tkeep
    io.axis_tvalid := dut.io.axis_tvalid
    io.axis_tlast  := dut.io.axis_tlast
    io.axis_tuser  := dut.io.axis_tuser
}
