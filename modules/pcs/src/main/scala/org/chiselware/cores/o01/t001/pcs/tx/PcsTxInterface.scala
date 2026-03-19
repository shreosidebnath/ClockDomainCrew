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
import chisel3.util._
import org.chiselware.cores.o01.t001.pcs.Lfsr


/** PCS Transmit Physical Interface logic
  *
  * Performs scrambling of the 64-bit payload using the polynomial G(x) = 1 + x^{39} + x^{58}.
  * This ensures DC balance and sufficient transition density on the high-speed serial link.
  *
  * @constructor create the TX interface logic
  * @param dataW bus width (32 or 64)
  * @param gbxIfEn enable gearbox flow control
  * @param bitReverse reverse output bits for SERDES lane orientation
  * @param scramblerDisable bypass scrambling (for loopback testing)
  * @param prbs31En enable PRBS31 generation for bit error rate testing (BERT)
  * @param serdesPipeline output register stages
  * @author ClockDomainCrew
  */
class PcsTxInterface(
    val dataW: Int = 64,
    val gbxIfEn: Boolean = true,
    val bitReverse: Boolean = true,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 1) extends Module {

  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  val hdrW = 2
  val io = IO(new Bundle {
    val encodedTxData = Input(UInt(dataW.W))
    val encodedTxDataValid = Input(Bool())
    val encodedTxHdr = Input(UInt(hdrW.W))
    val encodedTxHdrValid = Input(Bool())
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

    val cfgTxPrbs31Enable = Input(Bool())
  })

  io.txGbxReqSync := Mux(gbxIfEn.B, io.serdesTxGbxReqSync, false.B)
  io.txGbxReqStall := Mux(gbxIfEn.B, io.serdesTxGbxReqStall, false.B)

  // Scrambler
  val scramblerStateReg = RegInit("h3FFFFFFFFFFFFFF".U(58.W))
  val scramblerInst = Module(new Lfsr(
    lfsrW = 58,
    lfsrPoly = BigInt("8000000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = true
  ))

  scramblerInst.io.dataIn := io.encodedTxData
  scramblerInst.io.stateIn := scramblerStateReg
  when(!gbxIfEn.B || io.encodedTxDataValid) {
    scramblerStateReg := scramblerInst.io.stateOut
  }

  // PRBS31 Gen
  val prbs31StateReg = RegInit("h7FFFFFFF".U(31.W))
  val prbs31GenInst = Module(new Lfsr(
    lfsrW = 31,
    lfsrPoly = BigInt("10000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW + hdrW,
    dataInEn = false,
    dataOutEn = true
  ))
  prbs31GenInst.io.stateIn := prbs31StateReg
  prbs31GenInst.io.dataIn := 0.U

  // Output Regs
  val serdesTxDataReg = Reg(UInt(dataW.W))
  val serdesTxHdrReg = Reg(UInt(hdrW.W))

  when(prbs31En.B && io.cfgTxPrbs31Enable) {
    if (gbxIfEn) {
      when(io.encodedTxDataValid) {
        prbs31StateReg := prbs31GenInst.io.stateOut
      }
    } else {
      prbs31StateReg := prbs31GenInst.io.stateOut
    }
    serdesTxDataReg := ~prbs31GenInst.io.dataOut(dataW + hdrW - 1, hdrW)
    serdesTxHdrReg := ~prbs31GenInst.io.dataOut(hdrW - 1, 0)
  }.otherwise {
    serdesTxDataReg :=
      Mux(scramblerDisable.B, io.encodedTxData, scramblerInst.io.dataOut)
    serdesTxHdrReg := io.encodedTxHdr
  }

  val txDataInt =
    if (bitReverse)
      Reverse(serdesTxDataReg)
    else
      serdesTxDataReg
  val txHdrInt =
    if (bitReverse)
      Reverse(serdesTxHdrReg)
    else
      serdesTxHdrReg

  io.serdesTxData := ShiftRegister(txDataInt, serdesPipeline)
  io.serdesTxHdr := ShiftRegister(txHdrInt, serdesPipeline)
  io.serdesTxDataValid :=
    ShiftRegister(RegNext(io.encodedTxDataValid), serdesPipeline)
  io.serdesTxHdrValid :=
    ShiftRegister(RegNext(io.encodedTxHdrValid), serdesPipeline)
  io.serdesTxGbxSync :=
    ShiftRegister(RegNext(io.txGbxSync), serdesPipeline)
}