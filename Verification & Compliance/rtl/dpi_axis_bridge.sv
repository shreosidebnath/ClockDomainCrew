`timescale 1ns/1ps
module dpi_axis_bridge #(
  parameter int DATA_W = 64,
  parameter int KEEP_W = DATA_W/8
)(
  input  logic                 clk,
  input  logic                 resetn,
  // Python -> DUT (TX into DUT)
  output logic [DATA_W-1:0]    tx_tdata,
  output logic [KEEP_W-1:0]    tx_tkeep,
  output logic                 tx_tvalid,
  input  logic                 tx_tready,
  output logic                 tx_tlast,
  // DUT -> Python (RX out of DUT)
  input  logic [DATA_W-1:0]    rx_tdata,
  input  logic [KEEP_W-1:0]    rx_tkeep,
  input  logic                 rx_tvalid,
  output logic                 rx_tready,
  input  logic                 rx_tlast
);

  // -------- DPI-C declarations --------
  import "DPI-C" function int dpi_sock_init(input int listen_port);
  import "DPI-C" function int dpi_sock_poll_rx(output byte data[], input int maxlen);
  import "DPI-C" function int dpi_sock_send_tx(input byte data[], input int len);

  initial begin
    int rc;
    rc = dpi_sock_init(9090); // listen on 127.0.0.1:9090
  end

  // -------- DUT -> Python (collect a frame and send once at tlast) --------
  byte rx_bytes[$];
  assign rx_tready = 1'b1;  // simple always-ready bridge

  always_ff @(posedge clk) begin
    if (!resetn) begin
      rx_bytes.delete();
    end else if (rx_tvalid && rx_tready) begin
      int used = $countones(rx_tkeep);
      byte b [0:KEEP_W-1];
      // Adjust packing if your DUT uses different byte/lane order
      {b[7],b[6],b[5],b[4],b[3],b[2],b[1],b[0]} = rx_tdata;
      for (int i=0;i<used;i++) rx_bytes.push_back(b[i]);
      if (rx_tlast) begin
        if (rx_bytes.size() > 0) begin
          void'(dpi_sock_send_tx(rx_bytes, rx_bytes.size()));
          rx_bytes.delete();
        end
      end
    end
  end

  // -------- Python -> DUT (pull bytes from socket and stream as AXIS) -----
  typedef enum logic [1:0] {IDLE, SEND} tx_state_e;
  tx_state_e state;
  byte txq[$];

  always_ff @(posedge clk) begin
    if (!resetn) begin
      state       <= IDLE;
      tx_tvalid   <= 1'b0;
      tx_tlast    <= 1'b0;
      tx_tkeep    <= '0;
      tx_tdata    <= '0;
    end else begin
      // nonblocking poll for new bytes (each recv() is one “chunk” from Python)
      byte buf [0:2047];
      int got = dpi_sock_poll_rx(buf, buf.size());
      if (got > 0) begin
        for (int i=0;i<got;i++) txq.push_back(buf[i]);
      end

      case (state)
        IDLE: begin
          if (txq.size() > 0) begin
            byte beat[0:KEEP_W-1];
            int used = (txq.size() < KEEP_W) ? txq.size() : KEEP_W;
            for (int i=0;i<used;i++) beat[i] = txq.pop_front();
            tx_tdata  <= {beat[7],beat[6],beat[5],beat[4],beat[3],beat[2],beat[1],beat[0]};
            tx_tkeep  <= (1 << used) - 1;
            tx_tlast  <= (txq.size() == 0);  // one recv() == one frame
            tx_tvalid <= 1'b1;
            state     <= SEND;
          end
        end
        SEND: begin
          if (tx_tvalid && tx_tready) begin
            tx_tvalid <= 1'b0;
            tx_tlast  <= 1'b0;
            tx_tkeep  <= '0;
            tx_tdata  <= '0;
            state     <= IDLE;
          end
        end
      endcase
    end
  end

endmodule
