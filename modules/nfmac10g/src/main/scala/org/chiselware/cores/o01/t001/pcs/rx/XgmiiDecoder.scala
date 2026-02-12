package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class XgmiiDecoder(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val hdrW: Int = 2,
  val gbxIfEn: Boolean = false,
) extends Module {
  
  // Parameter Constraints
  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  
  // IO Definition
  val io = IO(new Bundle {
    // 10GBASE-R encoded input
    val encoded_rx_data       = Input(UInt(dataW.W))
    val encoded_rx_data_valid = Input(Bool()) // Default true in SV, must be driven in Chisel
    val encoded_rx_hdr        = Input(UInt(hdrW.W))
    val encoded_rx_hdr_valid  = Input(Bool()) // Default true in SV
    
    // XGMII interface
    val xgmii_rxd      = Output(UInt(dataW.W))
    val xgmii_rxc      = Output(UInt(ctrlW.W))
    val xgmii_rx_valid = Output(Bool())
    
    // Status
    val rx_bad_block      = Output(Bool())
    val rx_sequence_error = Output(Bool())
  })

  // Internal Constants
  val dataWInt = 64
  val ctrlWInt = 8
  val segCnt   = dataWInt / dataW

  // XGMII Control Codes
  val XGMII_IDLE   = 0x07.U(8.W)
  val XGMII_LPI    = 0x06.U(8.W)
  val XGMII_START  = 0xfb.U(8.W)
  val XGMII_TERM   = 0xfd.U(8.W)
  val XGMII_ERROR  = 0xfe.U(8.W)
  val XGMII_SEQ_OS = 0x9c.U(8.W)
  val XGMII_RES_0  = 0x1c.U(8.W)
  val XGMII_RES_1  = 0x3c.U(8.W)
  val XGMII_RES_2  = 0x7c.U(8.W)
  val XGMII_RES_3  = 0xbc.U(8.W)
  val XGMII_RES_4  = 0xdc.U(8.W)
  val XGMII_RES_5  = 0xf7.U(8.W)
  val XGMII_SIG_OS = 0x5c.U(8.W)

  // 7-bit Encoded Control Codes
  val CTRL_IDLE  = 0x00.U(7.W)
  val CTRL_LPI   = 0x06.U(7.W)
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
  val BLOCK_TYPE_CTRL     = 0x1e.U(8.W) // C7 C6 C5 C4 C3 C2 C1 C0 BT
  val BLOCK_TYPE_OS_4     = 0x2d.U(8.W) // D7 D6 D5 O4 C3 C2 C1 C0 BT
  val BLOCK_TYPE_START_4  = 0x33.U(8.W) // D7 D6 D5    C3 C2 C1 C0 BT
  val BLOCK_TYPE_OS_START = 0x66.U(8.W) // D7 D6 D5    O0 D3 D2 D1 BT
  val BLOCK_TYPE_OS_04    = 0x55.U(8.W) // D7 D6 D5 O4 O0 D3 D2 D1 BT
  val BLOCK_TYPE_START_0  = 0x78.U(8.W) // D7 D6 D5 D4 D3 D2 D1    BT
  val BLOCK_TYPE_OS_0     = 0x4b.U(8.W) // C7 C6 C5 C4 O0 D3 D2 D1 BT
  val BLOCK_TYPE_TERM_0   = 0x87.U(8.W) // C7 C6 C5 C4 C3 C2 C1    BT
  val BLOCK_TYPE_TERM_1   = 0x99.U(8.W) // C7 C6 C5 C4 C3 C2    D0 BT
  val BLOCK_TYPE_TERM_2   = 0xaa.U(8.W) // C7 C6 C5 C4 C3    D1 D0 BT
  val BLOCK_TYPE_TERM_3   = 0xb4.U(8.W) // C7 C6 C5 C4    D2 D1 D0 BT
  val BLOCK_TYPE_TERM_4   = 0xcc.U(8.W) // C7 C6 C5    D3 D2 D1 D0 BT
  val BLOCK_TYPE_TERM_5   = 0xd2.U(8.W) // C7 C6    D4 D3 D2 D1 D0 BT
  val BLOCK_TYPE_TERM_6   = 0xe1.U(8.W) // C7    D5 D4 D3 D2 D1 D0 BT
  val BLOCK_TYPE_TERM_7   = 0xff.U(8.W) //    D6 D5 D4 D3 D2 D1 D0 BT

  // Internal Signals
  val encodedRxDataInt      = Wire(UInt(dataWInt.W))
  val encodedRxDataValidInt = Wire(Bool())
  val encodedRxHdrInt       = Wire(UInt(hdrW.W))

  val decodedCtrl = Wire(Vec(8, UInt(8.W)))
  val decodeErr   = Wire(Vec(8, Bool()))

  // Registers
  // Initialized to 0
  val xgmiiRxdReg         = RegInit(0.U(dataWInt.W))
  val xgmiiRxcReg         = RegInit(0.U(ctrlWInt.W))
  val xgmiiRxValidReg     = RegInit(0.U(segCnt.W))
  val rxBadBlockReg       = RegInit(false.B)
  val rxSequenceErrorReg  = RegInit(false.B)
  val frameReg            = RegInit(false.B)

  // Next State Wires
  val xgmiiRxdNext        = Wire(UInt(dataWInt.W))
  val xgmiiRxcNext        = Wire(UInt(ctrlWInt.W))
  val xgmiiRxValidNext    = Wire(UInt(segCnt.W))
  val rxBadBlockNext      = Wire(Bool())
  val rxSequenceErrorNext = Wire(Bool())
  val frameNext           = Wire(Bool())

  // Output Assignments
  io.xgmii_rxd := xgmiiRxdReg(dataW - 1, 0)
  io.xgmii_rxc := xgmiiRxcReg(ctrlW - 1, 0)
  io.xgmii_rx_valid := Mux(gbxIfEn.B, xgmiiRxValidReg(0), true.B)

  io.rx_bad_block := rxBadBlockReg
  io.rx_sequence_error := rxSequenceErrorReg

  // -------------------------------------------------------------------------
  // Input Gearbox (Repack In)
  // -------------------------------------------------------------------------
  if (dataW == 64) {
    encodedRxDataInt      := io.encoded_rx_data
    encodedRxDataValidInt := Mux(gbxIfEn.B, io.encoded_rx_data_valid, true.B)
    encodedRxHdrInt       := io.encoded_rx_hdr
  } else {
    // 32-bit Repacking
    val encodedRxDataReg      = RegInit(0.U((dataWInt - dataW).W))
    val encodedRxDataValidReg = RegInit(false.B)
    val encodedRxHdrReg       = RegInit(0.U(hdrW.W))

    encodedRxDataInt := Cat(io.encoded_rx_data, encodedRxDataReg)
    
    // Valid logic involves previous cycle valid reg
    encodedRxDataValidInt := encodedRxDataValidReg && Mux(gbxIfEn.B, io.encoded_rx_data_valid, true.B)
    encodedRxHdrInt       := encodedRxHdrReg

    when(!gbxIfEn.B || io.encoded_rx_data_valid) {
      encodedRxDataReg      := io.encoded_rx_data
      encodedRxDataValidReg := io.encoded_rx_hdr_valid
      when(io.encoded_rx_hdr_valid) {
        encodedRxHdrReg := io.encoded_rx_hdr
      }
    }
  }

  // -------------------------------------------------------------------------
  // Main Combinational Logic
  // -------------------------------------------------------------------------
  
  // 1. Defaults (Error State)
  // Corresponds to top of always_comb
  val errVec = VecInit(Seq.fill(ctrlWInt)(XGMII_ERROR))
  xgmiiRxdNext        := Cat(errVec.reverse) // Concatenate vector elements
  xgmiiRxcNext        := -1.S(ctrlWInt.W).asUInt // All 1s
  xgmiiRxValidNext    := 0.U
  rxBadBlockNext      := false.B
  rxSequenceErrorNext := false.B
  frameNext           := frameReg

  // 2. Decode Control Codes Loop
  for (i <- 0 until ctrlWInt) {
    // Extract 7-bit slice: 7*i+8 +: 7
    val encodedSlice = encodedRxDataInt(7 * i + 14, 7 * i + 8)
    
    decodedCtrl(i) := XGMII_ERROR // Default
    decodeErr(i)   := true.B      // Default error

    switch(encodedSlice) {
      is(CTRL_IDLE) {
        decodedCtrl(i) := XGMII_IDLE
        decodeErr(i)   := false.B
      }
      is(CTRL_LPI) {
        decodedCtrl(i) := XGMII_LPI
        decodeErr(i)   := false.B
      }
      is(CTRL_ERROR) {
        decodedCtrl(i) := XGMII_ERROR
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_0) {
        decodedCtrl(i) := XGMII_RES_0
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_1) {
        decodedCtrl(i) := XGMII_RES_1
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_2) {
        decodedCtrl(i) := XGMII_RES_2
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_3) {
        decodedCtrl(i) := XGMII_RES_3
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_4) {
        decodedCtrl(i) := XGMII_RES_4
        decodeErr(i)   := false.B
      }
      is(CTRL_RES_5) {
        decodedCtrl(i) := XGMII_RES_5
        decodeErr(i)   := false.B
      }
    }
  }

  // 3. Repack Output (Output Gearbox for 32-bit mode)
  if (segCnt > 1) {
    // Disable Verilator linting equivalent not needed in Chisel
    // Shift upper 32 bits of previous cycle to lower 32 bits of next
    val rxdShifted = xgmiiRxdReg(dataWInt - 1, dataW)
    val rxcShifted = xgmiiRxcReg(ctrlWInt - 1, ctrlW)
    val validShifted = xgmiiRxValidReg(segCnt - 1, 1)

    val errPad = Fill(ctrlW, XGMII_ERROR)
    val ctrlPad = Fill(ctrlW, 1.U(1.W))

    xgmiiRxdNext     := Cat(errPad, rxdShifted)
    xgmiiRxcNext     := Cat(ctrlPad, rxcShifted)
    xgmiiRxValidNext := Cat(0.U(1.W), validShifted)
  }

  // 4. Main Decoding Block
  when(!encodedRxDataValidInt) {
    // Wait for data, retain previous calculations (which were mostly defaults or gearbox shifts)
  } .elsewhen(encodedRxHdrInt(0) === 0.U) {
    // Data Block
    xgmiiRxdNext     := encodedRxDataInt
    xgmiiRxcNext     := 0.U
    xgmiiRxValidNext := -1.S(segCnt.W).asUInt // All 1s
    rxBadBlockNext   := false.B
  } .otherwise {
    // Control Block
    xgmiiRxValidNext := -1.S(segCnt.W).asUInt // All 1s
    
    // Helper slices for cleaner code
    val dataHigh = encodedRxDataInt(63, 40)
    val dataMid  = encodedRxDataInt(39, 8)
    val blockTypeNibble = encodedRxDataInt(7, 4)

    // Note: To construct xgmiiRxdNext (64 bits), we usually use Cat(byte7, byte6, ... byte0)
    // or Cat(slice_high, slice_low).
    // We must reconstruct the full width for every case because Chisel doesn't support partial wire assignment.

    // Using switch on the upper nibble of block type
    switch(blockTypeNibble) {
      is(BLOCK_TYPE_CTRL(7, 4)) {
        // C7..C0 BT
        // decodedCtrl is a Vec(8), we need to flatten it to UInt
        // Vec(0) is LSB (byte 0).
        xgmiiRxdNext   := decodedCtrl.asUInt
        xgmiiRxcNext   := 0xff.U(8.W)
        rxBadBlockNext := decodeErr.asUInt =/= 0.U
      }
      is(BLOCK_TYPE_OS_4(7, 4)) {
        // D7 D6 D5 O4 C3 C2 C1 C0 BT
        // RXD: [Data(63:40)] [OS/Err(39:32)] [Ctrl(31:0)]
        val rxd31_0 = decodedCtrl.take(4).reverse // Get elements 0-3, reverse for Cat
        val rxdLow  = Cat(rxd31_0) 
        val rxdHigh = encodedRxDataInt(63, 40)
        
        val osByte = Mux(encodedRxDataInt(39, 36) === O_SEQ_OS, XGMII_SEQ_OS, XGMII_ERROR)
        xgmiiRxdNext := Cat(rxdHigh, osByte, rxdLow)
        
        xgmiiRxcNext := 0x1f.U(8.W) // 4'h1 (upper) ## 4'hf (lower) -> 0001 1111 -> 0x1f
        
        when(encodedRxDataInt(39, 36) === O_SEQ_OS) {
           rxBadBlockNext := Cat(decodeErr.take(4).reverse) =/= 0.U
        } .otherwise {
           rxBadBlockNext := true.B
        }
      }
      is(BLOCK_TYPE_START_4(7, 4)) {
        // D7 D6 D5    C3 C2 C1 C0 BT
        // RXD: [Data(63:40)] [START] [Ctrl(31:0)]
        val rxdLow = Cat(decodedCtrl.take(4).reverse)
        xgmiiRxdNext := Cat(encodedRxDataInt(63, 40), XGMII_START, rxdLow)
        xgmiiRxcNext := 0x1f.U(8.W)
        
        rxBadBlockNext      := Cat(decodeErr.take(4).reverse) =/= 0.U
        rxSequenceErrorNext := frameReg
        frameNext           := true.B
      }
      is(BLOCK_TYPE_OS_START(7, 4)) {
        // D7 D6 D5    O0 D3 D2 D1 BT
        // Structure is tricky here. 
        // RXD Lower: [OS/Err] [Data(31:8)]
        // RXD Upper: [Data(63:40)] [START]
        
        // Lower Part
        val osByteLow = Mux(encodedRxDataInt(35, 32) === O_SEQ_OS, XGMII_SEQ_OS, XGMII_ERROR)
        val rxdLow    = Cat(osByteLow, encodedRxDataInt(31, 8))
        
        // Upper Part
        val rxdHigh   = Cat(encodedRxDataInt(63, 40), XGMII_START)
        
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        
        // RXC: Upper nibble 1, Lower nibble 1 -> 0x11
        xgmiiRxcNext := 0x11.U(8.W)
        
        val lowBad = encodedRxDataInt(35, 32) =/= O_SEQ_OS
        rxBadBlockNext      := lowBad
        rxSequenceErrorNext := frameReg
        frameNext           := true.B
      }
      is(BLOCK_TYPE_OS_04(7, 4)) {
        // D7 D6 D5 O4 O0 D3 D2 D1 BT
        // RXD Lower: [OS/Err] [Data(31:8)]
        // RXD Upper: [Data(63:40)] [OS/Err]
        
        val osByteLow = Mux(encodedRxDataInt(35, 32) === O_SEQ_OS, XGMII_SEQ_OS, XGMII_ERROR)
        val rxdLow    = Cat(osByteLow, encodedRxDataInt(31, 8))
        
        val osByteMid = Mux(encodedRxDataInt(39, 36) === O_SEQ_OS, XGMII_SEQ_OS, XGMII_ERROR)
        val rxdHigh   = Cat(encodedRxDataInt(63, 40), osByteMid)
        
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        xgmiiRxcNext := 0x11.U(8.W)
        
        val lowBad = encodedRxDataInt(35, 32) =/= O_SEQ_OS
        val midBad = encodedRxDataInt(39, 36) =/= O_SEQ_OS
        rxBadBlockNext := lowBad || midBad
      }
      is(BLOCK_TYPE_START_0(7, 4)) {
        // D7 D6 D5 D4 D3 D2 D1    BT
        xgmiiRxdNext        := Cat(encodedRxDataInt(63, 8), XGMII_START)
        xgmiiRxcNext        := 0x01.U(8.W)
        rxBadBlockNext      := false.B
        rxSequenceErrorNext := frameReg
        frameNext           := true.B
      }
      is(BLOCK_TYPE_OS_0(7, 4)) {
        // C7 C6 C5 C4 O0 D3 D2 D1 BT
        val osByteLow = Mux(encodedRxDataInt(35, 32) === O_SEQ_OS, XGMII_SEQ_OS, XGMII_ERROR)
        val rxdLow    = Cat(osByteLow, encodedRxDataInt(31, 8))
        
        // Upper: C7..C4
        val rxdHigh   = Cat(decodedCtrl(7), decodedCtrl(6), decodedCtrl(5), decodedCtrl(4))
        
        xgmiiRxdNext := Cat(rxdHigh, rxdLow)
        xgmiiRxcNext := 0xf1.U(8.W)
        
        val lowBad   = encodedRxDataInt(35, 32) =/= O_SEQ_OS
        val upperErr = Cat(decodeErr(7), decodeErr(6), decodeErr(5), decodeErr(4)) =/= 0.U
        rxBadBlockNext := lowBad || upperErr
      }
      is(BLOCK_TYPE_TERM_0(7, 4)) {
        // C7 C6 C5 C4 C3 C2 C1    BT
        // decodedCtrl[7:1] ## TERM
        val rxdHigh = Cat(decodedCtrl.drop(1).reverse) // 7..1
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM)
        xgmiiRxcNext := 0xff.U(8.W)
        
        rxBadBlockNext      := Cat(decodeErr.drop(1).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_1(7, 4)) {
        // C7..C2 ## TERM ## D0
        val rxdHigh = Cat(decodedCtrl.drop(2).reverse) // 7..2
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(15, 8))
        xgmiiRxcNext := 0xfe.U(8.W)
        
        rxBadBlockNext      := Cat(decodeErr.drop(2).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_2(7, 4)) {
        // C7..C3 ## TERM ## D1 D0
        val rxdHigh = Cat(decodedCtrl.drop(3).reverse) // 7..3
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(23, 8))
        xgmiiRxcNext := 0xfc.U(8.W)

        rxBadBlockNext      := Cat(decodeErr.drop(3).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_3(7, 4)) {
        // C7..C4 ## TERM ## D2..D0
        val rxdHigh = Cat(decodedCtrl.drop(4).reverse) // 7..4
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(31, 8))
        xgmiiRxcNext := 0xf8.U(8.W)

        rxBadBlockNext      := Cat(decodeErr.drop(4).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_4(7, 4)) {
        // C7..C5 ## TERM ## D3..D0
        val rxdHigh = Cat(decodedCtrl.drop(5).reverse) // 7..5
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(39, 8))
        xgmiiRxcNext := 0xf0.U(8.W)

        rxBadBlockNext      := Cat(decodeErr.drop(5).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_5(7, 4)) {
        // C7..C6 ## TERM ## D4..D0
        val rxdHigh = Cat(decodedCtrl.drop(6).reverse) // 7..6
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(47, 8))
        xgmiiRxcNext := 0xe0.U(8.W)

        rxBadBlockNext      := Cat(decodeErr.drop(6).reverse) =/= 0.U
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_6(7, 4)) {
        // C7 ## TERM ## D5..D0
        val rxdHigh = decodedCtrl(7)
        xgmiiRxdNext := Cat(rxdHigh, XGMII_TERM, encodedRxDataInt(55, 8))
        xgmiiRxcNext := 0xc0.U(8.W)

        rxBadBlockNext      := decodeErr(7)
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
      is(BLOCK_TYPE_TERM_7(7, 4)) {
        // TERM ## D6..D0
        xgmiiRxdNext := Cat(XGMII_TERM, encodedRxDataInt(63, 8))
        xgmiiRxcNext := 0x80.U(8.W)

        rxBadBlockNext      := false.B
        rxSequenceErrorNext := !frameReg
        frameNext           := false.B
      }
    }
    
    // Default case of switch handled by initialization at top (step 1)
    // If switch matched nothing, defaults remain (Error)
  }

  // 5. Final Validation Check
  // Check all block type bits to detect bad encodings
  when(!encodedRxDataValidInt) {
    // wait for block
  } .elsewhen(encodedRxHdrInt === SYNC_DATA) {
    // data - nothing encoded
  } .elsewhen(encodedRxHdrInt === SYNC_CTRL) {
    // Control - check for bad block types (Full 8-bit check)
    // The previous switch only checked the top 4 bits. 
    // This verifies the exact block type byte.
    val bt = encodedRxDataInt(7, 0)
    val validBlockType = 
      bt === BLOCK_TYPE_CTRL ||
      bt === BLOCK_TYPE_OS_4 ||
      bt === BLOCK_TYPE_START_4 ||
      bt === BLOCK_TYPE_OS_START ||
      bt === BLOCK_TYPE_OS_04 ||
      bt === BLOCK_TYPE_START_0 ||
      bt === BLOCK_TYPE_OS_0 ||
      bt === BLOCK_TYPE_TERM_0 ||
      bt === BLOCK_TYPE_TERM_1 ||
      bt === BLOCK_TYPE_TERM_2 ||
      bt === BLOCK_TYPE_TERM_3 ||
      bt === BLOCK_TYPE_TERM_4 ||
      bt === BLOCK_TYPE_TERM_5 ||
      bt === BLOCK_TYPE_TERM_6 ||
      bt === BLOCK_TYPE_TERM_7
    
    when(!validBlockType) {
      xgmiiRxdNext     := Cat(errVec.reverse)
      xgmiiRxcNext     := -1.S(ctrlWInt.W).asUInt
      rxBadBlockNext   := true.B
    }
  } .otherwise {
    // Invalid header
    xgmiiRxdNext     := Cat(errVec.reverse)
    xgmiiRxcNext     := -1.S(ctrlWInt.W).asUInt
    rxBadBlockNext   := true.B
  }

  // 6. Sequential Updates
  xgmiiRxdReg        := xgmiiRxdNext
  xgmiiRxcReg        := xgmiiRxcNext
  xgmiiRxValidReg    := xgmiiRxValidNext
  rxBadBlockReg      := rxBadBlockNext
  rxSequenceErrorReg := rxSequenceErrorNext
  frameReg           := frameNext

}

object XgmiiDecoder {
  def apply(p: XgmiiDecoderParams): XgmiiDecoder = Module(new XgmiiDecoder(
    dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn
  ))
} 

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  XgmiiDecoderParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new XgmiiDecoder(
        dataW = p.dataW, ctrlW = p.ctrlW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    // Synthesis collateral generation
    sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    RunScriptFile.create(mainClassName, XgmiiDecoderParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}