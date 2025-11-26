import chisel3._
import chisel3.util.HasBlackBoxResource

class NfMac10gBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    // Clocks and reset
    val tx_clk0          = Input(Clock())
    val rx_clk0          = Input(Clock())
    val reset            = Input(Bool())
    val tx_dcm_locked    = Input(Bool())
    val rx_dcm_locked    = Input(Bool())

    // Flow control and configuration
    val tx_ifg_delay           = Input(UInt(8.W))
    val pause_val              = Input(UInt(16.W))
    val pause_req              = Input(Bool())
    val tx_configuration_vector= Input(UInt(80.W))
    val rx_configuration_vector= Input(UInt(80.W))
    val status_vector          = Output(UInt(2.W))

    // TX AXIS
    val tx_axis_aresetn = Input(Bool())
    val tx_axis_tdata   = Input(UInt(64.W))
    val tx_axis_tkeep   = Input(UInt(8.W))
    val tx_axis_tvalid  = Input(Bool())
    val tx_axis_tready  = Output(Bool())
    val tx_axis_tlast   = Input(Bool())
    val tx_axis_tuser   = Input(UInt(1.W))

    // RX AXIS
    val rx_axis_aresetn = Input(Bool())
    val rx_axis_tdata   = Output(UInt(64.W))
    val rx_axis_tkeep   = Output(UInt(8.W))
    val rx_axis_tvalid  = Output(Bool())
    val rx_axis_tlast   = Output(Bool())
    val rx_axis_tuser   = Output(UInt(1.W))
  })
  addResource("/nfmac10g.v")
  addResource("/rst_mod.v")
  addResource("/tx.v")
  addResource("/rx.v")
  addResource("/axis2xgmii.v")
  addResource("/xgmii_includes.vh")
}
