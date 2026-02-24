package org.chiselware.cores.o01.t001.pcs.tx
import chisel3._
import chisel3.util._

class XgmiiEncoder(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val hdrW: Int = 2,
    val gbxIfEn: Boolean = false,
    val gbxCnt: Int = 1) extends Module {

  // Parameter validations
  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")

  val io = IO(new Bundle {
    val xgmii_txd = Input(UInt(dataW.W))
    val xgmii_txc = Input(UInt(ctrlW.W))
    val xgmii_tx_valid = Input(Bool())
    val tx_gbx_sync_in = Input(UInt(gbxCnt.W))

    val encoded_tx_data = Output(UInt(dataW.W))
    val encoded_tx_data_valid = Output(Bool())
    val encoded_tx_hdr = Output(UInt(hdrW.W))
    val encoded_tx_hdr_valid = Output(Bool())
    val tx_gbx_sync_out = Output(UInt(gbxCnt.W))

    val tx_bad_block = Output(Bool())
  })

  // Internal Constants
  val dataWInt = 64
  val ctrlWInt = 8
  val useHdrVld = gbxIfEn || dataW != 64
  val segCnt = dataWInt / dataW

  // XGMII Control Codes
  val XGMII_IDLE = 0x07.U(8.W)
  val XGMII_LPI = 0x06.U(8.W)
  val XGMII_START = 0xfb.U(8.W)
  val XGMII_TERM = 0xfd.U(8.W)
  val XGMII_ERROR = 0xfe.U(8.W)
  val XGMII_SEQ_OS = 0x9c.U(8.W)
  val XGMII_RES_0 = 0x1c.U(8.W)
  val XGMII_RES_1 = 0x3c.U(8.W)
  val XGMII_RES_2 = 0x7c.U(8.W)
  val XGMII_RES_3 = 0xbc.U(8.W)
  val XGMII_RES_4 = 0xdc.U(8.W)
  val XGMII_RES_5 = 0xf7.U(8.W)
  val XGMII_SIG_OS = 0x5c.U(8.W)

  // 10GBASE-R Control Codes
  val CTRL_IDLE = 0x00.U(7.W)
  val CTRL_LPI = 0x06.U(7.W)
  val CTRL_ERROR = 0x1e.U(7.W)
  val CTRL_RES_0 = 0x2d.U(7.W)
  val CTRL_RES_1 = 0x33.U(7.W)
  val CTRL_RES_2 = 0x4b.U(7.W)
  val CTRL_RES_3 = 0x55.U(7.W)
  val CTRL_RES_4 = 0x66.U(7.W)
  val CTRL_RES_5 = 0x78.U(7.W)

  val O_SEQ_OS = 0x0.U(4.W)
  val O_SIG_OS = 0xf.U(4.W)

  val SYNC_DATA = "b10".U(2.W)
  val SYNC_CTRL = "b01".U(2.W)

  // Block Types
  val BLOCK_TYPE_CTRL = 0x1e.U(8.W)
  val BLOCK_TYPE_OS_4 = 0x2d.U(8.W)
  val BLOCK_TYPE_START_4 = 0x33.U(8.W)
  val BLOCK_TYPE_OS_START = 0x66.U(8.W)
  val BLOCK_TYPE_OS_04 = 0x55.U(8.W)
  val BLOCK_TYPE_START_0 = 0x78.U(8.W)
  val BLOCK_TYPE_OS_0 = 0x4b.U(8.W)
  val BLOCK_TYPE_TERM_0 = 0x87.U(8.W)
  val BLOCK_TYPE_TERM_1 = 0x99.U(8.W)
  val BLOCK_TYPE_TERM_2 = 0xaa.U(8.W)
  val BLOCK_TYPE_TERM_3 = 0xb4.U(8.W)
  val BLOCK_TYPE_TERM_4 = 0xcc.U(8.W)
  val BLOCK_TYPE_TERM_5 = 0xd2.U(8.W)
  val BLOCK_TYPE_TERM_6 = 0xe1.U(8.W)
  val BLOCK_TYPE_TERM_7 = 0xff.U(8.W)

  // --- Registers ---
  val encoded_tx_data_reg = RegInit(0.U(dataWInt.W))
  val encoded_tx_data_valid_reg = RegInit(0.U(segCnt.W))
  val encoded_tx_hdr_reg = RegInit(0.U(hdrW.W))
  val encoded_tx_hdr_valid_reg = RegInit(false.B)
  val tx_gbx_sync_reg = RegInit(0.U(gbxCnt.W))
  val tx_bad_block_reg = RegInit(false.B)

  // Next state wires
  val encoded_tx_data_next = Wire(UInt(dataWInt.W))
  val encoded_tx_data_valid_next = Wire(UInt(segCnt.W))
  val encoded_tx_hdr_next = Wire(UInt(hdrW.W))
  val encoded_tx_hdr_valid_next = Wire(Bool())
  val tx_gbx_sync_next = Wire(UInt(gbxCnt.W))
  val tx_bad_block_next = Wire(Bool())

  // --- Input Repacking Logic ---
  val xgmii_txd_int = Wire(UInt(dataWInt.W))
  val xgmii_txc_int = Wire(UInt(ctrlWInt.W))
  val xgmii_tx_valid_int = Wire(Bool())

  if (dataW == 64) {
    xgmii_txd_int := io.xgmii_txd
    xgmii_txc_int := io.xgmii_txc
    xgmii_tx_valid_int := io.xgmii_tx_valid
  } else {
    // 32-bit accumulation logic
    val xgmii_txd_reg = RegInit(0.U((dataWInt - dataW).W))
    val xgmii_txc_reg = RegInit(0.U((ctrlWInt - ctrlW).W))
    val xgmii_tx_valid_reg = RegInit(false.B)

    xgmii_txd_int := Cat(io.xgmii_txd, xgmii_txd_reg)
    xgmii_txc_int := Cat(io.xgmii_txc, xgmii_txc_reg)

    val valid_pulse =
      if (gbxIfEn)
        io.xgmii_tx_valid
      else
        true.B
    xgmii_tx_valid_int := xgmii_tx_valid_reg && valid_pulse

    when(!gbxIfEn.B || io.xgmii_tx_valid) {
      xgmii_txd_reg := io.xgmii_txd
      xgmii_txc_reg := io.xgmii_txc
      xgmii_tx_valid_reg := !xgmii_tx_valid_reg

      if (gbxIfEn) {
        when(io.tx_gbx_sync_in(0)) {
          xgmii_tx_valid_reg := false.B
        }
      }
    }
  }

  // --- Control Code Encoding ---
  val encoded_ctrl_vec = Wire(Vec(ctrlWInt, UInt(7.W)))
  val encode_err_vec = Wire(Vec(ctrlWInt, Bool()))

  val xgmii_txd_bytes = Wire(Vec(8, UInt(8.W)))
  val xgmii_txc_bits = Wire(Vec(8, Bool()))

  for (i <- 0 until 8) {
    xgmii_txd_bytes(i) := xgmii_txd_int(i * 8 + 7, i * 8)
    xgmii_txc_bits(i) := xgmii_txc_int(i)
  }

  for (i <- 0 until ctrlWInt) {
    when(xgmii_txc_bits(i)) {
      encode_err_vec(i) := false.B
      encoded_ctrl_vec(i) := CTRL_ERROR

      switch(xgmii_txd_bytes(i)) {
        is(XGMII_IDLE) { encoded_ctrl_vec(i) := CTRL_IDLE }
        is(XGMII_LPI) { encoded_ctrl_vec(i) := CTRL_LPI }
        is(XGMII_ERROR) { encoded_ctrl_vec(i) := CTRL_ERROR }
        is(XGMII_RES_0) { encoded_ctrl_vec(i) := CTRL_RES_0 }
        is(XGMII_RES_1) { encoded_ctrl_vec(i) := CTRL_RES_1 }
        is(XGMII_RES_2) { encoded_ctrl_vec(i) := CTRL_RES_2 }
        is(XGMII_RES_3) { encoded_ctrl_vec(i) := CTRL_RES_3 }
        is(XGMII_RES_4) { encoded_ctrl_vec(i) := CTRL_RES_4 }
        is(XGMII_RES_5) { encoded_ctrl_vec(i) := CTRL_RES_5 }
        is(XGMII_SIG_OS) {
          encoded_ctrl_vec(i) := CTRL_ERROR
          encode_err_vec(i) := true.B
        }
      }

      val known_codes = Seq(
        XGMII_IDLE,
        XGMII_LPI,
        XGMII_ERROR,
        XGMII_RES_0,
        XGMII_RES_1,
        XGMII_RES_2,
        XGMII_RES_3,
        XGMII_RES_4,
        XGMII_RES_5
      )
      val is_known = known_codes.map(c => xgmii_txd_bytes(i) === c).reduce(_ ||
        _)
      when(!is_known) {
        encoded_ctrl_vec(i) := CTRL_ERROR
        encode_err_vec(i) := true.B
      }

    }.otherwise {
      encoded_ctrl_vec(i) := CTRL_ERROR
      encode_err_vec(i) := true.B
    }
  }

  val encoded_ctrl_flat = Cat(encoded_ctrl_vec.reverse)
  val encode_err_flat = Cat(encode_err_vec.reverse)

  def ctrlSlice(
      high: Int,
      low: Int
    ): UInt = {
    Cat((low to high).map(i => encoded_ctrl_vec(i)).reverse)
  }

  // --- Main Combinatorial Logic ---
  encoded_tx_data_next := Cat(Fill(ctrlWInt, CTRL_ERROR), BLOCK_TYPE_CTRL)
  encoded_tx_data_valid_next := 0.U
  encoded_tx_hdr_next := SYNC_CTRL
  encoded_tx_hdr_valid_next := false.B
  tx_gbx_sync_next := 0.U
  tx_bad_block_next := false.B

  if (segCnt > 1) {
    val upper_half = encoded_tx_data_reg(dataWInt - 1, dataW)
    encoded_tx_data_next := Cat(0.U(dataW.W), upper_half)
    encoded_tx_data_valid_next :=
      Cat(false.B, encoded_tx_data_valid_reg(segCnt - 1, 1))
    encoded_tx_hdr_next := 0.U
    encoded_tx_hdr_valid_next := false.B
  }

  when(xgmii_tx_valid_int) {
    encoded_tx_data_valid_next := Fill(segCnt, 1.U)
    encoded_tx_hdr_valid_next := true.B

    when(xgmii_txc_int === 0.U) {
      encoded_tx_data_next := xgmii_txd_int
      encoded_tx_hdr_next := SYNC_DATA
      tx_bad_block_next := false.B
    }.otherwise {
      encoded_tx_hdr_next := SYNC_CTRL
      val d = xgmii_txd_bytes
      val c = xgmii_txc_int

      when(c === 0x1f.U && d(4) === XGMII_SEQ_OS) {
        encoded_tx_data_next :=
          Cat(d(7), d(6), d(5), O_SEQ_OS, ctrlSlice(3, 0), BLOCK_TYPE_OS_4)
        tx_bad_block_next := encode_err_vec(0) || encode_err_vec(1) ||
          encode_err_vec(2) || encode_err_vec(3)
      }.elsewhen(c === 0x1f.U && d(4) === XGMII_START) {
        encoded_tx_data_next :=
          Cat(d(7), d(6), d(5), 0.U(4.W), ctrlSlice(3, 0), BLOCK_TYPE_START_4)
        tx_bad_block_next := encode_err_vec(0) || encode_err_vec(1) ||
          encode_err_vec(2) || encode_err_vec(3)
      }.elsewhen(c === 0x11.U && d(0) === XGMII_SEQ_OS &&
        d(4) === XGMII_START) {
        encoded_tx_data_next := Cat(
          d(7),
          d(6),
          d(5),
          0.U(4.W),
          O_SEQ_OS,
          d(3),
          d(2),
          d(1),
          BLOCK_TYPE_OS_START
        )
        tx_bad_block_next := false.B
      }.elsewhen(c === 0x11.U && d(0) === XGMII_SEQ_OS &&
        d(4) === XGMII_SEQ_OS) {
        encoded_tx_data_next := Cat(
          d(7),
          d(6),
          d(5),
          O_SEQ_OS,
          O_SEQ_OS,
          d(3),
          d(2),
          d(1),
          BLOCK_TYPE_OS_04
        )
        tx_bad_block_next := false.B
      }.elsewhen(c === 0x01.U && d(0) === XGMII_START) {
        encoded_tx_data_next := Cat(xgmii_txd_int(63, 8), BLOCK_TYPE_START_0)
        tx_bad_block_next := false.B
      }.elsewhen(c === 0xf1.U && d(0) === XGMII_SEQ_OS) {
        encoded_tx_data_next :=
          Cat(ctrlSlice(7, 4), O_SEQ_OS, d(3), d(2), d(1), BLOCK_TYPE_OS_0)
        tx_bad_block_next := encode_err_vec(4) || encode_err_vec(5) ||
          encode_err_vec(6) || encode_err_vec(7)
      }.elsewhen(c === 0xff.U && d(0) === XGMII_TERM) {
        encoded_tx_data_next :=
          Cat(ctrlSlice(7, 1), 0.U(7.W), BLOCK_TYPE_TERM_0)
        tx_bad_block_next := encode_err_flat(7, 1) =/= 0.U
      }.elsewhen(c === 0xfe.U && d(1) === XGMII_TERM) {
        encoded_tx_data_next :=
          Cat(ctrlSlice(7, 2), 0.U(6.W), d(0), BLOCK_TYPE_TERM_1)
        tx_bad_block_next := encode_err_flat(7, 2) =/= 0.U
      }.elsewhen(c === 0xfc.U && d(2) === XGMII_TERM) {
        encoded_tx_data_next := Cat(
          ctrlSlice(7, 3),
          0.U(5.W),
          xgmii_txd_int(15, 0),
          BLOCK_TYPE_TERM_2
        )
        tx_bad_block_next := encode_err_flat(7, 3) =/= 0.U
      }.elsewhen(c === 0xf8.U && d(3) === XGMII_TERM) {
        encoded_tx_data_next := Cat(
          ctrlSlice(7, 4),
          0.U(4.W),
          xgmii_txd_int(23, 0),
          BLOCK_TYPE_TERM_3
        )
        tx_bad_block_next := encode_err_flat(7, 4) =/= 0.U
      }.elsewhen(c === 0xf0.U && d(4) === XGMII_TERM) {
        encoded_tx_data_next := Cat(
          ctrlSlice(7, 5),
          0.U(3.W),
          xgmii_txd_int(31, 0),
          BLOCK_TYPE_TERM_4
        )
        tx_bad_block_next := encode_err_flat(7, 5) =/= 0.U
      }.elsewhen(c === 0xe0.U && d(5) === XGMII_TERM) {
        encoded_tx_data_next := Cat(
          ctrlSlice(7, 6),
          0.U(2.W),
          xgmii_txd_int(39, 0),
          BLOCK_TYPE_TERM_5
        )
        tx_bad_block_next := encode_err_flat(7, 6) =/= 0.U
      }.elsewhen(c === 0xc0.U && d(6) === XGMII_TERM) {
        encoded_tx_data_next := Cat(
          ctrlSlice(7, 7),
          0.U(1.W),
          xgmii_txd_int(47, 0),
          BLOCK_TYPE_TERM_6
        )
        tx_bad_block_next := encode_err_vec(7)
      }.elsewhen(c === 0x80.U && d(7) === XGMII_TERM) {
        encoded_tx_data_next := Cat(xgmii_txd_int(55, 0), BLOCK_TYPE_TERM_7)
        tx_bad_block_next := false.B
      }.elsewhen(c === 0xff.U) {
        encoded_tx_data_next := Cat(encoded_ctrl_flat, BLOCK_TYPE_CTRL)
        tx_bad_block_next := encode_err_flat =/= 0.U
      }.otherwise {
        encoded_tx_data_next := Cat(Fill(ctrlWInt, CTRL_ERROR), BLOCK_TYPE_CTRL)
        tx_bad_block_next := true.B
      }
    }
  }

  if (gbxIfEn) {
    when(!xgmii_tx_valid_int) {
      tx_bad_block_next := false.B
    }
  }

  tx_gbx_sync_next := io.tx_gbx_sync_in

  encoded_tx_data_reg := encoded_tx_data_next
  encoded_tx_data_valid_reg := encoded_tx_data_valid_next
  encoded_tx_hdr_reg := encoded_tx_hdr_next
  encoded_tx_hdr_valid_reg := encoded_tx_hdr_valid_next
  tx_gbx_sync_reg := tx_gbx_sync_next
  tx_bad_block_reg := tx_bad_block_next

  io.encoded_tx_data := encoded_tx_data_reg(dataW - 1, 0)

  if (gbxIfEn) {
    io.encoded_tx_data_valid := encoded_tx_data_valid_reg(0)
    io.tx_gbx_sync_out := tx_gbx_sync_reg
  } else {
    io.encoded_tx_data_valid := true.B
    io.tx_gbx_sync_out := 0.U
  }

  io.encoded_tx_hdr := encoded_tx_hdr_reg

  if (useHdrVld) {
    io.encoded_tx_hdr_valid := encoded_tx_hdr_valid_reg
  } else {
    io.encoded_tx_hdr_valid := true.B
  }

  io.tx_bad_block := tx_bad_block_reg
}

object XgmiiEncoder {
  def apply(p: XgmiiEncoderParams): XgmiiEncoder = Module(new XgmiiEncoder(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    hdrW = p.hdrW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt
  ))
}

// object Main extends App {
//   val mainClassName = "Pcs"
//   val coreDir = s"modules/${mainClassName.toLowerCase()}"
//   XgmiiEncoderParams.synConfigMap.foreach { case (configName, p) =>
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
//     // Synthesis collateral generation
//     sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(mainClassName, XgmiiEncoderParams.synConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }
