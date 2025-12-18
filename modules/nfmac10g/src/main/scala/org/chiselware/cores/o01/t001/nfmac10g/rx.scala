package org.chiselware.cores.o01.t001.nfmac10g
import chisel3._
import chisel3.util._

// RX Wrapper Module
// Combines XGMII to AXIS conversion with pause frame handling
class Rx extends Module {
  val io = IO(new Bundle {
    // Clock and Reset (implicit in Module)
    val rst = Input(Bool())
    
    // Stats
    val good_frames = Output(UInt(32.W))
    val bad_frames = Output(UInt(32.W))
    val rx_pause_active = Output(Bool())
    
    // Configuration vectors
    val configuration_vector = Input(UInt(80.W))
    val cfg_rx_pause_enable = Input(Bool())
    val cfg_sub_quanta_count = Input(UInt(8.W))  // Number of clock cycles per quanta
    val rx_statistics_vector = Output(UInt(30.W))
    val rx_statistics_valid = Output(Bool())
    
    // XGMII input
    val xgmii_rxd = Input(UInt(64.W))
    val xgmii_rxc = Input(UInt(8.W))
    
    // AXIS output
    val axis_aresetn = Input(Bool())
    val axis_tdata = Output(UInt(64.W))
    val axis_tkeep = Output(UInt(8.W))
    val axis_tvalid = Output(Bool())
    val axis_tlast = Output(Bool())
    val axis_tuser = Output(UInt(1.W))
  })

  // Internal wires
  val tuser_i = Wire(UInt(1.W))
  val tdata_internal = Wire(UInt(64.W))
  val tkeep_internal = Wire(UInt(8.W))
  val tvalid_internal = Wire(Bool())
  val tlast_internal = Wire(Bool())

  // Instantiate xgmii2axis module
  val xgmii2axis_mod = Module(new Xgmii2Axis)
  
  // Connect xgmii2axis
  xgmii2axis_mod.io.rst := io.rst
  xgmii2axis_mod.io.configuration_vector := io.configuration_vector
  xgmii2axis_mod.io.xgmii_d := io.xgmii_rxd
  xgmii2axis_mod.io.xgmii_c := io.xgmii_rxc
  xgmii2axis_mod.io.aresetn := io.axis_aresetn
  
  // Connect internal wires
  tdata_internal := xgmii2axis_mod.io.tdata
  tkeep_internal := xgmii2axis_mod.io.tkeep
  tvalid_internal := xgmii2axis_mod.io.tvalid
  tlast_internal := xgmii2axis_mod.io.tlast
  tuser_i := xgmii2axis_mod.io.tuser
  
  // Connect stats
  io.good_frames := xgmii2axis_mod.io.good_frames
  io.bad_frames := xgmii2axis_mod.io.bad_frames
  io.rx_statistics_vector := xgmii2axis_mod.io.rx_statistics_vector
  io.rx_statistics_valid := xgmii2axis_mod.io.rx_statistics_valid

  // Instantiate RxPause module
  val pause_mod = Module(new RxPause)
  
  // Connect RxPause
  pause_mod.io.rst := io.rst
  pause_mod.io.cfg_rx_pause_enable := io.cfg_rx_pause_enable
  pause_mod.io.cfg_sub_quanta_count := io.cfg_sub_quanta_count
  pause_mod.io.aresetn := io.axis_aresetn
  pause_mod.io.tdata_i := tdata_internal
  pause_mod.io.tkeep_i := tkeep_internal
  pause_mod.io.tvalid_i := tvalid_internal
  pause_mod.io.tlast_i := tlast_internal
  pause_mod.io.tuser_i := tuser_i
  
  // Connect outputs
  io.axis_tdata := tdata_internal
  io.axis_tkeep := tkeep_internal
  io.axis_tvalid := tvalid_internal
  io.axis_tlast := tlast_internal
  io.axis_tuser := pause_mod.io.tuser_o
  io.rx_pause_active := pause_mod.io.rx_pause_active
}

object Rx {
  def apply(): Rx = Module(new Rx)
}
