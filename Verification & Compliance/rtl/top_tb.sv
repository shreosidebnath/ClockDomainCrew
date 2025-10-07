`timescale 1ns/1ps
module top_tb;
  logic clk=0, resetn=0;
  always #3.2 clk = ~clk; // ~156.25 MHz

  // AXIS wires
  logic [63:0] tx_tdata, rx_tdata;
  logic [7:0]  tx_tkeep, rx_tkeep;
  logic        tx_tvalid, tx_tready, tx_tlast;
  logic        rx_tvalid, rx_tready, rx_tlast;

  // DUT under test (replace with your MAC later)
  // For bring-up: simple axis loopback
  assign rx_tdata  = tx_tdata;
  assign rx_tkeep  = tx_tkeep;
  assign rx_tvalid = tx_tvalid & tx_tready;
  assign rx_tlast  = tx_tlast;

  // Bridge
  dpi_axis_bridge u_br (
    .clk(clk), .resetn(resetn),
    .tx_tdata(tx_tdata), .tx_tkeep(tx_tkeep),
    .tx_tvalid(tx_tvalid), .tx_tready(tx_tready),
    .tx_tlast(tx_tlast),
    .rx_tdata(rx_tdata), .rx_tkeep(rx_tkeep),
    .rx_tvalid(rx_tvalid), .rx_tready(rx_tready),
    .rx_tlast(rx_tlast)
  );

  initial begin
    repeat(10) @(posedge clk);
    resetn = 1;
  end
endmodule
