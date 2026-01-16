package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import firrtl2.options.TargetDirAnnotation

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class RxTest extends AnyFlatSpec with Matchers with ChiselScalatestTester {

  private type AxisBeat = (BigInt, Int, Boolean, BigInt)

  private val idleWord = BigInt("0707070707070707", 16)

  // Execute the main test for each configuration
  for ((configName, config) <- RxParams.simConfigMap) {
    defineTests(configName, config)
  }

  // Create a directory for storing the Scala coverage reports
  new File("generated/scalaCoverage").mkdirs()

  /** Helper: drive an XGMII word */
  private def driveXgmii(dut: RxTb, d: BigInt, c: Int): Unit = {
    dut.io.xgmii_rxd.poke(d.U(64.W))
    dut.io.xgmii_rxc.poke(c.U(8.W))
  }

  /** Helper: step and optionally sample AXIS outputs */
  private def stepAndCollect(dut: RxTb, n: Int, collected: ArrayBuffer[AxisBeat]): Unit = {
    for (_ <- 0 until n) {
      // Sample AXIS on each cycle when valid
      if (dut.io.axis_tvalid.peek().litToBoolean) {
        val data = dut.io.axis_tdata.peek().litValue
        val keep = dut.io.axis_tkeep.peek().litValue.toInt
        val last = dut.io.axis_tlast.peek().litToBoolean
        val user = dut.io.axis_tuser.peek().litValue
        collected += ((data, keep, last, user))
      }
      dut.clock.step(1)
    }
  }

  private def defineTests(configName: String, p: RxParams): Unit = {
    behavior of s"Rx directed tests (config: $configName)"

    private val backendAnnotations = Seq(
      VerilatorBackendAnnotation,
      TargetDirAnnotation("modules/nfmac10g/generated")
      // Uncomment for debug:
      // WriteVcdAnnotation
    )

    it should "reset cleanly and pass a basic lane0 frame through to AXIS" in {
      test(new RxTb).withAnnotations(backendAnnotations) { dut =>
        dut.clock.setTimeout(0)

        // -----------------------
        // 1) Initialize ALL inputs
        // -----------------------
        dut.io.rst.poke(true.B)

        dut.io.configuration_vector.poke(0.U)
        dut.io.cfg_rx_pause_enable.poke(false.B)
        dut.io.cfg_sub_quanta_count.poke(10.U)

        // Hold AXIS side in reset initially (active-low)
        dut.io.axis_aresetn.poke(false.B)

        // Drive XGMII idle by default: I on all lanes, C=0xFF
        driveXgmii(dut, idleWord, 0xFF)

        dut.clock.step(5)

        // Release resets
        dut.io.rst.poke(false.B)
        dut.io.axis_aresetn.poke(true.B)
        dut.clock.step(5)

        // -----------------------
        // 2) Drive a simple XGMII frame (lane0 start)
        // -----------------------
        // Word 0: start of frame on lane0: d(7,0)=S (0xFB), c(0)=1.
        // We'll put some payload bytes in the upper lanes.
        // NOTE: This is a simplified stimulus; exact preamble alignment may differ based on your Rx design.
        //
        // Layout (byte lanes 7..0):
        //   [b7 b6 b5 b4 b3 b2 b1 b0]
        // Here b0 is bits(7,0).
        //
        val start = 0xFB
        val payload56 = BigInt(56, rng) // 7 bytes
        val word0 = (payload56 << 8) | BigInt(start) // payload in b7..b1, S in b0

        // Control: only lane0 is control (bit0=1), rest are data => 0b0000_0001
        driveXgmii(dut, word0, 0x01)
        dut.clock.step(1)

        // Word 1: full data
        val rng = new Random(0xC0FFEE) // stable seed
        def randWord64(): BigInt = BigInt(64, rng) // [0, 2^64)
        val word1 = randWord64()
        driveXgmii(dut, word1, 0x00)
        dut.clock.step(1)

        // Word 2: terminate example (put T in lane4 and mark lanes 4..7 as control)
        // This is just one terminate pattern to exercise end-of-frame handling.
        val terminate = 0xFD
        // Put T at byte lane 0 for simplicity (you can vary later)
        val word2 = BigInt("07070707070707", 16) << 8 | BigInt(terminate) // mostly idle with T in b0
        // Mark lane0 as control; you can expand later to match your FSM’s terminate cases
        driveXgmii(dut, word2, 0x01)

        // Now return to idle
        dut.clock.step(1)
        driveXgmii(dut, idleWord, 0xFF)

        // -----------------------
        // 3) Collect AXIS output for a few cycles
        // -----------------------
        val collected = ArrayBuffer.empty[AxisBeat]
        stepAndCollect(dut, 30, collected)

        // -----------------------
        // 4) Basic assertions (bring-up checks)
        // -----------------------
        collected.nonEmpty shouldBe true

        val sawLast = collected.exists(_._3)
        sawLast shouldBe true

        // Optional: print what you saw (helps debug early)
        info(s"Collected ${collected.size} AXIS beats")
        collected.zipWithIndex.foreach { case ((data, keep, last, user), i) =>
          info(f"Beat $i: data=0x$data%016x keep=0x$keep%02x last=$last user=$user")
        }
      }
    }
  }
}
