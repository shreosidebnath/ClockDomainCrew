module axis_loopback #(
  parameter DATA_W = 64,
  parameter KEEP_W = DATA_W/8
)(
  input  logic                 clk156,
  input  logic                 resetn,
  // TX input
  input  logic [DATA_W-1:0]    tx_axis_tdata,
  input  logic [KEEP_W-1:0]    tx_axis_tkeep,
  input  logic                 tx_axis_tvalid,
  output logic                 tx_axis_tready,
  input  logic                 tx_axis_tlast,
  // RX output
  output logic [DATA_W-1:0]    rx_axis_tdata,
  output logic [KEEP_W-1:0]    rx_axis_tkeep,
  output logic                 rx_axis_tvalid,
  input  logic                 rx_axis_tready,
  output logic                 rx_axis_tlast
);
  assign tx_axis_tready = rx_axis_tready; // simple backpressure
  always_ff @(posedge clk156 or negedge resetn) begin
    if (!resetn) begin
      rx_axis_tdata  <= '0;
      rx_axis_tkeep  <= '0;
      rx_axis_tvalid <= 1'b0;
      rx_axis_tlast  <= 1'b0;
    end else begin
      rx_axis_tdata  <= tx_axis_tdata;
      rx_axis_tkeep  <= tx_axis_tkeep;
      rx_axis_tvalid <= tx_axis_tvalid;
      rx_axis_tlast  <= tx_axis_tlast;
    end
  end
endmodule
