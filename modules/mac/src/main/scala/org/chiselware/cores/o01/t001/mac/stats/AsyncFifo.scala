package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class AsyncFifo(val p: AsyncFifoParams) extends RawModule {
  val io = IO(new Bundle {
    val sClk = Input(Clock())
    val sRst = Input(Bool())
    val mClk = Input(Clock())
    val mRst = Input(Bool())

    // Slave Interface
    val sAxisTdata  = Input(UInt(p.dataW.W))
    val sAxisTkeep  = Input(UInt(p.keepW.W))
    val sAxisTstrb  = Input(UInt(p.keepW.W))
    val sAxisTvalid = Input(Bool())
    val sAxisTready = Output(Bool())
    val sAxisTlast  = Input(Bool())
    val sAxisTid    = Input(UInt(p.idW.W))
    val sAxisTdest  = Input(UInt(p.destW.W))
    val sAxisTuser  = Input(UInt(p.userW.W))

    // Master Interface
    val mAxisTdata  = Output(UInt(p.dataW.W))
    val mAxisTkeep  = Output(UInt(p.keepW.W))
    val mAxisTstrb  = Output(UInt(p.keepW.W))
    val mAxisTvalid = Output(Bool())
    val mAxisTready = Input(Bool())
    val mAxisTlast  = Output(Bool())
    val mAxisTid    = Output(UInt(p.idW.W))
    val mAxisTdest  = Output(UInt(p.destW.W))
    val mAxisTuser  = Output(UInt(p.userW.W))
  })

  val aw = log2Ceil(p.depth)
  val payloadWidth = p.dataW + p.keepW + p.keepW + 1 + p.idW + p.destW + p.userW

  // Memory - Single file generation via Reg(Vec)
  val mem = withClockAndReset(io.sClk, io.sRst) { Reg(Vec(p.depth, UInt(payloadWidth.W))) }

  // --- Write Domain ---
  withClockAndReset(io.sClk, io.sRst) {
    val wrPtr = RegInit(0.U((aw + 1).W))
    val wrPtrGray = RegInit(0.U((aw + 1).W))
    val rdPtrGraySync = ShiftRegister(0.U((aw + 1).W), 2) // Wire logic placeholder

    val full = wrPtrGray === Cat(~rdPtrGraySync(aw, aw - 1), rdPtrGraySync(aw - 2, 0))
    io.sAxisTready := !full

    when(io.sAxisTvalid && io.sAxisTready) {
      val sPayload = Cat(io.sAxisTuser, io.sAxisTdest, io.sAxisTid, io.sAxisTlast, io.sAxisTstrb, io.sAxisTkeep, io.sAxisTdata)
      mem(wrPtr(aw - 1, 0)) := sPayload
      val nextWrPtr = wrPtr + 1.U
      wrPtr := nextWrPtr
      wrPtrGray := (nextWrPtr ^ (nextWrPtr >> 1))
    }
  }

  // --- Read Domain ---
  withClockAndReset(io.mClk, io.mRst) {
    val rdPtr = RegInit(0.U((aw + 1).W))
    val rdPtrGray = RegInit(0.U((aw + 1).W))
    val wrPtrGraySync = ShiftRegister(0.U((aw + 1).W), 2) // Wire logic placeholder

    val validReg = RegInit(false.B)
    val dataOutReg = RegInit(0.U(payloadWidth.W))
    val empty = rdPtrGray === wrPtrGraySync
    val readReady = !validReg || io.mAxisTready

    when(readReady && !empty) {
      dataOutReg := mem(rdPtr(aw - 1, 0))
      val nextRdPtr = rdPtr + 1.U
      rdPtr := nextRdPtr
      rdPtrGray := (nextRdPtr ^ (nextRdPtr >> 1))
    }

    when(readReady) {
      validReg := !empty
    }

    // Unpack Master Output
    io.mAxisTvalid := validReg
    io.mAxisTdata  := dataOutReg(p.dataW - 1, 0)
    io.mAxisTkeep  := dataOutReg(p.dataW + p.keepW - 1, p.dataW)
    io.mAxisTstrb  := dataOutReg(p.dataW + 2 * p.keepW - 1, p.dataW + p.keepW)
    io.mAxisTlast  := dataOutReg(p.dataW + 2 * p.keepW)
    io.mAxisTid    := dataOutReg(p.dataW + 2 * p.keepW + p.idW, p.dataW + 2 * p.keepW + 1)
    io.mAxisTdest  := dataOutReg(p.dataW + 2 * p.keepW + p.idW + p.destW, p.dataW + 2 * p.keepW + p.idW + 1)
    io.mAxisTuser  := dataOutReg(payloadWidth - 1)
  }
}

object AsyncFifoMain extends App {
  val mainClassName = "Mac"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  AsyncFifoParams.synConfigMap.foreach { case (configName, p) =>
    ChiselStage.emitSystemVerilog(
      new AsyncFifo(p),
      firtoolOpts = Array("-o", s"${coreDir}/generated/synTestCases/$configName", "--split-verilog", "--strip-debug-info")
    )
  }
}