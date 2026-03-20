package org.chiselware.cores.o01.t001.mac.stats

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StatsCollectTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/StatsCollectorTests")
  )

  behavior of "StatsCollect"

  it should "elaborate and run with cnt = 1" in {
    val p = StatsCollectParams(
      cnt = 1,
      incW = 8,
      dataW = 16,
      idW = 8,
      userW = 1,
      updatePeriod = 4,
      idBase = 0,
      strEn = false
    )

    test(new StatsCollect(p)).withAnnotations(testAnnos) { dut =>
      dut.io.stat_inc(0).poke(3.U)
      dut.io.stat_valid(0).poke(true.B)
      dut.io.stat_str(0).poke(0.U)
      dut.io.m_axis_stat_tready.poke(true.B)
      dut.io.gate.poke(true.B)
      dut.io.update.poke(false.B)

      dut.clock.step()
      dut.clock.step()
    }
  }
}