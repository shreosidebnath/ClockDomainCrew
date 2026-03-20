package org.chiselware.cores.o01.t001.pcs.rx

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PcsRxFrameSyncCoverageTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  behavior of "PcsRxFrameSync coverage"

  it should "cover bitslipMaxCycles high-cycle branch" in {
    test(new PcsRxFrameSync(bitslipHighCycles = 8, bitslipLowCycles = 3)) { dut =>
      dut.io.serdesRxHdrValid.poke(false.B)
      dut.io.serdesRxHdr.poke(0.U)
      dut.clock.step(1)

      dut.io.rxBlockLock.expect(false.B)
      dut.io.serdesRxBitslip.expect(false.B)
    }
  }
}