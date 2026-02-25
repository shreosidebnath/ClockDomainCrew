package org.chiselware.cores.o01.t001.mac
import _root_.circt.stage.ChiselStage
import chisel3._
import org.chiselware.cores.o01.t001.mac.rx.Xgmii2Axis64
import org.chiselware.cores.o01.t001.mac.tx.Axis2Xgmii64
import org.chiselware.syn.{ RunScriptFile, StaTclFile, YosysTclFile }

class Mac(
    val dataW: Int = 64,
    val ctrlW: Int = 8,
    val txGbxIfEn: Boolean = false,
    val rxGbxIfEn: Boolean = false,
    val gbxCnt: Int = 1,
    val paddingEn: Boolean = true,
    val dicEn: Boolean = true,
    val minFrameLen: Int = 64,
    val ptpTsEn: Boolean = false,
    val ptpTsFmtTod: Boolean = true,
    val ptpTsW: Int = 96,
    val pfcEn: Boolean = false,
    val pauseEn: Boolean = false) extends RawModule {

  val keepW = dataW / 8
  val macCtrlEn = pauseEn || pfcEn
  val txUserW = 1
  val rxUserW =
    (if (ptpTsEn)
       ptpTsW
     else
       0) + 1
  val txUserWInt =
    (if (macCtrlEn)
       1
     else
       0) + txUserW
  val txTagW = 8 // Extracted from s_axis_tx.ID_W

  // Check configuration
  require(
    dataW == 64,
    s"Error: Interface width must be 64 (instance dataW=$dataW)"
  )
  require(
    keepW * 8 == dataW && ctrlW * 8 == dataW,
    "Error: Interface requires byte (8-bit) granularity"
  )

  val io = IO(new Bundle {
    // Explicit Clocks and Resets
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())

    // Transmit interface (AXI stream)
    val sAxisTx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = dataW,
      keepW = keepW,
      idEn = true,
      idW = txTagW,
      userEn = true,
      userW = txUserW
    )))
    val mAxisTxCpl =
      new AxisInterface(AxisInterfaceParams(
        dataW = 96,
        keepW = 1,
        idW = 8
      ))

    // Receive interface (AXI stream)
    val mAxisRx =
      new AxisInterface(AxisInterfaceParams(
        dataW = dataW,
        keepW = keepW,
        userEn = true,
        userW = rxUserW
      ))

    // XGMII interface
    val xgmiiRxd = Input(UInt(dataW.W))
    val xgmiiRxc = Input(UInt(ctrlW.W))
    val xgmiiRxValid = Input(Bool())
    val xgmiiTxd = Output(UInt(dataW.W))
    val xgmiiTxc = Output(UInt(ctrlW.W))
    val xgmiiTxValid = Output(Bool())

    val txGbxReqSync = Input(UInt(gbxCnt.W))
    val txGbxReqStall = Input(Bool())
    val txGbxSync = Output(UInt(gbxCnt.W))

    // PTP
    val txPtpTs = Input(UInt(ptpTsW.W))
    val rxPtpTs = Input(UInt(ptpTsW.W))

    // Link-level Flow Control (LFC)
    val txLfcReq = Input(Bool())
    val txLfcResend = Input(Bool())
    val rxLfcEn = Input(Bool())
    val rxLfcReq = Output(Bool())
    val rxLfcAck = Input(Bool())

    // Priority Flow Control (PFC)
    val txPfcReq = Input(UInt(8.W))
    val txPfcResend = Input(Bool())
    val rxPfcEn = Input(UInt(8.W))
    val rxPfcReq = Output(UInt(8.W))
    val rxPfcAck = Input(UInt(8.W))

    // Pause interface
    val txLfcPauseEn = Input(Bool())
    val txPauseReq = Input(Bool())
    val txPauseAck = Output(Bool())

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

    val statTxMcf = Output(Bool())
    val statRxMcf = Output(Bool())
    val statTxLfcPkt = Output(Bool())
    val statTxLfcXon = Output(Bool())
    val statTxLfcXoff = Output(Bool())
    val statTxLfcPaused = Output(Bool())
    val statTxPfcPkt = Output(Bool())
    val statTxPfcXon = Output(UInt(8.W))
    val statTxPfcXoff = Output(UInt(8.W))
    val statTxPfcPaused = Output(UInt(8.W))
    val statRxLfcPkt = Output(Bool())
    val statRxLfcXon = Output(Bool())
    val statRxLfcXoff = Output(Bool())
    val statRxLfcPaused = Output(Bool())
    val statRxPfcPkt = Output(Bool())
    val statRxPfcXon = Output(UInt(8.W))
    val statRxPfcXoff = Output(UInt(8.W))
    val statRxPfcPaused = Output(UInt(8.W))

    // Configuration
    val cfgTxMaxPktLen = Input(UInt(16.W))
    val cfgTxIfg = Input(UInt(8.W))
    val cfgTxEnable = Input(Bool())
    val cfgRxMaxPktLen = Input(UInt(16.W))
    val cfgRxEnable = Input(Bool())

    val cfgMcfRxEthDstMcast = Input(UInt(48.W))
    val cfgMcfRxCheckEthDstMcast = Input(Bool())
    val cfgMcfRxEthDstUcast = Input(UInt(48.W))
    val cfgMcfRxCheckEthDstUcast = Input(Bool())
    val cfgMcfRxEthSrc = Input(UInt(48.W))
    val cfgMcfRxCheckEthSrc = Input(Bool())
    val cfgMcfRxEthType = Input(UInt(16.W))
    val cfgMcfRxOpcodeLfc = Input(UInt(16.W))
    val cfgMcfRxCheckOpcodeLfc = Input(Bool())
    val cfgMcfRxOpcodePfc = Input(UInt(16.W))
    val cfgMcfRxCheckOpcodePfc = Input(Bool())
    val cfgMcfRxForward = Input(Bool())
    val cfgMcfRxEnable = Input(Bool())

    val cfgTxLfcEthDst = Input(UInt(48.W))
    val cfgTxLfcEthSrc = Input(UInt(48.W))
    val cfgTxLfcEthType = Input(UInt(16.W))
    val cfgTxLfcOpcode = Input(UInt(16.W))
    val cfgTxLfcEn = Input(Bool())
    val cfgTxLfcQuanta = Input(UInt(16.W))
    val cfgTxLfcRefresh = Input(UInt(16.W))

    val cfgTxPfcEthDst = Input(UInt(48.W))
    val cfgTxPfcEthSrc = Input(UInt(48.W))
    val cfgTxPfcEthType = Input(UInt(16.W))
    val cfgTxPfcOpcode = Input(UInt(16.W))
    val cfgTxPfcEn = Input(Bool())
    val cfgTxPfcQuanta = Input(Vec(8, UInt(16.W)))
    val cfgTxPfcRefresh = Input(Vec(8, UInt(16.W)))

    val cfgRxLfcOpcode = Input(UInt(16.W))
    val cfgRxLfcEn = Input(Bool())
    val cfgRxPfcOpcode = Input(UInt(16.W))
    val cfgRxPfcEn = Input(Bool())
  })

  // Internal Interface declarations
  val axisTxInt = Wire(new AxisInterface(AxisInterfaceParams(
    dataW = dataW,
    keepW = keepW,
    idEn = true,
    idW = txTagW,
    userEn = true,
    userW = txUserWInt
  )))
  val axisRxInt = Wire(new AxisInterface(AxisInterfaceParams(
    dataW = dataW,
    keepW = keepW,
    userEn = true,
    userW = rxUserW
  )))

  // -------------------------------------------------------------
  // RX MAC Submodule (Mapped to rxClk domain)
  // -------------------------------------------------------------
  withClockAndReset(io.rxClk, io.rxRst) {
    val axisXgmiiRxInst = Module(new Xgmii2Axis64(
      dataW = dataW,
      ctrlW = ctrlW,
      gbxIfEn = rxGbxIfEn,
      ptpTsEn = ptpTsEn,
      ptpTsFmtTod = ptpTsFmtTod,
      ptpTsW = ptpTsW
    ))

    // Inputs
    axisXgmiiRxInst.io.xgmiiRxd := io.xgmiiRxd
    axisXgmiiRxInst.io.xgmiiRxc := io.xgmiiRxc
    axisXgmiiRxInst.io.xgmiiRxValid := io.xgmiiRxValid
    axisXgmiiRxInst.io.ptpTs := io.rxPtpTs
    axisXgmiiRxInst.io.cfgRxMaxPktLen := io.cfgRxMaxPktLen
    axisXgmiiRxInst.io.cfgRxEnable := io.cfgRxEnable

    // Outputs
    axisRxInt <> axisXgmiiRxInst.io.mAxisRx

    io.rxStartPacket := axisXgmiiRxInst.io.rxStartPacket
    io.statRxByte := axisXgmiiRxInst.io.statRxByte
    io.statRxPktLen := axisXgmiiRxInst.io.statRxPktLen
    io.statRxPktFragment := axisXgmiiRxInst.io.statRxPktFragment
    io.statRxPktJabber := axisXgmiiRxInst.io.statRxPktJabber
    io.statRxPktUcast := axisXgmiiRxInst.io.statRxPktUcast
    io.statRxPktMcast := axisXgmiiRxInst.io.statRxPktMcast
    io.statRxPktBcast := axisXgmiiRxInst.io.statRxPktBcast
    io.statRxPktVlan := axisXgmiiRxInst.io.statRxPktVlan
    io.statRxPktGood := axisXgmiiRxInst.io.statRxPktGood
    io.statRxPktBad := axisXgmiiRxInst.io.statRxPktBad
    io.statRxErrOversize := axisXgmiiRxInst.io.statRxErrOversize
    io.statRxErrBadFcs := axisXgmiiRxInst.io.statRxErrBadFcs
    io.statRxErrBadBlock := axisXgmiiRxInst.io.statRxErrBadBlock
    io.statRxErrFraming := axisXgmiiRxInst.io.statRxErrFraming
    io.statRxErrPreamble := axisXgmiiRxInst.io.statRxErrPreamble
  }

  // -------------------------------------------------------------
  // TX MAC Submodule (Mapped to txClk domain)
  // -------------------------------------------------------------
  withClockAndReset(io.txClk, io.txRst) {
    val axisXgmiiTxInst = Module(new Axis2Xgmii64(
      dataW = dataW,
      ctrlW = ctrlW,
      gbxIfEn = txGbxIfEn,
      gbxCnt = gbxCnt,
      paddingEn = paddingEn,
      dicEn = dicEn,
      minFrameLen = minFrameLen,
      ptpTsEn = ptpTsEn,
      ptpTsFmtTod = ptpTsFmtTod,
      ptpTsW = ptpTsW,
      txCplCtrlInTuser = macCtrlEn
    ))

    // Inputs
    axisXgmiiTxInst.io.sAxisTx <> axisTxInt

    io.txGbxSync := axisXgmiiTxInst.io.txGbxSync
    axisXgmiiTxInst.io.txGbxReqSync := io.txGbxReqSync
    axisXgmiiTxInst.io.txGbxReqStall := io.txGbxReqStall
    axisXgmiiTxInst.io.ptpTs := io.txPtpTs
    axisXgmiiTxInst.io.cfgTxMaxPktLen := io.cfgTxMaxPktLen
    axisXgmiiTxInst.io.cfgTxIfg := io.cfgTxIfg
    axisXgmiiTxInst.io.cfgTxEnable := io.cfgTxEnable

    // Outputs
    io.mAxisTxCpl <> axisXgmiiTxInst.io.mAxisTxCpl
    io.xgmiiTxd := axisXgmiiTxInst.io.xgmiiTxd
    io.xgmiiTxc := axisXgmiiTxInst.io.xgmiiTxc
    io.xgmiiTxValid := axisXgmiiTxInst.io.xgmiiTxValid

    io.txStartPacket := axisXgmiiTxInst.io.txStartPacket
    io.statTxByte := axisXgmiiTxInst.io.statTxByte
    io.statTxPktLen := axisXgmiiTxInst.io.statTxPktLen
    io.statTxPktUcast := axisXgmiiTxInst.io.statTxPktUcast
    io.statTxPktMcast := axisXgmiiTxInst.io.statTxPktMcast
    io.statTxPktBcast := axisXgmiiTxInst.io.statTxPktBcast
    io.statTxPktVlan := axisXgmiiTxInst.io.statTxPktVlan
    io.statTxPktGood := axisXgmiiTxInst.io.statTxPktGood
    io.statTxPktBad := axisXgmiiTxInst.io.statTxPktBad
    io.statTxErrOversize := axisXgmiiTxInst.io.statTxErrOversize
    io.statTxErrUser := axisXgmiiTxInst.io.statTxErrUser
    io.statTxErrUnderflow := axisXgmiiTxInst.io.statTxErrUnderflow
  }

  // -------------------------------------------------------------
  // MAC Control / Bridging Logic
  // -------------------------------------------------------------
  if (macCtrlEn) {
    // Implement control logic here
  } else {

    // Connect external TX input to internal TX MAC
    AxisTie(io.sAxisTx, axisTxInt)

    // Connect internal RX MAC to external RX output
    AxisTie(axisRxInt, io.mAxisRx)

    // Tie off unused flow-control outputs securely
    io.rxLfcReq := false.B
    io.rxPfcReq := 0.U
    io.txPauseAck := false.B

    io.statTxMcf := false.B
    io.statRxMcf := false.B
    io.statTxLfcPkt := false.B
    io.statTxLfcXon := false.B
    io.statTxLfcXoff := false.B
    io.statTxLfcPaused := false.B
    io.statTxPfcPkt := false.B
    io.statTxPfcXon := 0.U
    io.statTxPfcXoff := 0.U
    io.statTxPfcPaused := 0.U
    io.statRxLfcPkt := false.B
    io.statRxLfcXon := false.B
    io.statRxLfcXoff := false.B
    io.statRxLfcPaused := false.B
    io.statRxPfcPkt := false.B
    io.statRxPfcXon := 0.U
    io.statRxPfcXoff := 0.U
    io.statRxPfcPaused := 0.U
  }
}

object Mac {
  def apply(p: MacParams): Mac = Module(new Mac(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    txGbxIfEn = p.txGbxIfEn,
    rxGbxIfEn = p.rxGbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW,
    pfcEn = p.pfcEn,
    pauseEn = p.pauseEn
  ))
}

object Main extends App {
  val MainClassName = "Mac"
  val coreDir = s"modules/${MainClassName.toLowerCase()}"
  MacParams.SynConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Mac(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        txGbxIfEn = p.txGbxIfEn,
        rxGbxIfEn = p.rxGbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        pfcEn = p.pfcEn,
        pauseEn = p.pauseEn
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
      MainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      MainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      MainClassName,
      MacParams.SynConfigs,
      s"${coreDir}/generated/synTestCases"
    )
  }
}
