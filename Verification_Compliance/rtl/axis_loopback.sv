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
  import "DPI-C" function void socket_init(input string ip, input int port);
  import "DPI-C" function void socket_send(input bit [63:0] data, input int len);
  import "DPI-C" function int  socket_recv(output bit [63:0] data, input int len);
  import "DPI-C" function void socket_close();

  // RTL logic below
  assign tx_axis_tready = rx_axis_tready;

  initial begin
    socket_init("127.0.0.1", 9000);
  end

  always_ff @(posedge clk156) begin
    bit [63:0] recv_data;
    int recv_len;

    if (tx_axis_tvalid && tx_axis_tready) begin
      $display("[SV] Calling socket_send with %h", tx_axis_tdata);
      socket_send(tx_axis_tdata, 8);
    end

    recv_len = socket_recv(recv_data, 8);
    if (recv_len > 0) begin
      $display("[SV] Received echo %h", recv_data);
      rx_axis_tdata  <= recv_data;
      rx_axis_tvalid <= 1;
      rx_axis_tkeep  <= '1;
      rx_axis_tlast  <= 1;
    end else begin
      rx_axis_tvalid <= 0;
    end
  end


  final begin
    socket_close();
  end

endmodule
