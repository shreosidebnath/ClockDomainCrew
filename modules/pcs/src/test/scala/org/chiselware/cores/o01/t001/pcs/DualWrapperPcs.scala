package org.chiselware.cores.o01.t001.pcs

import chisel3._

class DualWrapperPcs extends Module {
  private val dataW = 64
  private val ctrlW = dataW / 8
  private val hdrW = 2

  val io = IO(new Bundle {
    val rst = Input(Bool())

    // Drive one set of TX XGMII into both DUTs
    val xgmii_txd = Input(UInt(dataW.W))
    val xgmii_txc = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())

    // Observe both RX XGMII
    val chisel_rxd = Output(UInt(dataW.W))
    val chisel_rxc = Output(UInt(ctrlW.W))
    val chisel_rx_valid = Output(Bool())

    val bb_rxd = Output(UInt(dataW.W))
    val bb_rxc = Output(UInt(ctrlW.W))
    val bb_rx_valid = Output(Bool())

    // Status compare
    val chisel_rx_block_lock = Output(Bool())
    val bb_rx_block_lock = Output(Bool())
    val chisel_rx_status = Output(Bool())
    val bb_rx_status = Output(Bool())

    // Expose TX SERDES for header/type checks
    val bb_tx_data = Output(UInt(dataW.W))
    val bb_tx_hdr = Output(UInt(hdrW.W))
    val bb_tx_dv = Output(Bool())
    val bb_tx_hv = Output(Bool())

    val ch_tx_data = Output(UInt(dataW.W))
    val ch_tx_hdr = Output(UInt(hdrW.W))
    val ch_tx_dv = Output(Bool())
    val ch_tx_hv = Output(Bool())

    // RX error/status signals
    val bb_rx_bad_block = Output(Bool())
    val bb_rx_sequence_error = Output(Bool())
    val bb_rx_error_count = Output(UInt(7.W))

    val ch_rx_bad_block = Output(Bool())
    val ch_rx_sequence_error = Output(Bool())
    val ch_rx_error_count = Output(UInt(7.W))

    // Tap: drive RX by loopback OR externally injected blocks
    val tap_enable = Input(Bool())
    val tap_serdes_rx_data = Input(UInt(dataW.W))
    val tap_serdes_rx_data_valid = Input(Bool())
    val tap_serdes_rx_hdr = Input(UInt(hdrW.W))
    val tap_serdes_rx_hdr_valid = Input(Bool())
  })

  // =========================
  // BlackBox PCS IO (snake_case)
  // =========================
  private type PcsBbIoLike = {
    val rx_clk: Clock
    val rx_rst: Bool
    val tx_clk: Clock
    val tx_rst: Bool

    val xgmii_txd: UInt
    val xgmii_txc: UInt
    val xgmii_tx_valid: Bool

    val xgmii_rxd: UInt
    val xgmii_rxc: UInt
    val xgmii_rx_valid: Bool

    val tx_gbx_req_sync: Bool
    val tx_gbx_req_stall: Bool
    val tx_gbx_sync: Bool

    val serdes_tx_data: UInt
    val serdes_tx_data_valid: Bool
    val serdes_tx_hdr: UInt
    val serdes_tx_hdr_valid: Bool
    val serdes_tx_gbx_req_sync: Bool
    val serdes_tx_gbx_req_stall: Bool
    val serdes_tx_gbx_sync: Bool

    val serdes_rx_data: UInt
    val serdes_rx_data_valid: Bool
    val serdes_rx_hdr: UInt
    val serdes_rx_hdr_valid: Bool

    val rx_error_count: UInt
    val rx_bad_block: Bool
    val rx_sequence_error: Bool
    val rx_block_lock: Bool
    val rx_status: Bool

    val cfg_tx_prbs31_enable: Bool
    val cfg_rx_prbs31_enable: Bool
  }

  private def hookupBbIo(pcsIo: PcsBbIoLike): Unit = {
    pcsIo.tx_clk := clock
    pcsIo.rx_clk := clock
    pcsIo.tx_rst := io.rst
    pcsIo.rx_rst := io.rst

    pcsIo.xgmii_txd := io.xgmii_txd
    pcsIo.xgmii_txc := io.xgmii_txc
    pcsIo.xgmii_tx_valid := io.xgmii_tx_valid

    pcsIo.cfg_tx_prbs31_enable := false.B
    pcsIo.cfg_rx_prbs31_enable := false.B

    pcsIo.tx_gbx_sync := true.B
    pcsIo.serdes_tx_gbx_req_sync := false.B
    pcsIo.serdes_tx_gbx_req_stall := false.B

    val rxData = Mux(io.tap_enable, io.tap_serdes_rx_data, pcsIo.serdes_tx_data)
    val rxDataValid = 
      Mux(io.tap_enable, io.tap_serdes_rx_data_valid, pcsIo.serdes_tx_data_valid)
    val rxHdr  = Mux(io.tap_enable, io.tap_serdes_rx_hdr, pcsIo.serdes_tx_hdr)
    val rxHdrValid = 
      Mux(io.tap_enable, io.tap_serdes_rx_hdr_valid, pcsIo.serdes_tx_hdr_valid)

    pcsIo.serdes_rx_data := rxData
    pcsIo.serdes_rx_data_valid := rxDataValid
    pcsIo.serdes_rx_hdr := rxHdr
    pcsIo.serdes_rx_hdr_valid := rxHdrValid
  }

  // =========================
  // Chisel PCS IO (camelCase)
  // =========================
  private type PcsChIoLike = {
    val rxClk: Clock
    val rxRst: Bool
    val txClk: Clock
    val txRst: Bool

    val xgmiiTxd: UInt
    val xgmiiTxc: UInt
    val xgmiiTxValid: Bool

    val xgmiiRxd: UInt
    val xgmiiRxc: UInt
    val xgmiiRxValid: Bool

    val txGbxReqSync: Bool
    val txGbxReqStall: Bool
    val txGbxSync: Bool

    val serdesTxData: UInt
    val serdesTxDataValid: Bool
    val serdesTxHdr: UInt
    val serdesTxHdrValid: Bool
    val serdesTxGbxReqSync: Bool
    val serdesTxGbxReqStall: Bool
    val serdesTxGbxSync: Bool

    val serdesRxData: UInt
    val serdesRxDataValid: Bool
    val serdesRxHdr: UInt
    val serdesRxHdrValid: Bool

    val rxErrorCount: UInt
    val rxBadBlock: Bool
    val rxSequenceError: Bool
    val rxBlockLock: Bool
    val rxStatus: Bool

    val cfgTxPrbs31Enable: Bool
    val cfgRxPrbs31Enable: Bool
  }

  private def hookupChIo(pcsIo: PcsChIoLike): Unit = {
    pcsIo.txClk := clock
    pcsIo.rxClk := clock
    pcsIo.txRst := io.rst
    pcsIo.rxRst := io.rst

    pcsIo.xgmiiTxd := io.xgmii_txd
    pcsIo.xgmiiTxc := io.xgmii_txc
    pcsIo.xgmiiTxValid := io.xgmii_tx_valid

    pcsIo.cfgTxPrbs31Enable := false.B
    pcsIo.cfgRxPrbs31Enable := false.B

    pcsIo.txGbxSync := true.B
    pcsIo.serdesTxGbxReqSync := false.B
    pcsIo.serdesTxGbxReqStall := false.B

    val rxData = Mux(io.tap_enable, io.tap_serdes_rx_data, pcsIo.serdesTxData)
    val rxDataValid = 
      Mux(io.tap_enable, io.tap_serdes_rx_data_valid, pcsIo.serdesTxDataValid)
    val rxHdr  = Mux(io.tap_enable, io.tap_serdes_rx_hdr, pcsIo.serdesTxHdr)
    val rxHdrValid = 
      Mux(io.tap_enable, io.tap_serdes_rx_hdr_valid, pcsIo.serdesTxHdrValid)

    pcsIo.serdesRxData := rxData
    pcsIo.serdesRxDataValid := rxDataValid
    pcsIo.serdesRxHdr := rxHdr
    pcsIo.serdesRxHdrValid := rxHdrValid
  }

  val bbParams = PcsBbParams(
    dataW = dataW,
    hdrW = hdrW,
    txGbxIfEn = true,
    rxGbxIfEn = true,
    bitReverse = true,
    scramblerDisable = false,
    prbs31En = false,
    txSerdesPipeline = 1,
    rxSerdesPipeline = 1,
    bitslipHighCycles = 0,
    bitslipLowCycles = 7,
    count125Us = 125000.0 / 6.4,
  )

  // BlackBox PCS
  val bb = Module(new PcsBb(bbParams))
  hookupBbIo(bb.io)

  io.bb_rxd := bb.io.xgmii_rxd
  io.bb_rxc := bb.io.xgmii_rxc
  io.bb_rx_valid := bb.io.xgmii_rx_valid
  io.bb_rx_block_lock := bb.io.rx_block_lock
  io.bb_rx_status := bb.io.rx_status

  io.bb_tx_data := bb.io.serdes_tx_data
  io.bb_tx_hdr := bb.io.serdes_tx_hdr
  io.bb_tx_dv := bb.io.serdes_tx_data_valid
  io.bb_tx_hv := bb.io.serdes_tx_hdr_valid

  io.bb_rx_bad_block := bb.io.rx_bad_block
  io.bb_rx_sequence_error := bb.io.rx_sequence_error
  io.bb_rx_error_count := bb.io.rx_error_count

  // Chisel PCS
  val ch = Module(new PcsTb(PcsParams(dataW = dataW, ctrlW = ctrlW, hdrW = hdrW)))
  hookupChIo(ch.io)

  io.chisel_rxd := ch.io.xgmiiRxd
  io.chisel_rxc := ch.io.xgmiiRxc
  io.chisel_rx_valid := ch.io.xgmiiRxValid
  io.chisel_rx_block_lock := ch.io.rxBlockLock
  io.chisel_rx_status := ch.io.rxStatus

  io.ch_tx_data := ch.io.serdesTxData
  io.ch_tx_hdr := ch.io.serdesTxHdr
  io.ch_tx_dv := ch.io.serdesTxDataValid
  io.ch_tx_hv := ch.io.serdesTxHdrValid

  io.ch_rx_bad_block := ch.io.rxBadBlock
  io.ch_rx_sequence_error := ch.io.rxSequenceError
  io.ch_rx_error_count := ch.io.rxErrorCount
}