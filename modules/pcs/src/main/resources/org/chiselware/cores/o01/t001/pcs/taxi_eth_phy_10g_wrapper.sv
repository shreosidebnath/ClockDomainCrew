`resetall
`timescale 1ns / 1ps
`default_nettype none

module taxi_eth_phy_10g_wrapper #(
    parameter int DATA_W = 64,
    parameter int CTRL_W = (DATA_W/8),
    parameter int HDR_W  = 2,
    parameter bit TX_GBX_IF_EN = 1'b1,
    parameter bit RX_GBX_IF_EN = 1'b1,
    parameter bit BIT_REVERSE = 1'b1,
    parameter bit SCRAMBLER_DISABLE = 1'b0,
    parameter bit PRBS31_EN = 1'b0,
    parameter int TX_SERDES_PIPELINE = 1,
    parameter int RX_SERDES_PIPELINE = 1,
    parameter int BITSLIP_HIGH_CYCLES = 0,
    parameter int BITSLIP_LOW_CYCLES = 7,
    
    // keep EXACT default expression
    parameter real COUNT_125US = 125000/6.4
)(
    input  wire logic               rx_clk,
    input  wire logic               rx_rst,
    input  wire logic               tx_clk,
    input  wire logic               tx_rst,

    // XGMII
    input  wire logic [DATA_W-1:0]  xgmii_txd,
    input  wire logic [CTRL_W-1:0]  xgmii_txc,
    input  wire logic               xgmii_tx_valid,
    output wire logic [DATA_W-1:0]  xgmii_rxd,
    output wire logic [CTRL_W-1:0]  xgmii_rxc,
    output wire logic               xgmii_rx_valid,

    output wire logic               tx_gbx_req_sync,
    output wire logic               tx_gbx_req_stall,
    input  wire logic               tx_gbx_sync,

    // SERDES
    output wire logic [DATA_W-1:0]  serdes_tx_data,
    output wire logic               serdes_tx_data_valid,
    output wire logic [HDR_W-1:0]   serdes_tx_hdr,
    output wire logic               serdes_tx_hdr_valid,
    input  wire logic               serdes_tx_gbx_req_sync,
    input  wire logic               serdes_tx_gbx_req_stall,
    output wire logic               serdes_tx_gbx_sync,

    input  wire logic [DATA_W-1:0]  serdes_rx_data,
    input  wire logic               serdes_rx_data_valid,
    input  wire logic [HDR_W-1:0]   serdes_rx_hdr,
    input  wire logic               serdes_rx_hdr_valid,
    output wire logic               serdes_rx_bitslip,
    output wire logic               serdes_rx_reset_req,

    // Status
    output wire logic               tx_bad_block,
    output wire logic [6:0]         rx_error_count,
    output wire logic               rx_bad_block,
    output wire logic               rx_sequence_error,
    output wire logic               rx_block_lock,
    output wire logic               rx_high_ber,
    output wire logic               rx_status,

    // Config
    input  wire logic               cfg_tx_prbs31_enable,
    input  wire logic               cfg_rx_prbs31_enable
);

    // IMPORTANT: Set params to match Chisel exactly
     taxi_eth_phy_10g #(
        .DATA_W(DATA_W),
        .CTRL_W(CTRL_W),
        .HDR_W(HDR_W),
        .TX_GBX_IF_EN(TX_GBX_IF_EN),
        .RX_GBX_IF_EN(RX_GBX_IF_EN),
        .BIT_REVERSE(BIT_REVERSE),
        .SCRAMBLER_DISABLE(SCRAMBLER_DISABLE),
        .PRBS31_EN(PRBS31_EN),
        .TX_SERDES_PIPELINE(TX_SERDES_PIPELINE),
        .RX_SERDES_PIPELINE(RX_SERDES_PIPELINE),
        .BITSLIP_HIGH_CYCLES(BITSLIP_HIGH_CYCLES),
        .BITSLIP_LOW_CYCLES(BITSLIP_LOW_CYCLES),
        .COUNT_125US(COUNT_125US)
  ) dut (
        .rx_clk(rx_clk),
        .rx_rst(rx_rst),
        .tx_clk(tx_clk),
        .tx_rst(tx_rst),

        .xgmii_txd(xgmii_txd),
        .xgmii_txc(xgmii_txc),
        .xgmii_tx_valid(xgmii_tx_valid),
        .xgmii_rxd(xgmii_rxd),
        .xgmii_rxc(xgmii_rxc),
        .xgmii_rx_valid(xgmii_rx_valid),

        .tx_gbx_req_sync(tx_gbx_req_sync),
        .tx_gbx_req_stall(tx_gbx_req_stall),
        .tx_gbx_sync(tx_gbx_sync),

        .serdes_tx_data(serdes_tx_data),
        .serdes_tx_data_valid(serdes_tx_data_valid),
        .serdes_tx_hdr(serdes_tx_hdr),
        .serdes_tx_hdr_valid(serdes_tx_hdr_valid),
        .serdes_tx_gbx_req_sync(serdes_tx_gbx_req_sync),
        .serdes_tx_gbx_req_stall(serdes_tx_gbx_req_stall),
        .serdes_tx_gbx_sync(serdes_tx_gbx_sync),

        .serdes_rx_data(serdes_rx_data),
        .serdes_rx_data_valid(serdes_rx_data_valid),
        .serdes_rx_hdr(serdes_rx_hdr),
        .serdes_rx_hdr_valid(serdes_rx_hdr_valid),
        .serdes_rx_bitslip(serdes_rx_bitslip),
        .serdes_rx_reset_req(serdes_rx_reset_req),

        .tx_bad_block(tx_bad_block),
        .rx_error_count(rx_error_count),
        .rx_bad_block(rx_bad_block),
        .rx_sequence_error(rx_sequence_error),
        .rx_block_lock(rx_block_lock),
        .rx_high_ber(rx_high_ber),
        .rx_status(rx_status),

        .cfg_tx_prbs31_enable(cfg_tx_prbs31_enable),
        .cfg_rx_prbs31_enable(cfg_rx_prbs31_enable)
    );

endmodule

`resetall