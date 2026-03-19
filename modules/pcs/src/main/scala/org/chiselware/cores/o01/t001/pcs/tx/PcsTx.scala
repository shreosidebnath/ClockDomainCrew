// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._


/** PCS Transmit Path Top-Level
  *
  * Integrates the XGMII Encoder and the PCS TX Interface. It converts 64-bit 
  * XGMII data/control signals into encoded, scrambled 66-bit blocks (64b data + 2b header).
  *
  * @constructor create a new PCS TX path
  * @param dataW internal data width (64)
  * @param ctrlW control width (8)
  * @param hdrW sync header width (2)
  * @param gbxIfEn enable gearbox handshaking
  * @param bitReverse reverse bit order for specific SERDES architectures
  * @param scramblerDisable bypass the x^58 + x^39 + 1$ scrambler
  * @param prbs31En enable internal PRBS31 pattern generation
  * @param serdesPipeline number of output pipeline stages
  * @author ClockDomainCrew
  */
class PcsTx(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = true,
    val bitReverse: Boolean = true,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 1) extends Module {
  val io = IO(new Bundle {
    val xgmiiTxd = Input(UInt(dataW.W))
    val xgmiiTxc = Input(UInt(ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val txGbxReqSync = Output(Bool())
    val txGbxReqStall = Output(Bool())
    val txGbxSync = Input(Bool())

    val serdesTxData = Output(UInt(dataW.W))
    val serdesTxDataValid = Output(Bool())
    val serdesTxHdr = Output(UInt(hdrW.W))
    val serdesTxHdrValid = Output(Bool())
    val serdesTxGbxReqSync = Input(Bool())
    val serdesTxGbxReqStall = Input(Bool())
    val serdesTxGbxSync = Output(Bool())

    val txBadBlock = Output(Bool())
    val cfgTxPrbs31Enable = Input(Bool())
  })

  val encodedTxData = Wire(UInt(dataW.W))
  val encodedTxDataValid = Wire(Bool())
  val encodedTxHdr = Wire(UInt(hdrW.W))
  val encodedTxHdrValid = Wire(Bool())
  val txGbxSyncInt = Wire(Bool())

  // Encoder
  val encoderInst = Module(new XgmiiEncoder(
    dataW = dataW,
    ctrlW = ctrlW,
    gbxIfEn = gbxIfEn,
    gbxCnt = 1
  ))
  encoderInst.io.xgmiiTxd := io.xgmiiTxd
  encoderInst.io.xgmiiTxc := io.xgmiiTxc
  encoderInst.io.xgmiiTxValid := io.xgmiiTxValid
  encoderInst.io.txGbxSyncIn := io.txGbxSync

  encodedTxData := encoderInst.io.encodedTxData
  encodedTxDataValid := encoderInst.io.encodedTxDataValid
  encodedTxHdr := encoderInst.io.encodedTxHdr
  encodedTxHdrValid := encoderInst.io.encodedTxHdrValid
  txGbxSyncInt := encoderInst.io.txGbxSyncOut

  io.txBadBlock := encoderInst.io.txBadBlock

  // TX Interface
  val txIfInst = Module(new PcsTxInterface(
    dataW = dataW,
    gbxIfEn = gbxIfEn,
    bitReverse = bitReverse,
    scramblerDisable = scramblerDisable,
    prbs31En = prbs31En,
    serdesPipeline = serdesPipeline
  ))

  txIfInst.io.encodedTxData := encodedTxData
  txIfInst.io.encodedTxDataValid := encodedTxDataValid
  txIfInst.io.encodedTxHdr := encodedTxHdr
  txIfInst.io.encodedTxHdrValid := encodedTxHdrValid

  io.txGbxReqSync := txIfInst.io.txGbxReqSync
  io.txGbxReqStall := txIfInst.io.txGbxReqStall
  txIfInst.io.txGbxSync := txGbxSyncInt

  io.serdesTxData := txIfInst.io.serdesTxData
  io.serdesTxDataValid := txIfInst.io.serdesTxDataValid
  io.serdesTxHdr := txIfInst.io.serdesTxHdr
  io.serdesTxHdrValid := txIfInst.io.serdesTxHdrValid

  txIfInst.io.serdesTxGbxReqSync := io.serdesTxGbxReqSync
  txIfInst.io.serdesTxGbxReqStall := io.serdesTxGbxReqStall
  io.serdesTxGbxSync := txIfInst.io.serdesTxGbxSync

  txIfInst.io.cfgTxPrbs31Enable := io.cfgTxPrbs31Enable
}