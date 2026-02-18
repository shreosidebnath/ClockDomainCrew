error id: file:///C:/Users/benja/ethernet-mac-chisel/src/main/scala/ethernet/xgmiitoaxis.scala:
file:///C:/Users/benja/ethernet-mac-chisel/src/main/scala/ethernet/xgmiitoaxis.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -U.
	 -U#
	 -U().
	 -scala/Predef.U.
	 -scala/Predef.U#
	 -scala/Predef.U().
offset: 16083
uri: file:///C:/Users/benja/ethernet-mac-chisel/src/main/scala/ethernet/xgmiitoaxis.scala
text:
```scala
package ethernet
import chisel3._
import chisel3.util._
import ethernet.EthMacDefinitions.

class Xgmii2AxisConfig(
  val dataW: Int = 64,
  val gbxIfEN: Boolean = false,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val txCplCtrlInTuser: Boolean = true
) {
  val ctrlW: Int = dataW / 8
  val keepW: Int = dataW / 8
  val userW: Int = if (txCplCtrlInTuser) 2 else 1
  val ptpTsW: Int = if (ptpTsFmtTod) 96 else 64
}

class AxisStream(val dataW: Int, val userW: Int, val idW: Int = 8)
    extends Bundle {
  val tdata  = UInt(dataW.W)
  val tkeep  = UInt((dataW / 8).W)
  val tvalid = Bool()
  val tready = Bool()
  val tlast  = Bool()
  val tuser  = UInt(userW.W)
  val tid    = UInt(idW.W)
}

class Xgmii2AxisIO(cfg: Xgmii2AxisConfig) extends Bundle {

  val clk = Input(Clock())
  val rst = Input(Bool())

  // AXI Stream input
  val sAxisTx = Flipped(new AxisStream(cfg.dataW, cfg.userW))

  // AXI completion output
  val mAxisTxCpl = new AxisStream(cfg.dataW, cfg.userW)

  // XGMII
  val xgmiiTxd      = Output(UInt(cfg.dataW.W))
  val xgmiiTxc      = Output(UInt(cfg.ctrlW.W))
  val xgmiiTxValid =  Output(Bool())

  val txGbxReqSync  = Input(UInt(cfg.GBX_CNT.W))
  val txGbxReqStall = Input(Bool())
  val txGbxSync     = Output(UInt(cfg.GBX_CNT.W))

  // PTP
  val ptpTs = Input(UInt(cfg.ptpTsW.W))

  // Configuration
  val cfgTxMaxPktLen = Input(UInt(16.W))
  val cfgTxIfg         = Input(UInt(8.W))
  val cfgTxEnable      = Input(Bool())

  // Status
  val txStartPacket    = Output(UInt(2.W))
  val statTxByte       = Output(UInt(4.W))
  val statTxPktLen     = Output(UInt(16.W))
  val statTxPktUcast   = Output(Bool())
  val statTxPktMcast   = Output(Bool())
  val statTxPktBcast   = Output(Bool())
  val statTxPktVlan    = Output(Bool())
  val statTxPktGood    = Output(Bool())
  val statTxPktBad     = Output(Bool())
  val statTxErrOversize = Output(Bool())
  val statTxErrUser     = Output(Bool())
  val statTxErrUnderflow = Output(Bool())
}

class xgmii2axis(cfg: Xgmii2AxisConfig) extends Module {

  require(cfg.dataW == 64, "Interface width must be 64")
  require(cfg.keepW * 8 == cfg.dataW, "Byte granularity required")

  val io = IO(new Xgmii2AxisIO(cfg))

  withClockAndReset(io.clk, io.rst) {

    // ============================================================
    // Ethernet / XGMII Constants
    // ============================================================

    val ethPre  = "h55".U(8.W)
    val ethSfd  = "hD5".U(8.W)

    val xgmiiIdle  = "h07".U(8.W)
    val xgmiiStart = "hfb".U(8.W)
    val xgmiiTerm = "hfd".U(8.W)
    val xgmiiError = "hfe".U(8.W)

    // ============================================================
    // State Machine
    // ============================================================

    val sIdle :: sPayload :: sPad :: sFcs1 ::
      sFcs2 :: sErr :: sIfg :: Nil = Enum(7)

    val stateReg  = RegInit(sIdle)
    val stateNext = WireDefault(stateReg)

    switch(stateReg) {
      is(sIdle) {
        when(io.s_axis_tx.tvalid && io.cfg_tx_enable) {
          stateNext := sPayload
        }
      }

      is(sPayload) {
        when(io.s_axis_tx.tlast) {
          stateNext := Mux(cfg.PADDING_EN.B, sPad, sFcs1)
        }
      }

      is(sPad) {
        stateNext := sFcs1
      }

      is(sFcs1) {
        stateNext := sFcs2
      }

      is(sFcs2) {
        stateNext := sIfg
      }

      is(sIfg) {
        stateNext := sIdle
      }

      is(sErr) {
        stateNext := sIfg
      }
    }

    stateReg := stateNext

    // ============================================================
    // Default Outputs
    // ============================================================
   //xgmii output
    io.xgmiitxd      := 0.U
    io.xgmiiTxc      := Fill(cfg.CTRL_W, 1.U(1.W))
    io.xgmiiTxValid := false.B
    io.txGbxSync    := 0.U

    io.txStartPacket := 0.U
    io.statTxByte := 0.U
    io.statTxPktLen := 0.U
    io.statTxPktUcast := false.B
    io.statTxPktMcast := false.B
    io.statTxPktBcast := false.B
    io.statTxPktVlan := false.B
    io.statTxPktGood := false.B
    io.statTxPktBad := false.B
    io.statTxErrOversize := false.B
    io.statTxErrUser := false.B
    io.statTxErrUnderflow  := false.B

    io.sAxisTx.tready := (stateReg === sPayload)

    io.mAxisTxCpl.tvalid := false.B
    io.mAxisTxCpl.tdata  := 0.U
    io.mAxisTxCpl.tkeep  := 0.U
    io.mAxisTxCpl.tlast  := false.B
    io.mAxisTxCpl.tuser  := 0.U
    io.mAxisTxCpl.tid    := 0.U
  }
  // ============================================================
// Datapath control
// ============================================================

val resetCrc  = Wire(Bool())
val updateCrc = Wire(Bool())

// ============================================================
// Lane swap
// ============================================================

val swapLanesReg  = RegInit(false.B)
val swapLanesNext = Wire(Bool())

val swapTxdReg = RegInit(0.U(32.W))
val swapTxcReg = RegInit(0.U(4.W))

// ============================================================
// AXIS staging
// ============================================================

val sTdataReg  = RegInit(0.U(cfg.dataW.W))
val sTdataNext = Wire(UInt(cfg.dataW.W))

val sEmptyReg  = RegInit(0.U(emptyW.W))
val sEmptyNext = Wire(UInt(emptyW.W))

// ============================================================
// FCS datapath outputs
// ============================================================

val fcsOutputTxd0 = Wire(UInt(cfg.dataW.W))
val fcsOutputTxd1 = Wire(UInt(cfg.dataW.W))
val fcsOutputTxc0 = Wire(UInt(cfg.dataW.W))
val fcsOutputTxc1 = Wire(UInt(cfg.ctrlW.W))

// ============================================================
// IFG / frame tracking
// ============================================================

val ifgOffset = Wire(UInt(8.W))

val frameStartReg  = RegInit(false.B)
val frameStartNext = Wire(Bool())
val frameReg  = RegInit(false.B)
val frameNext = Wire(Bool())
val frameErrorReg  = RegInit(false.B)
val frameErrorNext = Wire(Bool())
val frameOversizeReg  = RegInit(false.B)
val frameOversizeNext = Wire(Bool())
val frameMinCountReg  = RegInit(0.U(MIN_LEN_W.W))
val frameMinCountNext = Wire(UInt(MIN_LEN_W.W))
val hdrPtrReg  = RegInit(0.U(2.W))
val hdrPtrNext = Wire(UInt(2.W))
val isMcastReg  = RegInit(false.B)
val isMcastNext = Wire(Bool())
val isBcastReg  = RegInit(false.B)
val isBcastNext = Wire(Bool())
val is8021qReg  = RegInit(false.B)
val is8021qNext = Wire(Bool())
val frameLenReg  = RegInit(0.U(16.W))
val frameLenNext = Wire(UInt(16.W))
val frameLenLimCycReg  = RegInit(0.U(13.W))
val frameLenLimCycNext = Wire(UInt(13.W))
val frameLenLimLastReg  = RegInit(0.U(3.W))
val frameLenLimLastNext = Wire(UInt(3.W))
val frameLenLimCheckReg  = RegInit(false.B)
val frameLenLimCheckNext = Wire(Bool())

// ============================================================
// IFG / DIC counters
// ============================================================

val ifgCntReg  = RegInit(0.U(8.W))
val ifgCntNext = Wire(UInt(8.W))

val ifgCountReg  = RegInit(0.U(8.W))
val ifgCountNext = Wire(UInt(8.W))

val deficitIdleCountReg  = RegInit(0.U(2.W))
val deficitIdleCountNext = Wire(UInt(2.W))

// ============================================================
// AXI ready
// ============================================================

val sAxisTxTreadyReg  = RegInit(false.B)
val sAxisTxTreadyNext = Wire(Bool())

// ============================================================
// PTP completion
// ============================================================

val mAxisTxCplTsReg     = RegInit(0.U(cfg.PTP_TS_W.W))
val mAxisTxCplTsAdjReg = RegInit(0.U(cfg.PTP_TS_W.W))

val mAxisTxCplTagReg  = RegInit(0.U(TX_TAG_W.W))
val mAxisTxCplTagNext = Wire(UInt(TX_TAG_W.W))

val mAxisTxCplValidReg     = RegInit(false.B)
val mAxisTxCplValidIntReg  = RegInit(false.B)
val mAxisTxCplTsBorrowReg  = RegInit(false.B)

// ============================================================
// CRC state
// ============================================================

val crcStateReg = Reg(Vec(8, UInt(32.W)))
val crcState    = Wire(Vec(8, UInt(32.W)))

// ============================================================
// Timestamp accumulators
// ============================================================

val lastTsReg = RegInit(0.U(20.W))
val tsIncReg  = RegInit(0.U(20.W))

// ============================================================
// XGMII outputs
// ============================================================

val xgmiiTxdReg  = RegInit(Fill(cfg.ctrlW, xgmiiIdle))
val xgmiiTxdNext = Wire(UInt(cfg.dataW.W))

val xgmiiTxcReg  = RegInit(Fill(cfg.ctrlW, 1.U(1.W)))
val xgmiiTxcNext = Wire(UInt(cfg.ctrlW.W))
val xgmiiTxValidReg = RegInit(false.B)

val txGbxSyncReg = RegInit(0.U(cfg.GBX_CNT.W))

val startPacketReg = RegInit(0.U(2.W))

val statTxByteReg         = RegInit(0.U(4.W))
val statTxPktLenReg       = RegInit(0.U(16.W))
val statTxPktUcastReg     = RegInit(false.B)
val statTxPktMcastReg     = RegInit(false.B)
val statTxPktBcastReg     = RegInit(false.B)
val statTxPktVlanReg      = RegInit(false.B)
val statTxPktGoodReg      = RegInit(false.B)
val statTxPktBadReg       = RegInit(false.B)
val statTxErrOversizeReg  = RegInit(false.B)
val statTxErrUserReg      = RegInit(false.B)
val statTxErrUnderflowReg = RegInit(false.B)

// ============================================================
// Continuous connections (SV assigns)
// ============================================================

// AXIS ready
io.s_axis_tx.tReady :=
  sAxisTxTreadyReg && (!cfg.GBX_IF_EN.B || !io.tx_gbx_req_stall)

// XGMII
io.xgmiiTxd      := xgmiiTxdReg
io.xgmiiTxc      := xgmiiTxcReg
io.xgmiiTxValid := Mux(cfg.gbxIfEn.B, xgmiiTxValidReg, true.B)
io.txGbxSync    := Mux(cfg.gbxIfEn.B, txGbxSyncReg, 0.U)

// Completion AXIS
io.mAxisTxCpl.tData :=
  Mux(cfg.ptpTsEn.B,
    Mux(!cfg.ptpTsFmtTod.B || mAxisTxCplTsBorrowReg,
      mAxisTxCplTsReg,
      mAxisTxCplTsAdjReg),
    0.U
  )

io.mAxisTxCpl.tKeep  := 1.U
io.mAxisTxCpl.tValid := mAxisTxCplValidReg
io.mAxisTxCpl.tLast  := true.B
io.mAxisTxCpl.tId    := mAxisTxCplTagReg
io.mAxisTxCpl.tUser  := 0.U

io.txStartPacket        := startPacketReg
io.statTxByte           := statTxByteReg
io.statTxPktLen         := statTxPktLenReg
io.statTxPktUcast       := statTxPktUcastReg
io.statTxPktMcast       := statTxPktMcastReg
io.statTxPktBcast       := statTxPktBcastReg
io.statTxPktVlan        := statTxPktVlanReg
io.statTxPktGood        := statTxPktGoodReg
io.statTxPktBad         := statTxPktBadReg
io.statTxErrOversize    := statTxErrOversizeReg
io.statTxErrUser        := statTxErrUserReg
io.statTxErrUnderflow   := statTxErrUnderflowReg


  // State enumeration
val stateIdle :: statePreamble :: statePayload :: stateFCS :: stateFCS2 :: Nil = Enum(5)
val state = RegInit(stateIdle)

// 8 parallel CRC states (matching your 8 LFSR instances)
val crcState = RegInit(VecInit(Seq.fill(8)("hFFFFFFFF".U(32.W))))
val crcNext = Wire(Vec(8, UInt(32.W)))

// Data Masking (Zeros out invalid bytes according to tkeep)
val tdataVec = io.s_axis_tx.bits.asTypeOf(Vec(8, UInt(8.W)))
val maskedVec = Wire(Vec(8, UInt(8.W)))
for (i <- 0 until 8) {
  maskedVec(i) := Mux(io.s_axis_tx_tkeep(i), tdataVec(i), 0.U)
}
val s_tdata_masked = maskedVec.asUInt

// Identify empty bytes (Matches Verilog casez logic)
val s_empty = MuxCase(0.U, Seq(
  (io.s_axis_tx_tkeep === "b00000001".U) -> 7.U,
  (io.s_axis_tx_tkeep === "b00000011".U) -> 6.U,
  (io.s_axis_tx_tkeep === "b00000111".U) -> 5.U,
  (io.s_axis_tx_tkeep === "b00001111".U) -> 4.U,
  (io.s_axis_tx_tkeep === "b00011111".U) -> 3.U,
  (io.s_axis_tx_tkeep === "b00111111".U) -> 2.U,
  (io.s_axis_tx_tkeep === "b01111111".U) -> 1.U,
  (io.s_axis_tx_tkeep === "b11111111".U) -> 0.U
))

// Calculate all 8 CRCs in parallel (like your 8 LFSR instances)
for (i <- 0 until 8) {
  // Each CRC instance processes (i+1)*8 bits of data
  // Using crcState(7) as the previous state (cascaded like your LFSR array)
  crcNext(i) := Mux(io.s_axis_tx.fire,
    EthMacDefinitions.crcN(crcState(7), s_tdata_masked(8*(i+1)-1, 0), (i+1)*8),
    crcState(i)
  )
}

// Update CRC registers
when(io.s_axis_tx.fire) {
  crcState := crcNext
  when(io.s_axis_tx_tlast) { 
    state := stateFCS 
  }
}

// Temporary wires for FCS output
val fcs_d0 = Wire(UInt(64.W))
val fcs_c0 = Wire(UInt(8.W))

// Default: send Idle
fcs_d0 := EthMacDefinitions.qwIdleD
fcs_c0 := EthMacDefinitions.qwIdleC

// FCS insertion logic using the appropriate CRC state based on s_empty
// Note: Using crcState(s_empty) or crcState(s_empty-1) depending on alignment
switch(s_empty) {
  is(0.U) { // 8 bytes valid - FCS goes in next cycle
    fcs_d0 := s_tdata_masked
    fcs_c0 := 0.U
  }
  is(1.U) { // 7 bytes valid - use crcState(6)
    fcs_d0 := Cat(~crcState(6)(7,0), s_tdata_masked(55,0))
    fcs_c0 := 0.U
  }
  is(2.U) { // 6 bytes valid - use crcState(5)
    fcs_d0 := Cat(~crcState(5)(15,0), s_tdata_masked(47,0))
    fcs_c0 := 0.U
  }
  is(3.U) { // 5 bytes valid - use crcState(4)
    fcs_d0 := Cat(~crcState(4)(23,0), s_tdata_masked(39,0))
    fcs_c0 := 0.U
  }
  is(4.U) { // 4 bytes valid - use crcState(3)
    fcs_d0 := Cat(~crcState(3), s_tdata_masked(31,0))
    fcs_c0 := 0.U
  }
  is(5.U) { // 3 bytes valid - use crcState(2)
    fcs_d0 := Cat(Fill(3, EthMacDefinitions.I), EthMacDefinitions.T, 
                  ~crcState(2), s_tdata_masked(23,0))
    fcs_c0 := "hF0".U
  }
  is(6.U) { // 2 bytes valid - use crcState(1)
    fcs_d0 := Cat(Fill(2, EthMacDefinitions.I), EthMacDefinitions.T, 
                  ~crcState(1), s_tdata_masked(15,0))
    fcs_c0 := "hE0".U
  }
  is(7.U) { // 1 byte valid - use crcState(0)
    fcs_d0 := Cat(Fill(3, EthMacDefinitions.I), EthMacDefinitions.T, 
                  ~crcState(0), s_tdata_masked(7,0))
    fcs_c0 := "hC0".U
  }
}

// Second FCS cycle for cases where FCS spans two cycles
val fcs_d1 = Wire(UInt(64.W))
val fcs_c1 = Wire(UInt(8.W))
fcs_d1 := EthMacDefinitions.qwIdleD
fcs_c1 := EthMacDefinitions.qwIdleC

when(state === stateFCS2) {
  switch(s_empty) {
    is(0.U) { // 8 bytes case - send FCS in second cycle
      fcs_d1 := Cat(Fill(3, EthMacDefinitions.I), EthMacDefinitions.T, ~crcState(7))
      fcs_c1 := "hF0".U
    }
    is(4.U) { // 4 bytes case - send terminate in second cycle
      fcs_d1 := Cat(Fill(7, EthMacDefinitions.I), EthMacDefinitions.T)
      fcs_c1 := "h80".U
    }
  }
}

// Output FSM
io.xgmiiD := Fill(8, EthMacDefinitions.I)
io.xgmiiC := "hFF".U
io.s_axis_tx.ready := (state === statePayload)

switch(state) {
  is(stateIdle) {
    when(io.s_axis_tx.valid) { 
      state := statePreamble 
    }
  }
  
  is(statePreamble) {
    io.xgmiiD := EthMacDefinitions.preambleLane0D
    io.xgmiiC := EthMacDefinitions.preambleLane0C
    // Reset all CRC registers
    crcState.foreach(_ := "hFFFFFFFF".U)
    state := statePayload
  }
  
  is(statePayload) {
    io.xgmiiD := s_tdata_masked
    io.xgmiiC := 0.U
  }
  
  is(stateFCS) {
    io.xgmiiD := fcs_d0
    io.xgmiiC := fcs_c0
    
    // Go to FCS2 if we need a second cycle
    when(s_empty === 0.U || s_empty === 4.U) {
      state := stateFCS2
    }.otherwise {
      state := stateIdle
    }
  }
  
  is(stateFCS2) {
    io.xgmiiD := fcs_d1
    io.xgmiiC := fcs_c1
    state := stateIdle
  }
}

// Completion Status Outputs
io.mAxisTxCpl.tKeep  := 1.U
io.mAxisTxCpl.tValid := (state === stateFCS) || (state === stateFCS2)
io.mAxisTxCpl.tLast  := true.B
io.mAxisTxCpl.tId    := 0.U
io.mAxisTxCpl.tUser  := 0.U@@
}



```


#### Short summary: 

empty definition using pc, found symbol in pc: 