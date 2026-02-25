package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

object XgmiiDecoder {
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

  // 7-bit Encoded Control Codes
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

  def apply(p: XgmiiDecoderParams): XgmiiDecoder = Module(new XgmiiDecoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn
  ))
}

class XgmiiDecoder(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = false) extends Module {

  import XgmiiDecoder._

  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")

  val io = IO(new Bundle {
    val encodedRxData = Input(UInt(dataW.W))
    val encodedRxDataValid = Input(Bool())
    val encodedRxHdr = Input(UInt(hdrW.W))
    val encodedRxHdrValid = Input(Bool())

    val xgmiiRxd = Output(UInt(dataW.W))
    val xgmiiRxc = Output(UInt(ctrlW.W))
    val xgmiiRxValid = Output(Bool())

    val rxBadBlock = Output(Bool())
    val rxSequenceError = Output(Bool())
  })

  // Internal Constants
  val dataWInt = 64
  val ctrlWInt = 8
  val segCnt = dataWInt / dataW

  // Internal Signals
  val encodedRxDataInt = Wire(UInt(dataWInt.W))
  val encodedRxDataValidInt = Wire(Bool())
  val encodedRxHdrInt = Wire(UInt(hdrW.W))

  val decodedCtrl = Wire(Vec(8, UInt(8.W)))
  val decodeErr = Wire(Vec(8, Bool()))

  // Registers
  val xgmiiRxdReg = RegInit(0.U(dataWInt.W))
  val xgmiiRxcReg = RegInit(0.U(ctrlWInt.W))
  val xgmiiRxValidReg = RegInit(0.U(segCnt.W))
  val rxBadBlockReg = RegInit(false.B)
  val rxSequenceErrorReg = RegInit(false.B)
  val frameReg = RegInit(false.B)

  // Next State Wires
  val xgmiiRxdNext = Wire(UInt(dataWInt.W))
  val xgmiiRxcNext = Wire(UInt(ctrlWInt.W))
  val xgmiiRxValidNext = Wire(UInt(segCnt.W))
  val rxBadBlockNext = Wire(Bool())
  val rxSequenceErrorNext = Wire(Bool())
  val frameNext = Wire(Bool())

  // Output Assignments
  io.xgmiiRxd := xgmiiRxdReg(dataW - 1, 0)
  io.xgmiiRxc := xgmiiRxcReg(ctrlW - 1, 0)
  io.xgmiiRxValid := Mux(gbxIfEn.B, xgmiiRxValidReg(0), true.B)

  io.rxBadBlock := rxBadBlockReg
  io.rxSequenceError := rxSequenceErrorReg

  // -------------------------------------------------------------------------
  // Input Gearbox (Repack In)
  // -------------------------------------------------------------------------
  if (dataW == 64) {
    encodedRxDataInt := io.encodedRxData
    encodedRxDataValidInt := Mux(gbxIfEn.B, io.encodedRxDataValid, true.B)
    encodedRxHdrInt := io.encodedRxHdr
  } else {
    val encodedRxDataReg = RegInit(0.U((dataWInt - dataW).W))
    val encodedRxDataValidReg = RegInit(false.B)
    val encodedRxHdrReg = RegInit(0.U(hdrW.W))

    encodedRxDataInt := Cat(io.encodedRxData, encodedRxDataReg)
    encodedRxDataValidInt :=
      encodedRxDataValidReg && Mux(gbxIfEn.B, io.encodedRxDataValid, true.B)
    encodedRxHdrInt := encodedRxHdrReg

    when(!gbxIfEn.B || io.encodedRxDataValid) {
      encodedRxDataReg := io.encodedRxData
      encodedRxDataValidReg := io.encodedRxHdrValid
      when(io.encodedRxHdrValid) {
        encodedRxHdrReg := io.encodedRxHdr
      }
    }
  }

  // -------------------------------------------------------------------------
  // Main Combinational Logic
  // -------------------------------------------------------------------------

  // 1. Defaults (Error State)
  val errVec = VecInit(Seq.fill(ctrlWInt)(XgmiiError))
  xgmiiRxdNext := Cat(errVec.reverse)
  xgmiiRxcNext := -1.S(ctrlWInt.W).asUInt
  xgmiiRxValidNext := 0.U
  rxBadBlockNext := false.B
  rxSequenceErrorNext := false.B
  frameNext := frameReg

  // 2. Decode Control Codes Loop
  for (i <- 0 until ctrlWInt) {
    val encodedSlice = encodedRxDataInt(7 * i + 14, 7 * i + 8)

    decodedCtrl(i) := XgmiiError
    decodeErr(i) := true.B

    switch(encodedSlice) {
      is(CtrlIdle) {
        decodedCtrl(i) := XgmiiIdle
        decodeErr(i) := false.B
      }
      is(CtrlLpi) {
        decodedCtrl(i) := XgmiiLpi
        decodeErr(i) := false.B
      }
      is(CtrlError) {
        decodedCtrl(i) := XgmiiError
        decodeErr(i) := false.B
      }
      is(CtrlRes0) {
        decodedCtrl(i) := XgmiiRes0
        decodeErr(i) := false.B
      }
      is(CtrlRes1) {
        decodedCtrl(i) := XgmiiRes1
        decodeErr(i) := false.B
      }
      is(CtrlRes2) {
        decodedCtrl(i) := XgmiiRes2
        decodeErr(i) := false.B
      }
      is(CtrlRes3) {
        decodedCtrl(i) := XgmiiRes3
        decodeErr(i) := false.B
      }
      is(CtrlRes4) {
        decodedCtrl(i) := XgmiiRes4
        decodeErr(i) := false.B
      }
      is(CtrlRes5) {
        decodedCtrl(i) := XgmiiRes5
        decodeErr(i) := false.B
      }
    }
  }

  // 3. Repack Output
  if (segCnt > 1) {
    val rxdShifted = xgmiiRxdReg(dataWInt - 1, dataW)
    val rxcShifted = xgmiiRxcReg(ctrlWInt - 1, ctrlW)
    val validShifted = xgmiiRxValidReg(segCnt - 1, 1)

    val errPad = Fill(ctrlW, XgmiiError)
    val ctrlPad = Fill(ctrlW, 1.U(1.W))

    xgmiiRxdNext := Cat(errPad, rxdShifted)
    xgmiiRxcNext := Cat(ctrlPad, rxcShifted)
    xgmiiRxValidNext := Cat(0.U(1.W), validShifted)
  }

  // 4. Main Decoding Block
  when(!encodedRxDataValidInt) {
    // Wait for data
  }.elsewhen(encodedRxHdrInt(0) === 0.U) {
    xgmiiRxdNext := encodedRxDataInt
    xgmiiRxcNext := 0.U
    xgmiiRxValidNext := -1.S(segCnt.W).asUInt
    rxBadBlockNext := false.B
  }.otherwise {
    xgmiiRxValidNext := -1.S(segCnt.W).asUInt

    val blockTypeNibble = encodedRxDataInt(7, 4)

    switch(blockTypeNibble) {
      is(BlockTypeCtrl(7, 4)) {
        xgmiiRxdNext := decodedCtrl.asUInt
        xgmiiRxcNext := 0xff.U(8.W)
        rxBadBlockNext := decodeErr.asUInt =/= 0.U
      }
      is(BlockTypeOs4(7, 4)) {
        val rxdLow = Cat(decodedCtrl.take(4).reverse)
        val rxdHigh = encodedRxDataInt(63, 40)
        val osByte = Mux(
          encodedRxDataInt(39, 36) === OSeqOs,
          XgmiiSeqOs,
          XgmiiError
        )
        xgmiiRxdNext := Cat(rxdHigh, osByte, rxdLow)
        xgmiiRxcNext := 0x1f.U(8.W)
        when(encodedRxDataInt(39, 36) === OSeqOs) {
          rxBadBlockNext := Cat(decodeErr.take(4).reverse) =/= 0.U
        }.otherwise {
          rxBadBlockNext := true.B
        }
      }
      is(BlockTypeStart4(7, 4)) {
        val rxdLow = Cat(decodedCtrl.take(4).reverse)
        xgmiiRxdNext := Cat(encodedRxDataInt(63, 40), XgmiiStart, rxdLow)
        xgmiiRxcNext := 0x1f.U(8.W)
        rxBadBlockNext := Cat(decodeErr.take(4).reverse) =/= 0.U
        rxSequenceErrorNext := frameReg
        frameNext := true.B
      }
      is(BlockTypeOsStart(7, 4)) {
        val osByteLow = Mux(
          encodedRxDataInt(35, 32) === OSeqOs,
          XgmiiSeqOs,
          XgmiiError
        )
        val rxdLow = Cat(osByteLow, encodedRxDataInt(31, 8))
        val rxdHigh = Cat(encodedRxDataInt(63, 40), XgmiiStart)
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        xgmiiRxcNext := 0x11.U(8.W)
        rxBadBlockNext := encodedRxDataInt(35, 32) =/= OSeqOs
        rxSequenceErrorNext := frameReg
        frameNext := true.B
      }
      is(BlockTypeOs04(7, 4)) {
        val osByteLow = Mux(
          encodedRxDataInt(35, 32) === OSeqOs,
          XgmiiSeqOs,
          XgmiiError
        )
        val rxdLow = Cat(osByteLow, encodedRxDataInt(31, 8))
        val osByteMid = Mux(
          encodedRxDataInt(39, 36) === OSeqOs,
          XgmiiSeqOs,
          XgmiiError
        )
        val rxdHigh = Cat(encodedRxDataInt(63, 40), osByteMid)
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        xgmiiRxcNext := 0x11.U(8.W)
        rxBadBlockNext :=
          (encodedRxDataInt(35, 32) =/= OSeqOs) ||
            (encodedRxDataInt(39, 36) =/= OSeqOs)
      }
      is(BlockTypeStart0(7, 4)) {
        xgmiiRxdNext := Cat(encodedRxDataInt(63, 8), XgmiiStart)
        xgmiiRxcNext := 0x01.U(8.W)
        rxBadBlockNext := false.B
        rxSequenceErrorNext := frameReg
        frameNext := true.B
      }
      is(BlockTypeOs0(7, 4)) {
        val osByteLow = Mux(
          encodedRxDataInt(35, 32) === OSeqOs,
          XgmiiSeqOs,
          XgmiiError
        )
        val rxdLow = Cat(osByteLow, encodedRxDataInt(31, 8))
        val rxdHigh = Cat(
          decodedCtrl(7),
          decodedCtrl(6),
          decodedCtrl(5),
          decodedCtrl(4)
        )
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        xgmiiRxcNext := 0xf1.U(8.W)
        rxBadBlockNext :=
          (encodedRxDataInt(35, 32) =/= OSeqOs) ||
            (Cat(decodeErr(7), decodeErr(6), decodeErr(5), decodeErr(4)) =/=
              0.U)
      }
      is(BlockTypeTerm0(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(1).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm)
        xgmiiRxcNext := 0xff.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(1).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm1(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(2).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm, encodedRxDataInt(15, 8))
        xgmiiRxcNext := 0xfe.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(2).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm2(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(3).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm, encodedRxDataInt(23, 8))
        xgmiiRxcNext := 0xfc.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(3).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm3(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(4).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm, encodedRxDataInt(31, 8))
        xgmiiRxcNext := 0xf8.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(4).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm4(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(5).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm, encodedRxDataInt(39, 8))
        xgmiiRxcNext := 0xf0.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(5).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm5(7, 4)) {
        val rxdHigh = Cat(decodedCtrl.drop(6).reverse)
        xgmiiRxdNext := Cat(rxdHigh, XgmiiTerm, encodedRxDataInt(47, 8))
        xgmiiRxcNext := 0xe0.U(8.W)
        rxBadBlockNext := Cat(decodeErr.drop(6).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm6(7, 4)) {
        xgmiiRxdNext := Cat(decodedCtrl(7), XgmiiTerm, encodedRxDataInt(55, 8))
        xgmiiRxcNext := 0xc0.U(8.W)
        rxBadBlockNext := decodeErr(7)
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
      is(BlockTypeTerm7(7, 4)) {
        xgmiiRxdNext := Cat(XgmiiTerm, encodedRxDataInt(63, 8))
        xgmiiRxcNext := 0x80.U(8.W)
        rxBadBlockNext := false.B
        rxSequenceErrorNext := !frameReg
        frameNext := false.B
      }
    }
  }

  // 5. Final Validation Check
  when(!encodedRxDataValidInt) {
    // wait for block
  }.elsewhen(encodedRxHdrInt === SyncData) {
    // data - nothing encoded
  }.elsewhen(encodedRxHdrInt === SyncCtrl) {
    val bt = encodedRxDataInt(7, 0)
    val validBlockType =
      bt === BlockTypeCtrl ||
        bt === BlockTypeOs4 ||
        bt === BlockTypeStart4 ||
        bt === BlockTypeOsStart ||
        bt === BlockTypeOs04 ||
        bt === BlockTypeStart0 ||
        bt === BlockTypeOs0 ||
        bt === BlockTypeTerm0 ||
        bt === BlockTypeTerm1 ||
        bt === BlockTypeTerm2 ||
        bt === BlockTypeTerm3 ||
        bt === BlockTypeTerm4 ||
        bt === BlockTypeTerm5 ||
        bt === BlockTypeTerm6 ||
        bt === BlockTypeTerm7

    when(!validBlockType) {
      xgmiiRxdNext := Cat(errVec.reverse)
      xgmiiRxcNext := -1.S(ctrlWInt.W).asUInt
      rxBadBlockNext := true.B
    }
  }.otherwise {
    xgmiiRxdNext := Cat(errVec.reverse)
    xgmiiRxcNext := -1.S(ctrlWInt.W).asUInt
    rxBadBlockNext := true.B
  }

  // 6. Sequential Updates
  xgmiiRxdReg := xgmiiRxdNext
  xgmiiRxcReg := xgmiiRxcNext
  xgmiiRxValidReg := xgmiiRxValidNext
  rxBadBlockReg := rxBadBlockNext
  rxSequenceErrorReg := rxSequenceErrorNext
  frameReg := frameNext
}

// object Main extends App {
//   val MainClassName = "Pcs"
//   val coreDir = s"modules/${MainClassName.toLowerCase()}"
//   XgmiiDecoderParams.SynConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new XgmiiDecoder(
//         dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn
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
//     RunScriptFile.create(MainClassName, XgmiiDecoderParams.SynConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }
