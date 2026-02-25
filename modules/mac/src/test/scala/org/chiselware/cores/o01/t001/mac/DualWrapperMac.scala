package org.chiselware.cores.o01.t001.mac

import chisel3._

class DualWrapperMac extends Module {
  val DATA_W = 64
  val CTRL_W = DATA_W / 8
  val USER_W = 1
  val ID_W = 8

  val io = IO(new Bundle {
    val xgmiiRxd = Input(UInt(DATA_W.W))
    val xgmiiRxc = Input(UInt(CTRL_W.W))
    val xgmiiRxValid = Input(Bool())

    val rxReady = Input(Bool())
    val cfgRxMaxPktLen = Input(UInt(16.W))

    val chiselRxTdata = Output(UInt(DATA_W.W))
    val chiselRxTkeep = Output(UInt(CTRL_W.W))
    val chiselRxTvalid = Output(Bool())
    val chiselRxTlast = Output(Bool())
    val chiselRxTuser = Output(UInt(USER_W.W))
    val chiselRxTid = Output(UInt(ID_W.W))

    val chiselStatRxPktGood = Output(Bool())
    val chiselStatRxPktBad = Output(Bool())
    val chiselStatRxErrBadFcs = Output(Bool())
    val chiselStatRxErrPreamble = Output(Bool())
    val chiselStatRxErrFraming = Output(Bool())
    val chiselStatRxErrOversize = Output(Bool())
    val chiselStatRxPktFragment = Output(Bool())

    val verilogRxTdata = Output(UInt(DATA_W.W))
    val verilogRxTkeep = Output(UInt(CTRL_W.W))
    val verilogRxTvalid = Output(Bool())
    val verilogRxTlast = Output(Bool())
    val verilogRxTuser = Output(UInt(USER_W.W))
    val verilogRxTid = Output(UInt(ID_W.W))

    val verilogStatRxPktGood = Output(Bool())
    val verilogStatRxPktBad = Output(Bool())
    val verilogStatRxErrBadFcs = Output(Bool())
    val verilogStatRxErrPreamble = Output(Bool())
    val verilogStatRxErrFraming = Output(Bool())
    val verilogStatRxErrOversize = Output(Bool())
    val verilogStatRxPktFragment = Output(Bool())
  })

  val bbParams = MacBbParams()
  val chiselDut = Module(new MacBb(bbParams))
  val origDut = Module(new MacBb(bbParams))

  for (d <- Seq(chiselDut, origDut)) {
    d.io.rxClk := clock
    d.io.txClk := clock
    d.io.rxRst := reset.asBool
    d.io.txRst := reset.asBool

    d.io.cfgTxMaxPktLen := 1518.U
    d.io.cfgTxIfg := 12.U
    d.io.cfgTxEnable := true.B
    d.io.cfgRxMaxPktLen := io.cfgRxMaxPktLen
    d.io.cfgRxEnable := true.B

    d.io.sAxisTxTdata := 0.U
    d.io.sAxisTxTkeep := 0.U
    d.io.sAxisTxTvalid := false.B
    d.io.sAxisTxTlast := false.B
    d.io.sAxisTxTuser := 0.U
    d.io.sAxisTxTid := 0.U

    d.io.mAxisTxCplTready := true.B

    d.io.txGbxReqSync := 0.U
    d.io.txGbxReqStall := false.B

    d.io.txPtpTs := 0.U
    d.io.rxPtpTs := 0.U
  }

  chiselDut.io.xgmiiRxd := io.xgmiiRxd
  chiselDut.io.xgmiiRxc := io.xgmiiRxc
  chiselDut.io.xgmiiRxValid := io.xgmiiRxValid

  origDut.io.xgmiiRxd := io.xgmiiRxd
  origDut.io.xgmiiRxc := io.xgmiiRxc
  origDut.io.xgmiiRxValid := io.xgmiiRxValid

  chiselDut.io.mAxisRxTready := io.rxReady
  origDut.io.mAxisRxTready := io.rxReady

  io.chiselRxTdata := chiselDut.io.mAxisRxTdata
  io.chiselRxTkeep := chiselDut.io.mAxisRxTkeep
  io.chiselRxTvalid := chiselDut.io.mAxisRxTvalid
  io.chiselRxTlast := chiselDut.io.mAxisRxTlast
  io.chiselRxTuser := chiselDut.io.mAxisRxTuser
  io.chiselRxTid := chiselDut.io.mAxisRxTid

  io.verilogRxTdata := origDut.io.mAxisRxTdata
  io.verilogRxTkeep := origDut.io.mAxisRxTkeep
  io.verilogRxTvalid := origDut.io.mAxisRxTvalid
  io.verilogRxTlast := origDut.io.mAxisRxTlast
  io.verilogRxTuser := origDut.io.mAxisRxTuser
  io.verilogRxTid := origDut.io.mAxisRxTid

  io.chiselStatRxPktGood := chiselDut.io.statRxPktGood
  io.chiselStatRxPktFragment := chiselDut.io.statRxPktFragment
  io.chiselStatRxPktBad := chiselDut.io.statRxPktBad
  io.chiselStatRxErrBadFcs := chiselDut.io.statRxErrBadFcs
  io.chiselStatRxErrPreamble := chiselDut.io.statRxErrPreamble
  io.chiselStatRxErrFraming := chiselDut.io.statRxErrFraming
  io.chiselStatRxErrOversize := chiselDut.io.statRxErrOversize

  io.verilogStatRxPktGood := origDut.io.statRxPktGood
  io.verilogStatRxPktFragment := origDut.io.statRxPktFragment
  io.verilogStatRxPktBad := origDut.io.statRxPktBad
  io.verilogStatRxErrBadFcs := origDut.io.statRxErrBadFcs
  io.verilogStatRxErrPreamble := origDut.io.statRxErrPreamble
  io.verilogStatRxErrFraming := origDut.io.statRxErrFraming
  io.verilogStatRxErrOversize := origDut.io.statRxErrOversize
}