package org.chiselware.cores.o01.t001.mac

import chisel3._
import chisel3.experimental.IntParam
import chisel3.util.HasBlackBoxResource

object MacBbFiles {
  val macFiles: Seq[String] = Seq(
    "taxi_axis_if.sv",
    "taxi_lfsr.sv",
    "taxi_axis_null_src.sv",
    "taxi_axis_tie.sv",
    "taxi_axis_xgmii_rx_64.sv",
    "taxi_axis_xgmii_tx_64.sv",

    // IMPORTANT: uses defaults -> Verilator v5.044+ REQUIRED
    "taxi_eth_mac_10g.sv",

    // wrapper must instantiate taxi_eth_mac_10g
    "taxi_eth_mac_10g_wrapper.sv"
  )
}

case class MacBbParams(
  dataW: Int = 64,
  idW: Int = 8,
  userW: Int = 1,
  gbxCnt: Int = 1,
  bbFiles: Seq[String] = MacBbFiles.macFiles
)

class MacBb(p: MacBbParams)
  extends BlackBox(Map(
    "DATA_W" -> IntParam(p.dataW),
    "CTRL_W" -> IntParam(p.dataW/8),
    "ID_W"   -> IntParam(p.idW),
    "USER_W" -> IntParam(p.userW),
    "GBX_CNT"-> IntParam(p.gbxCnt)
  )) with HasBlackBoxResource {

  override val desiredName = "taxi_eth_mac_10g_wrapper"

  private val ctrlW = p.dataW/8

  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())

    val s_axis_tx_tdata  = Input(UInt(p.dataW.W))
    val s_axis_tx_tkeep  = Input(UInt(ctrlW.W))
    val s_axis_tx_tvalid = Input(Bool())
    val s_axis_tx_tready = Output(Bool())
    val s_axis_tx_tlast  = Input(Bool())
    val s_axis_tx_tuser  = Input(UInt(p.userW.W))
    val s_axis_tx_tid    = Input(UInt(p.idW.W))

    val m_axis_tx_cpl_tdata  = Output(UInt(p.dataW.W))
    val m_axis_tx_cpl_tkeep  = Output(UInt(ctrlW.W))
    val m_axis_tx_cpl_tvalid = Output(Bool())
    val m_axis_tx_cpl_tready = Input(Bool())
    val m_axis_tx_cpl_tlast  = Output(Bool())
    val m_axis_tx_cpl_tuser  = Output(UInt(p.userW.W))
    val m_axis_tx_cpl_tid    = Output(UInt(p.idW.W))

    val m_axis_rx_tdata  = Output(UInt(p.dataW.W))
    val m_axis_rx_tkeep  = Output(UInt(ctrlW.W))
    val m_axis_rx_tvalid = Output(Bool())
    val m_axis_rx_tready = Input(Bool())
    val m_axis_rx_tlast  = Output(Bool())
    val m_axis_rx_tuser  = Output(UInt(p.userW.W))
    val m_axis_rx_tid    = Output(UInt(p.idW.W))

    val xgmii_rxd      = Input(UInt(p.dataW.W))
    val xgmii_rxc      = Input(UInt(ctrlW.W))
    val xgmii_rx_valid = Input(Bool())
    val xgmii_txd      = Output(UInt(p.dataW.W))
    val xgmii_txc      = Output(UInt(ctrlW.W))
    val xgmii_tx_valid = Output(Bool())

    val tx_gbx_req_sync  = Input(UInt(p.gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync      = Output(UInt(p.gbxCnt.W))

    val cfg_tx_max_pkt_len = Input(UInt(16.W))
    val cfg_tx_ifg         = Input(UInt(8.W))
    val cfg_tx_enable      = Input(Bool())
    val cfg_rx_max_pkt_len = Input(UInt(16.W))
    val cfg_rx_enable      = Input(Bool())

    val tx_ptp_ts = Input(UInt(96.W))   // if PTP_TS_FMT_TOD=1 -> 96
    val rx_ptp_ts = Input(UInt(96.W))

    val stat_rx_pkt_good      = Output(Bool())
    val stat_rx_pkt_bad       = Output(Bool())
    val stat_rx_err_bad_fcs   = Output(Bool())
    val stat_rx_err_preamble  = Output(Bool())
    val stat_rx_err_framing   = Output(Bool())
    val stat_rx_err_oversize  = Output(Bool())
    val stat_rx_pkt_fragment  = Output(Bool())

    val stat_tx_pkt_good        = Output(Bool())
    val stat_tx_pkt_bad         = Output(Bool())
    val stat_tx_err_oversize    = Output(Bool())
    val stat_tx_err_user        = Output(Bool())
    val stat_tx_err_underflow   = Output(Bool())
  })

  p.bbFiles.foreach(f => addResource(s"/org/chiselware/cores/o01/t001/mac/$f"))
}
