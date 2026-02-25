package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._

object XgmiiEncoder {
  // XGMII Control Codes
  val XgmiiIdle = 0x07.U(8.W)
  val XgmiiLpi = 0x06.U(8.W)
  val XgmiiStart = 0xfb.U(8.W)
  val XgmiiTerm = 0xfd.U(8.W)
  val XgmiiError = 0xfe.U(8.W)
  val XgmiiSeqOs = 0x9c.U(8.W)
  val XgmiiRes0 = 0x1c.U(8.W)
  val XgmiiRes1 = 0x3c.U(8.W)
  val XgmiiRes2 = 0x7c.U(8.W)
  val XgmiiRes3 = 0xbc.U(8.W)
  val XgmiiRes4 = 0xdc.U(8.W)
  val XgmiiRes5 = 0xf7.U(8.W)
  val XgmiiSigOs = 0x5c.U(8.W)

  // 10GBASE-R Control Codes
  val CtrlIdle = 0x00.U(7.W)
  val CtrlLpi = 0x06.U(7.W)
  val CtrlError = 0x1e.U(7.W)
  val CtrlRes0 = 0x2d.U(7.W)
  val CtrlRes1 = 0x33.U(7.W)
  val CtrlRes2 = 0x4b.U(7.W)
  val CtrlRes3 = 0x55.U(7.W)
  val CtrlRes4 = 0x66.U(7.W)
  val CtrlRes5 = 0x78.U(7.W)

  val OSeqOs = 0x0.U(4.W)
  val OSigOs = 0xf.U(4.W)

  val SyncData = "b10".U(2.W)
  val SyncCtrl = "b01".U(2.W)

  // Block Types
  val BlockTypeCtrl = 0x1e.U(8.W)
  val BlockTypeOs4 = 0x2d.U(8.W)
  val BlockTypeStart4 = 0x33.U(8.W)
  val BlockTypeOsStart = 0x66.U(8.W)
  val BlockTypeOs04 = 0x55.U(8.W)
  val BlockTypeStart0 = 0x78.U(8.W)
  val BlockTypeOs0 = 0x4b.U(8.W)
  val BlockTypeTerm0 = 0x87.U(8.W)
  val BlockTypeTerm1 = 0x99.U(8.W)
  val BlockTypeTerm2 = 0xaa.U(8.W)
  val BlockTypeTerm3 = 0xb4.U(8.W)
  val BlockTypeTerm4 = 0xcc.U(8.W)
  val BlockTypeTerm5 = 0xd2.U(8.W)
  val BlockTypeTerm6 = 0xe1.U(8.W)
  val BlockTypeTerm7 = 0xff.U(8.W)

  def apply(p: XgmiiEncoderParams): XgmiiEncoder = Module(new XgmiiEncoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt
  ))
}

class XgmiiEncoder(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = false,
    val gbxCnt: Int = 1) extends Module {

  import XgmiiEncoder._

  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")

  val io = IO(new Bundle {
    val xgmiiTxd = Input(UInt(dataW.W))
    val xgmiiTxc = Input(UInt(ctrlW.W))
    val xgmiiTxValid = Input(Bool())
    val txGbxSyncIn = Input(UInt(gbxCnt.W))

    val encodedTxData = Output(UInt(dataW.W))
    val encodedTxDataValid = Output(Bool())
    val encodedTxHdr = Output(UInt(hdrW.W))
    val encodedTxHdrValid = Output(Bool())
    val txGbxSyncOut = Output(UInt(gbxCnt.W))

    val txBadBlock = Output(Bool())
  })

  // Internal Constants
  val dataWInt = 64
  val ctrlWInt = 8
  val useHdrVld = gbxIfEn || dataW != 64
  val segCnt = dataWInt / dataW

  // --- Registers ---
  val encodedTxDataReg = RegInit(0.U(dataWInt.W))
  val encodedTxDataValidReg = RegInit(0.U(segCnt.W))
  val encodedTxHdrReg = RegInit(0.U(hdrW.W))
  val encodedTxHdrValidReg = RegInit(false.B)
  val txGbxSyncReg = RegInit(0.U(gbxCnt.W))
  val txBadBlockReg = RegInit(false.B)

  // Next state wires
  val encodedTxDataNext = Wire(UInt(dataWInt.W))
  val encodedTxDataValidNext = Wire(UInt(segCnt.W))
  val encodedTxHdrNext = Wire(UInt(hdrW.W))
  val encodedTxHdrValidNext = Wire(Bool())
  val txGbxSyncNext = Wire(UInt(gbxCnt.W))
  val txBadBlockNext = Wire(Bool())

  // --- Input Repacking Logic ---
  val xgmiiTxdInt = Wire(UInt(dataWInt.W))
  val xgmiiTxcInt = Wire(UInt(ctrlWInt.W))
  val xgmiiTxValidInt = Wire(Bool())

  if (dataW == 64) {
    xgmiiTxdInt := io.xgmiiTxd
    xgmiiTxcInt := io.xgmiiTxc
    xgmiiTxValidInt := io.xgmiiTxValid
  } else {
    val xgmiiTxdReg = RegInit(0.U((dataWInt - dataW).W))
    val xgmiiTxcReg = RegInit(0.U((ctrlWInt - ctrlW).W))
    val xgmiiTxValidReg = RegInit(false.B)

    xgmiiTxdInt := Cat(io.xgmiiTxd, xgmiiTxdReg)
    xgmiiTxcInt := Cat(io.xgmiiTxc, xgmiiTxcReg)

    val validPulse =
      if (gbxIfEn)
        io.xgmiiTxValid
      else
        true.B
    xgmiiTxValidInt := xgmiiTxValidReg && validPulse

    when(!gbxIfEn.B || io.xgmiiTxValid) {
      xgmiiTxdReg := io.xgmiiTxd
      xgmiiTxcReg := io.xgmiiTxc
      xgmiiTxValidReg := !xgmiiTxValidReg

      if (gbxIfEn) {
        when(io.txGbxSyncIn(0)) {
          xgmiiTxValidReg := false.B
        }
      }
    }
  }

  // --- Control Code Encoding ---
  val encodedCtrlVec = Wire(Vec(ctrlWInt, UInt(7.W)))
  val encodeErrVec = Wire(Vec(ctrlWInt, Bool()))

  val xgmiiTxdBytes = Wire(Vec(8, UInt(8.W)))
  val xgmiiTxcBits = Wire(Vec(8, Bool()))

  for (i <- 0 until 8) {
    xgmiiTxdBytes(i) := xgmiiTxdInt(i * 8 + 7, i * 8)
    xgmiiTxcBits(i) := xgmiiTxcInt(i)
  }

  for (i <- 0 until ctrlWInt) {
    when(xgmiiTxcBits(i)) {
      encodeErrVec(i) := false.B
      encodedCtrlVec(i) := CtrlError

      switch(xgmiiTxdBytes(i)) {
        is(XgmiiIdle) { encodedCtrlVec(i) := CtrlIdle }
        is(XgmiiLpi) { encodedCtrlVec(i) := CtrlLpi }
        is(XgmiiError) { encodedCtrlVec(i) := CtrlError }
        is(XgmiiRes0) { encodedCtrlVec(i) := CtrlRes0 }
        is(XgmiiRes1) { encodedCtrlVec(i) := CtrlRes1 }
        is(XgmiiRes2) { encodedCtrlVec(i) := CtrlRes2 }
        is(XgmiiRes3) { encodedCtrlVec(i) := CtrlRes3 }
        is(XgmiiRes4) { encodedCtrlVec(i) := CtrlRes4 }
        is(XgmiiRes5) { encodedCtrlVec(i) := CtrlRes5 }
        is(XgmiiSigOs) {
          encodedCtrlVec(i) := CtrlError
          encodeErrVec(i) := true.B
        }
      }

      val knownCodes = Seq(
        XgmiiIdle,
        XgmiiLpi,
        XgmiiError,
        XgmiiRes0,
        XgmiiRes1,
        XgmiiRes2,
        XgmiiRes3,
        XgmiiRes4,
        XgmiiRes5
      )
      val isKnown = knownCodes.map(c => xgmiiTxdBytes(i) === c).reduce(_ || _)
      when(!isKnown) {
        encodedCtrlVec(i) := CtrlError
        encodeErrVec(i) := true.B
      }

    }.otherwise {
      encodedCtrlVec(i) := CtrlError
      encodeErrVec(i) := true.B
    }
  }

  val encodedCtrlFlat = Cat(encodedCtrlVec.reverse)
  val encodeErrFlat = Cat(encodeErrVec.reverse)

  def ctrlSlice(
      high: Int,
      low: Int
    ): UInt =
    Cat((low to high).map(i => encodedCtrlVec(i)).reverse)

  // --- Main Combinatorial Logic ---
  encodedTxDataNext := Cat(Fill(ctrlWInt, CtrlError), BlockTypeCtrl)
  encodedTxDataValidNext := 0.U
  encodedTxHdrNext := SyncCtrl
  encodedTxHdrValidNext := false.B
  txGbxSyncNext := 0.U
  txBadBlockNext := false.B

  if (segCnt > 1) {
    val upperHalf = encodedTxDataReg(dataWInt - 1, dataW)
    encodedTxDataNext := Cat(0.U(dataW.W), upperHalf)
    encodedTxDataValidNext :=
      Cat(false.B, encodedTxDataValidReg(segCnt - 1, 1))
    encodedTxHdrNext := 0.U
    encodedTxHdrValidNext := false.B
  }

  when(xgmiiTxValidInt) {
    encodedTxDataValidNext := Fill(segCnt, 1.U)
    encodedTxHdrValidNext := true.B

    when(xgmiiTxcInt === 0.U) {
      encodedTxDataNext := xgmiiTxdInt
      encodedTxHdrNext := SyncData
      txBadBlockNext := false.B
    }.otherwise {
      encodedTxHdrNext := SyncCtrl
      val d = xgmiiTxdBytes
      val c = xgmiiTxcInt

      when(c === 0x1f.U && d(4) === XgmiiSeqOs) {
        encodedTxDataNext :=
          Cat(d(7), d(6), d(5), OSeqOs, ctrlSlice(3, 0), BlockTypeOs4)
        txBadBlockNext :=
          encodeErrVec(0) || encodeErrVec(1) ||
            encodeErrVec(2) || encodeErrVec(3)
      }.elsewhen(c === 0x1f.U && d(4) === XgmiiStart) {
        encodedTxDataNext :=
          Cat(d(7), d(6), d(5), 0.U(4.W), ctrlSlice(3, 0), BlockTypeStart4)
        txBadBlockNext :=
          encodeErrVec(0) || encodeErrVec(1) ||
            encodeErrVec(2) || encodeErrVec(3)
      }.elsewhen(c === 0x11.U && d(0) === XgmiiSeqOs && d(4) === XgmiiStart) {
        encodedTxDataNext := Cat(
          d(7),
          d(6),
          d(5),
          0.U(4.W),
          OSeqOs,
          d(3),
          d(2),
          d(1),
          BlockTypeOsStart
        )
        txBadBlockNext := false.B
      }.elsewhen(c === 0x11.U && d(0) === XgmiiSeqOs && d(4) === XgmiiSeqOs) {
        encodedTxDataNext := Cat(
          d(7),
          d(6),
          d(5),
          OSeqOs,
          OSeqOs,
          d(3),
          d(2),
          d(1),
          BlockTypeOs04
        )
        txBadBlockNext := false.B
      }.elsewhen(c === 0x01.U && d(0) === XgmiiStart) {
        encodedTxDataNext := Cat(xgmiiTxdInt(63, 8), BlockTypeStart0)
        txBadBlockNext := false.B
      }.elsewhen(c === 0xf1.U && d(0) === XgmiiSeqOs) {
        encodedTxDataNext :=
          Cat(ctrlSlice(7, 4), OSeqOs, d(3), d(2), d(1), BlockTypeOs0)
        txBadBlockNext :=
          encodeErrVec(4) || encodeErrVec(5) ||
            encodeErrVec(6) || encodeErrVec(7)
      }.elsewhen(c === 0xff.U && d(0) === XgmiiTerm) {
        encodedTxDataNext :=
          Cat(ctrlSlice(7, 1), 0.U(7.W), BlockTypeTerm0)
        txBadBlockNext := encodeErrFlat(7, 1) =/= 0.U
      }.elsewhen(c === 0xfe.U && d(1) === XgmiiTerm) {
        encodedTxDataNext :=
          Cat(ctrlSlice(7, 2), 0.U(6.W), d(0), BlockTypeTerm1)
        txBadBlockNext := encodeErrFlat(7, 2) =/= 0.U
      }.elsewhen(c === 0xfc.U && d(2) === XgmiiTerm) {
        encodedTxDataNext := Cat(
          ctrlSlice(7, 3),
          0.U(5.W),
          xgmiiTxdInt(15, 0),
          BlockTypeTerm2
        )
        txBadBlockNext := encodeErrFlat(7, 3) =/= 0.U
      }.elsewhen(c === 0xf8.U && d(3) === XgmiiTerm) {
        encodedTxDataNext := Cat(
          ctrlSlice(7, 4),
          0.U(4.W),
          xgmiiTxdInt(23, 0),
          BlockTypeTerm3
        )
        txBadBlockNext := encodeErrFlat(7, 4) =/= 0.U
      }.elsewhen(c === 0xf0.U && d(4) === XgmiiTerm) {
        encodedTxDataNext := Cat(
          ctrlSlice(7, 5),
          0.U(3.W),
          xgmiiTxdInt(31, 0),
          BlockTypeTerm4
        )
        txBadBlockNext := encodeErrFlat(7, 5) =/= 0.U
      }.elsewhen(c === 0xe0.U && d(5) === XgmiiTerm) {
        encodedTxDataNext := Cat(
          ctrlSlice(7, 6),
          0.U(2.W),
          xgmiiTxdInt(39, 0),
          BlockTypeTerm5
        )
        txBadBlockNext := encodeErrFlat(7, 6) =/= 0.U
      }.elsewhen(c === 0xc0.U && d(6) === XgmiiTerm) {
        encodedTxDataNext := Cat(
          ctrlSlice(7, 7),
          0.U(1.W),
          xgmiiTxdInt(47, 0),
          BlockTypeTerm6
        )
        txBadBlockNext := encodeErrVec(7)
      }.elsewhen(c === 0x80.U && d(7) === XgmiiTerm) {
        encodedTxDataNext := Cat(xgmiiTxdInt(55, 0), BlockTypeTerm7)
        txBadBlockNext := false.B
      }.elsewhen(c === 0xff.U) {
        encodedTxDataNext := Cat(encodedCtrlFlat, BlockTypeCtrl)
        txBadBlockNext := encodeErrFlat =/= 0.U
      }.otherwise {
        encodedTxDataNext := Cat(Fill(ctrlWInt, CtrlError), BlockTypeCtrl)
        txBadBlockNext := true.B
      }
    }
  }

  if (gbxIfEn) {
    when(!xgmiiTxValidInt) {
      txBadBlockNext := false.B
    }
  }

  txGbxSyncNext := io.txGbxSyncIn

  encodedTxDataReg := encodedTxDataNext
  encodedTxDataValidReg := encodedTxDataValidNext
  encodedTxHdrReg := encodedTxHdrNext
  encodedTxHdrValidReg := encodedTxHdrValidNext
  txGbxSyncReg := txGbxSyncNext
  txBadBlockReg := txBadBlockNext

  io.encodedTxData := encodedTxDataReg(dataW - 1, 0)

  if (gbxIfEn) {
    io.encodedTxDataValid := encodedTxDataValidReg(0)
    io.txGbxSyncOut := txGbxSyncReg
  } else {
    io.encodedTxDataValid := true.B
    io.txGbxSyncOut := 0.U
  }

  io.encodedTxHdr := encodedTxHdrReg

  if (useHdrVld) {
    io.encodedTxHdrValid := encodedTxHdrValidReg
  } else {
    io.encodedTxHdrValid := true.B
  }

  io.txBadBlock := txBadBlockReg
}

// object Main extends App {
//   val MainClassName = "Pcs"
//   val coreDir = s"modules/${MainClassName.toLowerCase()}"
//   XgmiiEncoderParams.SynConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new XgmiiEncoder(
//         dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn, gbxCnt = p.gbxCnt
//       ),
//       firtoolOpts = Array(
//         "--lowering-options=disallowLocalVariables,disallowPackedArrays",
//         "--disable-all-randomization",
//         "--strip-debug-info",
//         "--split-verilog",
//         s"-o=${coreDir}/generated/synTestCases/$configName"
//       )
//     )
//     SdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(MainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(MainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(MainClassName, XgmiiEncoderParams.SynConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }
