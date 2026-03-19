// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._

/** PCS Receive Path Top-Level
  *
  * This module integrates the Physical Interface (Descrambler, Frame Sync, BER Monitor)
  * and the XGMII Decoder. It transforms the raw 66-bit blocks from the SERDES
  * into standard 64-bit XGMII data and control signals.
  *
  * @constructor create a new PCS RX path
  * @param dataW internal data width
  * @param ctrlW control width
  * @param hdrW sync header width
  * @param gbxIfEn enable gearbox handshaking for clock-data alignment
  * @param bitReverse reverse bit order for specific SERDES architectures
  * @param scramblerDisable bypass the descrambler for debug/loopback
  * @param prbs31En enable hardware checking of PRBS31 test patterns
  * @param serdesPipeline number of input pipeline stages
  * @param bitslipHighCycles number of cycles to hold the bitslip pulse high
  * @param bitslipLowCycles dead-time (cooldown) between bitslip attempts
  * @param count125Us constant used for the 125us Bit Error Rate (BER) window
  * @author ClockDomainCrew
  */
class PcsRx(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = true,
    val bitReverse: Boolean = true,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 1,
    val bitslipHighCycles: Int = 0,
    val bitslipLowCycles: Int = 7,
    val count125Us: Double = 125000.0 / 6.4) extends Module {
  val io = IO(new Bundle {
    // XGMII interface
    val xgmiiRxd = Output(UInt(dataW.W))
    val xgmiiRxc = Output(UInt(ctrlW.W))
    val xgmiiRxValid = Output(Bool())

    // SERDES interface
    val serdesRxData = Input(UInt(dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())

    // Status
    val rxErrorCount = Output(UInt(7.W))
    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())

    // Configuration
    val cfgRxPrbs31Enable = Input(Bool())
  })

  // -------------------------------------------------------------------------
  // 1. Instantiation of RX Interface (Physical Coding Sublayer)
  // -------------------------------------------------------------------------
  val rxIf = Module(new PcsRxInterface(
    dataW = dataW,
    gbxIfEn = gbxIfEn,
    bitReverse = bitReverse,
    scramblerDisable = scramblerDisable,
    prbs31En = prbs31En,
    serdesPipeline = serdesPipeline,
    bitslipHighCycles = bitslipHighCycles,
    bitslipLowCycles = bitslipLowCycles,
    count125Us = count125Us
  ))

  // Connect SERDES inputs
  rxIf.io.serdesRxData := io.serdesRxData
  rxIf.io.serdesRxDataValid := io.serdesRxDataValid
  rxIf.io.serdesRxHdr := io.serdesRxHdr
  rxIf.io.serdesRxHdrValid := io.serdesRxHdrValid

  // Connect Physical Status/Config
  io.serdesRxBitslip := rxIf.io.serdesRxBitslip
  io.serdesRxResetReq := rxIf.io.serdesRxResetReq
  io.rxErrorCount := rxIf.io.rxErrorCount
  io.rxBlockLock := rxIf.io.rxBlockLock
  io.rxHighBer := rxIf.io.rxHighBer
  io.rxStatus := rxIf.io.rxStatus
  rxIf.io.cfgRxPrbs31Enable := io.cfgRxPrbs31Enable

  // -------------------------------------------------------------------------
  // 2. Instantiation of XGMII Decoder (Base-R to XGMII Translation)
  // -------------------------------------------------------------------------
  val decoder = Module(new XgmiiDecoder(
    dataW = dataW,
    ctrlW = ctrlW,
    gbxIfEn = gbxIfEn
  ))

  // Connection between RX IF and Decoder
  decoder.io.encodedRxData := rxIf.io.encodedRxData
  decoder.io.encodedRxDataValid := rxIf.io.encodedRxDataValid
  decoder.io.encodedRxHdr := rxIf.io.encodedRxHdr
  decoder.io.encodedRxHdrValid := rxIf.io.encodedRxHdrValid

  // -------------------------------------------------------------------------
  // 3. Feedback Loop & Final Outputs
  // -------------------------------------------------------------------------

  // Connect XGMII Data/Valid outputs to top level
  io.xgmiiRxd := decoder.io.xgmiiRxd
  io.xgmiiRxc := decoder.io.xgmiiRxc
  io.xgmiiRxValid := decoder.io.xgmiiRxValid

  // Feed decoder status back to the RX Interface watchdog
  rxIf.io.rxBadBlock := decoder.io.rxBadBlock
  rxIf.io.rxSequenceError := decoder.io.rxSequenceError

  // Also expose these status signals to the top level
  io.rxBadBlock := decoder.io.rxBadBlock
  io.rxSequenceError := decoder.io.rxSequenceError
}