package org.chiselware.cores.o01.t001.nfmac10g

import org.chiselware.cores.o01.t001.nfmac10g.bfm._

import chisel3._
import chiseltest._
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.io.File
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class Xgmii2AxisTest extends AnyFlatSpec with Matchers with ChiselScalatestTester {

  private val rng = new Random(0xC0FFEE) // fixed seed for reproducibility
  // Execute the main test for each configuration
  for ((configName, config) <- Xgmii2AxisParams.simConfigMap) {
    defineTests(configName, config)
  }

  // Create a directory for storing the Scala coverage reports
  new File("generated/scalaCoverage").mkdirs()

  private val idleWord: BigInt = BigInt("0707070707070707", 16)

  /** Helper: drive XGMII word */
  private def driveXgmii(dut: Xgmii2AxisTb, data: BigInt, ctrl: Int): Unit = {
    dut.io.xgmii_d.poke(data.U(64.W))
    dut.io.xgmii_c.poke(ctrl.U(8.W))
  }

  private def defineTests(configName: String, p: Xgmii2AxisParams): Unit = {
    behavior of s"xgmii2axis - RX directed tests (config: $configName)"

    private val backendAnnotations = Seq(
      VerilatorBackendAnnotation,
      TargetDirAnnotation("modules/nfmac10g/generated")
      // Uncomment for debugging:
      // WriteVcdAnnotation
    )

    it should "reset cleanly and output an AXIS frame for a basic lane0 SOF" in {
      test(new Xgmii2AxisTb()).withAnnotations(backendAnnotations) { dut =>
        dut.clock.setTimeout(0)

        // -----------------------
        // 1) Initialize inputs
        // -----------------------
        dut.io.rst.poke(true.B)
        dut.io.aresetn.poke(false.B)
        dut.io.configuration_vector.poke(0.U)

        // XGMII idle: I on all lanes, control on all lanes
        driveXgmii(dut, idleWord, 0xFF)

        dut.clock.step(5)

        // Release resets
        dut.io.rst.poke(false.B)
        dut.io.aresetn.poke(true.B)
        dut.clock.step(5)

        // AXIS sink BFM (TB drives tready + captures beats on handshake)
        val sink = new AxisSinkBfm(
          tvalid = dut.io.tvalid,
          tready = dut.io.tready,
          tdata  = dut.io.tdata,
          tkeep  = dut.io.tkeep,
          tlast  = dut.io.tlast,
          tuser  = dut.io.tuser,
          clock  = dut.clock,
          stallProbability = 0.0 // try 0.0 first
        )
        sink.init()

        // -----------------------
        // 2) Drive a simple frame
        // -----------------------
        // Word0: SOF on lane0: d(7,0)=S and c(0)=1
        val payload56 = BigInt(56, rng) // random 7 bytes
        val word0 = (payload56 << 8) | BigInt(0xFB)
        driveXgmii(dut, word0, 0x01) // only lane0 is control
        dut.clock.step(1)

        // Word1: payload data
        val word1 = BigInt(64, rng)
        driveXgmii(dut, word1, 0x00) // all data
        dut.clock.step(1)

        // Word2: basic terminate (keep simple)
        // Put T in lane0 and mark lane0 as control.
        // After this, go back to idle.
        val idle56 = BigInt(56, rng)
        val word2 = (idle56 << 8) | BigInt(0xFD)
        driveXgmii(dut, word2, 0x01)
        dut.clock.step(1)

        // Back to idle
        driveXgmii(dut, idleWord, 0xFF)

        // -----------------------
        // 3) Collect AXIS output
        // -----------------------
        sink.step(30)
        val frames = sink.popAllFrames()

        // -----------------------
        // 4) Basic assertions
        // -----------------------
        frames.nonEmpty shouldBe true
        info(s"Captured ${frames.size} AXIS frames")
        val first = frames.head

        // Print for bring-up
        info(s"Frame has ${first.beats.size} beats; last beat keep=0x${first.beats.last.keep.toHexString}")
      }
    }
  }
}
