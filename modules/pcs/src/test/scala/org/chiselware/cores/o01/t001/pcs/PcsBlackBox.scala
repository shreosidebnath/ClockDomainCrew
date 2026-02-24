import chisel3._
import chisel3.util.HasBlackBoxResource

class PcsBlackBox extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {})

  addResource(
    "org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_rx_ber_mon.sv"
  )
  addResource(
    "/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_rx_frame_sync.sv"
  )
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_rx_if.sv")
  addResource(
    "/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_rx_watchdog.sv"
  )
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_rx.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_tx_if.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g_tx.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_eth_phy_10g.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_lfsr.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_xgmii_baser_dec.sv")
  addResource("/org/chiselware/cores/o01/t001/pcs/taxi_xgmii_baser_enc.sv")
}
