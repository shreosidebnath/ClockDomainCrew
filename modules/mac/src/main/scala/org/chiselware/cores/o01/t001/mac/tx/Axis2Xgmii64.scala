package org.chiselware.cores.o01.t001.mac.tx
import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import org.chiselware.cores.o01.t001.mac.{
  AxisInterface, AxisInterfaceParams, Lfsr
}
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

class Axis2Xgmii64(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val gbxIfEn: Boolean = false,
    val gbxCnt: Int = 1,
    val paddingEn: Boolean = true,
    val dicEn: Boolean = true,
    val minFrameLen: Int = 64,
    val ptpTsEn: Boolean = false,
    val ptpTsFmtTod: Boolean = true,
    val ptpTsW: Int = 96,
    val txCplCtrlInTuser: Boolean = true) extends Module {

  val keepW = dataW / 8
  val userW =
    if (txCplCtrlInTuser)
      2
    else
      1

  val txTagW = 8

  val emptyW = log2Ceil(keepW + 1)
  val minLenW = log2Ceil(minFrameLen - 4 - ctrlW + 1 + 1)

  require(
    dataW == 64,
    s"Error: Interface width must be 64 (instance dataW=$dataW)"
  )
  require(
    keepW * 8 == dataW && ctrlW * 8 == dataW,
    "Error: Interface requires byte (8-bit) granularity"
  )

  val io = IO(new Bundle {
    // Transmit interface (AXI stream)
    val sAxisTx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = dataW,
      keepW = keepW,
      idEn = true,
      idW = txTagW,
      userEn = true,
      userW = userW
    )))
    val mAxisTxCpl =
      new AxisInterface(AxisInterfaceParams(
        dataW = 96,
        keepW = 1,
        idW = 8
      ))

    // XGMII output
    val xgmiiTxd = Output(UInt(dataW.W))
    val xgmiiTxc = Output(UInt(ctrlW.W))
    val xgmiiTxValid = Output(Bool())

    val txGbxReqSync = Input(UInt(gbxCnt.W))
    val txGbxReqStall = Input(Bool())
    val txGbxSync = Output(UInt(gbxCnt.W))

    // PTP
    val ptpTs = Input(UInt(ptpTsW.W))

    // Configuration
    val cfgTxMaxPktLen = Input(UInt(16.W))
    val cfgTxIfg = Input(UInt(8.W))
    val cfgTxEnable = Input(Bool())

    // Status
    val txStartPacket = Output(UInt(2.W))
    val statTxByte = Output(UInt(4.W))
    val statTxPktLen = Output(UInt(16.W))
    val statTxPktUcast = Output(Bool())
    val statTxPktMcast = Output(Bool())
    val statTxPktBcast = Output(Bool())
    val statTxPktVlan = Output(Bool())
    val statTxPktGood = Output(Bool())
    val statTxPktBad = Output(Bool())
    val statTxErrOversize = Output(Bool())
    val statTxErrUser = Output(Bool())
    val statTxErrUnderflow = Output(Bool())
  })

  // Constants
  val EthPre = "h55".U(8.W)
  val EthSfd = "hD5".U(8.W)
  val XgmiiIdle = "h07".U(8.W)
  val XgmiiStart = "hfb".U(8.W)
  val XgmiiTerm = "hfd".U(8.W)
  val XgmiiError = "hfe".U(8.W)

  val StateIdle = 0.U(3.W)
  val StatePayload = 1.U(3.W)
  val StatePad = 2.U(3.W)
  val StateFcs1 = 3.U(3.W)
  val StateFcs2 = 4.U(3.W)
  val StateErr = 5.U(3.W)
  val StateIfg = 6.U(3.W)

  // Registers
  val stateReg = RegInit(StateIdle)

  val swapLanesReg = RegInit(false.B)
  val swapTxdReg = RegInit(0.U(32.W))
  val swapTxcReg = RegInit(0.U(4.W))

  val sTdataReg = RegInit(0.U(dataW.W))
  val sEmptyReg = RegInit(0.U(emptyW.W))

  val frameStartReg = RegInit(false.B)
  val frameReg = RegInit(false.B)
  val frameErrorReg = RegInit(false.B)
  val frameOversizeReg = RegInit(false.B)
  val frameMinCountReg = RegInit(0.U(minLenW.W))
  val hdrPtrReg = RegInit(0.U(2.W))
  val isMcastReg = RegInit(false.B)
  val isBcastReg = RegInit(false.B)
  val is8021qReg = RegInit(false.B)
  val frameLenReg = RegInit(0.U(16.W))
  val frameLenLimCycReg = RegInit(0.U(13.W))
  val frameLenLimLastReg = RegInit(0.U(3.W))
  val frameLenLimCheckReg = RegInit(false.B)
  val ifgCntReg = RegInit(0.U(8.W))

  val ifgCountReg = RegInit(0.U(8.W))
  val deficitIdleCountReg = RegInit(0.U(2.W))

  val sAxisTxTreadyReg = RegInit(false.B)

  val mAxisTxCplTsReg = RegInit(0.U(ptpTsW.W))
  val mAxisTxCplTsAdjReg = RegInit(0.U(ptpTsW.W))
  val mAxisTxCplTagReg = RegInit(0.U(txTagW.W))
  val mAxisTxCplValidReg = RegInit(false.B)
  val mAxisTxCplValidIntReg = RegInit(false.B)
  val mAxisTxCplTsBorrowReg = RegInit(false.B)

  val crcStateReg = RegInit(VecInit(Seq.fill(8)("hffffffff".U(32.W))))

  val lastTsReg = RegInit(0.U(20.W))
  val tsIncReg = RegInit(0.U(20.W))

  val xgmiiTxdReg = RegInit(VecInit(Seq.fill(ctrlW)(XgmiiIdle)).asUInt)
  val xgmiiTxcReg = RegInit(((1 << ctrlW) - 1).U(ctrlW.W))
  val xgmiiTxValidReg = RegInit(false.B)
  val txGbxSyncReg = RegInit(0.U(gbxCnt.W))

  val startPacketReg = RegInit(0.U(2.W))

  val statTxByteReg = RegInit(0.U(4.W))
  val statTxPktLenReg = RegInit(0.U(16.W))
  val statTxPktUcastReg = RegInit(false.B)
  val statTxPktMcastReg = RegInit(false.B)
  val statTxPktBcastReg = RegInit(false.B)
  val statTxPktVlanReg = RegInit(false.B)
  val statTxPktGoodReg = RegInit(false.B)
  val statTxPktBadReg = RegInit(false.B)
  val statTxErrOversizeReg = RegInit(false.B)
  val statTxErrUserReg = RegInit(false.B)
  val statTxErrUnderflowReg = RegInit(false.B)

  // Combinational Fallbacks (_next logic)
  val stateNext = WireDefault(StateIdle)
  val resetCrc = WireDefault(false.B)
  val updateCrc = WireDefault(false.B)

  val swapLanesNext = WireDefault(swapLanesReg)
  val frameStartNext = WireDefault(false.B)
  val frameNext = WireDefault(frameReg)
  val frameErrorNext = WireDefault(frameErrorReg)
  val frameOversizeNext = WireDefault(frameOversizeReg)
  val frameMinCountNext = WireDefault(frameMinCountReg)
  val hdrPtrNext = WireDefault(hdrPtrReg)
  val isMcastNext = WireDefault(isMcastReg)
  val isBcastNext = WireDefault(isBcastReg)
  val is8021qNext = WireDefault(is8021qReg)
  val frameLenNext = WireDefault(frameLenReg)
  val frameLenLimCycNext = WireDefault(frameLenLimCycReg)
  val frameLenLimLastNext = WireDefault(frameLenLimLastReg)
  val frameLenLimCheckNext = WireDefault(frameLenLimCheckReg)
  val ifgCntNext = WireDefault(ifgCntReg)

  val ifgCountNext = WireDefault(ifgCountReg)
  val deficitIdleCountNext = WireDefault(deficitIdleCountReg)

  val sAxisTxTreadyNext = WireDefault(false.B)
  val sTdataNext = WireDefault(sTdataReg)
  val sEmptyNext = WireDefault(sEmptyReg)
  val mAxisTxCplTagNext = WireDefault(mAxisTxCplTagReg)

  val xgmiiTxdNext = WireDefault(VecInit(Seq.fill(ctrlW)(XgmiiIdle)).asUInt)
  val xgmiiTxcNext = WireDefault(((1 << ctrlW) - 1).U(ctrlW.W))

  val statTxByteNext = WireDefault(0.U(4.W))
  val statTxPktLenNext = WireDefault(0.U(16.W))
  val statTxPktUcastNext = WireDefault(false.B)
  val statTxPktMcastNext = WireDefault(false.B)
  val statTxPktBcastNext = WireDefault(false.B)
  val statTxPktVlanNext = WireDefault(false.B)
  val statTxPktGoodNext = WireDefault(false.B)
  val statTxPktBadNext = WireDefault(false.B)
  val statTxErrOversizeNext = WireDefault(false.B)
  val statTxErrUserNext = WireDefault(false.B)
  val statTxErrUnderflowNext = WireDefault(false.B)

  // Direct Outputs
  io.sAxisTx.tready :=
    sAxisTxTreadyReg && (!gbxIfEn.B || !io.txGbxReqStall)
  io.xgmiiTxd := xgmiiTxdReg
  io.xgmiiTxc := xgmiiTxcReg
  io.xgmiiTxValid := Mux(gbxIfEn.B, xgmiiTxValidReg, true.B)
  io.txGbxSync := Mux(gbxIfEn.B, txGbxSyncReg, 0.U)

  io.txStartPacket := startPacketReg
  io.statTxByte := statTxByteReg
  io.statTxPktLen := statTxPktLenReg
  io.statTxPktUcast := statTxPktUcastReg
  io.statTxPktMcast := statTxPktMcastReg
  io.statTxPktBcast := statTxPktBcastReg
  io.statTxPktVlan := statTxPktVlanReg
  io.statTxPktGood := statTxPktGoodReg
  io.statTxPktBad := statTxPktBadReg
  io.statTxErrOversize := statTxErrOversizeReg
  io.statTxErrUser := statTxErrUserReg
  io.statTxErrUnderflow := statTxErrUnderflowReg

  if (ptpTsEn) {
    io.mAxisTxCpl.tdata := Mux(
      !ptpTsFmtTod.B || mAxisTxCplTsBorrowReg,
      mAxisTxCplTsReg,
      mAxisTxCplTsAdjReg
    )
  } else {
    io.mAxisTxCpl.tdata := 0.U
  }

  io.mAxisTxCpl.tkeep := 1.U
  io.mAxisTxCpl.tstrb := 1.U
  io.mAxisTxCpl.tvalid := mAxisTxCplValidReg
  io.mAxisTxCpl.tlast := true.B
  io.mAxisTxCpl.tid := mAxisTxCplTagReg
  io.mAxisTxCpl.tdest := 0.U
  io.mAxisTxCpl.tuser := 0.U

  // Helper Function
  def keep2empty(k: UInt): UInt = {
    val out = WireDefault(0.U(3.W))
    val k8 = k(7, 0)

    when(k8 === BitPat("b???????0")) { out := 7.U }
      .elsewhen(k8 === BitPat("b??????01")) { out := 7.U }
      .elsewhen(k8 === BitPat("b?????011")) { out := 6.U }
      .elsewhen(k8 === BitPat("b????0111")) { out := 5.U }
      .elsewhen(k8 === BitPat("b???01111")) { out := 4.U }
      .elsewhen(k8 === BitPat("b??011111")) { out := 3.U }
      .elsewhen(k8 === BitPat("b?0111111")) { out := 2.U }
      .elsewhen(k8 === BitPat("b01111111")) { out := 1.U }
      .elsewhen(k8 === BitPat("b11111111")) { out := 0.U }

    out
  }

  // Mask input data
  val sAxisTxTdataMaskedVec = Wire(Vec(keepW, UInt(8.W)))
  for (n <- 0 until keepW) {
    if (n == 0) {
      sAxisTxTdataMaskedVec(n) := io.sAxisTx.tdata(7, 0)
    } else {
      sAxisTxTdataMaskedVec(n) := Mux(
        io.sAxisTx.tkeep(n),
        io.sAxisTx.tdata((n * 8) + 7, n * 8),
        0.U(8.W)
      )
    }
  }
  val sAxisTxTdataMasked = sAxisTxTdataMaskedVec.asUInt

  // CRC Generation
  val crcState = Wire(Vec(8, UInt(32.W)))
  for (n <- 0 until 8) {
    val crcInst = Module(new Lfsr(
      lfsrW = 32,
      lfsrPoly = BigInt("4c11db7", 16),
      lfsrGalois = true,
      lfsrFeedForward = false,
      reverse = true,
      dataW = 8 * (n + 1),
      dataInEn = true,
      dataOutEn = false
    ))
    crcInst.io.dataIn := sTdataReg(8 * (n + 1) - 1, 0)
    crcInst.io.stateIn := crcStateReg(7)
    crcState(n) := crcInst.io.stateOut
  }

  // FCS Cycle Logic
  val fcsOutputTxd0 = WireDefault(0.U(dataW.W))
  val fcsOutputTxd1 = WireDefault(0.U(dataW.W))
  val fcsOutputTxc0 = WireDefault(0.U(ctrlW.W))
  val fcsOutputTxc1 = WireDefault(0.U(ctrlW.W))
  val ifgOffset = WireDefault(0.U(8.W))

  switch(sEmptyReg) {
    is(7.U) {
      fcsOutputTxd0 := Cat(
        XgmiiIdle,
        XgmiiIdle,
        XgmiiTerm,
        ~crcState(0),
        sTdataReg(7, 0)
      )
      fcsOutputTxd1 := VecInit(Seq.fill(8)(XgmiiIdle)).asUInt
      fcsOutputTxc0 := "b11100000".U
      fcsOutputTxc1 := "b11111111".U
      ifgOffset := 3.U
    }
    is(6.U) {
      fcsOutputTxd0 :=
        Cat(XgmiiIdle, XgmiiTerm, ~crcState(1), sTdataReg(15, 0))
      fcsOutputTxd1 := VecInit(Seq.fill(8)(XgmiiIdle)).asUInt
      fcsOutputTxc0 := "b11000000".U
      fcsOutputTxc1 := "b11111111".U
      ifgOffset := 2.U
    }
    is(5.U) {
      fcsOutputTxd0 := Cat(XgmiiTerm, ~crcState(2), sTdataReg(23, 0))
      fcsOutputTxd1 := VecInit(Seq.fill(8)(XgmiiIdle)).asUInt
      fcsOutputTxc0 := "b10000000".U
      fcsOutputTxc1 := "b11111111".U
      ifgOffset := 1.U
    }
    is(4.U) {
      fcsOutputTxd0 := Cat(~crcState(3), sTdataReg(31, 0))
      fcsOutputTxd1 :=
        Cat(VecInit(Seq.fill(7)(XgmiiIdle)).asUInt, XgmiiTerm)
      fcsOutputTxc0 := "b00000000".U
      fcsOutputTxc1 := "b11111111".U
      ifgOffset := 8.U
    }
    is(3.U) {
      fcsOutputTxd0 := Cat(~crcState(4)(23, 0), sTdataReg(39, 0))
      fcsOutputTxd1 := Cat(
        VecInit(Seq.fill(6)(XgmiiIdle)).asUInt,
        XgmiiTerm,
        ~crcStateReg(4)(31, 24)
      )
      fcsOutputTxc0 := "b00000000".U
      fcsOutputTxc1 := "b11111110".U
      ifgOffset := 7.U
    }
    is(2.U) {
      fcsOutputTxd0 := Cat(~crcState(5)(15, 0), sTdataReg(47, 0))
      fcsOutputTxd1 := Cat(
        VecInit(Seq.fill(5)(XgmiiIdle)).asUInt,
        XgmiiTerm,
        ~crcStateReg(5)(31, 16)
      )
      fcsOutputTxc0 := "b00000000".U
      fcsOutputTxc1 := "b11111100".U
      ifgOffset := 6.U
    }
    is(1.U) {
      fcsOutputTxd0 := Cat(~crcState(6)(7, 0), sTdataReg(55, 0))
      fcsOutputTxd1 := Cat(
        VecInit(Seq.fill(4)(XgmiiIdle)).asUInt,
        XgmiiTerm,
        ~crcStateReg(6)(31, 8)
      )
      fcsOutputTxc0 := "b00000000".U
      fcsOutputTxc1 := "b11111000".U
      ifgOffset := 5.U
    }
    is(0.U) {
      fcsOutputTxd0 := sTdataReg
      fcsOutputTxd1 := Cat(
        VecInit(Seq.fill(3)(XgmiiIdle)).asUInt,
        XgmiiTerm,
        ~crcStateReg(7)
      )
      fcsOutputTxc0 := "b00000000".U
      fcsOutputTxc1 := "b11110000".U
      ifgOffset := 4.U
    }
  }

  // FSM Logic
  when(io.sAxisTx.tvalid && io.sAxisTx.tready) {
    frameNext := !io.sAxisTx.tlast
  }

  when(gbxIfEn.B && io.txGbxReqStall) {
    stateNext := stateReg
    frameStartNext := frameStartReg
    sAxisTxTreadyNext := sAxisTxTreadyReg
  }.otherwise {

    when(frameMinCountReg > ctrlW.U) {
      frameMinCountNext := frameMinCountReg - ctrlW.U
    }.otherwise {
      frameMinCountNext := 0.U
    }

    when(!frameLenReg(15, 3).andR) {
      frameLenNext := frameLenReg + ctrlW.U
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
        isMcastNext := sTdataReg(0)
        isBcastNext := sTdataReg(47, 0).andR
      }
      is(1.U) {
        is8021qNext :=
          Cat(sTdataReg(39, 32), sTdataReg(47, 40)) === "h8100".U
      }
    }

    when(ifgCntReg(7, 3) =/= 0.U) {
      ifgCntNext := ifgCntReg - ctrlW.U
    }.otherwise {
      ifgCntNext := 0.U
    }

    switch(stateReg) {
      is(StateIdle) {
        frameErrorNext := false.B
        frameOversizeNext := false.B
        frameMinCountNext := (minFrameLen - 4 - ctrlW).U
        hdrPtrNext := 0.U
        frameLenNext := 0.U

        val maxPktLim = io.cfgTxMaxPktLen - 5.U
        frameLenLimCycNext := maxPktLim(15, 3)
        frameLenLimLastNext := maxPktLim(2, 0)
        frameLenLimCheckNext := false.B
        resetCrc := true.B
        sAxisTxTreadyNext := io.cfgTxEnable

        sTdataNext := sAxisTxTdataMasked
        sEmptyNext := keep2empty(io.sAxisTx.tkeep)
        mAxisTxCplTagNext := io.sAxisTx.tid

        when(io.sAxisTx.tvalid && io.sAxisTx.tready) {
          xgmiiTxdNext :=
            Cat(EthSfd, VecInit(Seq.fill(6)(EthPre)).asUInt, XgmiiStart)
          xgmiiTxcNext := "b00000001".U
          frameStartNext := true.B
          sAxisTxTreadyNext := true.B
          stateNext := StatePayload
        }.otherwise {
          swapLanesNext := false.B
          ifgCountNext := 0.U
          deficitIdleCountNext := 0.U
          stateNext := StateIdle
        }
      }

      is(StatePayload) {
        updateCrc := true.B
        sAxisTxTreadyNext := true.B
        xgmiiTxdNext := sTdataReg
        xgmiiTxcNext := 0.U
        sTdataNext := sAxisTxTdataMasked
        sEmptyNext := keep2empty(io.sAxisTx.tkeep)
        statTxByteNext := ctrlW.U

        when(io.sAxisTx.tvalid && io.sAxisTx.tlast) {
          when(frameLenLimCheckReg) {
            when(frameLenLimLastReg 
              (7.U - keep2empty(io.sAxisTx.tkeep))) {
              frameOversizeNext := true.B
            }
          }
        }.otherwise {
          when(frameLenLimCheckReg) {
            frameOversizeNext := true.B
          }
        }

        when(paddingEn.B && frameMinCountReg =/= 0.U) {
          when(frameMinCountReg > ctrlW.U) {
            sEmptyNext := 0.U
          }.elsewhen(keep2empty(io.sAxisTx.tkeep) >
            (ctrlW.U - frameMinCountReg)) {
            sEmptyNext := ctrlW.U - frameMinCountReg
          }
        }

        when(!io.sAxisTx.tvalid || io.sAxisTx.tlast || frameOversizeNext) {
          sAxisTxTreadyNext := frameNext
          frameErrorNext :=
            !io.sAxisTx.tvalid || io.sAxisTx.tuser(0) || frameOversizeNext
          statTxErrUserNext := io.sAxisTx.tuser(0)
          statTxErrUnderflowNext := !io.sAxisTx.tvalid

          when(paddingEn.B && frameMinCountReg =/= 0.U &&
            frameMinCountReg > ctrlW.U) {
            stateNext := StatePad
          }.otherwise {
            when(frameErrorNext) {
              stateNext := StateErr
            }.otherwise {
              stateNext := StateFcs1
            }
          }
        }.otherwise {
          stateNext := StatePayload
        }
      }

      is(StatePad) {
        sAxisTxTreadyNext := frameNext
        xgmiiTxdNext := sTdataReg
        xgmiiTxcNext := 0.U
        sTdataNext := 0.U
        sEmptyNext := 0.U
        statTxByteNext := ctrlW.U
        updateCrc := true.B

        when(frameMinCountReg > ctrlW.U) {
          stateNext := StatePad
        }.otherwise {
          sEmptyNext := ctrlW.U - frameMinCountReg
          when(frameErrorReg) {
            stateNext := StateErr
          }.otherwise {
            stateNext := StateFcs1
          }
        }
      }

      is(StateFcs1) {
        sAxisTxTreadyNext := frameNext
        xgmiiTxdNext := fcsOutputTxd0
        xgmiiTxcNext := fcsOutputTxc0
        updateCrc := true.B

        val activeIfg = Mux(io.cfgTxIfg > 12.U, io.cfgTxIfg, 12.U)
        ifgCountNext := activeIfg - ifgOffset +
          Mux(swapLanesReg, 4.U, 0.U) + deficitIdleCountReg

        when(sEmptyReg <= 4.U) {
          statTxByteNext := ctrlW.U
          stateNext := StateFcs2
        }.otherwise {
          statTxByteNext := 12.U - sEmptyReg
          frameLenNext := frameLenReg + (12.U - sEmptyReg)
          statTxPktLenNext := frameLenNext
          statTxPktGoodNext := !frameErrorReg
          statTxPktBadNext := frameErrorReg
          statTxPktUcastNext := !isMcastReg
          statTxPktMcastNext := isMcastReg && !isBcastReg
          statTxPktBcastNext := isBcastReg
          statTxPktVlanNext := is8021qReg
          statTxErrOversizeNext := frameOversizeReg
          stateNext := StateIfg
        }
      }

      is(StateFcs2) {
        sAxisTxTreadyNext := frameNext
        xgmiiTxdNext := fcsOutputTxd1
        xgmiiTxcNext := fcsOutputTxc1

        statTxByteNext := 4.U - sEmptyReg
        frameLenNext := frameLenReg + (4.U - sEmptyReg)
        statTxPktLenNext := frameLenNext
        statTxPktGoodNext := !frameErrorReg
        statTxPktBadNext := frameErrorReg
        statTxPktUcastNext := !isMcastReg
        statTxPktMcastNext := isMcastReg && !isBcastReg
        statTxPktBcastNext := isBcastReg
        statTxPktVlanNext := is8021qReg
        statTxErrOversizeNext := frameOversizeReg

        val ifgTemp = ifgCountReg

        when(dicEn.B) {
          when(ifgTemp > 7.U) {
            stateNext := StateIfg
          }.otherwise {
            when(ifgTemp >= 4.U) {
              deficitIdleCountNext := ifgTemp - 4.U
              swapLanesNext := true.B
            }.otherwise {
              deficitIdleCountNext := ifgTemp(1, 0)
              ifgCountNext := 0.U
              swapLanesNext := false.B
            }
            sAxisTxTreadyNext := io.cfgTxEnable
            stateNext := StateIdle
          }
        }.otherwise {
          when(ifgTemp > 4.U) {
            stateNext := StateIfg
          }.otherwise {
            sAxisTxTreadyNext := io.cfgTxEnable
            swapLanesNext := ifgTemp =/= 0.U
            stateNext := StateIdle
          }
        }
      }

      is(StateErr) {
        sAxisTxTreadyNext := frameNext
        xgmiiTxdNext :=
          Cat(XgmiiTerm, VecInit(Seq.fill(7)(XgmiiError)).asUInt)
        xgmiiTxcNext := ((1 << ctrlW) - 1).U
        ifgCountNext := Mux(io.cfgTxIfg > 12.U, io.cfgTxIfg, 12.U)

        statTxPktLenNext := frameLenReg
        statTxPktGoodNext := !frameErrorReg
        statTxPktBadNext := frameErrorReg
        statTxPktUcastNext := !isMcastReg
        statTxPktMcastNext := isMcastReg && !isBcastReg
        statTxPktBcastNext := isBcastReg
        statTxPktVlanNext := is8021qReg
        statTxErrOversizeNext := frameOversizeReg
        stateNext := StateIfg
      }

      is(StateIfg) {
        sAxisTxTreadyNext := frameNext

        val ifgTemp = Mux(ifgCountReg > 8.U, ifgCountReg - 8.U, 0.U)
        ifgCountNext := ifgTemp

        when(dicEn.B) {
          when(ifgTemp > 7.U || frameReg) {
            stateNext := StateIfg
          }.otherwise {
            when(ifgTemp >= 4.U) {
              deficitIdleCountNext := ifgTemp - 4.U
              swapLanesNext := true.B
            }.otherwise {
              deficitIdleCountNext := ifgTemp(1, 0)
              ifgCountNext := 0.U
              swapLanesNext := false.B
            }
            sAxisTxTreadyNext := io.cfgTxEnable
            stateNext := StateIdle
          }
        }.otherwise {
          when(ifgTemp > 4.U || frameReg) {
            stateNext := StateIfg
          }.otherwise {
            sAxisTxTreadyNext := io.cfgTxEnable
            swapLanesNext := ifgTemp =/= 0.U
            stateNext := StateIdle
          }
        }
      }
    }
  }

  // Register assignments
  stateReg := stateNext
  swapLanesReg := swapLanesNext
  frameStartReg := frameStartNext
  frameReg := frameNext
  frameErrorReg := frameErrorNext
  frameOversizeReg := frameOversizeNext
  frameMinCountReg := frameMinCountNext
  hdrPtrReg := hdrPtrNext
  isMcastReg := isMcastNext
  isBcastReg := isBcastNext
  is8021qReg := is8021qNext
  frameLenReg := frameLenNext
  frameLenLimCycReg := frameLenLimCycNext
  frameLenLimLastReg := frameLenLimLastNext
  frameLenLimCheckReg := frameLenLimCheckNext
  ifgCntReg := ifgCntNext

  ifgCountReg := ifgCountNext
  deficitIdleCountReg := deficitIdleCountNext

  sTdataReg := sTdataNext
  sEmptyReg := sEmptyNext
  sAxisTxTreadyReg := sAxisTxTreadyNext

  mAxisTxCplTagReg := mAxisTxCplTagNext
  mAxisTxCplValidReg := false.B
  mAxisTxCplValidIntReg := false.B

  startPacketReg := 0.U

  statTxByteReg := statTxByteNext
  statTxPktLenReg := statTxPktLenNext
  statTxPktUcastReg := statTxPktUcastNext
  statTxPktMcastReg := statTxPktMcastNext
  statTxPktBcastReg := statTxPktBcastNext
  statTxPktVlanReg := statTxPktVlanNext
  statTxPktGoodReg := statTxPktGoodNext
  statTxPktBadReg := statTxPktBadNext
  statTxErrOversizeReg := statTxErrOversizeNext
  statTxErrUserReg := statTxErrUserNext
  statTxErrUnderflowReg := statTxErrUnderflowNext

  if (ptpTsEn && ptpTsFmtTod) {
    mAxisTxCplValidReg := mAxisTxCplValidIntReg
    mAxisTxCplTsAdjReg := Cat(
      mAxisTxCplTsReg(95, 48) + 1.U,
      0.U(2.W),
      (Cat(
        0.U(1.W),
        mAxisTxCplTsReg(45, 16)
      ).asSInt - 1000000000.S)(29, 0).asUInt,
      mAxisTxCplTsReg(15, 0)
    )
    mAxisTxCplTsBorrowReg :=
      (Cat(0.U(1.W), mAxisTxCplTsReg(45, 16)).asSInt - 1000000000.S)(31)
  }

  when(gbxIfEn.B && io.txGbxReqStall) {
    xgmiiTxValidReg := false.B
  }.otherwise {
    when(frameStartReg) {
      when(swapLanesReg) {
        if (ptpTsEn) {
          if (ptpTsFmtTod) {
            val ptpLow = io.ptpTs(45, 0) + (tsIncReg >> 1)
            mAxisTxCplTsReg := Cat(io.ptpTs(95, 48), ptpLow(45, 0))
          } else {
            mAxisTxCplTsReg := io.ptpTs + (tsIncReg >> 1)
          }
        }
        startPacketReg := "b10".U
      }.otherwise {
        if (ptpTsEn)
          mAxisTxCplTsReg := io.ptpTs
        startPacketReg := "b01".U
      }

      if (txCplCtrlInTuser) {
        if (ptpTsFmtTod)
          mAxisTxCplValidIntReg := (io.sAxisTx.tuser >> 1) === 0.U
        else
          mAxisTxCplValidReg := (io.sAxisTx.tuser >> 1) === 0.U
      } else {
        if (ptpTsFmtTod)
          mAxisTxCplValidIntReg := true.B
        else
          mAxisTxCplValidReg := true.B
      }
    }

    for (i <- 0 until 7) {
      crcStateReg(i) := crcState(i)
    }

    when(updateCrc) {
      crcStateReg(7) := crcState(7)
    }
    when(resetCrc) {
      crcStateReg(7) := "hffffffff".U(32.W)
    }

    swapTxdReg := xgmiiTxdNext(63, 32)
    swapTxcReg := xgmiiTxcNext(7, 4)

    when(swapLanesReg) {
      xgmiiTxdReg := Cat(xgmiiTxdNext(31, 0), swapTxdReg)
      xgmiiTxcReg := Cat(xgmiiTxcNext(3, 0), swapTxcReg)
    }.otherwise {
      xgmiiTxdReg := xgmiiTxdNext
      xgmiiTxcReg := xgmiiTxcNext
    }
    xgmiiTxValidReg := true.B
  }

  txGbxSyncReg := io.txGbxReqSync
  lastTsReg := io.ptpTs(19, 0)
  tsIncReg := io.ptpTs(19, 0) - lastTsReg
}

object Axis2Xgmii64 {
  def apply(p: Axis2Xgmii64Params): Axis2Xgmii64 = Module(new Axis2Xgmii64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW,
    txCplCtrlInTuser = p.txCplCtrlInTuser
  ))
}

object Main extends App {
  val MainClassName = "Mac"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  Axis2Xgmii64Params.SynConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Axis2Xgmii64(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        txCplCtrlInTuser = p.txCplCtrlInTuser
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
    YosysTclFile.create(
      mainClassName = MainClassName,
      outputDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      mainClassName = MainClassName,
      outputDir = s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      mainClassName = MainClassName,
      synConfigs = Axis2Xgmii64Params.SynConfigs,
      outputDir = s"${coreDir}/generated/synTestCases"
    )
  }
}