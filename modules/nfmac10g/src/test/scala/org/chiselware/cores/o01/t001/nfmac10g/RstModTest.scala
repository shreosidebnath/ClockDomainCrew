// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.coverage._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Assertions._
import firrtl2.options.TargetDirAnnotation
import scala.util.Random
import java.io.{File}

class RstModTest extends AnyFlatSpec with Matchers with ChiselScalatestTester {

  // Execute the main test for each configuration
  for ((configName, config) <- RstModParams.simConfigMap) {
    main(configName, config)
  }

  // Create a directory for storing the Scala coverage reports
  val scalaCoverageDir = new File("generated/scalaCoverage")
  scalaCoverageDir.mkdir()


  /** Main test function executes one complete test for one configuration */
  def main(configName: String, p: RstModParams): Unit = {

    behavior of s"RstMod directed tests (config: $configName)"

    val backendAnnotations = Seq(
      // WriteVcdAnnotation,
      // WriteFstAnnotation,
      VerilatorBackendAnnotation,
      // IcarusBackendAnnotation,
      // VcsBackendAnnotation,
      TargetDirAnnotation("modules/nfmac10g/generated")
    )

    it should "perform these tests successfully " in {
      test(new RstModTb())
        .withAnnotations(backendAnnotations) { dut =>
          dut.clock.setTimeout(0)
          // Initialize the inputs
          dut.io.reset.poke(0.U)
          dut.io.dcm_locked.poke(false.B)

          // Sequence: reset, loop: load, hold
          info("Reset to zero")
          dut.io.reset.poke(true.B)
          dut.clock.step()
          dut.reset.poke(false.B)
          dut.io.rst.expect(0.U)
        //   info("Test with random data")
        //   for (i <- 1 to 10) {
        //     val myData = BigInt(1, scala.util.Random)
        //     dut.io.reset.poke(1.U)
        //     dut.io.in.poke(myData)
        //     dut.clock.step()
        //     dut.io.out.expect(myData)
        //     dut.io.enable.poke(0.U)
        //     dut.clock.step()
        //     dut.io.out.expect(myData)
        //   }
        }
    }
  }
}
