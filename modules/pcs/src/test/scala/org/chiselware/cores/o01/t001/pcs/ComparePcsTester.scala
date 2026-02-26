package org.chiselware.cores.o01.t001.pcs

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chiseltest.simulator.VerilatorFlags
import org.scalatest.matchers.should.Matchers

class ComparePcsTester extends AnyFreeSpec with ChiselScalatestTester with Matchers {
  // --------------------------------------------------------------------------
  // Test cases
  // --------------------------------------------------------------------------
  it should "PCS: Chisel vs Verilog BlackBox matches on IDLE stream (loopback)" in {
    test(new DualWrapperPcs)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        // clock
        dut.clock.setTimeout(0)
        dut.clock.step(1)

        // reset
        dut.io.rst.poke(true.B)
        dut.io.xgmii_tx_valid.poke(true.B)
        dut.io.xgmii_txd.poke("h0707070707070707".U)
        dut.io.xgmii_txc.poke("hFF".U)
        dut.clock.step(20)
        dut.io.rst.poke(false.B)

        // give it time to lock (64b/66b sync headers etc.)
        dut.clock.step(200)

        // Drive IDLE and compare for a while
        val idleD = "h0707070707070707".U
        val idleC = "hFF".U

        for (_ <- 0 until 200) {
          dut.io.xgmii_txd.poke(idleD)
          dut.io.xgmii_txc.poke(idleC)
          dut.io.xgmii_tx_valid.poke(true.B)

          dut.clock.step(1)

          // Optionally require both “valid” before strict compare
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

          if (chValid && bbValid) {
            dut.io.chisel_rxd.expect(dut.io.bb_rxd.peek())
            dut.io.chisel_rxc.expect(dut.io.bb_rxc.peek())
          }
        }

        dut.io.chisel_rx_block_lock.expect(dut.io.bb_rx_block_lock.peek())
        dut.io.chisel_rx_status.expect(dut.io.bb_rx_status.peek())
      }
  }


}