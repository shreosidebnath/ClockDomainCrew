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

class PcsRxBerMon(val count125Us: Double = 125000.0 / 6.4) extends Module {

  val hdrW = 2
  val io = IO(new Bundle {
    // SERDES interface
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())

    // Status
    val rxHighBer = Output(Bool())
  })

  val count125UsInt = count125Us.toInt
  val countW = log2Ceil(count125UsInt + 1)

  // Registers
  val timeCountReg = RegInit(count125UsInt.U(countW.W))
  val berCountReg = RegInit(0.U(4.W))
  val rxHighBerReg = RegInit(false.B)

  io.rxHighBer := rxHighBerReg

  // 1. Timer Decrement Logic
  when(timeCountReg > 0.U) {
    timeCountReg := timeCountReg - 1.U
  }

  // 2. Main BER Logic
  when(io.serdesRxHdrValid) {
    val isValidHeader =
      io.serdesRxHdr === PcsRxBerMonConstants.SyncCtrl ||
        io.serdesRxHdr === PcsRxBerMonConstants.SyncData

    when(isValidHeader) {
      when(berCountReg =/= 15.U) {
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    }.otherwise {
      when(berCountReg === 15.U) {
        rxHighBerReg := true.B
      }.otherwise {
        berCountReg := berCountReg + 1.U
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    }
  }

  // 3. Timer Expiration / Reset Logic
  when(timeCountReg === 0.U && io.serdesRxHdrValid) {
    berCountReg := 0.U
    timeCountReg := count125UsInt.U
  }
}

object PcsRxBerMonConstants {
  val SyncData = "b10".U(2.W)
  val SyncCtrl = "b01".U(2.W)
}