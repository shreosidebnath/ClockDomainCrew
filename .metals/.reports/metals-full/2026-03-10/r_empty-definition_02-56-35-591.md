error id: file:///C:/Users/benja/ClockDomainCrew/ClockDomainCrew/modules/mac/src/test/scala/org/chiselware/cores/o01/t001/mac/DualWrapperMac.scala:
file:///C:/Users/benja/ClockDomainCrew/ClockDomainCrew/modules/mac/src/test/scala/org/chiselware/cores/o01/t001/mac/DualWrapperMac.scala
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/chiselDut/io.
	 -chiselDut/io.
	 -scala/Predef.chiselDut.io.
offset: 6606
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
    d.io.sAxisTxTdata  := io.tx_tdata
    d.io.sAxisTxTkeep  := io.tx_tkeep
    d.io.sAxisTxTvalid := io.tx_tvalid
    d.io.sAxisTxTlast  := io.tx_tlast
    d.io.sAxisTxTuser  := io.tx_tuser
    d.io.sAxisTxTid    := io.tx_tid

    // Misc inputs
    d.io.txGbxReqSync  := 0.U
    d.io.txGbxReqStall := false.B

    d.io.txPtpTs := 0.U
    d.io.rxPtpTs := 0.U
  }

  // Drive XGMII RX into both
  chiselDut.io.xgmiiRxd      := io.xgmii_rxd
  chiselDut.io.xgmiiRxc      := io.xgmii_rxc
  chiselDut.io.xgmiiRxValid := io.xgmii_rx_valid

  origDut.io.xgmiiRxd      := io.xgmii_rxd
  origDut.io.xgmiiRxc      := io.xgmii_rxc
  origDut.io.xgmiiRxValid := io.xgmii_rx_valid

  // Ready for RX stream
  chiselDut.io.mAxisRxTready := io.rx_ready
  origDut.io.mAxisRxTready   := io.rx_ready

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
  io.chiselStatRxBadFcs  := chiselDut.io.statRxBadFcs
  io.chiselStatRxPreamble := chiselDut.io.statRxPreamble
  io.chiselStatRxFraming  := chiselDut.io.statRxFraming
  io.chiselStatRxOversize := chiselDut.io.statRxOversize

  // Export RX status (Golden / Verilog DUT)
  io.verilogStatRxPktGood     := origDut.io.statRxPktGood
  io.verilogStatRxPktFragment := origDut.io.statRxPktFragment
  io.verilogStatRxPktBad      := origDut.io.statRxPktBad
  io.verilogStatRxBadFcs  := origDut.io.statRxBadFcs
  io.verilogStatRxPreamble := origDut.io.statRxPreamble
  io.verilogStatRxFraming  := origDut.io.statRxFraming
  io.verilogStatRxOversize := origDut.io.statRxOversize

  // Export TX status (Chisel DUT)
  io.chiselStatTxPktGood      := chiselDut.io.stat_tx_pkt_good
  io.chiselStatTxPktBad       := chiselDut.io.stat_tx_pkt_bad
  io.chiselStatTxErrOversize  := chiselDut.io.stat_tx_err_oversize
  io.chiselStatTxErrUser      := chiselDut.i@@o.stat_tx_err_user
  io.chiselStatTxErrUnderflow := chiselDut.io.stat_tx_err_underflow

  // Export TX status (Golden / Verilog DUT)
  io.verilogStatTxPktGood      := origDut.io.stat_tx_pkt_good
  io.verilogStatTxPktBad       := origDut.io.stat_tx_pkt_bad
  io.verilogStatTxErrOversize  := origDut.io.stat_tx_err_oversize
  io.verilogStatTxErrUser      := origDut.io.stat_tx_err_user
  io.verilogStatTxErrUnderflow := origDut.io.stat_tx_err_underflow

  // Export XGMII TX
  io.chisel_xgmii_txd      := chiselDut.io.xgmii_txd
  io.chisel_xgmii_txc      := chiselDut.io.xgmii_txc
  io.chisel_xgmii_tx_valid := chiselDut.io.xgmii_tx_valid

  io.verilog_xgmii_txd      := origDut.io.xgmii_txd
  io.verilog_xgmii_txc      := origDut.io.xgmii_txc
  io.verilog_xgmii_tx_valid := origDut.io.xgmii_tx_valid
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 