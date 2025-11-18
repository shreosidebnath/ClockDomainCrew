// Minimal Verilator-friendly stub for Xilinx xxv_ethernet_0
// Drives GT lock/powergood, basic clocks, and AXI-Lite handshakes so the EXDES TB can run.

module xxv_ethernet_0 (
    // GT serial I/O
    input  logic        gt_rxp_in,
    input  logic        gt_rxn_in,
    output logic        gt_txp_out,
    output logic        gt_txn_out,

    // Clocks/status that EXDES expects from the IP
    output logic        tx_clk_out_0,
    input  logic        rx_core_clk_0,   // unused, just to match port map
    output logic        rx_clk_out_0,

    // AXI-Lite clock/reset
    input  logic        s_axi_aclk_0,
    input  logic        s_axi_aresetn_0,

    // AXI-Lite write
    input  logic [17:0] s_axi_awaddr_0,
    input  logic        s_axi_awvalid_0,
    output logic        s_axi_awready_0,
    input  logic [31:0] s_axi_wdata_0,
    input  logic [3:0]  s_axi_wstrb_0,
    input  logic        s_axi_wvalid_0,
    output logic        s_axi_wready_0,
    output logic [1:0]  s_axi_bresp_0,
    output logic        s_axi_bvalid_0,
    input  logic        s_axi_bready_0,

    // AXI-Lite read
    input  logic [17:0] s_axi_araddr_0,
    input  logic        s_axi_arvalid_0,
    output logic        s_axi_arready_0,
    output logic [31:0] s_axi_rdata_0,
    output logic [1:0]  s_axi_rresp_0,
    output logic        s_axi_rvalid_0,
    input  logic        s_axi_rready_0,

    // Misc/status used by EXDES
    input  logic        pm_tick_0,
    input  logic        rx_reset_0,
    input  logic        tx_reset_0,   // <— added to match EXDES connection
    output logic        user_rx_reset_0,
    output logic        rxrecclkout_0,

    output logic [31:0] user_reg0_0,

    // RX AXI-Stream (outputs from IP to fabric)
    output logic        rx_axis_tvalid_0,
    output logic [63:0] rx_axis_tdata_0,
    output logic        rx_axis_tlast_0,
    output logic [7:0]  rx_axis_tkeep_0,
    output logic        rx_axis_tuser_0,

    // RX preamble/status
    output logic [55:0] rx_preambleout_0,

    // A few RX stats needed by pkt_gen_mon/EXDES
    output logic        stat_rx_block_lock_0,
    output logic        stat_rx_framing_err_valid_0,
    output logic        stat_rx_framing_err_0,
    output logic        stat_rx_hi_ber_0,
    output logic        stat_rx_valid_ctrl_code_0,
    output logic        stat_rx_bad_code_0,
    output logic [1:0]  stat_rx_total_packets_0,
    output logic        stat_rx_total_good_packets_0,
    output logic [3:0]  stat_rx_total_bytes_0,
    output logic [13:0] stat_rx_total_good_bytes_0,
    output logic        stat_rx_packet_small_0,
    output logic        stat_rx_jabber_0,
    output logic        stat_rx_packet_large_0,
    output logic        stat_rx_oversize_0,
    output logic        stat_rx_undersize_0,
    output logic        stat_rx_toolong_0,
    output logic        stat_rx_fragment_0,
    output logic        stat_rx_packet_64_bytes_0,
    output logic        stat_rx_packet_65_127_bytes_0,
    output logic        stat_rx_packet_128_255_bytes_0,
    output logic        stat_rx_packet_256_511_bytes_0,
    output logic        stat_rx_packet_512_1023_bytes_0,
    output logic        stat_rx_packet_1024_1518_bytes_0,
    output logic        stat_rx_packet_1519_1522_bytes_0,
    output logic        stat_rx_packet_1523_1548_bytes_0,
    // ---- Added jumbo RX packet bins to match EXDES wiring ----
    output logic        stat_rx_packet_1549_2047_bytes_0,
    output logic        stat_rx_packet_2048_4095_bytes_0,
    output logic        stat_rx_packet_4096_8191_bytes_0,
    output logic        stat_rx_packet_8192_9215_bytes_0,
    // ----------------------------------------------------------
    output logic [1:0]  stat_rx_bad_fcs_0,
    output logic        stat_rx_packet_bad_fcs_0,
    output logic [1:0]  stat_rx_stomped_fcs_0,
    output logic        stat_rx_truncated_0,
    output logic        stat_rx_local_fault_0,
    output logic        stat_rx_remote_fault_0,
    output logic        stat_rx_internal_local_fault_0,
    output logic        stat_rx_received_local_fault_0,
    output logic        stat_rx_status_0,
    output logic        stat_rx_unicast_0,
    output logic        stat_rx_multicast_0,
    output logic        stat_rx_broadcast_0,
    output logic        stat_rx_vlan_0,
    output logic        stat_rx_inrangeerr_0,
    output logic        stat_rx_bad_preamble_0,
    output logic        stat_rx_bad_sfd_0,
    output logic        stat_rx_got_signal_os_0,
    output logic        stat_rx_test_pattern_mismatch_0,

    // TX reset/AXI-Stream to IP (from fabric into IP)
    output logic        user_tx_reset_0,
    output logic        tx_axis_tready_0,   // ready from IP
    input  logic        tx_axis_tvalid_0,
    input  logic [63:0] tx_axis_tdata_0,
    input  logic        tx_axis_tlast_0,
    input  logic [7:0]  tx_axis_tkeep_0,
    input  logic        tx_axis_tuser_0,
    output logic        tx_unfout_0,
    input  logic        tx_preamblein_0,

    // TX controls/stats
    input  logic        ctl_tx_send_lfi_0,
    input  logic        ctl_tx_send_rfi_0,
    input  logic        ctl_tx_send_idle_0,

    output logic        stat_tx_total_packets_0,
    output logic [3:0]  stat_tx_total_bytes_0,
    output logic        stat_tx_total_good_packets_0,
    output logic [13:0] stat_tx_total_good_bytes_0,
    output logic        stat_tx_packet_64_bytes_0,
    output logic        stat_tx_packet_65_127_bytes_0,
    output logic        stat_tx_packet_128_255_bytes_0,
    output logic        stat_tx_packet_256_511_bytes_0,
    output logic        stat_tx_packet_512_1023_bytes_0,
    output logic        stat_tx_packet_1024_1518_bytes_0,
    output logic        stat_tx_packet_1519_1522_bytes_0,
    output logic        stat_tx_packet_1523_1548_bytes_0,
    output logic        stat_tx_packet_small_0,
    output logic        stat_tx_packet_large_0,
    output logic        stat_tx_packet_1549_2047_bytes_0,
    output logic        stat_tx_packet_2048_4095_bytes_0,
    output logic        stat_tx_packet_4096_8191_bytes_0,
    output logic        stat_tx_packet_8192_9215_bytes_0,
    output logic        stat_tx_unicast_0,
    output logic        stat_tx_multicast_0,
    output logic        stat_tx_broadcast_0,
    output logic        stat_tx_vlan_0,
    output logic        stat_tx_bad_fcs_0,
    output logic        stat_tx_frame_error_0,
    output logic        stat_tx_local_fault_0,

    // GT resets/clocking
    input  logic        gtwiz_reset_tx_datapath_0,
    input  logic        gtwiz_reset_rx_datapath_0,
    output logic        gtpowergood_out_0,
    input  logic [2:0]  txoutclksel_in_0,
    input  logic [2:0]  rxoutclksel_in_0,
    input  logic        qpllreset_in_0,

    // Reference clock and misc reset/clock
    input  logic        gt_refclk_p,
    input  logic        gt_refclk_n,
    output logic        gt_refclk_out,
    input  logic        sys_reset,
    input  logic        dclk
);

  // --- Minimal behavior to unblock the testbench ---

  // Drive serial outs low (no actual SerDes)
  assign gt_txp_out = 1'b0;
  assign gt_txn_out = 1'b0;

  // Mirror AXI clock onto "fabric" clock outs
  assign rx_clk_out_0 = s_axi_aclk_0;
  assign tx_clk_out_0 = s_axi_aclk_0;
  assign rxrecclkout_0 = s_axi_aclk_0;

  // Bring GT up immediately
  assign gtpowergood_out_0    = 1'b1;
  assign stat_rx_block_lock_0 = 1'b1;

  // Keep user resets deasserted
  assign user_rx_reset_0 = 1'b0;
  assign user_tx_reset_0 = 1'b0;

  // AXI-Lite: always-ready, simple combinational completion
  assign s_axi_awready_0 = 1'b1;
  assign s_axi_wready_0  = 1'b1;
  assign s_axi_bresp_0   = 2'b00;
  assign s_axi_bvalid_0  = s_axi_awvalid_0 & s_axi_wvalid_0;

  assign s_axi_arready_0 = 1'b1;
  assign s_axi_rresp_0   = 2'b00;
  // Return a fixed "version" or ok value for any read
  assign s_axi_rvalid_0  = s_axi_arvalid_0;
  assign s_axi_rdata_0   = 32'h0001_0000;

  // TX AXI-Stream handshake: always ready, never underflow
  assign tx_axis_tready_0 = 1'b1;
  assign tx_unfout_0      = 1'b0;

  // RX AXI-Stream: hold idle
  assign rx_axis_tvalid_0 = 1'b0;
  assign rx_axis_tdata_0  = 64'h0;
  assign rx_axis_tlast_0  = 1'b0;
  assign rx_axis_tkeep_0  = 8'h00;
  assign rx_axis_tuser_0  = 1'b0;
  assign rx_preambleout_0 = 56'h0;

  // Tie off all stats to benign values
  assign stat_rx_framing_err_valid_0    = 1'b0;
  assign stat_rx_framing_err_0          = 1'b0;
  assign stat_rx_hi_ber_0               = 1'b0;
  assign stat_rx_valid_ctrl_code_0      = 1'b0;
  assign stat_rx_bad_code_0             = 1'b0;
  assign stat_rx_total_packets_0        = 2'b00;
  assign stat_rx_total_good_packets_0   = 1'b0;
  assign stat_rx_total_bytes_0          = 4'h0;
  assign stat_rx_total_good_bytes_0     = 14'h0;
  assign stat_rx_packet_small_0         = 1'b0;
  assign stat_rx_jabber_0               = 1'b0;
  assign stat_rx_packet_large_0         = 1'b0;
  assign stat_rx_oversize_0             = 1'b0;
  assign stat_rx_undersize_0            = 1'b0;
  assign stat_rx_toolong_0              = 1'b0;
  assign stat_rx_fragment_0             = 1'b0;
  assign stat_rx_packet_64_bytes_0      = 1'b0;
  assign stat_rx_packet_65_127_bytes_0  = 1'b0;
  assign stat_rx_packet_128_255_bytes_0 = 1'b0;
  assign stat_rx_packet_256_511_bytes_0 = 1'b0;
  assign stat_rx_packet_512_1023_bytes_0= 1'b0;
  assign stat_rx_packet_1024_1518_bytes_0 = 1'b0;
  assign stat_rx_packet_1519_1522_bytes_0  = 1'b0;
  assign stat_rx_packet_1523_1548_bytes_0  = 1'b0;
  // Added jumbo bins -> tie low
  assign stat_rx_packet_1549_2047_bytes_0  = 1'b0;
  assign stat_rx_packet_2048_4095_bytes_0  = 1'b0;
  assign stat_rx_packet_4096_8191_bytes_0  = 1'b0;
  assign stat_rx_packet_8192_9215_bytes_0  = 1'b0;

  assign stat_rx_bad_fcs_0               = 2'b00;
  assign stat_rx_packet_bad_fcs_0        = 1'b0;
  assign stat_rx_stomped_fcs_0           = 2'b00;
  assign stat_rx_truncated_0             = 1'b0;
  assign stat_rx_local_fault_0           = 1'b0;
  assign stat_rx_remote_fault_0          = 1'b0;
  assign stat_rx_internal_local_fault_0  = 1'b0;
  assign stat_rx_received_local_fault_0  = 1'b0;
  assign stat_rx_status_0                = 1'b1; // "OK" status
  assign stat_rx_unicast_0               = 1'b0;
  assign stat_rx_multicast_0             = 1'b0;
  assign stat_rx_broadcast_0             = 1'b0;
  assign stat_rx_vlan_0                  = 1'b0;
  assign stat_rx_inrangeerr_0            = 1'b0;
  assign stat_rx_bad_preamble_0          = 1'b0;
  assign stat_rx_bad_sfd_0               = 1'b0;
  assign stat_rx_got_signal_os_0         = 1'b0;
  assign stat_rx_test_pattern_mismatch_0 = 1'b0;

  assign stat_tx_total_packets_0         = 1'b0;
  assign stat_tx_total_bytes_0           = 4'h0;
  assign stat_tx_total_good_packets_0    = 1'b0;
  assign stat_tx_total_good_bytes_0      = 14'h0;
  assign stat_tx_packet_64_bytes_0       = 1'b0;
  assign stat_tx_packet_65_127_bytes_0   = 1'b0;
  assign stat_tx_packet_128_255_bytes_0  = 1'b0;
  assign stat_tx_packet_256_511_bytes_0  = 1'b0;
  assign stat_tx_packet_512_1023_bytes_0 = 1'b0;
  assign stat_tx_packet_1024_1518_bytes_0= 1'b0;
  assign stat_tx_packet_1519_1522_bytes_0= 1'b0;
  assign stat_tx_packet_1523_1548_bytes_0= 1'b0;
  assign stat_tx_packet_small_0          = 1'b0;
  assign stat_tx_packet_large_0          = 1'b0;
  assign stat_tx_packet_1549_2047_bytes_0= 1'b0;
  assign stat_tx_packet_2048_4095_bytes_0= 1'b0;
  assign stat_tx_packet_4096_8191_bytes_0= 1'b0;
  assign stat_tx_packet_8192_9215_bytes_0= 1'b0;
  assign stat_tx_unicast_0               = 1'b0;
  assign stat_tx_multicast_0             = 1'b0;
  assign stat_tx_broadcast_0             = 1'b0;
  assign stat_tx_vlan_0                  = 1'b0;
  assign stat_tx_bad_fcs_0               = 1'b0;
  assign stat_tx_frame_error_0           = 1'b0;
  assign stat_tx_local_fault_0           = 1'b0;

  // Simple constant refclk out; no PLL modeled here
  assign gt_refclk_out = 1'b0;

  // User register: tie to a recognizable constant
  assign user_reg0_0 = 32'h0000_0001;

endmodule
