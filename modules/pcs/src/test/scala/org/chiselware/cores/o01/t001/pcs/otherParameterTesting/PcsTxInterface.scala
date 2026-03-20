package org.chiselware.cores.o01.t001.pcs.tx

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PcsTxInterfaceCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "PcsTxInterface coverage"

  it should "reject invalid data width" in {
    intercept[IllegalArgumentException] {
      test(new PcsTxInterface(dataW = 48)) { _ => }
    }
  }

  it should "cover PRBS state update when gearbox flow control is disabled" in {
    test(new PcsTxInterface(
      dataW = 64,
      gbxIfEn = false,
      prbs31En = true,
      bitReverse = true,
      serdesPipeline = 0
    )) { dut =>
      dut.io.cfgTxPrbs31Enable.poke(true.B)
      dut.io.encodedTxData.poke("h123456789abcdef0".U)
      dut.io.encodedTxHdr.poke("b10".U)
      dut.io.encodedTxDataValid.poke(false.B)
      dut.io.encodedTxHdrValid.poke(false.B)
      dut.io.txGbxSync.poke(false.B)
      dut.io.serdesTxGbxReqSync.poke(false.B)
      dut.io.serdesTxGbxReqStall.poke(false.B)
      dut.clock.step(1)
    }
  }

  it should "cover non-bit-reversed TX output path" in {
    test(new PcsTxInterface(
      dataW = 64,
      gbxIfEn = true,
      prbs31En = false,
      bitReverse = false,
      serdesPipeline = 0
    )) { dut =>
      dut.io.cfgTxPrbs31Enable.poke(false.B)
      dut.io.encodedTxData.poke("h0123456789abcdef".U)
      dut.io.encodedTxHdr.poke("b01".U)
      dut.io.encodedTxDataValid.poke(true.B)
      dut.io.encodedTxHdrValid.poke(true.B)
      dut.io.txGbxSync.poke(false.B)
      dut.io.serdesTxGbxReqSync.poke(false.B)
      dut.io.serdesTxGbxReqStall.poke(false.B)
      dut.clock.step(1)
    }
  }
}