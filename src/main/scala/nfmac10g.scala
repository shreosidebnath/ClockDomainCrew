import circt.stage.ChiselStage
import chisel3._
import chisel3.util._

// Top Level 10G Ethernet MAC Module
class NfMac10g extends Module {
  val io = IO(new Bundle {
    // Clocks and resets
    // Note: In Chisel, we have implicit clock and reset
    // These additional clocks would need to be handled separately
    val tx_clk0 = Input(Clock())
    val rx_clk0 = Input(Clock())
    val reset = Input(Bool())
    val tx_dcm_locked = Input(Bool())
    val rx_dcm_locked = Input(Bool())
    
    // Flow control
    val tx_ifg_delay = Input(UInt(8.W))
    val pause_val = Input(UInt(16.W))
    val pause_req = Input(Bool())
    
    // Configuration and status vectors
    val tx_configuration_vector = Input(UInt(80.W))
    val rx_configuration_vector = Input(UInt(80.W))
    val status_vector = Output(UInt(2.W))
    
    // Xilinx-incompatible control signals
    val cfg_station_macaddr = Input(UInt(48.W))
    val cfg_rx_pause_enable = Input(Bool())
    val cfg_sub_quanta_count = Input(UInt(8.W))  // Number of clock cycles per quanta
    val carrier_sense = Input(Bool())
    
    // Statistic Vector Signals
    val tx_statistics_vector = Output(UInt(26.W))
    val tx_statistics_valid = Output(Bool())
    val rx_statistics_vector = Output(UInt(30.W))
    val rx_statistics_valid = Output(Bool())
    
    // XGMII
    val xgmii_txd = Output(UInt(64.W))
    val xgmii_txc = Output(UInt(8.W))
    val xgmii_rxd = Input(UInt(64.W))
    val xgmii_rxc = Input(UInt(8.W))
    
    // TX AXIS
    val tx_axis_aresetn = Input(Bool())
    val tx_axis_tdata = Input(UInt(64.W))
    val tx_axis_tkeep = Input(UInt(8.W))
    val tx_axis_tvalid = Input(Bool())
    val tx_axis_tready = Output(Bool())
    val tx_axis_tlast = Input(Bool())
    val tx_axis_tuser = Input(UInt(1.W))
    
    // RX AXIS
    val rx_axis_aresetn = Input(Bool())
    val rx_axis_tdata = Output(UInt(64.W))
    val rx_axis_tkeep = Output(UInt(8.W))
    val rx_axis_tvalid = Output(Bool())
    val rx_axis_tlast = Output(Bool())
    val rx_axis_tuser = Output(UInt(1.W))
    val rx_good_frames = Output(UInt(32.W))
    val rx_bad_frames = Output(UInt(32.W))
  })

  // Internal signals
  val rx_pause_active = Wire(Bool())
  val tx_rst = Wire(Bool())
  val rx_rst = Wire(Bool())

  // Instantiate TX reset module
  // Note: In real implementation, this would need to be in the tx_clk0 domain
  val tx_rst_mod = Module(new RstMod)
  tx_rst_mod.io.reset := io.reset
  tx_rst_mod.io.dcm_locked := io.tx_dcm_locked
  tx_rst := tx_rst_mod.io.rst

  // Instantiate RX reset module
  // Note: In real implementation, this would need to be in the rx_clk0 domain
  val rx_rst_mod = Module(new RstMod)
  rx_rst_mod.io.reset := io.reset
  rx_rst_mod.io.dcm_locked := io.rx_dcm_locked
  rx_rst := rx_rst_mod.io.rst

  // Status vector - currently unused
  io.status_vector := 0.U

  // Instantiate TX module
  // Note: In real implementation, this would be in the tx_clk0 domain
  val tx_mod = Module(new Tx)
  
  // Connect TX module
  tx_mod.io.rst := tx_rst
  tx_mod.io.configuration_vector := io.tx_configuration_vector
  tx_mod.io.axis_aresetn := io.tx_axis_aresetn
  tx_mod.io.axis_tdata := io.tx_axis_tdata
  tx_mod.io.axis_tkeep := io.tx_axis_tkeep
  tx_mod.io.axis_tvalid := io.tx_axis_tvalid
  tx_mod.io.axis_tlast := io.tx_axis_tlast
  tx_mod.io.axis_tuser := io.tx_axis_tuser
  tx_mod.io.carrier_sense := io.carrier_sense
  tx_mod.io.cfg_rx_pause_enable := io.cfg_rx_pause_enable
  tx_mod.io.cfg_station_macaddr := io.cfg_station_macaddr
  tx_mod.io.cfg_tx_pause_refresh := io.pause_val
  tx_mod.io.rx_pause_active := rx_pause_active
  tx_mod.io.tx_pause_send := io.pause_req
  
  // Connect TX outputs
  io.tx_axis_tready := tx_mod.io.axis_tready
  io.tx_statistics_vector := tx_mod.io.tx_statistics_vector
  io.tx_statistics_valid := tx_mod.io.tx_statistics_valid
  io.xgmii_txc := tx_mod.io.xgmii_txc
  io.xgmii_txd := tx_mod.io.xgmii_txd

  // Instantiate RX module
  // Note: In real implementation, this would be in the rx_clk0 domain
  val rx_mod = Module(new Rx)
  
  // Connect RX module
  rx_mod.io.rst := rx_rst
  rx_mod.io.configuration_vector := io.rx_configuration_vector
  rx_mod.io.cfg_rx_pause_enable := io.cfg_rx_pause_enable
  rx_mod.io.cfg_sub_quanta_count := io.cfg_sub_quanta_count
  rx_mod.io.xgmii_rxd := io.xgmii_rxd
  rx_mod.io.xgmii_rxc := io.xgmii_rxc
  rx_mod.io.axis_aresetn := io.rx_axis_aresetn
  
  // Connect RX outputs
  rx_pause_active := rx_mod.io.rx_pause_active
  io.rx_statistics_vector := rx_mod.io.rx_statistics_vector
  io.rx_statistics_valid := rx_mod.io.rx_statistics_valid
  io.rx_axis_tdata := rx_mod.io.axis_tdata
  io.rx_axis_tkeep := rx_mod.io.axis_tkeep
  io.rx_axis_tvalid := rx_mod.io.axis_tvalid
  io.rx_axis_tlast := rx_mod.io.axis_tlast
  io.rx_axis_tuser := rx_mod.io.axis_tuser
  io.rx_good_frames := rx_mod.io.good_frames
  io.rx_bad_frames := rx_mod.io.bad_frames
}

object NfMac10g {
  def apply(): NfMac10g = Module(new NfMac10g)
}

// Generate Verilog
object NfMac10gVerilog extends App {
  ChiselStage.emitSystemVerilog(
    new NfMac10g,
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays",
      "--disable-all-randomization",
      "--strip-debug-info",
      "--split-verilog",
      s"-o=generated/NfMac10g"
    )
  )
}
