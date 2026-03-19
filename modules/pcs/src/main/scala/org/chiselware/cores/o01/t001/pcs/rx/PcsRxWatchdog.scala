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

class PcsRxWatchdog(val count125us: Double = (125000.0 / 6.4)) extends Module {
  val hdrW = 2
  val io = IO(new Bundle {
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())
    val serdesRxResetReq = Output(Bool())

    val rxBadBlock = Input(Bool())
    val rxSequenceError = Input(Bool())
    val rxBlockLock = Input(Bool())
    val rxHighBer = Input(Bool())

    val rxStatus = Output(Bool())
  })

  val count125UsInt = count125us.toInt

  // Registers
  val timeCountReg = RegInit(count125UsInt.U(log2Ceil(count125UsInt + 1).W))
  val errorCountReg = RegInit(0.U(4.W))
  val statusCountReg = RegInit(0.U(4.W))

  val sawCtrlShReg = RegInit(false.B)
  val blockErrorCountReg = RegInit(0.U(10.W))

  val serdesRxResetReqReg = RegInit(false.B)
  val rxStatusReg = RegInit(false.B)

  // Outputs
  io.serdesRxResetReq := serdesRxResetReqReg
  io.rxStatus := rxStatusReg

  // Next State Logic
  val timeCountNext = WireDefault(timeCountReg)
  val errorCountNext = WireDefault(errorCountReg)
  val statusCountNext = WireDefault(statusCountReg)
  val sawCtrlShNext = WireDefault(sawCtrlShReg)
  val blockErrorCountNext = WireDefault(blockErrorCountReg)
  val rxStatusNext = WireDefault(rxStatusReg)
  val serdesRxResetReqNext = WireDefault(false.B)

  // Monitor Logic
  when(io.rxBlockLock) {
    when(io.serdesRxHdr === "b01".U(2.W) && io.serdesRxHdrValid) {
      sawCtrlShNext := true.B
    }
    when((io.rxBadBlock || io.rxSequenceError) &&
      !blockErrorCountReg.andR) {
      blockErrorCountNext := blockErrorCountReg + 1.U
    }
  }.otherwise {
    rxStatusNext := false.B
    statusCountNext := 0.U
  }

  // Timer Logic
  when(timeCountReg =/= 0.U) {
    timeCountNext := timeCountReg - 1.U
  }.otherwise {
    timeCountNext := count125UsInt.U

    when(!sawCtrlShReg || blockErrorCountReg.andR) {
      errorCountNext := errorCountReg + 1.U
      statusCountNext := 0.U
    }.otherwise {
      errorCountNext := 0.U
      when(!statusCountReg.andR) {
        statusCountNext := statusCountReg + 1.U
      }
    }

    when(errorCountReg.andR) {
      errorCountNext := 0.U
      serdesRxResetReqNext := true.B
    }

    when(statusCountReg.andR) {
      rxStatusNext := true.B
    }

    sawCtrlShNext := false.B
    blockErrorCountNext := 0.U
  }

  // State Updates
  timeCountReg := timeCountNext
  errorCountReg := errorCountNext
  statusCountReg := statusCountNext
  sawCtrlShReg := sawCtrlShNext
  blockErrorCountReg := blockErrorCountNext
  rxStatusReg := rxStatusNext
  serdesRxResetReqReg := serdesRxResetReqNext
}