package org.chiselware.cores.o01.t001.mac.otherParameterTesting

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.chiselware.cores.o01.t001.mac.Lfsr

class LfsrTb(
  lfsrW: Int,
  lfsrPoly: BigInt,
  lfsrGalois: Boolean,
  lfsrFeedForward: Boolean,
  reverse: Boolean,
  dataW: Int,
  dataInEn: Boolean,
  dataOutEn: Boolean
) extends Module {
  val io = IO(new Bundle {
    val dataIn = Input(UInt(dataW.W))
    val stateIn = Input(UInt(lfsrW.W))
    val dataOut = Output(UInt(dataW.W))
    val stateOut = Output(UInt(lfsrW.W))
  })

  val dut = Module(new Lfsr(
    lfsrW = lfsrW,
    lfsrPoly = lfsrPoly,
    lfsrGalois = lfsrGalois,
    lfsrFeedForward = lfsrFeedForward,
    reverse = reverse,
    dataW = dataW,
    dataInEn = dataInEn,
    dataOutEn = dataOutEn
  ))

  dut.io.dataIn := io.dataIn
  dut.io.stateIn := io.stateIn
  io.dataOut := dut.io.dataOut
  io.stateOut := dut.io.stateOut
}

class LfsrTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/LfsrTests")
  )

  behavior of "Lfsr"

  it should "elaborate and evaluate Fibonacci non-reversed LFSR with data input/output enabled" in {
    test(new LfsrTb(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = false,
      lfsrFeedForward = false,
      reverse = false,
      dataW = 8,
      dataInEn = true,
      dataOutEn = true
    )).withAnnotations(testAnnos) { dut =>
      dut.io.stateIn.poke("h5a".U)
      dut.io.dataIn.poke("ha5".U)
      dut.io.stateOut.peek()
      dut.io.dataOut.peek()
    }
  }

  it should "elaborate and evaluate Fibonacci feedforward LFSR with data input disabled" in {
    test(new LfsrTb(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = false,
      lfsrFeedForward = true,
      reverse = false,
      dataW = 8,
      dataInEn = false,
      dataOutEn = true
    )).withAnnotations(testAnnos) { dut =>
      dut.io.stateIn.poke("h3c".U)
      dut.io.dataIn.poke("h00".U)
      dut.io.stateOut.peek()
      dut.io.dataOut.peek()
    }
  }

  it should "elaborate and evaluate Galois reversed LFSR with data output disabled" in {
    test(new LfsrTb(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = true,
      lfsrFeedForward = false,
      reverse = true,
      dataW = 8,
      dataInEn = true,
      dataOutEn = false
    )).withAnnotations(testAnnos) { dut =>
      dut.io.stateIn.poke("hc3".U)
      dut.io.dataIn.poke("h5a".U)
      dut.io.stateOut.peek()
      dut.io.dataOut.expect(0.U)
    }
  }
}