package org.chiselware.cores.o01.t001.nfmac10g
import chisel3._
import chisel3.util._

// TX Wrapper Module
// Combines padding control with AXIS to XGMII conversion
class Tx extends Module {
  val io = IO(new Bundle {
    // Clock and Reset (implicit in Module)
    val rst = Input(Bool())
    
    // AXIS input
    val axis_aresetn = Input(Bool())
    val axis_tdata = Input(UInt(64.W))
    val axis_tkeep = Input(UInt(8.W))
    val axis_tvalid = Input(Bool())
    val axis_tready = Output(Bool())
    val axis_tlast = Input(Bool())
    val axis_tuser = Input(UInt(1.W))
    
    // Flow control
    val carrier_sense = Input(Bool())
    val cfg_rx_pause_enable = Input(Bool())
    val cfg_station_macaddr = Input(UInt(48.W))
    val cfg_tx_pause_refresh = Input(UInt(16.W))
    val rx_pause_active = Input(Bool())
    val tx_pause_send = Input(Bool())
    
    // Configuration vector
    val configuration_vector = Input(UInt(80.W))
    
    // Statistics
    val tx_statistics_valid = Output(Bool())
    val tx_statistics_vector = Output(UInt(26.W))
    
    // XGMII output
    val xgmii_txc = Output(UInt(8.W))
    val xgmii_txd = Output(UInt(64.W))
  })

  // Internal signals between padding_ctrl and axis2xgmii
  val m_axis_tdata = Wire(UInt(64.W))
  val m_axis_tkeep = Wire(UInt(8.W))
  val m_axis_tvalid = Wire(Bool())
  val m_axis_tready = Wire(Bool())
  val m_axis_tlast = Wire(Bool())
  val m_axis_tuser = Wire(UInt(1.W))
  val dic = Wire(UInt(2.W))
  val lane4_start = Wire(Bool())

  // Instantiate padding_ctrl module
  val padding_ctrl_mod = Module(new PaddingCtrl)
  
  // Connect padding_ctrl inputs
  padding_ctrl_mod.io.rst := io.rst
  padding_ctrl_mod.io.aresetn := io.axis_aresetn
  padding_ctrl_mod.io.s_axis_tdata := io.axis_tdata
  padding_ctrl_mod.io.s_axis_tkeep := io.axis_tkeep
  padding_ctrl_mod.io.s_axis_tvalid := io.axis_tvalid
  padding_ctrl_mod.io.s_axis_tlast := io.axis_tlast
  padding_ctrl_mod.io.s_axis_tuser := io.axis_tuser
  padding_ctrl_mod.io.m_axis_tready := m_axis_tready
  padding_ctrl_mod.io.lane4_start := lane4_start
  padding_ctrl_mod.io.dic := dic
  padding_ctrl_mod.io.carrier_sense := io.carrier_sense
  padding_ctrl_mod.io.rx_pause_active := io.rx_pause_active
  padding_ctrl_mod.io.tx_pause_send := io.tx_pause_send
  padding_ctrl_mod.io.cfg_rx_pause_enable := io.cfg_rx_pause_enable
  padding_ctrl_mod.io.cfg_tx_pause_refresh := io.cfg_tx_pause_refresh
  padding_ctrl_mod.io.cfg_station_macaddr := io.cfg_station_macaddr
  
  // Connect padding_ctrl outputs
  io.axis_tready := padding_ctrl_mod.io.s_axis_tready
  m_axis_tdata := padding_ctrl_mod.io.m_axis_tdata
  m_axis_tkeep := padding_ctrl_mod.io.m_axis_tkeep
  m_axis_tvalid := padding_ctrl_mod.io.m_axis_tvalid
  m_axis_tlast := padding_ctrl_mod.io.m_axis_tlast
  m_axis_tuser := padding_ctrl_mod.io.m_axis_tuser

  // Instantiate axis2xgmii module
  val axis2xgmii_mod = Module(new Axis2Xgmii)
  
  // Connect axis2xgmii inputs
  axis2xgmii_mod.io.rst := io.rst
  axis2xgmii_mod.io.configuration_vector := io.configuration_vector
  axis2xgmii_mod.io.tdata := m_axis_tdata
  axis2xgmii_mod.io.tkeep := m_axis_tkeep
  axis2xgmii_mod.io.tvalid := m_axis_tvalid
  axis2xgmii_mod.io.tlast := m_axis_tlast
  axis2xgmii_mod.io.tuser := m_axis_tuser
  
  // Connect axis2xgmii outputs
  m_axis_tready := axis2xgmii_mod.io.tready
  lane4_start := axis2xgmii_mod.io.lane4_start
  dic := axis2xgmii_mod.io.dic_o
  io.xgmii_txd := axis2xgmii_mod.io.xgmii_d
  io.xgmii_txc := axis2xgmii_mod.io.xgmii_c
  io.tx_statistics_vector := axis2xgmii_mod.io.tx_statistics_vector
  io.tx_statistics_valid := axis2xgmii_mod.io.tx_statistics_valid
}

object Tx {
  def apply(): Tx = Module(new Tx)
}
