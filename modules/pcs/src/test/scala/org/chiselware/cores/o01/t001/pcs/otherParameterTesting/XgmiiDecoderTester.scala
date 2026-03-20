package org.chiselware.cores.o01.t001.pcs.rx

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class XgmiiDecoderCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "XgmiiDecoder coverage"

  it should "reject invalid data width" in {
    intercept[IllegalArgumentException] {
      test(new XgmiiDecoder(dataW = 48, ctrlW = 6)) { _ => }
    }
  }

  it should "reject invalid ctrl width" in {
    intercept[IllegalArgumentException] {
      test(new XgmiiDecoder(dataW = 64, ctrlW = 7)) { _ => }
    }
  }

  it should "cover 32-bit gearbox repack input and segmented output path" in {
    test(new XgmiiDecoder(dataW = 32, ctrlW = 4, gbxIfEn = true)) { dut =>
      // cycle 1: take outer when + inner hdr-valid when
      dut.io.encodedRxData.poke("hdeadbeef".U(32.W))
      dut.io.encodedRxDataValid.poke(true.B)
      dut.io.encodedRxHdr.poke("b01".U)
      dut.io.encodedRxHdrValid.poke(true.B)
      dut.clock.step(1)

      // cycle 2: still valid data, but hdr invalid
      dut.io.encodedRxData.poke("h12345678".U(32.W))
      dut.io.encodedRxDataValid.poke(true.B)
      dut.io.encodedRxHdr.poke("b10".U)
      dut.io.encodedRxHdrValid.poke(false.B)
      dut.clock.step(1)
    }
  }
}