package org.chiselware.cores.o01.t001.mac.rx
import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import org.chiselware.cores.o01.t001.mac.{
  AxisInterface, AxisInterfaceParams, Lfsr
}
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

object Xgmii2Axis64Constants {
  val EthPre = "h55".U(8.W)
  val EthSfd = "hD5".U(8.W)
  val XgmiiIdle = "h07".U(8.W)
  val XgmiiStart = "hfb".U(8.W)
  val XgmiiTerm = "hfd".U(8.W)
  val XgmiiError = "hfe".U(8.W)

  val StateIdle = 0.U(2.W)
  val StatePayload = 1.U(2.W)
  val StateLast = 2.U(2.W)
}

class Xgmii2Axis64(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val gbxIfEn: Boolean = false,
    val ptpTsEn: Boolean = false,
    val ptpTsFmtTod: Boolean = true,
    val ptpTsW: Int = 96) extends Module {

  import Xgmii2Axis64Constants._

  val keepW = dataW / 8
  val userW =
    (if (ptpTsEn)
       ptpTsW
     else
       0) + 1

  require(
    dataW == 64,
    s"Error: Interface width must be 64 (instance dataW=$dataW)"
  )
  require(
    keepW * 8 == dataW && ctrlW * 8 == dataW,
    "Error: Interface requires byte (8-bit) granularity"
  )

  val io = IO(new Bundle {
    val xgmiiRxd = Input(UInt(dataW.W))
    val xgmiiRxc = Input(UInt(ctrlW.W))
    val xgmiiRxValid = Input(Bool())

    val mAxisRx =
      new AxisInterface(AxisInterfaceParams(
        dataW = dataW,
        keepW = keepW,
        userEn = true,
        userW = userW
      ))

    val ptpTs = Input(UInt(ptpTsW.W))

    val cfgRxMaxPktLen = Input(UInt(16.W))
    val cfgRxEnable = Input(Bool())

    val rxStartPacket = Output(UInt(2.W))
    val statRxByte = Output(UInt(4.W))
    val statRxPktLen = Output(UInt(16.W))
    val statRxPktFragment = Output(Bool())
    val statRxPktJabber = Output(Bool())
    val statRxPktUcast = Output(Bool())
    val statRxPktMcast = Output(Bool())
    val statRxPktBcast = Output(Bool())
    val statRxPktVlan = Output(Bool())
    val statRxPktGood = Output(Bool())
    val statRxPktBad = Output(Bool())
    val statRxErrOversize = Output(Bool())
    val statRxErrBadFcs = Output(Bool())
    val statRxErrBadBlock = Output(Bool())
    val statRxErrFraming = Output(Bool())
    val statRxErrPreamble = Output(Bool())
  })

  // Registers
  val stateReg = RegInit(StateIdle)
  val lanesSwappedReg = RegInit(false.B)
  val lanesSwappedD1Reg = RegInit(false.B)
  val swapRxdReg = RegInit(0.U(32.W))
  val swapRxcReg = RegInit(0.U(4.W))
  val swapRxcTermReg = RegInit(0.U(4.W))

  val termPresentReg = RegInit(false.B)
  val termFirstCycleReg = RegInit(false.B)
  val termLaneReg = RegInit(0.U(3.W))
  val termLaneD0Reg = RegInit(0.U(3.W))
  val framingErrorReg = RegInit(false.B)
  val framingErrorD0Reg = RegInit(false.B)

  val xgmiiRxdD0Reg = RegInit(0.U(dataW.W))
  val xgmiiRxdD1Reg = RegInit(0.U(dataW.W))

  val xgmiiStartSwapReg = RegInit(false.B)
  val xgmiiStartD0Reg = RegInit(false.B)
  val xgmiiStartD1Reg = RegInit(false.B)

  val frameOversizeReg = RegInit(false.B)
  val preOkReg = RegInit(false.B)
  val hdrPtrReg = RegInit(0.U(2.W))
  val isMcastReg = RegInit(false.B)
  val isBcastReg = RegInit(false.B)
  val is8021qReg = RegInit(false.B)
  val frameLenReg = RegInit(0.U(16.W))
  val frameLenLimCycReg = RegInit(0.U(13.W))
  val frameLenLimLastReg = RegInit(0.U(3.W))
  val frameLenLimCheckReg = RegInit(false.B)

  val mAxisRxTdataReg = RegInit(0.U(dataW.W))
  val mAxisRxTkeepReg = RegInit(0.U(keepW.W))
  val mAxisRxTvalidReg = RegInit(false.B)
  val mAxisRxTlastReg = RegInit(false.B)
  val mAxisRxTuserReg = RegInit(false.B)

  val startPacketReg = RegInit(0.U(2.W))

  val statRxByteReg = RegInit(0.U(4.W))
  val statRxPktLenReg = RegInit(0.U(16.W))
  val statRxPktFragmentReg = RegInit(false.B)
  val statRxPktJabberReg = RegInit(false.B)
  val statRxPktUcastReg = RegInit(false.B)
  val statRxPktMcastReg = RegInit(false.B)
  val statRxPktBcastReg = RegInit(false.B)
  val statRxPktVlanReg = RegInit(false.B)
  val statRxPktGoodReg = RegInit(false.B)
  val statRxPktBadReg = RegInit(false.B)
  val statRxErrOversizeReg = RegInit(false.B)
  val statRxErrBadFcsReg = RegInit(false.B)
  val statRxErrBadBlockReg = RegInit(false.B)
  val statRxErrFramingReg = RegInit(false.B)
  val statRxErrPreambleReg = RegInit(false.B)

  val ptpTsReg = RegInit(0.U(ptpTsW.W))
  val ptpTsOutReg = RegInit(0.U(ptpTsW.W))
  val ptpTsAdjReg = RegInit(0.U(ptpTsW.W))
  val ptpTsBorrowReg = RegInit(false.B)

  val crcStateReg = RegInit("hffffffff".U(32.W))
  val crcValidReg = RegInit(0.U(8.W))

  val lastTsReg = RegInit(0.U(20.W))
  val tsIncReg = RegInit(0.U(20.W))

  // Wires for _next state
  val stateNext = WireDefault(StateIdle)
  val frameOversizeNext = WireDefault(frameOversizeReg)
  val preOkNext = WireDefault(preOkReg)
  val hdrPtrNext = WireDefault(hdrPtrReg)
  val isMcastNext = WireDefault(isMcastReg)
  val isBcastNext = WireDefault(isBcastReg)
  val is8021qNext = WireDefault(is8021qReg)
  val frameLenNext = WireDefault(frameLenReg)
  val frameLenLimCycNext = WireDefault(frameLenLimCycReg)
  val frameLenLimLastNext = WireDefault(frameLenLimLastReg)
  val frameLenLimCheckNext = WireDefault(frameLenLimCheckReg)

  val mAxisRxTdataNext = WireDefault(xgmiiRxdD1Reg)
  val mAxisRxTkeepNext = WireDefault(((1 << keepW) - 1).U(keepW.W))
  val mAxisRxTvalidNext = WireDefault(false.B)
  val mAxisRxTlastNext = WireDefault(false.B)
  val mAxisRxTuserNext = WireDefault(false.B)

  val ptpTsOutNext = WireDefault(ptpTsOutReg)

  val statRxByteNext = WireDefault(0.U(4.W))
  val statRxPktLenNext = WireDefault(0.U(16.W))
  val statRxPktFragmentNext = WireDefault(false.B)
  val statRxPktJabberNext = WireDefault(false.B)
  val statRxPktUcastNext = WireDefault(false.B)
  val statRxPktMcastNext = WireDefault(false.B)
  val statRxPktBcastNext = WireDefault(false.B)
  val statRxPktVlanNext = WireDefault(false.B)
  val statRxPktGoodNext = WireDefault(false.B)
  val statRxPktBadNext = WireDefault(false.B)
  val statRxErrOversizeNext = WireDefault(false.B)
  val statRxErrBadFcsNext = WireDefault(false.B)
  val statRxErrBadBlockNext = WireDefault(false.B)
  val statRxErrFramingNext = WireDefault(false.B)
  val statRxErrPreambleNext = WireDefault(false.B)

  // Mask input data
  // scalafix:off scala-027
  val xgmiiRxdMaskedVec = Wire(Vec(ctrlW, UInt(8.W)))
  val xgmiiTermVec = Wire(Vec(ctrlW, Bool()))

  for (n <- 0 until ctrlW) {
    val rxdByte = io.xgmiiRxd((n * 8) + 7, n * 8)
    if (n > 0) {
      xgmiiRxdMaskedVec(n) := Mux(io.xgmiiRxc(n), 0.U, rxdByte)
    } else {
      xgmiiRxdMaskedVec(n) := rxdByte
    }
    xgmiiTermVec(n) := io.xgmiiRxc(n) && (rxdByte === XgmiiTerm)
  }
  // scalafix:on scala-027

  val xgmiiRxdMasked = xgmiiRxdMaskedVec.asUInt
  val xgmiiTerm = xgmiiTermVec.asUInt

  val crcInst = Module(new Lfsr(
    lfsrW = 32,
    lfsrPoly = BigInt("4c11db7", 16),
    lfsrGalois = true,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = false
  ))
  // scalafix:off scala-027
  crcInst.io.dataIn := Mux(
    xgmiiStartSwapReg,
    Cat(xgmiiRxdMasked(63, 32), 0.U(32.W)),
    xgmiiRxdMasked
  )
  // scalafix:on scala-027
  crcInst.io.stateIn := crcStateReg
  val crcState = crcInst.io.stateOut

  // scalafix:off scala-027
  val crcValid = Wire(Vec(8, Bool()))
  // scalafix:on scala-027
  crcValid(7) := crcStateReg === (~"h2144df1c".U(32.W)).asUInt
  crcValid(6) := crcStateReg === (~"hc622f71d".U(32.W)).asUInt
  crcValid(5) := crcStateReg === (~"hb1c2a1a3".U(32.W)).asUInt
  crcValid(4) := crcStateReg === (~"h9d6cdf7e".U(32.W)).asUInt
  crcValid(3) := crcStateReg === (~"h6522df69".U(32.W)).asUInt
  crcValid(2) := crcStateReg === (~"he60914ae".U(32.W)).asUInt
  crcValid(1) := crcStateReg === (~"he38a6876".U(32.W)).asUInt
  crcValid(0) := crcStateReg === (~"h6b87b1ec".U(32.W)).asUInt

  // AXI Output Assignments
  io.mAxisRx.tdata := mAxisRxTdataReg
  io.mAxisRx.tkeep := mAxisRxTkeepReg
  io.mAxisRx.tstrb := mAxisRxTkeepReg
  io.mAxisRx.tvalid := mAxisRxTvalidReg
  io.mAxisRx.tlast := mAxisRxTlastReg
  io.mAxisRx.tid := 0.U
  io.mAxisRx.tdest := 0.U

  if (ptpTsEn) {
    io.mAxisRx.tuser := Cat(ptpTsOutReg, mAxisRxTuserReg)
  } else {
    io.mAxisRx.tuser := mAxisRxTuserReg
  }

  io.rxStartPacket := startPacketReg
  io.statRxByte := statRxByteReg
  io.statRxPktLen := statRxPktLenReg
  io.statRxPktFragment := statRxPktFragmentReg
  io.statRxPktJabber := statRxPktJabberReg
  io.statRxPktUcast := statRxPktUcastReg
  io.statRxPktMcast := statRxPktMcastReg
  io.statRxPktBcast := statRxPktBcastReg
  io.statRxPktVlan := statRxPktVlanReg
  io.statRxPktGood := statRxPktGoodReg
  io.statRxPktBad := statRxPktBadReg
  io.statRxErrOversize := statRxErrOversizeReg
  io.statRxErrBadFcs := statRxErrBadFcsReg
  io.statRxErrBadBlock := statRxErrBadBlockReg
  io.statRxErrFraming := statRxErrFramingReg
  io.statRxErrPreamble := statRxErrPreambleReg

  // scalafix:off scala-027
  when(gbxIfEn.B && !io.xgmiiRxValid) {
    stateNext := stateReg
  }.otherwise {
    when(!frameLenReg(15, 3).andR) {
      when(termPresentReg) {
        frameLenNext := frameLenReg + termLaneReg
      }.otherwise {
        frameLenNext := frameLenReg + ctrlW.U
      }
    }.otherwise {
      frameLenNext := "hffff".U
    }

    when(frameLenLimCycReg =/= 0.U) {
      frameLenLimCycNext := frameLenLimCycReg - 1.U
    }.otherwise {
      frameLenLimCycNext := 0.U
    }

    when(frameLenLimCycReg === 2.U) {
      frameLenLimCheckNext := true.B
    }

    when(!hdrPtrReg.andR) {
      hdrPtrNext := hdrPtrReg + 1.U
    }

    switch(hdrPtrReg) {
      is(0.U) {
        isMcastNext := xgmiiRxdD1Reg(0)
        isBcastNext := xgmiiRxdD1Reg(47, 0).andR
      }
      is(1.U) {
        is8021qNext :=
          Cat(xgmiiRxdD1Reg(39, 32), xgmiiRxdD1Reg(47, 40)) === "h8100".U
      }
    }

    switch(stateReg) {
      is(StateIdle) {
        frameOversizeNext := false.B
        frameLenNext := ctrlW.U
        frameLenLimCycNext := io.cfgRxMaxPktLen(15, 3)
        frameLenLimLastNext := io.cfgRxMaxPktLen(2, 0)
        frameLenLimCheckNext := false.B
        hdrPtrNext := 0.U

        preOkNext := xgmiiRxdD1Reg(63, 8) === "hD5555555555555".U

        when(xgmiiStartD1Reg && io.cfgRxEnable) {
          statRxByteNext := ctrlW.U
          stateNext := StatePayload
        }.otherwise {
          stateNext := StateIdle
        }
      }
      is(StatePayload) {
        mAxisRxTdataNext := xgmiiRxdD1Reg
        mAxisRxTvalidNext := true.B

        if (ptpTsEn) {
          ptpTsOutNext :=
            Mux(!ptpTsFmtTod.B || ptpTsBorrowReg, ptpTsReg, ptpTsAdjReg)
        }

        when(termPresentReg) {
          statRxByteNext := termLaneReg
          when(frameLenLimCheckReg &&
            (frameLenLimLastReg < termLaneReg)) {
            frameOversizeNext := true.B
          }
        }.otherwise {
          statRxByteNext := ctrlW.U
          when(frameLenLimCheckReg) {
            frameOversizeNext := true.B
          }
        }

        when(framingErrorReg || framingErrorD0Reg) {
          mAxisRxTlastNext := true.B
          mAxisRxTuserNext := true.B
          statRxPktBadNext := true.B
          statRxPktLenNext := frameLenNext
          statRxPktUcastNext := !isMcastReg
          statRxPktMcastNext := isMcastReg && !isBcastReg
          statRxPktBcastNext := isBcastReg
          statRxPktVlanNext := is8021qReg
          statRxErrOversizeNext := frameOversizeNext
          statRxErrFramingNext := true.B
          statRxErrPreambleNext := !preOkReg
          statRxPktFragmentNext := frameLenNext(15, 6) === 0.U
          statRxPktJabberNext := frameOversizeNext
          stateNext := StateIdle
        }.elsewhen(termFirstCycleReg) {
          mAxisRxTkeepNext :=
            ((1 << keepW) - 1).U(keepW.W) >> (ctrlW.U - 4.U - termLaneReg)
          mAxisRxTlastNext := true.B

          val crcIsValid =
            (termLaneReg === 0.U &&
              Mux(lanesSwappedD1Reg, crcValidReg(3), crcValidReg(7))) ||
              (termLaneReg === 1.U &&
                Mux(lanesSwappedD1Reg, crcValidReg(4), crcValid(0))) ||
              (termLaneReg === 2.U &&
                Mux(lanesSwappedD1Reg, crcValidReg(5), crcValid(1))) ||
              (termLaneReg === 3.U &&
                Mux(lanesSwappedD1Reg, crcValidReg(6), crcValid(2))) ||
              (termLaneReg === 4.U &&
                Mux(lanesSwappedD1Reg, crcValidReg(7), crcValid(3)))

          when(crcIsValid) {
            when(frameOversizeNext) {
              mAxisRxTuserNext := true.B
              statRxPktBadNext := true.B
            }.otherwise {
              mAxisRxTuserNext := false.B
              statRxPktGoodNext := true.B
            }
          }.otherwise {
            mAxisRxTuserNext := true.B
            statRxPktFragmentNext := frameLenNext(15, 6) === 0.U
            statRxPktJabberNext := frameOversizeNext
            statRxPktBadNext := true.B
            statRxErrBadFcsNext := true.B
          }

          statRxPktLenNext := frameLenNext
          statRxPktUcastNext := !isMcastReg
          statRxPktMcastNext := isMcastReg && !isBcastReg
          statRxPktBcastNext := isBcastReg
          statRxPktVlanNext := is8021qReg
          statRxErrOversizeNext := frameOversizeNext
          statRxErrPreambleNext := !preOkReg
          stateNext := StateIdle

        }.elsewhen(termPresentReg) {
          stateNext := StateLast
        }.otherwise {
          stateNext := StatePayload
        }
      }
      is(StateLast) {
        mAxisRxTdataNext := xgmiiRxdD1Reg
        mAxisRxTkeepNext :=
          ((1 << keepW) - 1).U(keepW.W) >> (ctrlW.U + 4.U - termLaneD0Reg)
        mAxisRxTvalidNext := true.B
        mAxisRxTlastNext := true.B

        val crcIsValid =
          (termLaneD0Reg === 5.U &&
            Mux(lanesSwappedD1Reg, crcValidReg(0), crcValidReg(4))) ||
            (termLaneD0Reg === 6.U &&
              Mux(lanesSwappedD1Reg, crcValidReg(1), crcValidReg(5))) ||
            (termLaneD0Reg === 7.U &&
              Mux(lanesSwappedD1Reg, crcValidReg(2), crcValidReg(6)))

        when(crcIsValid) {
          when(frameOversizeReg) {
            mAxisRxTuserNext := true.B
            statRxPktBadNext := true.B
          }.otherwise {
            mAxisRxTuserNext := false.B
            statRxPktGoodNext := true.B
          }
        }.otherwise {
          mAxisRxTuserNext := true.B
          statRxPktFragmentNext := frameLenReg(15, 6) === 0.U
          statRxPktJabberNext := frameOversizeReg
          statRxPktBadNext := true.B
          statRxErrBadFcsNext := true.B
        }

        statRxPktLenNext := frameLenReg
        statRxPktUcastNext := !isMcastReg
        statRxPktMcastNext := isMcastReg && !isBcastReg
        statRxPktBcastNext := isBcastReg
        statRxPktVlanNext := is8021qReg
        statRxErrOversizeNext := frameOversizeReg
        statRxErrPreambleNext := !preOkReg

        when(xgmiiStartD1Reg && io.cfgRxEnable) {
          stateNext := StatePayload
        }.otherwise {
          stateNext := StateIdle
        }
      }
    }
  }
  // scalafix:on scala-027

  // Sequential Logic Block
  stateReg := stateNext
  frameOversizeReg := frameOversizeNext
  preOkReg := preOkNext
  hdrPtrReg := hdrPtrNext
  isMcastReg := isMcastNext
  isBcastReg := isBcastNext
  is8021qReg := is8021qNext
  frameLenReg := frameLenNext
  frameLenLimCycReg := frameLenLimCycNext
  frameLenLimLastReg := frameLenLimLastNext
  frameLenLimCheckReg := frameLenLimCheckNext

  mAxisRxTdataReg := mAxisRxTdataNext
  mAxisRxTkeepReg := mAxisRxTkeepNext
  mAxisRxTvalidReg := mAxisRxTvalidNext
  mAxisRxTlastReg := mAxisRxTlastNext
  mAxisRxTuserReg := mAxisRxTuserNext

  ptpTsOutReg := ptpTsOutNext
  startPacketReg := 0.U

  statRxByteReg := statRxByteNext
  statRxPktLenReg := statRxPktLenNext
  statRxPktFragmentReg := statRxPktFragmentNext
  statRxPktJabberReg := statRxPktJabberNext
  statRxPktUcastReg := statRxPktUcastNext
  statRxPktMcastReg := statRxPktMcastNext
  statRxPktBcastReg := statRxPktBcastNext
  statRxPktVlanReg := statRxPktVlanNext
  statRxPktGoodReg := statRxPktGoodNext
  statRxPktBadReg := statRxPktBadNext
  statRxErrOversizeReg := statRxErrOversizeNext
  statRxErrBadFcsReg := statRxErrBadFcsNext
  statRxErrBadBlockReg := statRxErrBadBlockNext
  statRxErrFramingReg := statRxErrFramingNext
  statRxErrPreambleReg := statRxErrPreambleNext

  // scalafix:off scala-027
  when(!gbxIfEn.B || io.xgmiiRxValid) {
    swapRxdReg := xgmiiRxdMasked(63, 32)
    swapRxcReg := io.xgmiiRxc(7, 4)
    swapRxcTermReg := xgmiiTerm(7, 4)

    xgmiiStartSwapReg := false.B
    xgmiiStartD0Reg := xgmiiStartSwapReg

    if (ptpTsEn && ptpTsFmtTod) {
      ptpTsAdjReg(15, 0) := ptpTsReg(15, 0)

      val tsSub = Cat(0.U(1.W), ptpTsReg(45, 16)).asSInt - 1000000000.S(32.W)
      ptpTsBorrowReg := tsSub(31)

      val ptpAdjMid = tsSub(29, 0).asUInt
      val ptpAdjHigh = ptpTsReg(95, 48) + 1.U

      ptpTsAdjReg :=
        Cat(ptpAdjHigh, 0.U(2.W), ptpAdjMid, ptpTsReg(15, 0))
    }

    when(lanesSwappedReg) {
      xgmiiRxdD0Reg := Cat(xgmiiRxdMasked(31, 0), swapRxdReg)
      termPresentReg := false.B
      termFirstCycleReg := false.B
      termLaneReg := 0.U
      framingErrorReg := Cat(io.xgmiiRxc(3, 0), swapRxcReg) =/= 0.U

      for (i <- ctrlW - 1 to 0 by -1) {
        val termCond = Cat(xgmiiTerm(3, 0), swapRxcTermReg)(i)
        when(termCond) {
          termPresentReg := true.B
          termFirstCycleReg := (i <= 4).B
          termLaneReg := i.U

          val mask = ((1 << ctrlW) - 1).U >> (ctrlW - i)
          framingErrorReg :=
            (Cat(io.xgmiiRxc(3, 0), swapRxcReg) & mask) =/= 0.U
        }
      }
    }.otherwise {
      xgmiiRxdD0Reg := xgmiiRxdMasked
      termPresentReg := false.B
      termFirstCycleReg := false.B
      termLaneReg := 0.U
      framingErrorReg := io.xgmiiRxc =/= 0.U

      for (i <- ctrlW - 1 to 0 by -1) {
        when(xgmiiTerm(i)) {
          termPresentReg := true.B
          termFirstCycleReg := (i <= 4).B
          termLaneReg := i.U

          val mask = ((1 << ctrlW) - 1).U >> (ctrlW - i)
          framingErrorReg := (io.xgmiiRxc & mask) =/= 0.U
        }
      }
    }

    crcStateReg := crcState

    when(io.xgmiiRxc(0) && io.xgmiiRxd(7, 0) === XgmiiStart) {
      lanesSwappedReg := false.B
      xgmiiStartD0Reg := true.B
      xgmiiRxdD0Reg := xgmiiRxdMasked
      crcStateReg := "hffffffff".U(32.W)
      framingErrorReg := io.xgmiiRxc(7, 1) =/= 0.U
    }.elsewhen(io.xgmiiRxc(4) && io.xgmiiRxd(39, 32) === XgmiiStart) {
      lanesSwappedReg := true.B
      xgmiiStartSwapReg := true.B
      crcStateReg := (~"h6dd90a9d".U(32.W)).asUInt
      framingErrorReg := io.xgmiiRxc(7, 5) =/= 0.U
    }

    when(xgmiiStartSwapReg) {
      startPacketReg := 2.U
      if (ptpTsFmtTod) {
        val ptpLow = io.ptpTs(45, 0) + (tsIncReg >> 1)
        ptpTsReg := Cat(io.ptpTs(95, 48), ptpLow(45, 0))
      } else {
        ptpTsReg := io.ptpTs + (tsIncReg >> 1)
      }
    }

    when(xgmiiStartD0Reg && !lanesSwappedReg) {
      startPacketReg := 1.U
      ptpTsReg := io.ptpTs
    }

    lanesSwappedD1Reg := lanesSwappedReg
    termLaneD0Reg := termLaneReg
    framingErrorD0Reg := framingErrorReg
    crcValidReg := crcValid.asUInt

    xgmiiRxdD1Reg := xgmiiRxdD0Reg
    xgmiiStartD1Reg := xgmiiStartD0Reg
  }

  lastTsReg := io.ptpTs(19, 0)
  tsIncReg := io.ptpTs(19, 0) - lastTsReg
  // scalafix:on scala-027
}

object Xgmii2Axis64 {
  def apply(p: Xgmii2Axis64Params): Xgmii2Axis64 = Module(new Xgmii2Axis64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW
  ))
}

object Main extends App {
  val MainClassName = "Mac"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  Xgmii2Axis64Params.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Xgmii2Axis64(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    SdcFile.create(s"${coreDir}/generated/synTestCases/$configName")

    // TODO: where is this coming from?? needs fixing. Found errors in calls below.
    // YosysTclFile.create - unknown parameter names
    // YosysTclFile.create(
    //   MainClassName,
    //   s"${coreDir}/generated/synTestCases/$configName"
    // )

    // TODO: where is this coming from?? needs fixing. Found errors in calls below.
    // StaTclFile.create - unknown parameter names
    // StaTclFile.create(
    //   MainClassName,
    //   s"${coreDir}/generated/synTestCases/$configName"
    // )

    // TODO: where is this coming from?? needs fixing. Found errors in calls below.
    // RunScriptFile.create - unknown parameter names
    // RunScriptFile.create(
    //   MainClassName,
    //   Xgmii2Axis64Params.synConfigs,
    //   s"${coreDir}/generated/synTestCases"
    // )
  }
}