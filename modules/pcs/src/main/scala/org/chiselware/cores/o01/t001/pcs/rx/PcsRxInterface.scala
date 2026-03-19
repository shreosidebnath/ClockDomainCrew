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
import chisel3.util._
import org.chiselware.cores.o01.t001.pcs.Lfsr

/** PCS Receive Physical Interface logic
  *
  * Handles the low-level processing of 66-bit blocks. This includes bit reversal,
  * pipelining, self-synchronizing descrambling (polynomial 1 + x^{39} + x^{58}),
  * PRBS31 checking, and high-level link monitoring (Watchdog, BER, Frame Sync).
  *
  * @constructor create the RX interface logic
  * @param dataW bus width (32 or 64)
  * @param gbxIfEn enable gearbox flow control
  * @param bitReverse reverse input bits from SERDES
  * @param scramblerDisable bypass descrambling
  * @param prbs31En enable PRBS31 error checking
  * @param serdesPipeline input register stages
  * @param bitslipHighCycles number of cycles to hold the bitslip pulse high
  * @param bitslipLowCycles dead-time (cooldown) between bitslip attempts
  * @param count125Us cycles in a 125 microsecond window
  * @author ClockDomainCrew
  */
class PcsRxInterface(
    val dataW: Int = 64,
    val gbxIfEn: Boolean = true,
    val bitReverse: Boolean = true,
    val scramblerDisable: Boolean = false,
    val prbs31En: Boolean = false,
    val serdesPipeline: Int = 1,
    val bitslipHighCycles: Int = 0,
    val bitslipLowCycles: Int = 7,
    val count125Us: Double = 125000 / 6.4) extends Module {

  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  val hdrW = 2
  
  val io = IO(new Bundle {
    // 10GBASE-R encoded interface
    val encodedRxData = Output(UInt(dataW.W))
    val encodedRxDataValid = Output(Bool())
    val encodedRxHdr = Output(UInt(hdrW.W))
    val encodedRxHdrValid = Output(Bool())

    // SERDES interface
    val serdesRxData = Input(UInt(dataW.W))
    val serdesRxDataValid = Input(Bool())
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())
    val serdesRxResetReq = Output(Bool())

    // Status
    val rxBadBlock = Input(Bool())
    val rxSequenceError = Input(Bool())
    val rxErrorCount = Output(UInt(7.W))
    val rxBlockLock = Output(Bool())
    val rxHighBer = Output(Bool())
    val rxStatus = Output(Bool())

    // Configuration
    val cfgRxPrbs31Enable = Input(Bool())
  })

  
  val useHdrVld = gbxIfEn || dataW != 64

  // --- Bit Reversal ---
  val serdesRxDataRev = Wire(UInt(dataW.W))
  val serdesRxHdrRev = Wire(UInt(hdrW.W))

  if (bitReverse) {
    serdesRxDataRev := Reverse(io.serdesRxData)
    serdesRxHdrRev := Reverse(io.serdesRxHdr)
  } else {
    serdesRxDataRev := io.serdesRxData
    serdesRxHdrRev := io.serdesRxHdr
  }

  // --- Pipeline ---
  val serdesRxDataInt = Wire(UInt(dataW.W))
  val serdesRxDataValidInt = Wire(Bool())
  val serdesRxHdrInt = Wire(UInt(hdrW.W))
  val serdesRxHdrValidInt = Wire(Bool())

  if (serdesPipeline > 0) {
    serdesRxDataInt := ShiftRegister(serdesRxDataRev, serdesPipeline)

    val dataValidPipe = ShiftRegister(io.serdesRxDataValid, serdesPipeline)
    serdesRxDataValidInt := Mux(gbxIfEn.B, dataValidPipe, true.B)

    serdesRxHdrInt := ShiftRegister(serdesRxHdrRev, serdesPipeline)

    val hdrValidPipe = ShiftRegister(io.serdesRxHdrValid, serdesPipeline)
    serdesRxHdrValidInt := Mux(useHdrVld.B, hdrValidPipe, true.B)
  } else {
    serdesRxDataInt := serdesRxDataRev
    serdesRxDataValidInt := Mux(gbxIfEn.B, io.serdesRxDataValid, true.B)
    serdesRxHdrInt := serdesRxHdrRev
    serdesRxHdrValidInt := Mux(useHdrVld.B, io.serdesRxHdrValid, true.B)
  }

  // --- Descrambler ---
  val descrambledRxData = Wire(UInt(dataW.W))
  val scramblerStateReg = RegInit(VecInit(Seq.fill(58)(true.B)).asUInt)
  val scramblerState = Wire(UInt(58.W))

  val descrambler = Module(new Lfsr(
    lfsrW = 58,
    lfsrPoly = BigInt("8000000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = true,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = true
  ))

  descrambler.io.dataIn := serdesRxDataInt
  descrambler.io.stateIn := scramblerStateReg
  descrambledRxData := descrambler.io.dataOut
  scramblerState := descrambler.io.stateOut

  when(!gbxIfEn.B || serdesRxDataValidInt) {
    scramblerStateReg := scramblerState
  }

  // --- PRBS31 Check ---
  val prbs31StateReg = RegInit(VecInit(Seq.fill(31)(true.B)).asUInt)
  val prbs31State = Wire(UInt(31.W))
  val prbs31Data = Wire(UInt((dataW + hdrW).W))
  val prbs31DataReg = RegInit(0.U((dataW + hdrW).W))

  val prbs31Check = Module(new Lfsr(
    lfsrW = 31,
    lfsrPoly = BigInt("10000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = true,
    reverse = true,
    dataW = dataW + hdrW,
    dataInEn = true,
    dataOutEn = true
  ))

  val prbsInputConcat = Cat(serdesRxDataInt, serdesRxHdrInt)
  prbs31Check.io.dataIn := ~prbsInputConcat
  prbs31Check.io.stateIn := prbs31StateReg
  prbs31Data := prbs31Check.io.dataOut
  prbs31State := prbs31Check.io.stateOut

  // --- Error Counting Logic ---
  val rxErrorCount1Temp = Wire(UInt(6.W))
  val rxErrorCount2Temp = Wire(UInt(6.W))

  val prbsBits = prbs31DataReg.asBools
  val oddBits = prbsBits.zipWithIndex.filter(_._2 % 2 != 0).map(_._1)
  val evenBits = prbsBits.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)

  rxErrorCount1Temp := PopCount(oddBits)
  rxErrorCount2Temp := PopCount(evenBits)

  // --- Output Registers ---
  val encodedRxDataReg = RegInit(0.U(dataW.W))
  val encodedRxDataValidReg = RegInit(false.B)
  val encodedRxHdrReg = RegInit(0.U(hdrW.W))
  val encodedRxHdrValidReg = RegInit(false.B)

  val rxErrorCountReg = RegInit(0.U(7.W))
  val rxErrorCount1Reg = RegInit(0.U(6.W))
  val rxErrorCount2Reg = RegInit(0.U(6.W))

  encodedRxDataReg :=
    Mux(scramblerDisable.B, serdesRxDataInt, descrambledRxData)
  encodedRxDataValidReg := serdesRxDataValidInt
  encodedRxHdrReg := serdesRxHdrInt
  encodedRxHdrValidReg := serdesRxHdrValidInt

  if (prbs31En) {
    when(io.cfgRxPrbs31Enable && (!gbxIfEn.B || serdesRxDataValidInt)) {
      prbs31StateReg := prbs31State
      prbs31DataReg := prbs31Data
    }.otherwise {
      prbs31DataReg := 0.U
    }

    rxErrorCount1Reg := rxErrorCount1Temp
    rxErrorCount2Reg := rxErrorCount2Temp
    rxErrorCountReg := rxErrorCount1Reg +& rxErrorCount2Reg
  } else {
    rxErrorCountReg := 0.U
  }

  // --- Final Assignments ---
  io.encodedRxData := encodedRxDataReg
  io.encodedRxDataValid := Mux(gbxIfEn.B, encodedRxDataValidReg, true.B)
  io.encodedRxHdr := encodedRxHdrReg
  io.encodedRxHdrValid := Mux(useHdrVld.B, encodedRxHdrValidReg, true.B)

  io.rxErrorCount := rxErrorCountReg

  val serdesRxBitslipInt = Wire(Bool())
  val serdesRxResetReqInt = Wire(Bool())

  io.serdesRxBitslip :=
    serdesRxBitslipInt && !(prbs31En.B && io.cfgRxPrbs31Enable)
  io.serdesRxResetReq :=
    serdesRxResetReqInt && !(prbs31En.B && io.cfgRxPrbs31Enable)

  // --- Submodule Instantiations ---
  val frameSyncInst = Module(new PcsRxFrameSync(
    bitslipHighCycles = bitslipHighCycles,
    bitslipLowCycles = bitslipLowCycles
  ))
  frameSyncInst.io.serdesRxHdr := serdesRxHdrInt
  frameSyncInst.io.serdesRxHdrValid := serdesRxHdrValidInt
  serdesRxBitslipInt := frameSyncInst.io.serdesRxBitslip
  io.rxBlockLock := frameSyncInst.io.rxBlockLock

  val berMonInst = Module(new PcsRxBerMon(
    count125Us = count125Us
  ))
  berMonInst.io.serdesRxHdr := serdesRxHdrInt
  berMonInst.io.serdesRxHdrValid := serdesRxHdrValidInt
  io.rxHighBer := berMonInst.io.rxHighBer

  val watchdogInst = Module(new PcsRxWatchdog(
    count125us = count125Us
  ))
  watchdogInst.io.serdesRxHdr := serdesRxHdrInt
  watchdogInst.io.serdesRxHdrValid := serdesRxHdrValidInt
  serdesRxResetReqInt := watchdogInst.io.serdesRxResetReq
  watchdogInst.io.rxBadBlock := io.rxBadBlock
  watchdogInst.io.rxSequenceError := io.rxSequenceError
  watchdogInst.io.rxBlockLock := io.rxBlockLock
  watchdogInst.io.rxHighBer := io.rxHighBer
  io.rxStatus := watchdogInst.io.rxStatus
}