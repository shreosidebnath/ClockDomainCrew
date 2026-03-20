package org.chiselware.cores.o01.t001.pcs.rx

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PcsRxInterfaceCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "PcsRxInterface coverage"

  it should "reject invalid data width" in {
    intercept[IllegalArgumentException] {
      test(new PcsRxInterface(dataW = 48)) { _ => }
    }
  }

  it should "cover useHdrVld via dataW != 64" in {
    test(new PcsRxInterface(
      dataW = 32,
      gbxIfEn = false,
      serdesPipeline = 0
    )) { dut =>
      dut.io.serdesRxData.poke(0.U)
      dut.io.serdesRxDataValid.poke(false.B)
      dut.io.serdesRxHdr.poke("b10".U)
      dut.io.serdesRxHdrValid.poke(true.B)
      dut.io.rxBadBlock.poke(false.B)
      dut.io.rxSequenceError.poke(false.B)
      dut.io.cfgRxPrbs31Enable.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "cover non-reversed and non-pipelined input paths" in {
    test(new PcsRxInterface(
      dataW = 64,
      bitReverse = false,
      serdesPipeline = 0
    )) { dut =>
      dut.io.serdesRxData.poke("h0123456789abcdef".U)
      dut.io.serdesRxDataValid.poke(true.B)
      dut.io.serdesRxHdr.poke("b01".U)
      dut.io.serdesRxHdrValid.poke(true.B)
      dut.io.rxBadBlock.poke(false.B)
      dut.io.rxSequenceError.poke(false.B)
      dut.io.cfgRxPrbs31Enable.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "cover PRBS31 enabled counting logic" in {
    test(new PcsRxInterface(
      dataW = 64,
      gbxIfEn = true,
      prbs31En = true,
      serdesPipeline = 0
    )) { dut =>
      dut.io.serdesRxData.poke("h123456789abcdef0".U)
      dut.io.serdesRxHdr.poke("b10".U)
      dut.io.serdesRxHdrValid.poke(true.B)
      dut.io.rxBadBlock.poke(false.B)
      dut.io.rxSequenceError.poke(false.B)

      // Take the PRBS update path
      dut.io.cfgRxPrbs31Enable.poke(true.B)
      dut.io.serdesRxDataValid.poke(true.B)
      dut.clock.step(1)

      // Take the PRBS clear path
      dut.io.cfgRxPrbs31Enable.poke(false.B)
      dut.io.serdesRxDataValid.poke(true.B)
      dut.clock.step(1)
    }
  }
}