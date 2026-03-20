package org.chiselware.cores.o01.t001.pcs

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LfsrWrapper(
    val lfsrW: Int = 31,
    val lfsrPoly: BigInt = BigInt("10000001", 16),
    val lfsrGalois: Boolean = false,
    val lfsrFeedForward: Boolean = false,
    val reverse: Boolean = false,
    val dataW: Int = 8,
    val dataInEn: Boolean = true,
    val dataOutEn: Boolean = true
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

class LfsrCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "Lfsr coverage"

  private def pokeInputs(dut: LfsrWrapper, state: BigInt, data: BigInt): Unit = {
    dut.io.stateIn.poke(state.U(dut.lfsrW.W))
    dut.io.dataIn.poke(data.U(dut.dataW.W))
  }

  it should "cover reverse=false path" in {
    test(new LfsrWrapper(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = false,
      lfsrFeedForward = false,
      reverse = false,
      dataW = 8,
      dataInEn = true,
      dataOutEn = true
    )) { dut =>
      pokeInputs(dut, state = 0xA5, data = 0x3C)
      dut.io.stateOut.peek()
      dut.io.dataOut.peek()
    }
  }

  it should "cover Galois configuration path" in {
    test(new LfsrWrapper(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = true,
      lfsrFeedForward = false,
      reverse = false,
      dataW = 8,
      dataInEn = true,
      dataOutEn = true
    )) { dut =>
      pokeInputs(dut, state = 0x5A, data = 0xC3)
      dut.io.stateOut.peek()
      dut.io.dataOut.peek()
    }
  }

  it should "cover nextState empty-contribution branch" in {
    test(new LfsrWrapper(
      lfsrW = 1,
      lfsrPoly = BigInt(0),
      lfsrGalois = false,
      lfsrFeedForward = true,
      reverse = false,
      dataW = 1,
      dataInEn = false,
      dataOutEn = true
    )) { dut =>
      pokeInputs(dut, state = 1, data = 1)
      dut.io.stateOut.expect(0.U(1.W))
    }
  }

  it should "cover dataOutEn=false branch" in {
    test(new LfsrWrapper(
      lfsrW = 8,
      lfsrPoly = BigInt("07", 16),
      lfsrGalois = false,
      lfsrFeedForward = false,
      reverse = false,
      dataW = 8,
      dataInEn = true,
      dataOutEn = false
    )) { dut =>
      pokeInputs(dut, state = 0xF0, data = 0xAA)
      dut.io.dataOut.expect(0.U(8.W))
    }
  }
}