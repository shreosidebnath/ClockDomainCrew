package org.chiselware.cores.o01.t001.mac.tx

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import firrtl2.options.TargetDirAnnotation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class Axis2Xgmii64Tester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  private val testAnnos = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/mac/generated/Axis2Xgmii64Tests")
  )

  private def idleInput(dut: Axis2Xgmii64): Unit = {
    dut.io.sAxisTx.tdata.poke(0.U)
    dut.io.sAxisTx.tkeep.poke(0.U)
    dut.io.sAxisTx.tvalid.poke(false.B)
    dut.io.sAxisTx.tlast.poke(false.B)
    dut.io.sAxisTx.tuser.poke(0.U)
    dut.io.sAxisTx.tid.poke(0.U)
  }

  private def sendOneBeatFrame(
    dut: Axis2Xgmii64,
    data: BigInt,
    keep: Int = 0xff,
    tid: Int = 1,
    user: Int = 0
  ): Unit = {
    dut.io.sAxisTx.tdata.poke(data.U(64.W))
    dut.io.sAxisTx.tkeep.poke(keep.U(8.W))
    dut.io.sAxisTx.tvalid.poke(true.B)
    dut.io.sAxisTx.tlast.poke(true.B)
    dut.io.sAxisTx.tuser.poke(user.U(1.W))
    dut.io.sAxisTx.tid.poke(tid.U(8.W))

    var cycles = 0
    while (!dut.io.sAxisTx.tready.peek().litToBoolean && cycles < 50) {
      dut.clock.step()
      cycles += 1
    }

    dut.clock.step()
    idleInput(dut)
  }

  it should "exercise PTP ToD completion datapath on normal frame start" in {
    test(new Axis2Xgmii64(
      gbxIfEn = true,
      gbxCnt = 1,
      paddingEn = true,
      dicEn = true,
      minFrameLen = 64,
      ptpTsEn = true,
      ptpTsFmtTod = true,
      ptpTsW = 96
    )).withAnnotations(testAnnos) { dut =>

      dut.io.cfgTxEnable.poke(true.B)
      dut.io.cfgTxIfg.poke(12.U)
      dut.io.cfgTxMaxPktLen.poke(1518.U)
      dut.io.txGbxReqSync.poke(0.U)
      dut.io.txGbxReqStall.poke(false.B)
      dut.io.mAxisTxCpl.tready.poke(true.B)

      idleInput(dut)
      dut.io.ptpTs.poke(BigInt("000000000001000000000123", 16).U)
      dut.clock.step()

      sendOneBeatFrame(dut, BigInt("1122334455667788", 16), tid = 7)

      dut.io.ptpTs.poke(BigInt("000000000001000000000124", 16).U)
      dut.clock.step(5)

      dut.io.mAxisTxCpl.tdata.peek()
      dut.io.mAxisTxCpl.tvalid.peek()
      dut.io.mAxisTxCpl.tid.peek()
    }
  }

  it should "exercise PTP raw-counter completion datapath on normal frame start" in {
    test(new Axis2Xgmii64(
      gbxIfEn = true,
      gbxCnt = 1,
      paddingEn = true,
      dicEn = true,
      minFrameLen = 64,
      ptpTsEn = true,
      ptpTsFmtTod = false,
      ptpTsW = 96
    )).withAnnotations(testAnnos) { dut =>

      dut.io.cfgTxEnable.poke(true.B)
      dut.io.cfgTxIfg.poke(12.U)
      dut.io.cfgTxMaxPktLen.poke(1518.U)
      dut.io.txGbxReqSync.poke(0.U)
      dut.io.txGbxReqStall.poke(false.B)
      dut.io.mAxisTxCpl.tready.poke(true.B)

      idleInput(dut)
      dut.io.ptpTs.poke(BigInt("123456789abcdef", 16).U)
      dut.clock.step()

      sendOneBeatFrame(dut, BigInt("0102030405060708", 16), tid = 9)

      dut.io.ptpTs.poke(BigInt("123456789abcfff", 16).U)
      dut.clock.step(5)

      dut.io.mAxisTxCpl.tdata.peek()
      dut.io.mAxisTxCpl.tvalid.peek()
      dut.io.mAxisTxCpl.tid.peek()
    }
  }

  it should "exercise swapped-lane PTP timestamp capture path" in {
    test(new Axis2Xgmii64(
      gbxIfEn = true,
      gbxCnt = 1,
      paddingEn = true,
      dicEn = false,
      minFrameLen = 64,
      ptpTsEn = true,
      ptpTsFmtTod = false,
      ptpTsW = 96
    )).withAnnotations(testAnnos) { dut =>

      dut.io.cfgTxEnable.poke(true.B)
      dut.io.cfgTxIfg.poke(8.U) // encourage shorter path back to idle with lane swap
      dut.io.cfgTxMaxPktLen.poke(1518.U)
      dut.io.txGbxReqSync.poke(0.U)
      dut.io.txGbxReqStall.poke(false.B)
      dut.io.mAxisTxCpl.tready.poke(true.B)

      idleInput(dut)
      dut.io.ptpTs.poke(BigInt("100", 16).U)
      dut.clock.step()

      // First frame
      sendOneBeatFrame(dut, BigInt("aaaaaaaaaaaaaaaa", 16), tid = 1)
      dut.clock.step(5)

      // Second frame after IFG evolution; this is intended to trigger swapped-lane start path
      dut.io.ptpTs.poke(BigInt("180", 16).U)
      sendOneBeatFrame(dut, BigInt("bbbbbbbbbbbbbbbb", 16), tid = 2)

      dut.clock.step(8)

      dut.io.mAxisTxCpl.tdata.peek()
      dut.io.mAxisTxCpl.tvalid.peek()
      dut.io.txStartPacket.peek()
    }
  }
}