package org.chiselware.cores.o01.t001.pcs.tx

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class XgmiiEncoderCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "XgmiiEncoder coverage"

  it should "reject invalid data width" in {
    intercept[IllegalArgumentException] {
      test(new XgmiiEncoder(dataW = 48, ctrlW = 6)) { _ => }
    }
  }

  it should "reject invalid ctrl width" in {
    intercept[IllegalArgumentException] {
      test(new XgmiiEncoder(dataW = 64, ctrlW = 7)) { _ => }
    }
  }

  it should "cover 32-bit mode with gearbox disabled" in {
    test(new XgmiiEncoder(
      dataW = 32,
      ctrlW = 4,
      gbxIfEn = false
    )) { dut =>
      dut.io.xgmiiTxd.poke("h11223344".U)
      dut.io.xgmiiTxc.poke(0.U)
      dut.io.xgmiiTxValid.poke(false.B)
      dut.io.txGbxSyncIn.poke(0.U)
      dut.clock.step(1)

      dut.io.xgmiiTxd.poke("h55667788".U)
      dut.io.xgmiiTxc.poke(0.U)
      dut.io.xgmiiTxValid.poke(false.B)
      dut.io.txGbxSyncIn.poke(0.U)
      dut.clock.step(1)
    }
  }

  it should "cover 32-bit mode with gearbox sync reset path" in {
    test(new XgmiiEncoder(
      dataW = 32,
      ctrlW = 4,
      gbxIfEn = true
    )) { dut =>
      dut.io.xgmiiTxd.poke("haabbccdd".U)
      dut.io.xgmiiTxc.poke(0.U)
      dut.io.xgmiiTxValid.poke(true.B)
      dut.io.txGbxSyncIn.poke(1.U)
      dut.clock.step(1)
    }
  }

  it should "cover hdr-valid forced true path when gearbox is disabled in 64-bit mode" in {
    test(new XgmiiEncoder(
      dataW = 64,
      ctrlW = 8,
      gbxIfEn = false
    )) { dut =>
      dut.io.xgmiiTxd.poke("h0123456789abcdef".U)
      dut.io.xgmiiTxc.poke(0.U)
      dut.io.xgmiiTxValid.poke(false.B)
      dut.io.txGbxSyncIn.poke(0.U)
      dut.clock.step(1)

      dut.io.encodedTxDataValid.expect(true.B)
      dut.io.encodedTxHdrValid.expect(true.B)
      dut.io.txGbxSyncOut.expect(0.U)
    }
  }
}