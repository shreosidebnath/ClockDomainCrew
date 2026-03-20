package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CustomArbiterTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/CustomArbiterTests")
  )

  behavior of "CustomArbiter"

  it should "use LSB priority and hit masked_req_valid path in round-robin mode" in {
    test(new CustomArbiter(
      ports = 4,
      arbRoundRobin = true,
      arbBlock = false,
      arbBlockAck = false,
      lsbHighPrio = true
    )).withAnnotations(testAnnos) { dut =>

      // First request establishes a mask
      dut.io.req.poke("b0001".U)
      dut.io.ack.poke(0.U)
      dut.clock.step()

      dut.io.grantValid.expect(true.B)
      dut.io.grant.expect("b0001".U)
      dut.io.grantIndex.expect(0.U)

      // Second request should use masked_req_valid branch
      dut.io.req.poke("b0110".U)
      dut.io.ack.poke(0.U)
      dut.clock.step()

      dut.io.grantValid.expect(true.B)
      dut.io.grant.expect("b0010".U)
      dut.io.grantIndex.expect(1.U)
    }
  }

  it should "use LSB priority and hit fallback req_valid path when masked_req_valid is false" in {
    test(new CustomArbiter(
      ports = 4,
      arbRoundRobin = true,
      arbBlock = false,
      arbBlockAck = false,
      lsbHighPrio = true
    )).withAnnotations(testAnnos) { dut =>

      // First request establishes a mask that excludes lower requests next time
      dut.io.req.poke("b0100".U)
      dut.io.ack.poke(0.U)
      dut.clock.step()

      dut.io.grantValid.expect(true.B)
      dut.io.grant.expect("b0100".U)
      dut.io.grantIndex.expect(2.U)

      // New req exists, but masked_req becomes 0, so fallback branch should be used
      dut.io.req.poke("b0011".U)
      dut.io.ack.poke(0.U)
      dut.clock.step()

      dut.io.grantValid.expect(true.B)
      dut.io.grant.expect("b0001".U)
      dut.io.grantIndex.expect(0.U)
    }
  }

  it should "hit non-round-robin arbitration branch" in {
    test(new CustomArbiter(
      ports = 4,
      arbRoundRobin = false,
      arbBlock = false,
      arbBlockAck = false,
      lsbHighPrio = false
    )).withAnnotations(testAnnos) { dut =>

      dut.io.req.poke("b1010".U)
      dut.io.ack.poke(0.U)
      dut.clock.step()

      dut.io.grantValid.expect(true.B)
      dut.io.grant.expect("b1000".U) // MSB priority since lsbHighPrio = false
      dut.io.grantIndex.expect(3.U)
    }
  }
}