package org.chiselware.cores.o01.t001.mac.rx

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class Xgmii2Axis64Tester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/Xgmii2Axis64Tests")
  )

  behavior of "Xgmii2Axis64"

  private val idleData = BigInt("0707070707070707", 16)
  private val startData = BigInt("d5555555555555fb", 16)

  private def driveIdle(dut: Xgmii2Axis64): Unit = {
    dut.io.xgmiiRxd.poke(idleData.U(64.W))
    dut.io.xgmiiRxc.poke("hff".U(8.W))
    dut.io.xgmiiRxValid.poke(true.B)
  }

  private def driveStartLane0(dut: Xgmii2Axis64): Unit = {
    dut.io.xgmiiRxd.poke(startData.U(64.W))
    dut.io.xgmiiRxc.poke("h01".U(8.W))
    dut.io.xgmiiRxValid.poke(true.B)
  }

  it should "exercise PTP ToD tuser concatenation and timestamp adjustment path" in {
    test(new Xgmii2Axis64(
      gbxIfEn = true,
      ptpTsEn = true,
      ptpTsFmtTod = true,
      ptpTsW = 96
    )).withAnnotations(testAnnos) { dut =>

      dut.io.cfgRxEnable.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      dut.io.ptpTs.poke(BigInt("000000000001000000000123", 16).U)

      driveIdle(dut)
      dut.clock.step()

      driveStartLane0(dut)
      dut.clock.step()

      dut.io.ptpTs.poke(BigInt("000000000001000000000124", 16).U)
      driveIdle(dut)
      dut.clock.step()

      dut.io.ptpTs.poke(BigInt("000000000001000000000125", 16).U)
      driveIdle(dut)
      dut.clock.step()

      dut.io.mAxisRx.tuser.peek()
    }
  }

  it should "exercise PTP raw-counter timestamp update path" in {
    test(new Xgmii2Axis64(
      gbxIfEn = true,
      ptpTsEn = true,
      ptpTsFmtTod = false,
      ptpTsW = 96
    )).withAnnotations(testAnnos) { dut =>

      dut.io.cfgRxEnable.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      dut.io.ptpTs.poke(BigInt("123456789abcdef", 16).U)

      driveIdle(dut)
      dut.clock.step()

      dut.io.xgmiiRxd.poke(BigInt("070707fb07070707", 16).U(64.W))
      dut.io.xgmiiRxc.poke("h10".U(8.W))
      dut.io.xgmiiRxValid.poke(true.B)
      dut.clock.step()

      dut.io.ptpTs.poke(BigInt("123456789abcfff", 16).U)
      driveIdle(dut)
      dut.clock.step()

      dut.io.mAxisRx.tuser.peek()
    }
  }
}