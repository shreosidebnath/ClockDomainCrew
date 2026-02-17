package ethernet
import chisel3._
import chisel3.util._


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
  val xgmiiTxValid = Output(Bool())

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

    io.xgmii_txd      := 0.U
    io.xgmii_txc      := Fill(cfg.CTRL_W, 1.U(1.W))
    io.xgmii_tx_valid := false.B
    io.tx_gbx_sync    := 0.U

    io.tx_start_packet := 0.U
    io.stat_tx_byte := 0.U
    io.stat_tx_pkt_len := 0.U
    io.stat_tx_pkt_ucast := false.B
    io.stat_tx_pkt_mcast := false.B
    io.stat_tx_pkt_bcast := false.B
    io.stat_tx_pkt_vlan := false.B
    io.stat_tx_pkt_good := false.B
    io.stat_tx_pkt_bad := false.B
    io.stat_tx_err_oversize := false.B
    io.stat_tx_err_user := false.B
    io.stat_tx_err_underflow := false.B

    io.s_axis_tx.tready := (stateReg === sPayload)

    io.m_axis_tx_cpl.tvalid := false.B
    io.m_axis_tx_cpl.tdata  := 0.U
    io.m_axis_tx_cpl.tkeep  := 0.U
    io.m_axis_tx_cpl.tlast  := false.B
    io.m_axis_tx_cpl.tuser  := 0.U
    io.m_axis_tx_cpl.tid    := 0.U
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

val sTdataReg  = RegInit(0.U(cfg.DATA_W.W))
val sTdataNext = Wire(UInt(cfg.DATA_W.W))

val sEmptyReg  = RegInit(0.U(EMPTY_W.W))
val sEmptyNext = Wire(UInt(EMPTY_W.W))

// ============================================================
// FCS datapath outputs
// ============================================================

val fcsOutputTxd0 = Wire(UInt(cfg.DATA_W.W))
val fcsOutputTxd1 = Wire(UInt(cfg.DATA_W.W))
val fcsOutputTxc0 = Wire(UInt(cfg.CTRL_W.W))
val fcsOutputTxc1 = Wire(UInt(cfg.CTRL_W.W))

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

val xgmiiTxdReg  = RegInit(Fill(cfg.CTRL_W, XGMII_IDLE))
val xgmiiTxdNext = Wire(UInt(cfg.DATA_W.W))

val xgmiiTxcReg  = RegInit(Fill(cfg.CTRL_W, 1.U(1.W)))
val xgmiiTxcNext = Wire(UInt(cfg.CTRL_W.W))

val xgmiiTxValidReg = RegInit(false.B)

// ============================================================
// GBX + status
// ============================================================

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
io.s_axis_tx.tready :=
  sAxisTxTreadyReg && (!cfg.GBX_IF_EN.B || !io.tx_gbx_req_stall)

// XGMII
io.xgmii_txd      := xgmiiTxdReg
io.xgmii_txc      := xgmiiTxcReg
io.xgmii_tx_valid := Mux(cfg.GBX_IF_EN.B, xgmiiTxValidReg, true.B)
io.tx_gbx_sync    := Mux(cfg.GBX_IF_EN.B, txGbxSyncReg, 0.U)

// Completion AXIS
io.m_axis_tx_cpl.tdata :=
  Mux(cfg.PTP_TS_EN.B,
    Mux(!cfg.PTP_TS_FMT_TOD.B || mAxisTxCplTsBorrowReg,
      mAxisTxCplTsReg,
      mAxisTxCplTsAdjReg),
    0.U
  )

io.m_axis_tx_cpl.tkeep  := 1.U
io.m_axis_tx_cpl.tvalid := mAxisTxCplValidReg
io.m_axis_tx_cpl.tlast  := true.B
io.m_axis_tx_cpl.tid    := mAxisTxCplTagReg
io.m_axis_tx_cpl.tuser  := 0.U

// Status outputs
io.tx_start_packet        := startPacketReg
io.stat_tx_byte           := statTxByteReg
io.stat_tx_pkt_len        := statTxPktLenReg
io.stat_tx_pkt_ucast      := statTxPktUcastReg
io.stat_tx_pkt_mcast      := statTxPktMcastReg
io.stat_tx_pkt_bcast      := statTxPktBcastReg
io.stat_tx_pkt_vlan       := statTxPktVlanReg
io.stat_tx_pkt_good       := statTxPktGoodReg
io.stat_tx_pkt_bad        := statTxPktBadReg
io.stat_tx_err_oversize   := statTxErrOversizeReg
io.stat_tx_err_user       := statTxErrUserReg
io.stat_tx_err_underflow  := statTxErrUnderflowReg

}
