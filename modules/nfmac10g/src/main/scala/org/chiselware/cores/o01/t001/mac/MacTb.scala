package org.chiselware.cores.o01.t001.mac
import org.chiselware.cores.o01.t001.mac.AxisInterface
import org.chiselware.cores.o01.t001.mac.AxisInterfaceParams
import chisel3._
import chisel3.util._


class Axis2Xgmii64Tb(val p: Axis2Xgmii64Params) extends Module {
    val keepW = dataW / 8
    val txUserW = 1
    val rxUserW = (if (ptpTsEn) ptpTsW else 0) + 1
    val txTagW = 8 // Extracted from s_axis_tx.ID_W

    val io = IO(new Bundle {
        val rx_clk = Input(Clock())
        val rx_rst = Input(Bool())
        val tx_clk = Input(Clock())
        val tx_rst = Input(Bool())
        val s_axis_tx = Flipped(new AxisInterface(AxisInterfaceParams(dataW = p.dataW, keepW = keepW, idEn = true, idW = txTagW, userEn = true, userW = txUserW)))
        val m_axis_tx_cpl = new AxisInterface(AxisInterfaceParams( dataW = 96 keepW = 1, idW = 8))
        val m_axis_rx = new AxisInterface(AxisInterfaceParams(dataW = p.dataW, keepW = keepW, userEn = true, userW = rxUserW))
        val xgmii_rxd = Input(UInt(p.dataW.W))
        val xgmii_rxc = Input(UInt(p.ctrlW.W))
        val xgmii_rx_valid = Input(Bool())
        val xgmii_txd = Output(UInt(p.dataW.W))
        val xgmii_txc = Output(UInt(p.ctrlW.W))
        val xgmii_tx_valid = Output(Bool())
        val tx_gbx_req_sync = Input(UInt(p.gbxCnt.W))
        val tx_gbx_req_stall = Input(Bool())
        val tx_gbx_sync = Output(UInt(p.gbxCnt.W))
        val tx_ptp_ts = Input(UInt(p.ptpTsW.W))
        val rx_ptp_ts = Input(UInt(p.ptpTsW.W))
        val tx_lfc_req = Input(Bool())
        val tx_lfc_resend = Input(Bool())
        val rx_lfc_en = Input(Bool())
        val rx_lfc_req = Output(Bool())
        val rx_lfc_ack = Input(Bool())
        val tx_pfc_req = Input(UInt(8.W))
        val tx_pfc_resend = Input(Bool())
        val rx_pfc_en = Input(UInt(8.W))
        val rx_pfc_req = Output(UInt(8.W))
        val rx_pfc_ack = Input(UInt(8.W))
        val tx_lfc_pause_en = Input(Bool())
        val tx_pause_req = Input(Bool())
        val tx_pause_ack = Output(Bool())
        val tx_start_packet = Output(UInt(2.W))
        val stat_tx_byte = Output(UInt(4.W))
        val stat_tx_pkt_len = Output(UInt(16.W))
        val stat_tx_pkt_ucast = Output(Bool())
        val stat_tx_pkt_mcast = Output(Bool())
        val stat_tx_pkt_bcast = Output(Bool())
        val stat_tx_pkt_vlan = Output(Bool())
        val stat_tx_pkt_good = Output(Bool())
        val stat_tx_pkt_bad = Output(Bool())
        val stat_tx_err_oversize = Output(Bool())
        val stat_tx_err_user = Output(Bool())
        val stat_tx_err_underflow = Output(Bool())
        val rx_start_packet = Output(UInt(2.W))
        val stat_rx_byte = Output(UInt(4.W))
        val stat_rx_pkt_len = Output(UInt(16.W))
        val stat_rx_pkt_fragment = Output(Bool())
        val stat_rx_pkt_jabber = Output(Bool())
        val stat_rx_pkt_ucast = Output(Bool())
        val stat_rx_pkt_mcast = Output(Bool())
        val stat_rx_pkt_bcast = Output(Bool())
        val stat_rx_pkt_vlan = Output(Bool())
        val stat_rx_pkt_good = Output(Bool())
        val stat_rx_pkt_bad = Output(Bool())
        val stat_rx_err_oversize = Output(Bool())
        val stat_rx_err_bad_fcs = Output(Bool())
        val stat_rx_err_bad_block = Output(Bool())
        val stat_rx_err_framing = Output(Bool())
        val stat_rx_err_preamble = Output(Bool())
        val stat_tx_mcf = Output(Bool())
        val stat_rx_mcf = Output(Bool())
        val stat_tx_lfc_pkt = Output(Bool())
        val stat_tx_lfc_xon = Output(Bool())
        val stat_tx_lfc_xoff = Output(Bool())
        val stat_tx_lfc_paused = Output(Bool())
        val stat_tx_pfc_pkt = Output(Bool())
        val stat_tx_pfc_xon = Output(UInt(8.W))
        val stat_tx_pfc_xoff = Output(UInt(8.W))
        val stat_tx_pfc_paused = Output(UInt(8.W))
        val stat_rx_lfc_pkt = Output(Bool())
        val stat_rx_lfc_xon = Output(Bool())
        val stat_rx_lfc_xoff = Output(Bool())
        val stat_rx_lfc_paused = Output(Bool())
        val stat_rx_pfc_pkt = Output(Bool())
        val stat_rx_pfc_xon = Output(UInt(8.W))
        val stat_rx_pfc_xoff = Output(UInt(8.W))
        val stat_rx_pfc_paused = Output(UInt(8.W))
        val cfg_tx_max_pkt_len = Input(UInt(16.W))
        val cfg_tx_ifg = Input(UInt(8.W))
        val cfg_tx_enable = Input(Bool())
        val cfg_rx_max_pkt_len = Input(UInt(16.W))
        val cfg_rx_enable = Input(Bool())
        val cfg_mcf_rx_eth_dst_mcast = Input(UInt(48.W))
        val cfg_mcf_rx_check_eth_dst_mcast = Input(Bool())
        val cfg_mcf_rx_eth_dst_ucast = Input(UInt(48.W))
        val cfg_mcf_rx_check_eth_dst_ucast = Input(Bool())
        val cfg_mcf_rx_eth_src = Input(UInt(48.W))
        val cfg_mcf_rx_check_eth_src = Input(Bool())
        val cfg_mcf_rx_eth_type = Input(UInt(16.W))
        val cfg_mcf_rx_opcode_lfc = Input(UInt(16.W))
        val cfg_mcf_rx_check_opcode_lfc = Input(Bool())
        val cfg_mcf_rx_opcode_pfc = Input(UInt(16.W))
        val cfg_mcf_rx_check_opcode_pfc = Input(Bool())
        val cfg_mcf_rx_forward = Input(Bool())
        val cfg_mcf_rx_enable = Input(Bool())
        val cfg_tx_lfc_eth_dst = Input(UInt(48.W))
        val cfg_tx_lfc_eth_src = Input(UInt(48.W))
        val cfg_tx_lfc_eth_type = Input(UInt(16.W))
        val cfg_tx_lfc_opcode = Input(UInt(16.W))
        val cfg_tx_lfc_en = Input(Bool())
        val cfg_tx_lfc_quanta = Input(UInt(16.W))
        val cfg_tx_lfc_refresh = Input(UInt(16.W))
        val cfg_tx_pfc_eth_dst = Input(UInt(48.W))
        val cfg_tx_pfc_eth_src = Input(UInt(48.W))
        val cfg_tx_pfc_eth_type = Input(UInt(16.W))
        val cfg_tx_pfc_opcode = Input(UInt(16.W))
        val cfg_tx_pfc_en = Input(Bool())
        val cfg_tx_pfc_quanta = Input(Vec(8, UInt(16.W)))
        val cfg_tx_pfc_refresh = Input(Vec(8, UInt(16.W)))
        val cfg_rx_lfc_opcode = Input(UInt(16.W))
        val cfg_rx_lfc_en = Input(Bool())
        val cfg_rx_pfc_opcode = Input(UInt(16.W))
        val cfg_rx_pfc_en = Input(Bool())
    })

    val dut = Module(new Axis2Xgmii64(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        txCplCtrlInTuser = p.txCplCtrlInTuser
    ))

    dut.clock := io.clk
    dut.reset := io.rst
    io.s_axis_tx <> dut.io.s_axis_tx
    dut.io.m_axis_tx_cpl <> io.m_axis_tx_cpl
    io.xgmii_txd := dut.io.xgmii_txd
    io.xgmii_txc := dut.io.xgmii_txc
    io.xgmii_tx_valid := dut.io.xgmii_tx_valid
    dut.io.tx_gbx_req_sync := io.tx_gbx_req_sync
    dut.io.tx_gbx_req_stall := io.tx_gbx_req_stall
    io.tx_gbx_sync := dut.io.tx_gbx_sync
    dut.io.ptp_ts := io.ptp_ts
    dut.io.cfg_tx_max_pkt_len := io.cfg_tx_max_pkt_len
    dut.io.cfg_tx_ifg := io.cfg_tx_ifg
    dut.io.cfg_tx_enable := io.cfg_tx_enable
    io.tx_start_packet := dut.io.tx_start_packet
    io.stat_tx_byte := dut.io.stat_tx_byte
    io.stat_tx_pkt_len := dut.io.stat_tx_pkt_len
    io.stat_tx_pkt_ucast := dut.io.stat_tx_pkt_ucast
    io.stat_tx_pkt_mcast := dut.io.stat_tx_pkt_mcast
    io.stat_tx_pkt_bcast := dut.io.stat_tx_pkt_bcast
    io.stat_tx_pkt_vlan := dut.io.stat_tx_pkt_vlan
    io.stat_tx_pkt_good := dut.io.stat_tx_pkt_good
    io.stat_tx_pkt_bad := dut.io.stat_tx_pkt_bad
    io.stat_tx_err_oversize := dut.io.stat_tx_err_oversize
    io.stat_tx_err_user := dut.io.stat_tx_err_user
    io.stat_tx_err_underflow := dut.io.stat_tx_err_underflow
}