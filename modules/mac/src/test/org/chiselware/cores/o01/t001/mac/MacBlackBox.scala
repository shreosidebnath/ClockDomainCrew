import chisel3._
import chisel3.util.HasBlackBoxResource

class MacBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    // TODO: fix IO
  })

  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_null_src.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_tie.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_xgmii_rx_32.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_xgmii_rx_64.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_xgmii_tx_32.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_axis_xgmii_tx_64.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_eth_mac_10g.sv")
  addResource("/org/chiselware/cores/o01/t001/mac/taxi_lfsr.sv")
}
