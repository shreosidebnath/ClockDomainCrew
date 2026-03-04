package org.chiselware.cores.o01.t001.pcs

import chisel3._
import chisel3.experimental.{IntParam, DoubleParam}
import chisel3.util.HasBlackBoxResource

object PcsBbFiles {
  val pcsFiles: Seq[String] = Seq(
    // LFSR
    "taxi_lfsr.sv",

    // XGMII decoder/encoder
    "taxi_xgmii_baser_dec.sv",
    "taxi_xgmii_baser_enc.sv",

    // RX
    "taxi_eth_phy_10g_rx.sv",
    "taxi_eth_phy_10g_rx_if.sv",
    "taxi_eth_phy_10g_rx_ber_mon.sv",
    "taxi_eth_phy_10g_rx_frame_sync.sv",
    "taxi_eth_phy_10g_rx_watchdog.sv",

    // TX
    "taxi_eth_phy_10g_tx.sv",
    "taxi_eth_phy_10g_tx_if.sv",

    // Top Module
    "taxi_eth_phy_10g.sv",

    // wrapper must instantiate taxi_eth_phy_10g
    "taxi_eth_phy_10g_wrapper.sv"
  )
}

case class PcsBbParams(
  dataW: Int = 64,
  hdrW:  Int = 2,

  // These are compile-time params you said your Chisel PCS uses.
  txGbxIfEn: Boolean = true,
  rxGbxIfEn: Boolean = true,
  bitReverse: Boolean = true,
  scramblerDisable: Boolean = false,
  prbs31En: Boolean = false,
  txSerdesPipeline: Int = 1,
  rxSerdesPipeline: Int = 1,
  bitslipHighCycles: Int = 0,
  bitslipLowCycles: Int = 7,
  count125Us: Double = 125000.0/6.4,

  bbFiles: Seq[String] = PcsBbFiles.pcsFiles
)

class PcsBb(p: PcsBbParams)
  extends BlackBox(Map(
    "DATA_W" -> IntParam(p.dataW),
    "CTRL_W" -> IntParam(p.dataW / 8),
    "HDR_W"  -> IntParam(p.hdrW),

    "TX_GBX_IF_EN"       -> IntParam(if (p.txGbxIfEn) 1 else 0),
    "RX_GBX_IF_EN"       -> IntParam(if (p.rxGbxIfEn) 1 else 0),
    "BIT_REVERSE"        -> IntParam(if (p.bitReverse) 1 else 0),
    "SCRAMBLER_DISABLE"  -> IntParam(if (p.scramblerDisable) 1 else 0),
    "PRBS31_EN"          -> IntParam(if (p.prbs31En) 1 else 0),
    "TX_SERDES_PIPELINE" -> IntParam(p.txSerdesPipeline),
    "RX_SERDES_PIPELINE" -> IntParam(p.rxSerdesPipeline),
    "BITSLIP_HIGH_CYCLES"-> IntParam(p.bitslipHighCycles),
    "BITSLIP_LOW_CYCLES" -> IntParam(p.bitslipLowCycles),
    // Keep real number
    "COUNT_125US"        -> DoubleParam(p.count125Us)
  )) with HasBlackBoxResource {

  // This makes the emitted BlackBox module name match your wrapper module name
  override val desiredName = "taxi_eth_phy_10g_wrapper"

  private val ctrlW = p.dataW / 8

  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())

    // XGMII
    val xgmii_txd      = Input(UInt(p.dataW.W))
    val xgmii_txc      = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val xgmii_rxd      = Output(UInt(p.dataW.W))
    val xgmii_rxc      = Output(UInt(ctrlW.W))
    val xgmii_rx_valid = Output(Bool())

    // Gearbox handshake
    val tx_gbx_req_sync  = Output(Bool())
    val tx_gbx_req_stall = Output(Bool())
    val tx_gbx_sync      = Input(Bool())

    // SERDES (64b/66b)
    val serdes_tx_data       = Output(UInt(p.dataW.W))
    val serdes_tx_data_valid = Output(Bool())
    val serdes_tx_hdr        = Output(UInt(p.hdrW.W))
    val serdes_tx_hdr_valid  = Output(Bool())
    val serdes_tx_gbx_req_sync  = Input(Bool())
    val serdes_tx_gbx_req_stall = Input(Bool())
    val serdes_tx_gbx_sync      = Output(Bool())

    val serdes_rx_data       = Input(UInt(p.dataW.W))
    val serdes_rx_data_valid = Input(Bool())
    val serdes_rx_hdr        = Input(UInt(p.hdrW.W))
    val serdes_rx_hdr_valid  = Input(Bool())
    val serdes_rx_bitslip    = Output(Bool())
    val serdes_rx_reset_req  = Output(Bool())

    // Status
    val tx_bad_block      = Output(Bool())
    val rx_error_count    = Output(UInt(7.W))
    val rx_bad_block      = Output(Bool())
    val rx_sequence_error = Output(Bool())
    val rx_block_lock     = Output(Bool())
    val rx_high_ber       = Output(Bool())
    val rx_status         = Output(Bool())

    // Config
    val cfg_tx_prbs31_enable = Input(Bool())
    val cfg_rx_prbs31_enable = Input(Bool())
  })

  p.bbFiles.foreach(f => addResource(s"/org/chiselware/cores/o01/t001/pcs/$f"))
}