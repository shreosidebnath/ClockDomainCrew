// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.pcs
import _root_.circt.stage.ChiselStage
import chisel3._
import org.chiselware.cores.o01.t001.pcs.rx.PcsRx
import org.chiselware.cores.o01.t001.pcs.tx.PcsTx
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

/** 10GBASE-R Physical Coding Sublayer (PCS) Top-Level
  *
  * This module implements the 64b/66b PCS layer as defined in IEEE 802.3.
  * It performs 64b/66b encoding, scrambling, and gearboxing for the Transmit path,
  * and block synchronization, descrambling, and decoding for the Receive path.
  *
  * @constructor create a new PCS module
  * @param p configuration parameters defined in [[PcsParams]]
  * @author ClockDomainCrew
  */
class Pcs(val p: PcsParams) extends RawModule {
  val io = IO(new Bundle {
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())

    val xgmiiTxd = Input(UInt(p.dataW.W))
    val xgmiiTxc = Input(UInt(p.ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val xgmiiRxd = Output(UInt(p.dataW.W))
    val xgmiiRxc = Output(UInt(p.ctrlW.W))
    val xgmiiRxValid = Output(Bool())
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())

    // SERDES interface
    val serdesTxData = Output(UInt(p.dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(p.hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())
    val serdesRxData = Input(UInt(p.dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(p.hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())

    // Status
    val txBadBlock = Output(Bool())
    val rxErrorCount = Output(UInt(7.W))
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())

    // Configuration
    val cfgTxPrbs31Enable = Input(Bool())
    val cfgRxPrbs31Enable = Input(Bool())
  })

  // -------------------------------------------------------------------------
  // 1. RX Path Instantiation
  // -------------------------------------------------------------------------
  withClockAndReset(clock = io.rxClk, reset = io.rxRst) {
    val rx = Module(new PcsRx(
      dataW = p.dataW,
      ctrlW = p.ctrlW,
      hdrW = p.hdrW,
      gbxIfEn = p.rxGbxIfEn,
      bitReverse = p.bitReverse,
      scramblerDisable = p.scramblerDisable,
      prbs31En = p.prbs31En,
      serdesPipeline = p.rxSerdesPipeline,
      bitslipHighCycles = p.bitslipHighCycles,
      bitslipLowCycles = p.bitslipLowCycles,
      count125Us = p.count125Us
    ))

    // XGMII Output
    io.xgmiiRxd := rx.io.xgmiiRxd
    io.xgmiiRxc := rx.io.xgmiiRxc
    io.xgmiiRxValid := rx.io.xgmiiRxValid

    // SERDES Input
    rx.io.serdesRxData := io.serdesRxData
    rx.io.serdesRxDataValid := io.serdesRxDataValid
    rx.io.serdesRxHdr := io.serdesRxHdr
    rx.io.serdesRxHdrValid := io.serdesRxHdrValid

    // SERDES Control Output
    io.serdesRxBitslip := rx.io.serdesRxBitslip
    io.serdesRxResetReq := rx.io.serdesRxResetReq

    // Status
    io.rxErrorCount := rx.io.rxErrorCount
    io.rxBadBlock := rx.io.rxBadBlock
    io.rxSequenceError := rx.io.rxSequenceError
    io.rxBlockLock := rx.io.rxBlockLock
    io.rxHighBer := rx.io.rxHighBer
    io.rxStatus := rx.io.rxStatus

    // Configuration
    rx.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable
  }

  // -------------------------------------------------------------------------
  // 2. TX Path Instantiation
  // -------------------------------------------------------------------------
  withClockAndReset(clock = io.txClk, reset = io.txRst) {
    val tx = Module(new PcsTx(
      dataW = p.dataW,
      ctrlW = p.ctrlW,
      hdrW = p.hdrW,
      gbxIfEn = p.txGbxIfEn,
      bitReverse = p.bitReverse,
      scramblerDisable = p.scramblerDisable,
      prbs31En = p.prbs31En,
      serdesPipeline = p.txSerdesPipeline
    ))

    // XGMII Input
    tx.io.xgmiiTxd := io.xgmiiTxd
    tx.io.xgmiiTxc := io.xgmiiTxc
    tx.io.xgmiiTxValid := io.xgmiiTxValid

    // Gearbox Handshaking (TX Side)
    io.txGbxReqSync := tx.io.txGbxReqSync
    io.txGbxReqStall := tx.io.txGbxReqStall
    tx.io.txGbxSync := io.txGbxSync

    // SERDES Output
    io.serdesTxData := tx.io.serdesTxData
    io.serdesTxDataValid := tx.io.serdesTxDataValid
    io.serdesTxHdr := tx.io.serdesTxHdr
    io.serdesTxHdrValid := tx.io.serdesTxHdrValid
    io.serdesTxGbxSync := tx.io.serdesTxGbxSync

    // SERDES Handshaking
    tx.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
    tx.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall

    // Status
    io.txBadBlock := tx.io.txBadBlock

    // Configuration
    tx.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
  }
}

object Pcs {
  def apply(p: PcsParams): Pcs = Module(new Pcs(p))
}

object Main extends App {
  val MainClassName = "Pcs"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  PcsParams.SynConfigMap.foreach { case (configName, configParams) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Pcs(configParams),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    SdcFile.create(
      p = configParams,
      sdcFilePath = s"${coreDir}/generated/synTestCases/$configName"
    )
    YosysTclFile.create(
      mainClassName = MainClassName,
      synTestDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      mainClassName = MainClassName,
      synTestDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      mainClassName = MainClassName,
      configs = PcsParams.synConfigs,
      runDir = s"${coreDir}/generated/synTestCases"
    )
  }
}