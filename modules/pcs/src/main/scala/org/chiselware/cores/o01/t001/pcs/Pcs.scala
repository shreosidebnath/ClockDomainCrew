package org.chiselware.cores.o01.t001.pcs
import org.chiselware.cores.o01.t001.pcs.rx.PcsRx
import org.chiselware.cores.o01.t001.pcs.tx.PcsTx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class Pcs(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val hdrW: Int = 2,
  val txGbxIfEn: Boolean = false,
  val rxGbxIfEn: Boolean = false,
  val bitReverse: Boolean = false,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val txSerdesPipeline: Int = 0,
  val rxSerdesPipeline: Int = 0,
  val bitslipHighCycles: Int = 1,
  val bitslipLowCycles: Int = 7,
  val count125Us: Double = 125000.0/6.4
) extends RawModule {
  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())


    val xgmii_txd = Input(UInt(dataW.W))
    val xgmii_txc = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val xgmii_rxd = Output(UInt(dataW.W))
    val xgmii_rxc = Output(UInt(ctrlW.W))
    val xgmii_rx_valid = Output(Bool())
    val tx_gbx_req_sync = Output(Bool())
    val tx_gbx_req_stall = Output(Bool())
    val tx_gbx_sync = Input(Bool())

    /*
     * SERDES interface
     */
    val serdes_tx_data = Output(UInt(dataW.W))
    val serdes_tx_data_valid = Output(Bool())
    val serdes_tx_hdr = Output(UInt(hdrW.W))
    val serdes_tx_hdr_valid = Output(Bool())
    val serdes_tx_gbx_req_sync = Input(Bool())
    val serdes_tx_gbx_req_stall = Input(Bool())
    val serdes_tx_gbx_sync = Output(Bool())
    val serdes_rx_data = Input(UInt(dataW.W))
    val serdes_rx_data_valid = Input(Bool())
    val serdes_rx_hdr = Input(UInt(hdrW.W))
    val serdes_rx_hdr_valid = Input(Bool())
    val serdes_rx_bitslip = Output(Bool())
    val serdes_rx_reset_req = Output(Bool())

    /*
     * Status
     */
    val tx_bad_block = Output(Bool())
    val rx_error_count = Output(UInt(7.W))
    val rx_bad_block = Output(Bool())
    val rx_sequence_error = Output(Bool())
    val rx_block_lock = Output(Bool())
    val rx_high_ber = Output(Bool())
    val rx_status = Output(Bool())

    /*
    * Configuration
    */
    val cfg_tx_prbs31_enable = Input(Bool())
    val cfg_rx_prbs31_enable = Input(Bool())

  })

  // -------------------------------------------------------------------------
  // 1. RX Path Instantiation
  // -------------------------------------------------------------------------
  // Using withClockAndReset to apply the RX clock domain to the standard Module PcsRx
  withClockAndReset(io.rx_clk, io.rx_rst) {
    val rx = Module(new PcsRx(
      dataW = dataW,
      ctrlW = ctrlW,
      hdrW = hdrW,
      gbxIfEn = rxGbxIfEn,            // Specific RX parameter
      bitReverse = bitReverse,
      scramblerDisable = scramblerDisable,
      prbs31En = prbs31En,
      serdesPipeline = rxSerdesPipeline, // Specific RX parameter
      bitslipHighCycles = bitslipHighCycles,
      bitslipLowCycles = bitslipLowCycles,
      count125Us = count125Us
    ))

    // XGMII Output
    io.xgmii_rxd      := rx.io.xgmii_rxd
    io.xgmii_rxc      := rx.io.xgmii_rxc
    io.xgmii_rx_valid := rx.io.xgmii_rx_valid

    // SERDES Input
    rx.io.serdes_rx_data       := io.serdes_rx_data
    rx.io.serdes_rx_data_valid := io.serdes_rx_data_valid
    rx.io.serdes_rx_hdr        := io.serdes_rx_hdr
    rx.io.serdes_rx_hdr_valid  := io.serdes_rx_hdr_valid

    // SERDES Control Output
    io.serdes_rx_bitslip   := rx.io.serdes_rx_bitslip
    io.serdes_rx_reset_req := rx.io.serdes_rx_reset_req

    // Status
    io.rx_error_count    := rx.io.rx_error_count
    io.rx_bad_block      := rx.io.rx_bad_block
    io.rx_sequence_error := rx.io.rx_sequence_error
    io.rx_block_lock     := rx.io.rx_block_lock
    io.rx_high_ber       := rx.io.rx_high_ber
    io.rx_status         := rx.io.rx_status

    // Configuration
    rx.io.cfg_rx_prbs31_enable := io.cfg_rx_prbs31_enable
  }

  // -------------------------------------------------------------------------
  // 2. TX Path Instantiation
  // -------------------------------------------------------------------------
  // Using withClockAndReset to apply the TX clock domain to the standard Module PcsTx
  withClockAndReset(io.tx_clk, io.tx_rst) {
    val tx = Module(new PcsTx(
      dataW = dataW,
      ctrlW = ctrlW,
      hdrW = hdrW,
      gbxIfEn = txGbxIfEn,            // Specific TX parameter
      bitReverse = bitReverse,
      scramblerDisable = scramblerDisable,
      prbs31En = prbs31En,
      serdesPipeline = txSerdesPipeline // Specific TX parameter
    ))

    // XGMII Input
    tx.io.xgmii_txd      := io.xgmii_txd
    tx.io.xgmii_txc      := io.xgmii_txc
    tx.io.xgmii_tx_valid := io.xgmii_tx_valid

    // Gearbox Handshaking (TX Side)
    io.tx_gbx_req_sync   := tx.io.tx_gbx_req_sync
    io.tx_gbx_req_stall  := tx.io.tx_gbx_req_stall
    tx.io.tx_gbx_sync    := io.tx_gbx_sync

    // SERDES Output
    io.serdes_tx_data       := tx.io.serdes_tx_data
    io.serdes_tx_data_valid := tx.io.serdes_tx_data_valid
    io.serdes_tx_hdr        := tx.io.serdes_tx_hdr
    io.serdes_tx_hdr_valid  := tx.io.serdes_tx_hdr_valid
    io.serdes_tx_gbx_sync   := tx.io.serdes_tx_gbx_sync

    // SERDES Handshaking
    tx.io.serdes_tx_gbx_req_sync  := io.serdes_tx_gbx_req_sync
    tx.io.serdes_tx_gbx_req_stall := io.serdes_tx_gbx_req_stall

    // Status
    io.tx_bad_block := tx.io.tx_bad_block

    // Configuration
    tx.io.cfg_tx_prbs31_enable := io.cfg_tx_prbs31_enable
  }
}


object Pcs {
  def apply(p: PcsParams): Pcs = Module(new Pcs(
    dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, txGbxIfEn = p.txGbxIfEn, rxGbxIfEn = p.rxGbxIfEn, bitReverse = p.bitReverse, 
    scramblerDisable = p.scramblerDisable, prbs31En = p.prbs31En, txSerdesPipeline = p.txSerdesPipeline, rxSerdesPipeline = p.rxSerdesPipeline, 
    bitslipHighCycles = p.bitslipHighCycles, bitslipLowCycles = p.bitslipLowCycles, count125Us = p.count125Us
  ))
}

object Main extends App {
  val mainClassName = "Pcs"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  PcsParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Pcs(
        dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, txGbxIfEn = p.txGbxIfEn, rxGbxIfEn = p.rxGbxIfEn, bitReverse = p.bitReverse, 
        scramblerDisable = p.scramblerDisable, prbs31En = p.prbs31En, txSerdesPipeline = p.txSerdesPipeline, rxSerdesPipeline = p.rxSerdesPipeline, 
        bitslipHighCycles = p.bitslipHighCycles, bitslipLowCycles = p.bitslipLowCycles, count125Us = p.count125Us
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    // Synthesis collateral generation
    sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    RunScriptFile.create(mainClassName, PcsParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}