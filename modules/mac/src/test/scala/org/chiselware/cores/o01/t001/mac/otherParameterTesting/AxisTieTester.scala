package org.chiselware.cores.o01.t001.mac

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AxisTieTb(sParams: AxisInterfaceParams, mParams: AxisInterfaceParams) extends Module {
  val io = IO(new Bundle {
    val sAxis = Flipped(new AxisInterface(sParams))
    val mAxis = new AxisInterface(mParams)
  })

  AxisTie(io.sAxis, io.mAxis)
}

class AxisTieTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/AxisTieTests")
  )

  behavior of "AxisTie"

  it should "drive default values when keep/last/user are disabled" in {
    val sParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = false, strbEn = false, lastEn = false,
      idEn = true, destEn = false, userEn = false
    )
    val mParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = false, strbEn = false, lastEn = false,
      idEn = true, destEn = false, userEn = false
    )

    test(new AxisTieTb(sParams, mParams)).withAnnotations(testAnnos) { dut =>
      dut.io.sAxis.tdata.poke("h123456789abcdef0".U)
      dut.io.sAxis.tvalid.poke(true.B)
      dut.io.sAxis.tid.poke(3.U)
      dut.io.mAxis.tready.poke(true.B)

      dut.clock.step()

      dut.io.mAxis.tkeep.expect("hff".U)   // line 64
      dut.io.mAxis.tlast.expect(true.B)    // line 76
      dut.io.mAxis.tuser.expect(0.U)       // line 94
    }
  }

  it should "pass through tstrb when strb is enabled" in {
    val sParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = true, strbEn = true, lastEn = true,
      idEn = false, destEn = false, userEn = false
    )
    val mParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = true, strbEn = true, lastEn = true,
      idEn = false, destEn = false, userEn = false
    )

    test(new AxisTieTb(sParams, mParams)).withAnnotations(testAnnos) { dut =>
      dut.io.sAxis.tdata.poke(0.U)
      dut.io.sAxis.tkeep.poke("hff".U)
      dut.io.sAxis.tstrb.poke("h5a".U)
      dut.io.sAxis.tvalid.poke(true.B)
      dut.io.sAxis.tlast.poke(false.B)
      dut.io.mAxis.tready.poke(true.B)

      dut.clock.step()

      dut.io.mAxis.tstrb.expect("h5a".U)   // line 68
    }
  }

  it should "pass through tdest when dest is enabled" in {
    val sParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = true, strbEn = false, lastEn = true,
      idEn = false, destEn = true, destW = 8, userEn = false
    )
    val mParams = AxisInterfaceParams(
      dataW = 64, keepW = 8,
      keepEn = true, strbEn = false, lastEn = true,
      idEn = false, destEn = true, destW = 8, userEn = false
    )

    test(new AxisTieTb(sParams, mParams)).withAnnotations(testAnnos) { dut =>
      dut.io.sAxis.tdata.poke(0.U)
      dut.io.sAxis.tkeep.poke("hff".U)
      dut.io.sAxis.tvalid.poke(true.B)
      dut.io.sAxis.tlast.poke(true.B)
      dut.io.sAxis.tdest.poke("h2c".U)
      dut.io.mAxis.tready.poke(true.B)

      dut.clock.step()

      dut.io.mAxis.tdest.expect("h2c".U)   // line 86
    }
  }
}