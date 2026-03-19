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

/** PCS Receive Frame Synchronizer (Block Lock)
  *
  * This module achieves block lock by searching for valid 2-bit sync headers (01 or 10).
  * If the synchronizer detects 64 consecutive valid headers, it asserts 'rxBlockLock'.
  * If it detects 16 invalid headers within a 64-block window, it loses lock and 
  * pulses 'serdesRxBitslip' to shift the alignment by one bit and try again.
  *
  * @constructor create a new Frame Synchronizer
  * @param bitslipHighCycles number of cycles to hold the bitslip pulse high
  * @param bitslipLowCycles dead-time (cooldown) between bitslip attempts
  * @author ClockDomainCrew
  */
class PcsRxFrameSync(
    bitslipHighCycles: Int = 0,
    bitslipLowCycles: Int = 7) extends Module {
  val hdrW = 2
  val io = IO(new Bundle {
    // SERDES interface
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxBitslip = Output(Bool())

    // Status
    val rxBlockLock = Output(Bool())
  })


  val bitslipMaxCycles =
    if (bitslipHighCycles > bitslipLowCycles)
      bitslipHighCycles
    else
      bitslipLowCycles

  val bitslipCountW = log2Ceil(bitslipMaxCycles + 1)

  // Registers
  val shCountReg = RegInit(0.U(6.W))
  val shInvalidCountReg = RegInit(0.U(4.W))
  val bitslipCountReg = RegInit(0.U(bitslipCountW.W))
  val serdesRxBitslipReg = RegInit(false.B)
  val rxBlockLockReg = RegInit(false.B)

  // Next State Logic
  val shCountNext = WireDefault(shCountReg)
  val shInvalidCountNext = WireDefault(shInvalidCountReg)
  val bitslipCountNext = WireDefault(bitslipCountReg)
  val serdesRxBitslipNext = WireDefault(serdesRxBitslipReg)
  val rxBlockLockNext = WireDefault(rxBlockLockReg)

  val shCountAllOnes = shCountReg.andR
  val shInvalidCountAllOnes = shInvalidCountReg.andR

  when(bitslipCountReg =/= 0.U) {
    bitslipCountNext := bitslipCountReg - 1.U
  }.elsewhen(serdesRxBitslipReg) {
    serdesRxBitslipNext := false.B
    bitslipCountNext := bitslipLowCycles.U(bitslipCountW.W)
  }.elsewhen(!io.serdesRxHdrValid) {
    // wait for header - do nothing (defaults hold)
  }.elsewhen(
    io.serdesRxHdr === PcsRxFrameSyncConstants.SyncCtrl ||
      io.serdesRxHdr === PcsRxFrameSyncConstants.SyncData
  ) {
    // valid header
    shCountNext := shCountReg + 1.U

    when(shCountAllOnes) {
      shCountNext := 0.U
      shInvalidCountNext := 0.U
      when(shInvalidCountReg === 0.U) {
        rxBlockLockNext := true.B
      }
    }
  }.otherwise {
    // invalid header
    shCountNext := shCountReg + 1.U
    shInvalidCountNext := shInvalidCountReg + 1.U

    when(!rxBlockLockReg || shInvalidCountAllOnes) {
      shCountNext := 0.U
      shInvalidCountNext := 0.U
      rxBlockLockNext := false.B
      serdesRxBitslipNext := true.B
      bitslipCountNext := bitslipHighCycles.U(bitslipCountW.W)
    }.elsewhen(shCountAllOnes) {
      shCountNext := 0.U
      shInvalidCountNext := 0.U
    }
  }

  // Register Updates
  shCountReg := shCountNext
  shInvalidCountReg := shInvalidCountNext
  bitslipCountReg := bitslipCountNext
  serdesRxBitslipReg := serdesRxBitslipNext
  rxBlockLockReg := rxBlockLockNext

  // Output Assignments
  io.serdesRxBitslip := serdesRxBitslipReg
  io.rxBlockLock := rxBlockLockReg
}

object PcsRxFrameSyncConstants {
  val SyncData = "b10".U(2.W)
  val SyncCtrl = "b01".U(2.W)
}