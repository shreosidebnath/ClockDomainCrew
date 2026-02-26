package org.chiselware.cores.o01.t001.pcs

import chisel3._

class DualWrapperPcs extends Module {
  val dataW = 64
  val ctrlW = dataW / 8
  val hdrW  = 2

  val io = IO(new Bundle {
    val rst = Input(Bool())

    // Drive one set of TX XGMII into both DUTs
    val xgmii_txd      = Input(UInt(dataW.W))
    val xgmii_txc      = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())

    // Observe both RX XGMII
    val chisel_rxd      = Output(UInt(dataW.W))
    val chisel_rxc      = Output(UInt(ctrlW.W))
    val chisel_rx_valid = Output(Bool())

    val bb_rxd      = Output(UInt(dataW.W))
    val bb_rxc      = Output(UInt(ctrlW.W))
    val bb_rx_valid = Output(Bool())

    // Status compare
    val chisel_rx_block_lock = Output(Bool())
    val bb_rx_block_lock     = Output(Bool())
    val chisel_rx_status     = Output(Bool())
    val bb_rx_status         = Output(Bool())

    // Expose TX SERDES for header/type checks
    val bb_tx_data = Output(UInt(dataW.W))
    val bb_tx_hdr  = Output(UInt(hdrW.W))
    val bb_tx_dv   = Output(Bool())
    val bb_tx_hv   = Output(Bool())

    val ch_tx_data = Output(UInt(dataW.W))
    val ch_tx_hdr  = Output(UInt(hdrW.W))
    val ch_tx_dv   = Output(Bool())
    val ch_tx_hv   = Output(Bool())

    // (Optional but recommended) expose RX error flags for negative tests
    val bb_rx_bad_block      = Output(Bool())
    val bb_rx_sequence_error = Output(Bool())
    val bb_rx_error_count    = Output(UInt(7.W))

    val ch_rx_bad_block      = Output(Bool())
    val ch_rx_sequence_error = Output(Bool())
    val ch_rx_error_count    = Output(UInt(7.W))

    // Tap: drive RX by loopback OR externally injected blocks
    val tap_enable = Input(Bool())
    val tap_serdes_rx_data       = Input(UInt(dataW.W))
    val tap_serdes_rx_data_valid = Input(Bool())
    val tap_serdes_rx_hdr        = Input(UInt(hdrW.W))
    val tap_serdes_rx_hdr_valid  = Input(Bool())
  })

  private def hookupPcs(pcs: PcsBb): Unit = {
    pcs.io.tx_clk := clock
    pcs.io.rx_clk := clock
    pcs.io.tx_rst := io.rst
    pcs.io.rx_rst := io.rst

    pcs.io.xgmii_txd      := io.xgmii_txd
    pcs.io.xgmii_txc      := io.xgmii_txc
    pcs.io.xgmii_tx_valid := io.xgmii_tx_valid

    // config
    pcs.io.cfg_tx_prbs31_enable := false.B
    pcs.io.cfg_rx_prbs31_enable := false.B

    // GBX controls (not using gearbox handshake in this first test)
    pcs.io.tx_gbx_sync := true.B
    pcs.io.serdes_tx_gbx_req_sync  := false.B
    pcs.io.serdes_tx_gbx_req_stall := false.B

    // Tap mux: default is internal loopback (TX -> RX)
    val rxData = Mux(io.tap_enable, io.tap_serdes_rx_data, pcs.io.serdes_tx_data)
    val rxDVal = Mux(io.tap_enable, io.tap_serdes_rx_data_valid, pcs.io.serdes_tx_data_valid)
    val rxHdr  = Mux(io.tap_enable, io.tap_serdes_rx_hdr, pcs.io.serdes_tx_hdr)
    val rxHVal = Mux(io.tap_enable, io.tap_serdes_rx_hdr_valid, pcs.io.serdes_tx_hdr_valid)

    pcs.io.serdes_rx_data       := rxData
    pcs.io.serdes_rx_data_valid := rxDVal
    pcs.io.serdes_rx_hdr        := rxHdr
    pcs.io.serdes_rx_hdr_valid  := rxHVal
  }

  val bbParams = PcsBbParams(
    dataW = dataW,
    hdrW  = hdrW,
    txGbxIfEn = true,
    rxGbxIfEn = true,
    bitReverse = true,
    scramblerDisable = false,
    prbs31En = false,
    txSerdesPipeline = 1,
    rxSerdesPipeline = 1,
    bitslipHighCycles = 0,
    bitslipLowCycles = 7,
    count125Us = 125000.0/6.4
  )

  // === BlackBox PCS ===
  val bb = Module(new PcsBb(bbParams))
  hookupPcs(bb)

  io.bb_rxd           := bb.io.xgmii_rxd
  io.bb_rxc           := bb.io.xgmii_rxc
  io.bb_rx_valid      := bb.io.xgmii_rx_valid
  io.bb_rx_block_lock := bb.io.rx_block_lock
  io.bb_rx_status     := bb.io.rx_status

  io.bb_tx_data := bb.io.serdes_tx_data
  io.bb_tx_hdr  := bb.io.serdes_tx_hdr
  io.bb_tx_dv   := bb.io.serdes_tx_data_valid
  io.bb_tx_hv   := bb.io.serdes_tx_hdr_valid

  io.bb_rx_bad_block      := bb.io.rx_bad_block
  io.bb_rx_sequence_error := bb.io.rx_sequence_error
  io.bb_rx_error_count    := bb.io.rx_error_count

  // === "Chisel PCS" side (golden model for now) ===
  val ch = Module(new PcsBb(bbParams))
  hookupPcs(ch)

  io.chisel_rxd           := ch.io.xgmii_rxd
  io.chisel_rxc           := ch.io.xgmii_rxc
  io.chisel_rx_valid      := ch.io.xgmii_rx_valid
  io.chisel_rx_block_lock := ch.io.rx_block_lock
  io.chisel_rx_status     := ch.io.rx_status

  io.ch_tx_data := ch.io.serdes_tx_data
  io.ch_tx_hdr  := ch.io.serdes_tx_hdr
  io.ch_tx_dv   := ch.io.serdes_tx_data_valid
  io.ch_tx_hv   := ch.io.serdes_tx_hdr_valid

  io.ch_rx_bad_block      := ch.io.rx_bad_block
  io.ch_rx_sequence_error := ch.io.rx_sequence_error
  io.ch_rx_error_count    := ch.io.rx_error_count
}