error id: file:///C:/Users/benja/ClockDomainCrew/ClockDomainCrew/modules/mac/src/test/scala/org/chiselware/cores/o01/t001/mac/DualWrapperMac.scala:chiselStatRxPreamble.
file:///C:/Users/benja/ClockDomainCrew/ClockDomainCrew/modules/mac/src/test/scala/org/chiselware/cores/o01/t001/mac/DualWrapperMac.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/io/chiselStatRxPreamble.
	 -chisel3/io/chiselStatRxPreamble#
	 -chisel3/io/chiselStatRxPreamble().
	 -io/chiselStatRxPreamble.
	 -io/chiselStatRxPreamble#
	 -io/chiselStatRxPreamble().
	 -scala/Predef.io.chiselStatRxPreamble.
	 -scala/Predef.io.chiselStatRxPreamble#
	 -scala/Predef.io.chiselStatRxPreamble().
offset: 5704
uri: file:///C:/Users/benja/ClockDomainCrew/ClockDomainCrew/modules/mac/src/test/scala/org/chiselware/cores/o01/t001/mac/DualWrapperMac.scala
text:
```scala
package org.chiselware.cores.o01.t001.mac

import chisel3._

class DualWrapperMac extends Module {
  val dataW = 64
  val ctrlW = dataW / 8
  val userW = 1
  val idW   = 8

  val io = IO(new Bundle {
    // Drive XGMII RX into both DUTs
    val xgmiiRxd      = Input(UInt(dataW.W))
    val xgmiiRxc      = Input(UInt(ctrlW.W))
    val xgmiiRxValid = Input(Bool())

    // Ready for both RX outputs
    val rxReady = Input(Bool())

    // For oversize test
    val cfgRxMaxPktLen = Input(UInt(16.W))

    // Compare outputs: CHISEL vs VERILOG (golden)
    val chiselRxTdata  = Output(UInt(dataW.W))
    val chiselRxTkeep  = Output(UInt(ctrlW.W))
    val chiselRxTvalid = Output(Bool())
    val chiselRxTlast  = Output(Bool())
    val chiselRxTuser  = Output(UInt(userW.W))
    val chiselRxTid    = Output(UInt(idW.W))
    // for negative testing RX
    val chiselStatRxPktGood     = Output(Bool())
    val chiselStatRxPktBad      = Output(Bool())
    val chiselStatRxBadFcs  = Output(Bool())
    val chiselStatRxPreamble = Output(Bool())
    val chiselStatRxFraming  = Output(Bool())
    val chiselStatRxErrOversize = Output(Bool())
    val chiselStatRxPktFragment = Output(Bool())
    // for negative testing TX
    val chiselStatTxPktGood       = Output(Bool())
    val chiselStatTxPktBad        = Output(Bool())
    val chiselStatTxErrOversize   = Output(Bool())
    val chiselStatTxErrUser       = Output(Bool())
    val chiselStatTxErrUnderflow  = Output(Bool())

    val verilogRxTdata  = Output(UInt(dataW.W))
    val verilogRxTkeep  = Output(UInt(ctrlW.W))
    val verilogRxTvalid = Output(Bool())
    val verilogRxTlast  = Output(Bool())
    val verilogRxTuser  = Output(UInt(userW.W))
    val verilogRxTid    = Output(UInt(idW.W))
    // for negative testing RX
    val verilogStatRxPktGood     = Output(Bool())
    val verilogStatRxPktBad      = Output(Bool())
    val verilogStatRxBadFcs  = Output(Bool())
    val verilogStatRxPreamble = Output(Bool())
    val verilogStatRxFraming  = Output(Bool())
    val verilogStatRxErrOversize = Output(Bool())
    val verilogStatRxPktFragment = Output(Bool())
    // for negative testing TX
    val verilogStatTxPktGood       = Output(Bool())
    val verilogStatTxPktBad        = Output(Bool())
    val verilogStatTxErrOversize   = Output(Bool())
    val verilogStatTxErrUser       = Output(Bool())
    val verilogStatTxErrUnderflow  = Output(Bool())

    // TX IOs
    // Drive AXIS TX into both DUTs
    val txTdata  = Input(UInt(dataW.W))
    val txTkeep  = Input(UInt(ctrlW.W))
    val txTvalid = Input(Bool())
    val txTlast  = Input(Bool())
    val txTuser  = Input(UInt(userW.W))
    val txTid    = Input(UInt(idW.W))
    val txTready = Output(Bool()) // from DUT (they should match)

    // Export XGMII TX from both DUTs
    val chiselXgmiiTxd      = Output(UInt(dataW.W))
    val chiselXgmiiTxc      = Output(UInt(ctrlW.W))
    val chiselXgmiiTxValid = Output(Bool())

    val verilogXgmiiTxd      = Output(UInt(dataW.W))
    val verilogXgmiiTxc      = Output(UInt(ctrlW.W))
    val verilogXgmiiTxValid = Output(Bool())
  })

  // Instantiate both versions (for now both are the BB)
  val bbParams  = MacBbParams()
  val chiselDut = Module(new MacBb(bbParams))
  val origDut   = Module(new MacBb(bbParams))

  // Common clocks/resets
  for (d <- Seq(chiselDut, origDut)) {
    d.io.rxClk := clock
    d.io.txClk := clock
    d.io.rxRst := reset.asBool
    d.io.txRst := reset.asBool

    // Minimal configs
    d.io.cfgTxMaxPktLen   := 1518.U
    d.io.cfgTxIfg           := 12.U
    d.io.cfgTxEnable        := true.B
    d.io.cfgRxMaxPktLen   := io.cfgRxMaxPktLen
    d.io.cfgRxEnable        := true.B
    d.io.mAxisTxCplTready := true.B

    // TX 
    d.io.sAxisTxTdata  := io.txTdata
    d.io.sAxisTxTkeep  := io.txTkeep
    d.io.sAxisTxTvalid := io.txTvalid
    d.io.sAxisTxTlast  := io.txTlast
    d.io.sAxisTxTuser  := io.txTuser
    d.io.sAxisTxTid    := io.txTid

    // Misc inputs
    d.io.txGbxReqSync  := 0.U
    d.io.txGbxReqStall := false.B

    d.io.txPtpTs := 0.U
    d.io.rxPtpTs := 0.U
  }

  // Drive XGMII RX into both
  chiselDut.io.xgmiiRxd      := io.xgmiiRxd
  chiselDut.io.xgmiiRxc      := io.xgmiiRxc
  chiselDut.io.xgmiiRxValid := io.xgmiiRxValid

  origDut.io.xgmiiRxd      := io.xgmiiRxd
  origDut.io.xgmiiRxc      := io.xgmiiRxc
  origDut.io.xgmiiRxValid := io.xgmiiRxValid

  // Ready for RX stream
  chiselDut.io.mAxisRxTready := io.rxReady
  origDut.io.mAxisRxTready   := io.rxReady

  // Ready for TX stream (AXIS sink ready comes from DUT)
  io.tx_tready := chiselDut.io.sAxisTxTready
  assert(chiselDut.io.sAxisTxTready === origDut.io.sAxisTxTready)

  // Export both RX outputs
  io.chiselRxTdata  := chiselDut.io.mAxisRxTdata
  io.chiselRxTkeep  := chiselDut.io.mAxisRxTkeep
  io.chiselRxTvalid := chiselDut.io.mAxisRxTvalid
  io.chiselRxTlast  := chiselDut.io.mAxisRxTlast
  io.chiselRxTuser  := chiselDut.io.mAxisRxTuser
  io.chiselRXTid    := chiselDut.io.mAxisRx_tid

  io.verilogRxTdata  := origDut.io.mAxisRxTdata
  io.verilogRxTkeep  := origDut.io.mAxisRxTkeep
  io.verilogRxTvalid := origDut.io.mAxisRxTvalid
  io.verilogRxTlast  := origDut.io.mAxisRxTlast
  io.verilogRxTuser  := origDut.io.mAxisRxTuser
  io.verilogRXTid    := origDut.io.mAxisRx_tid

  // Export RX status (Chisel DUT)
  io.chiselStatRxPktGood     := chiselDut.io.statRxPktGood
  io.chiselStatRxPktFragment := chiselDut.io.statRxPktFragment
  io.chiselStatRxPktBad      := chiselDut.io.statRxPktBad
  io.chiselStatRxBadFcs  := chiselDut.io.statRxErrBadFcs
  io.chiselSt@@atRxPreamble := chiselDut.io.statRxErrPreamble
  io.chiselStatRxFraming  := chiselDut.io.statRxErrFraming
  io.chiselStatRxOversize := chiselDut.io.statRxErrOversize

  // Export RX status (Golden / Verilog DUT)
  io.verilogStatRxPktGood     := origDut.io.statRxPktGood
  io.verilogStatRxPktFragment := origDut.io.statRxPktFragment
  io.verilogStatRxPktBad      := origDut.io.statRxPktBad
  io.verilogStatRxBadFcs  := origDut.io.statRxErrBadFcs
  io.verilogStatRxPreamble := origDut.io.statRxErrPreamble
  io.verilogStatRxFraming  := origDut.io.statRxErrFraming
  io.verilogStatRxOversize := origDut.io.statRxErrOversize

  // Export TX status (Chisel DUT)
  io.chiselStatTxPktGood      := chiselDut.io.statTxPktGood
  io.chiselStatTxPktBad       := chiselDut.io.statTxPktBad
  io.chiselStatTxErrOversize  := chiselDut.io.statTxErrOversize
  io.chiselStatTxErrUser      := chiselDut.io.statTxErrUser
  io.chiselStatTxErrUnderflow := chiselDut.io.statTxErrUnderflow

  // Export TX status (Golden / Verilog DUT)
  io.verilogStatTxPktGood      := origDut.io.statTxPktGood
  io.verilogStatTxPktBad       := origDut.io.statTxPktBad
  io.verilogStatTxErrOversize  := origDut.io.statTxErrOversize
  io.verilogStatTxErrUser      := origDut.io.statTxErrUser
  io.verilogStatTxErrUnderflow := origDut.io.statTxErrUnderflow

  // Export XGMII TX
  io.chiselXgmiiTxd      := chiselDut.io.xgmiiTxd
  io.chiselXgmiiTxc      := chiselDut.io.xgmiiTxc
  io.chiselXgmiiTxValid := chiselDut.io.xgmiiTxValid

  io.verilogXgmiiTxd      := origDut.io.xgmiiTxd
  io.verilogXgmiiTxc      := origDut.io.xgmiiTxc
  io.verilogXgmiiTxValid := origDut.io.xgmiiTxValid
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 