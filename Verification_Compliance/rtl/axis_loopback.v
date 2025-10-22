module axis_loopback #
(
  parameter DATA_W = 64,
  parameter KEEP_W = DATA_W/8
)
(
  input              clk156,
  input              resetn,
  input  [DATA_W-1:0] tx_axis_tdata,
  input  [KEEP_W-1:0] tx_axis_tkeep,
  input               tx_axis_tvalid,
  output              tx_axis_tready,
  input               tx_axis_tlast,
  output reg [DATA_W-1:0] rx_axis_tdata,
  output reg [KEEP_W-1:0] rx_axis_tkeep,
  output reg              rx_axis_tvalid,
  input                   rx_axis_tready,
  output reg              rx_axis_tlast
);

  assign tx_axis_tready = rx_axis_tready;

  always @(posedge clk156 or negedge resetn) begin
    if (!resetn) begin
      rx_axis_tdata  <= 0;
      rx_axis_tkeep  <= 0;
      rx_axis_tvalid <= 0;
      rx_axis_tlast  <= 0;
    end else begin
      rx_axis_tdata  <= tx_axis_tdata;
      rx_axis_tkeep  <= tx_axis_tkeep;
      rx_axis_tvalid <= tx_axis_tvalid;
      rx_axis_tlast  <= tx_axis_tlast;
    end
  end
endmodule
