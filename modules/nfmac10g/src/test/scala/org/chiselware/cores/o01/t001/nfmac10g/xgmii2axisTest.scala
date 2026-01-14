// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import firrtl2.options.TargetDirAnnotation
import java.io.File

class Xgmii2AxisTest extends AnyFlatSpec with Matchers with ChiselScalatestTester {

  // Execute the main test for each configuration
  for ((configName, config) <- Xgmii2AxisParams.simConfigMap) {
    main(configName, config)
  }

  // Create a directory for storing the Scala coverage reports
  new File("generated/scalaCoverage").mkdir()

  /** Helper: drive XGMII word */
  private def driveXgmii(dut: Xgmii2AxisTb, d: BigInt, c: Int): Unit = {
    dut.io.xgmii_d.poke(d.U(64.W))
    dut.io.xgmii_c.poke(c.U(8.W))
  }

  /** Helper: collect AXIS beats for N cycles */
  private def collectAxis(dut: Xgmii2AxisTb, n: Int): Seq[(BigInt, Int, Boolean, BigInt)] = {
    val out = scala.collection.mutable.ArrayBuffer.empty[(BigInt, Int, Boolean, BigInt)]
    for (_ <- 0 until n) {
      if (dut.io.tvalid.peek().litToBoolean) {
        val data = dut.io.tdata.peek().litValue
        val keep = dut.io.tkeep.peek().litValue.toInt
        val last = dut.io.tlast.peek().litToBoolean
        val user = dut.io.tuser.peek().litValue
        out += ((data, keep, last, user))
      }
      dut.clock.step(1)
    }
    out.toSeq
  }

  def main(configName: String, p: Xgmii2AxisParams): Unit = {
    behavior of s"xgmii2axis - RX directed tests (config: $configName)"

    val backendAnnotations = Seq(
      VerilatorBackendAnnotation,
      TargetDirAnnotation("modules/nfmac10g/generated")
      // Uncomment for debugging:
      // WriteVcdAnnotation
    )

    it should "reset cleanly and output an AXIS frame for a basic lane0 SOF" in {
      test(new Xgmii2AxisTb()).withAnnotations(backendAnnotations) { dut =>
        dut.clock.setTimeout(0)

        import XgmiiConstants._

        // -----------------------
        // 1) Initialize inputs
        // -----------------------
        dut.io.rst.poke(true.B)
        dut.io.aresetn.poke(false.B)
        dut.io.configuration_vector.poke(0.U)

        // XGMII idle: I on all lanes, control on all lanes
        driveXgmii(dut, BigInt("0707070707070707", 16), 0xFF)

        dut.clock.step(5)

        // Release resets
        dut.io.rst.poke(false.B)
        dut.io.aresetn.poke(true.B)
        dut.clock.step(5)

        // -----------------------
        // 2) Drive a simple frame
        // -----------------------
        // Word0: SOF on lane0: d(7,0)=S and c(0)=1
        val word0 = (BigInt(0x11223344556677L) << 8) | BigInt(0xFB) // S = 0xFB in lane0
        driveXgmii(dut, word0, 0x01) // only lane0 is control
        dut.clock.step(1)

        // Word1: payload data
        val word1 = BigInt("0011223344556677", 16)
        driveXgmii(dut, word1, 0x00) // all data
        dut.clock.step(1)

        // Word2: basic terminate (keep simple)
        // Put T in lane0 and mark lane0 as control.
        // After this, go back to idle.
        val word2 = (BigInt("07070707070707", 16) << 8) | BigInt(0xFD) // T = 0xFD in lane0
        driveXgmii(dut, word2, 0x01)
        dut.clock.step(1)

        // Back to idle
        driveXgmii(dut, BigInt("0707070707070707", 16), 0xFF)

        // -----------------------
        // 3) Collect AXIS output
        // -----------------------
        val collected = collectAxis(dut, 30)

        // -----------------------
        // 4) Basic assertions
        // -----------------------
        collected.nonEmpty shouldBe true
        collected.exists(_._3) shouldBe true // saw tlast

        // Print for bring-up
        info(s"Collected ${collected.size} AXIS beats")
        collected.zipWithIndex.foreach { case ((data, keep, last, user), i) =>
          info(f"Beat $i: data=0x$data%016x keep=0x$keep%02x last=$last user=$user")
        }
      }
    }
  }
}
