module DualWrapperNfMac10g(
  input         clock,
  input         reset,
  input  [63:0] io_tx_axis_tdata, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  input         io_tx_axis_tvalid, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  input  [7:0]  io_tx_axis_tkeep, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  input         io_tx_axis_tlast, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  input         io_tx_axis_tuser, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  output [63:0] io_rx_axis_tdata_chisel, // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
  output [63:0] io_rx_axis_tdata_verilog // @[src/main/scala/dualwrappernfmac10g.scala 5:14]
);
  wire  origDut_tx_clk0; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_clk0; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_reset; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_dcm_locked; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_dcm_locked; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [7:0] origDut_tx_ifg_delay; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [15:0] origDut_pause_val; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_pause_req; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [79:0] origDut_tx_configuration_vector; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [79:0] origDut_rx_configuration_vector; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [1:0] origDut_status_vector; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_axis_aresetn; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [63:0] origDut_tx_axis_tdata; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [7:0] origDut_tx_axis_tkeep; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_axis_tvalid; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_axis_tready; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_axis_tlast; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_tx_axis_tuser; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_axis_aresetn; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [63:0] origDut_rx_axis_tdata; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire [7:0] origDut_rx_axis_tkeep; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_axis_tvalid; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_axis_tlast; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  wire  origDut_rx_axis_tuser; // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
  NfMac10gBlackBox origDut ( // @[src/main/scala/dualwrappernfmac10g.scala 18:25]
    .tx_clk0(origDut_tx_clk0),
    .rx_clk0(origDut_rx_clk0),
    .reset(origDut_reset),
    .tx_dcm_locked(origDut_tx_dcm_locked),
    .rx_dcm_locked(origDut_rx_dcm_locked),
    .tx_ifg_delay(origDut_tx_ifg_delay),
    .pause_val(origDut_pause_val),
    .pause_req(origDut_pause_req),
    .tx_configuration_vector(origDut_tx_configuration_vector),
    .rx_configuration_vector(origDut_rx_configuration_vector),
    .status_vector(origDut_status_vector),
    .tx_axis_aresetn(origDut_tx_axis_aresetn),
    .tx_axis_tdata(origDut_tx_axis_tdata),
    .tx_axis_tkeep(origDut_tx_axis_tkeep),
    .tx_axis_tvalid(origDut_tx_axis_tvalid),
    .tx_axis_tready(origDut_tx_axis_tready),
    .tx_axis_tlast(origDut_tx_axis_tlast),
    .tx_axis_tuser(origDut_tx_axis_tuser),
    .rx_axis_aresetn(origDut_rx_axis_aresetn),
    .rx_axis_tdata(origDut_rx_axis_tdata),
    .rx_axis_tkeep(origDut_rx_axis_tkeep),
    .rx_axis_tvalid(origDut_rx_axis_tvalid),
    .rx_axis_tlast(origDut_rx_axis_tlast),
    .rx_axis_tuser(origDut_rx_axis_tuser)
  );
  assign io_rx_axis_tdata_chisel = 64'h0; // @[src/main/scala/dualwrappernfmac10g.scala 38:28]
  assign io_rx_axis_tdata_verilog = origDut_rx_axis_tdata; // @[src/main/scala/dualwrappernfmac10g.scala 39:28]
  assign origDut_tx_clk0 = 1'h0;
  assign origDut_rx_clk0 = 1'h0;
  assign origDut_reset = 1'h0;
  assign origDut_tx_dcm_locked = 1'h0;
  assign origDut_rx_dcm_locked = 1'h0;
  assign origDut_tx_ifg_delay = 8'h0;
  assign origDut_pause_val = 16'h0;
  assign origDut_pause_req = 1'h0;
  assign origDut_tx_configuration_vector = 80'h0;
  assign origDut_rx_configuration_vector = 80'h0;
  assign origDut_tx_axis_aresetn = 1'h0;
  assign origDut_tx_axis_tdata = io_tx_axis_tdata; // @[src/main/scala/dualwrappernfmac10g.scala 31:29]
  assign origDut_tx_axis_tkeep = io_tx_axis_tkeep; // @[src/main/scala/dualwrappernfmac10g.scala 33:29]
  assign origDut_tx_axis_tvalid = io_tx_axis_tvalid; // @[src/main/scala/dualwrappernfmac10g.scala 32:29]
  assign origDut_tx_axis_tlast = io_tx_axis_tlast; // @[src/main/scala/dualwrappernfmac10g.scala 34:29]
  assign origDut_tx_axis_tuser = io_tx_axis_tuser; // @[src/main/scala/dualwrappernfmac10g.scala 35:29]
  assign origDut_rx_axis_aresetn = 1'h0;
endmodule
