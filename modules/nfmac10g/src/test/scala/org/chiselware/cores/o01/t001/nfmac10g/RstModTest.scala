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
          dut.io.reset.poke(1.U)
          dut.io.dcm_locked.poke(true.B)

          info("Holding reset to 1 for 3 cycles")
          dut.clock.step()
          dut.io.dcm_locked.poke(false.B)
          dut.io.rst.expect(1.U)

          dut.clock.step()
          dut.io.dcm_locked.poke(true.B)
          dut.io.rst.expect(1.U)

          dut.clock.step()
          dut.io.rst.expect(1.U)

          info("Releasing reset to 0")
          dut.io.reset.poke(0.U) 
          dut.clock.step() // State 0 to 1
          dut.io.rst.expect(1.U)

          dut.clock.step() // State 1 to 2
          dut.io.dcm_locked.poke(false.B)
          dut.io.rst.expect(1.U)

          dut.clock.step() // State 2 to 3
          dut.io.dcm_locked.poke(true.B)
          dut.io.rst.expect(1.U)

          dut.clock.step() // State 3 to 4
          dut.io.dcm_locked.poke(false.B)
          dut.io.rst.expect(1.U)

          dut.clock.step() // State stuck at 4
          dut.io.rst.expect(1.U)
          
          dut.clock.step() // State stuck at 4
          dut.io.rst.expect(1.U)

          info("Releasing dcm_locked to 1")
          dut.io.dcm_locked.poke(true.B)
          dut.clock.step() // State 4 to 5
          dut.io.rst.expect(1.U)

          dut.io.dcm_locked.poke(false.B)
          dut.clock.step() // State stuck at 5
          dut.io.rst.expect(0.U)

          dut.io.dcm_locked.poke(true.B)
          dut.clock.step() // State stuck at 5
          dut.io.rst.expect(0.U)

          info("Testing reset asynchronously")
          dut.io.reset.poke(1.U)
          dut.io.rst.expect(1.U)
        }
    }       
  }
}
