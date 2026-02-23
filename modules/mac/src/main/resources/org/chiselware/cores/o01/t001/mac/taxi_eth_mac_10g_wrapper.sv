`timescale 1ns/1ps
`default_nettype none

module taxi_eth_mac_10g_wrapper #(
    parameter DATA_W = 64,
    parameter CTRL_W = (DATA_W/8),

    // These three must match taxi_axis_if instantiation
    parameter ID_W   = 8,              // pick something reasonable; can be overridden
    parameter USER_W = 1,              // must match what MAC expects

    parameter logic TX_GBX_IF_EN = 1'b0,
    parameter logic RX_GBX_IF_EN = TX_GBX_IF_EN,
    parameter GBX_CNT = 1,
    parameter logic PTP_TS_EN = 1'b0,
    parameter logic PTP_TS_FMT_TOD = 1'b1,
    parameter PTP_TS_W = PTP_TS_FMT_TOD ? 96 : 64
)(
    input  logic                 rx_clk,
    input  logic                 rx_rst,
    input  logic                 tx_clk,
    input  logic                 tx_rst,

    // s_axis_tx (sink)
    input  logic [DATA_W-1:0]    s_axis_tx_tdata,
    input  logic [CTRL_W-1:0]    s_axis_tx_tkeep,
    input  logic                 s_axis_tx_tvalid,
    output logic                 s_axis_tx_tready,
    input  logic                 s_axis_tx_tlast,
    input  logic [USER_W-1:0]    s_axis_tx_tuser,
    input  logic [ID_W-1:0]      s_axis_tx_tid,

    // m_axis_tx_cpl (src)
    output logic [DATA_W-1:0]    m_axis_tx_cpl_tdata,
    output logic [CTRL_W-1:0]    m_axis_tx_cpl_tkeep,
    output logic                 m_axis_tx_cpl_tvalid,
    input  logic                 m_axis_tx_cpl_tready,
    output logic                 m_axis_tx_cpl_tlast,
    output logic [USER_W-1:0]    m_axis_tx_cpl_tuser,
    output logic [ID_W-1:0]      m_axis_tx_cpl_tid,

    // m_axis_rx (src)  (add tid if present in your taxi_axis_if)
    output logic [DATA_W-1:0]    m_axis_rx_tdata,
    output logic [CTRL_W-1:0]    m_axis_rx_tkeep,
    output logic                 m_axis_rx_tvalid,
    input  logic                 m_axis_rx_tready,
    output logic                 m_axis_rx_tlast,
    output logic [USER_W-1:0]    m_axis_rx_tuser,
    output logic [ID_W-1:0]      m_axis_rx_tid,

    // XGMII
    input  logic [DATA_W-1:0]    xgmii_rxd,
    input  logic [CTRL_W-1:0]    xgmii_rxc,
    input  logic                 xgmii_rx_valid,
    output logic [DATA_W-1:0]    xgmii_txd,
    output logic [CTRL_W-1:0]    xgmii_txc,
    output logic                 xgmii_tx_valid,
    input  logic [GBX_CNT-1:0]   tx_gbx_req_sync,
    input  logic                 tx_gbx_req_stall,
    output logic [GBX_CNT-1:0]   tx_gbx_sync,

    // PTP
    input  logic [PTP_TS_W-1:0]  tx_ptp_ts,
    input  logic [PTP_TS_W-1:0]  rx_ptp_ts,

    // Minimal config
    input  logic [15:0]          cfg_tx_max_pkt_len,
    input  logic [7:0]           cfg_tx_ifg,
    input  logic                 cfg_tx_enable,
    input  logic [15:0]          cfg_rx_max_pkt_len,
    input  logic                 cfg_rx_enable,

    // Status outputs
    output logic stat_rx_pkt_good,
    output logic stat_rx_pkt_bad,
    output logic stat_rx_err_bad_fcs,
    output logic stat_rx_err_preamble,
    output logic stat_rx_err_framing,
    output logic stat_rx_err_oversize,
    output logic stat_rx_pkt_fragment
);
    logic                 tx_lfc_req      = 1'b0;
    logic                 tx_lfc_resend   = 1'b0;
    logic                 rx_lfc_en       = 1'b0;
    logic                 rx_lfc_req;
    logic                 rx_lfc_ack      = 1'b0;

    logic [7:0]           tx_pfc_req      = 8'h00;
    logic                 tx_pfc_resend   = 1'b0;
    logic [7:0]           rx_pfc_en       = 8'h00;
    logic [7:0]           rx_pfc_req;
    logic [7:0]           rx_pfc_ack      = 8'h00;

    logic                 tx_lfc_pause_en = 1'b0;
    logic                 tx_pause_req    = 1'b0;
    logic                 tx_pause_ack;

    logic                 stat_clk        = 1'b0;
    logic                 stat_rst        = 1'b0;

    logic [1:0]           tx_start_packet;
    logic [3:0]           stat_tx_byte;
    logic [15:0]          stat_tx_pkt_len;
    logic                 stat_tx_pkt_ucast, stat_tx_pkt_mcast, stat_tx_pkt_bcast, stat_tx_pkt_vlan;
    logic                 stat_tx_pkt_good, stat_tx_pkt_bad;
    logic                 stat_tx_err_oversize, stat_tx_err_user, stat_tx_err_underflow;

    logic [1:0]           rx_start_packet;
    logic [3:0]           stat_rx_byte;
    logic [15:0]          stat_rx_pkt_len;
    logic                 stat_rx_pkt_jabber;
    logic                 stat_rx_pkt_ucast, stat_rx_pkt_mcast, stat_rx_pkt_bcast, stat_rx_pkt_vlan;
    logic                 stat_rx_err_bad_block;
    logic                 stat_rx_fifo_drop = 1'b0;

    logic                 stat_tx_mcf, stat_rx_mcf;
    logic                 stat_tx_lfc_pkt, stat_tx_lfc_xon, stat_tx_lfc_xoff, stat_tx_lfc_paused;
    logic                 stat_tx_pfc_pkt;
    logic [7:0]           stat_tx_pfc_xon, stat_tx_pfc_xoff, stat_tx_pfc_paused;
    logic                 stat_rx_lfc_pkt, stat_rx_lfc_xon, stat_rx_lfc_xoff, stat_rx_lfc_paused;
    logic                 stat_rx_pfc_pkt;
    logic [7:0]           stat_rx_pfc_xon, stat_rx_pfc_xoff, stat_rx_pfc_paused;

    // Config tie-offs (MCF/LFC/PFC)
    logic [47:0] cfg_mcf_rx_eth_dst_mcast         = 48'h01_80_C2_00_00_01;
    logic        cfg_mcf_rx_check_eth_dst_mcast   = 1'b1;
    logic [47:0] cfg_mcf_rx_eth_dst_ucast         = 48'd0;
    logic        cfg_mcf_rx_check_eth_dst_ucast   = 1'b0;
    logic [47:0] cfg_mcf_rx_eth_src               = 48'd0;
    logic        cfg_mcf_rx_check_eth_src         = 1'b0;
    logic [15:0] cfg_mcf_rx_eth_type              = 16'h8808;
    logic [15:0] cfg_mcf_rx_opcode_lfc            = 16'h0001;
    logic        cfg_mcf_rx_check_opcode_lfc      = 1'b1;
    logic [15:0] cfg_mcf_rx_opcode_pfc            = 16'h0101;
    logic        cfg_mcf_rx_check_opcode_pfc      = 1'b1;
    logic        cfg_mcf_rx_forward               = 1'b0;
    logic        cfg_mcf_rx_enable                = 1'b0;

    logic [47:0] cfg_tx_lfc_eth_dst               = 48'h01_80_C2_00_00_01;
    logic [47:0] cfg_tx_lfc_eth_src               = 48'h80_23_31_43_54_4C;
    logic [15:0] cfg_tx_lfc_eth_type              = 16'h8808;
    logic [15:0] cfg_tx_lfc_opcode                = 16'h0001;
    logic        cfg_tx_lfc_en                    = 1'b0;
    logic [15:0] cfg_tx_lfc_quanta                = 16'hffff;
    logic [15:0] cfg_tx_lfc_refresh               = 16'h7fff;

    logic [47:0] cfg_tx_pfc_eth_dst               = 48'h01_80_C2_00_00_01;
    logic [47:0] cfg_tx_pfc_eth_src               = 48'h80_23_31_43_54_4C;
    logic [15:0] cfg_tx_pfc_eth_type              = 16'h8808;
    logic [15:0] cfg_tx_pfc_opcode                = 16'h0101;
    logic        cfg_tx_pfc_en                    = 1'b0;
    logic [15:0] cfg_tx_pfc_quanta [8]            = '{8{16'hffff}};
    logic [15:0] cfg_tx_pfc_refresh[8]            = '{8{16'h7fff}};

    logic [15:0] cfg_rx_lfc_opcode                = 16'h0001;
    logic        cfg_rx_lfc_en                    = 1'b0;
    logic [15:0] cfg_rx_pfc_opcode                = 16'h0101;
    logic        cfg_rx_pfc_en                    = 1'b0;

    taxi_axis_if #(
        .DATA_W(DATA_W),
        .KEEP_W(CTRL_W),
        .USER_EN(1),
        .USER_W(USER_W),
        .ID_EN(1),
        .ID_W(ID_W)
    ) s_axis_tx();

    taxi_axis_if #(
        .DATA_W(DATA_W),
        .KEEP_W(CTRL_W),
        .USER_EN(1),
        .USER_W(USER_W),
        .ID_EN(1),
        .ID_W(ID_W)
    ) m_axis_tx_cpl();

    taxi_axis_if #(
        .DATA_W(DATA_W),
        .KEEP_W(CTRL_W),
        .USER_EN(1),
        .USER_W(USER_W),
        .ID_EN(1),
        .ID_W(ID_W)
    ) m_axis_rx();

    taxi_axis_if #(
        .DATA_W(DATA_W),
        .KEEP_W(CTRL_W),
        .USER_EN(1),
        .USER_W(USER_W),
        .ID_EN(1),
        .ID_W(ID_W)
    ) m_axis_stat();
    always_comb m_axis_stat.tready = 1'b1;

    // Sink hookups
    always_comb begin
        s_axis_tx.tdata  = s_axis_tx_tdata;
        s_axis_tx.tkeep  = s_axis_tx_tkeep;
        s_axis_tx.tvalid = s_axis_tx_tvalid;
        s_axis_tx.tlast  = s_axis_tx_tlast;
        s_axis_tx.tuser  = s_axis_tx_tuser;
        s_axis_tx.tid    = s_axis_tx_tid;
        s_axis_tx_tready = s_axis_tx.tready;
        // if your interface has tdest/tstrb: tie or drive as needed
    end

    // TX completion src hookups
    always_comb begin
        m_axis_tx_cpl_tdata  = m_axis_tx_cpl.tdata;
        m_axis_tx_cpl_tkeep  = m_axis_tx_cpl.tkeep;
        m_axis_tx_cpl_tvalid = m_axis_tx_cpl.tvalid;
        m_axis_tx_cpl.tready = m_axis_tx_cpl_tready;
        m_axis_tx_cpl_tlast  = m_axis_tx_cpl.tlast;
        m_axis_tx_cpl_tuser  = m_axis_tx_cpl.tuser;
        m_axis_tx_cpl_tid    = m_axis_tx_cpl.tid;
    end

    // RX src hookups
    always_comb begin
        m_axis_rx_tdata  = m_axis_rx.tdata;
        m_axis_rx_tkeep  = m_axis_rx.tkeep;
        m_axis_rx_tvalid = m_axis_rx.tvalid;
        m_axis_rx.tready = m_axis_rx_tready;
        m_axis_rx_tlast  = m_axis_rx.tlast;
        m_axis_rx_tuser  = m_axis_rx.tuser;
        m_axis_rx_tid    = m_axis_rx.tid;
    end

    taxi_eth_mac_10g #(
        .DATA_W(DATA_W),
        .CTRL_W(CTRL_W),
        .TX_GBX_IF_EN(TX_GBX_IF_EN),
        .RX_GBX_IF_EN(RX_GBX_IF_EN),
        .GBX_CNT(GBX_CNT),
        .PTP_TS_EN(PTP_TS_EN),
        .PTP_TS_FMT_TOD(PTP_TS_FMT_TOD)
    ) dut (
        .rx_clk(rx_clk),
        .rx_rst(rx_rst),
        .tx_clk(tx_clk),
        .tx_rst(tx_rst),

        .s_axis_tx(s_axis_tx),
        .m_axis_tx_cpl(m_axis_tx_cpl),
        .m_axis_rx(m_axis_rx),

        .xgmii_rxd(xgmii_rxd),
        .xgmii_rxc(xgmii_rxc),
        .xgmii_rx_valid(xgmii_rx_valid),
        .xgmii_txd(xgmii_txd),
        .xgmii_txc(xgmii_txc),
        .xgmii_tx_valid(xgmii_tx_valid),
        .tx_gbx_req_sync(tx_gbx_req_sync),
        .tx_gbx_req_stall(tx_gbx_req_stall),
        .tx_gbx_sync(tx_gbx_sync),

        .tx_ptp_ts(tx_ptp_ts),
        .rx_ptp_ts(rx_ptp_ts),

        .cfg_tx_max_pkt_len(cfg_tx_max_pkt_len),
        .cfg_tx_ifg(cfg_tx_ifg),
        .cfg_tx_enable(cfg_tx_enable),
        .cfg_rx_max_pkt_len(cfg_rx_max_pkt_len),
        .cfg_rx_enable(cfg_rx_enable),

        // LFC/PFC
        .tx_lfc_req(tx_lfc_req),
        .tx_lfc_resend(tx_lfc_resend),
        .rx_lfc_en(rx_lfc_en),
        .rx_lfc_req(rx_lfc_req),
        .rx_lfc_ack(rx_lfc_ack),

        .tx_pfc_req(tx_pfc_req),
        .tx_pfc_resend(tx_pfc_resend),
        .rx_pfc_en(rx_pfc_en),
        .rx_pfc_req(rx_pfc_req),
        .rx_pfc_ack(rx_pfc_ack),

        // Pause
        .tx_lfc_pause_en(tx_lfc_pause_en),
        .tx_pause_req(tx_pause_req),
        .tx_pause_ack(tx_pause_ack),

        // Statistics
        .stat_clk(stat_clk),
        .stat_rst(stat_rst),
        .m_axis_stat(m_axis_stat),

        // Status outputs
        .tx_start_packet(tx_start_packet),
        .stat_tx_byte(stat_tx_byte),
        .stat_tx_pkt_len(stat_tx_pkt_len),
        .stat_tx_pkt_ucast(stat_tx_pkt_ucast),
        .stat_tx_pkt_mcast(stat_tx_pkt_mcast),
        .stat_tx_pkt_bcast(stat_tx_pkt_bcast),
        .stat_tx_pkt_vlan(stat_tx_pkt_vlan),
        .stat_tx_pkt_good(stat_tx_pkt_good),
        .stat_tx_pkt_bad(stat_tx_pkt_bad),
        .stat_tx_err_oversize(stat_tx_err_oversize),
        .stat_tx_err_user(stat_tx_err_user),
        .stat_tx_err_underflow(stat_tx_err_underflow),

        .rx_start_packet(rx_start_packet),
        .stat_rx_byte(stat_rx_byte),
        .stat_rx_pkt_len(stat_rx_pkt_len),
        .stat_rx_pkt_fragment(stat_rx_pkt_fragment),
        .stat_rx_pkt_jabber(stat_rx_pkt_jabber),
        .stat_rx_pkt_ucast(stat_rx_pkt_ucast),
        .stat_rx_pkt_mcast(stat_rx_pkt_mcast),
        .stat_rx_pkt_bcast(stat_rx_pkt_bcast),
        .stat_rx_pkt_vlan(stat_rx_pkt_vlan),
        .stat_rx_pkt_good(stat_rx_pkt_good),
        .stat_rx_pkt_bad(stat_rx_pkt_bad),
        .stat_rx_err_oversize(stat_rx_err_oversize),
        .stat_rx_err_bad_fcs(stat_rx_err_bad_fcs),
        .stat_rx_err_bad_block(stat_rx_err_bad_block),
        .stat_rx_err_framing(stat_rx_err_framing),
        .stat_rx_err_preamble(stat_rx_err_preamble),
        .stat_rx_fifo_drop(stat_rx_fifo_drop),

        .stat_tx_mcf(stat_tx_mcf),
        .stat_rx_mcf(stat_rx_mcf),
        .stat_tx_lfc_pkt(stat_tx_lfc_pkt),
        .stat_tx_lfc_xon(stat_tx_lfc_xon),
        .stat_tx_lfc_xoff(stat_tx_lfc_xoff),
        .stat_tx_lfc_paused(stat_tx_lfc_paused),
        .stat_tx_pfc_pkt(stat_tx_pfc_pkt),
        .stat_tx_pfc_xon(stat_tx_pfc_xon),
        .stat_tx_pfc_xoff(stat_tx_pfc_xoff),
        .stat_tx_pfc_paused(stat_tx_pfc_paused),
        .stat_rx_lfc_pkt(stat_rx_lfc_pkt),
        .stat_rx_lfc_xon(stat_rx_lfc_xon),
        .stat_rx_lfc_xoff(stat_rx_lfc_xoff),
        .stat_rx_lfc_paused(stat_rx_lfc_paused),
        .stat_rx_pfc_pkt(stat_rx_pfc_pkt),
        .stat_rx_pfc_xon(stat_rx_pfc_xon),
        .stat_rx_pfc_xoff(stat_rx_pfc_xoff),
        .stat_rx_pfc_paused(stat_rx_pfc_paused),

        // Config extras
        .cfg_mcf_rx_eth_dst_mcast(cfg_mcf_rx_eth_dst_mcast),
        .cfg_mcf_rx_check_eth_dst_mcast(cfg_mcf_rx_check_eth_dst_mcast),
        .cfg_mcf_rx_eth_dst_ucast(cfg_mcf_rx_eth_dst_ucast),
        .cfg_mcf_rx_check_eth_dst_ucast(cfg_mcf_rx_check_eth_dst_ucast),
        .cfg_mcf_rx_eth_src(cfg_mcf_rx_eth_src),
        .cfg_mcf_rx_check_eth_src(cfg_mcf_rx_check_eth_src),
        .cfg_mcf_rx_eth_type(cfg_mcf_rx_eth_type),
        .cfg_mcf_rx_opcode_lfc(cfg_mcf_rx_opcode_lfc),
        .cfg_mcf_rx_check_opcode_lfc(cfg_mcf_rx_check_opcode_lfc),
        .cfg_mcf_rx_opcode_pfc(cfg_mcf_rx_opcode_pfc),
        .cfg_mcf_rx_check_opcode_pfc(cfg_mcf_rx_check_opcode_pfc),
        .cfg_mcf_rx_forward(cfg_mcf_rx_forward),
        .cfg_mcf_rx_enable(cfg_mcf_rx_enable),

        .cfg_tx_lfc_eth_dst(cfg_tx_lfc_eth_dst),
        .cfg_tx_lfc_eth_src(cfg_tx_lfc_eth_src),
        .cfg_tx_lfc_eth_type(cfg_tx_lfc_eth_type),
        .cfg_tx_lfc_opcode(cfg_tx_lfc_opcode),
        .cfg_tx_lfc_en(cfg_tx_lfc_en),
        .cfg_tx_lfc_quanta(cfg_tx_lfc_quanta),
        .cfg_tx_lfc_refresh(cfg_tx_lfc_refresh),

        .cfg_tx_pfc_eth_dst(cfg_tx_pfc_eth_dst),
        .cfg_tx_pfc_eth_src(cfg_tx_pfc_eth_src),
        .cfg_tx_pfc_eth_type(cfg_tx_pfc_eth_type),
        .cfg_tx_pfc_opcode(cfg_tx_pfc_opcode),
        .cfg_tx_pfc_en(cfg_tx_pfc_en),
        .cfg_tx_pfc_quanta(cfg_tx_pfc_quanta),
        .cfg_tx_pfc_refresh(cfg_tx_pfc_refresh),

        .cfg_rx_lfc_opcode(cfg_rx_lfc_opcode),
        .cfg_rx_lfc_en(cfg_rx_lfc_en),
        .cfg_rx_pfc_opcode(cfg_rx_pfc_opcode),
        .cfg_rx_pfc_en(cfg_rx_pfc_en)
    );

endmodule
