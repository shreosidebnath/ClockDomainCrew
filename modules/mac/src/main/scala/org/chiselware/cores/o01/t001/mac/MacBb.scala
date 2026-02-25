package org.chiselware.cores.o01.t001.mac

import chisel3._
import chisel3.experimental.IntParam
import chisel3.util.HasBlackBoxResource

object MacBbFiles {
  val MacFiles: Seq[String] = Seq(
    "taxi_axis_if.sv",
    "taxi_lfsr.sv",
    "taxi_axis_null_src.sv",
    "taxi_axis_tie.sv",
    "taxi_axis_xgmii_rx_64.sv",
    "taxi_axis_xgmii_tx_64.sv",
    "taxi_eth_mac_10g.sv",
    "taxi_eth_mac_10g_wrapper.sv"
  )
}

case class MacBbParams(
    dataW: Int = 64,
    idW: Int = 8,
    userW: Int = 1,
    gbxCnt: Int = 1,
    bbFiles: Seq[String] = MacBbFiles.MacFiles)

class MacBb(p: MacBbParams)
    extends BlackBox(Map(
      "DATA_W" -> IntParam(p.dataW),
      "CTRL_W" -> IntParam(p.dataW / 8),
      "ID_W" -> IntParam(p.idW),
      "USER_W" -> IntParam(p.userW),
      "GBX_CNT" -> IntParam(p.gbxCnt)
    )) with HasBlackBoxResource {

  override val desiredName = "taxi_eth_mac_10g_wrapper"

  private val ctrlW = p.dataW / 8

  val io = IO(new Bundle {
    val rxClk = Input(Clock())
    val rxRst = Input(Bool())
    val txClk = Input(Clock())
    val txRst = Input(Bool())

    val sAxisTxTdata = Input(UInt(p.dataW.W))
    val sAxisTxTkeep = Input(UInt(ctrlW.W))
    val sAxisTxTvalid = Input(Bool())
    val sAxisTxTready = Output(Bool())
    val sAxisTxTlast = Input(Bool())
    val sAxisTxTuser = Input(UInt(p.userW.W))
    val sAxisTxTid = Input(UInt(p.idW.W))

    val mAxisTxCplTdata = Output(UInt(p.dataW.W))
    val mAxisTxCplTkeep = Output(UInt(ctrlW.W))
    val mAxisTxCplTvalid = Output(Bool())
    val mAxisTxCplTready = Input(Bool())
    val mAxisTxCplTlast = Output(Bool())
    val mAxisTxCplTuser = Output(UInt(p.userW.W))
    val mAxisTxCplTid = Output(UInt(p.idW.W))

    val mAxisRxTdata = Output(UInt(p.dataW.W))
    val mAxisRxTkeep = Output(UInt(ctrlW.W))
    val mAxisRxTvalid = Output(Bool())
    val mAxisRxTready = Input(Bool())
    val mAxisRxTlast = Output(Bool())
    val mAxisRxTuser = Output(UInt(p.userW.W))
    val mAxisRxTid = Output(UInt(p.idW.W))

    val xgmiiRxd = Input(UInt(p.dataW.W))
    val xgmiiRxc = Input(UInt(ctrlW.W))
    val xgmiiRxValid = Input(Bool())
    val xgmiiTxd = Output(UInt(p.dataW.W))
    val xgmiiTxc = Output(UInt(ctrlW.W))
    val xgmiiTxValid = Output(Bool())

    val txGbxReqSync = Input(UInt(p.gbxCnt.W))
    val txGbxReqStall = Input(Bool())
    val txGbxSync = Output(UInt(p.gbxCnt.W))

    val cfgTxMaxPktLen = Input(UInt(16.W))
    val cfgTxIfg = Input(UInt(8.W))
    val cfgTxEnable = Input(Bool())
    val cfgRxMaxPktLen = Input(UInt(16.W))
    val cfgRxEnable = Input(Bool())

    val txPtpTs = Input(UInt(96.W))
    val rxPtpTs = Input(UInt(96.W))

    val statRxPktGood = Output(Bool())
    val statRxPktBad = Output(Bool())
    val statRxErrBadFcs = Output(Bool())
    val statRxErrPreamble = Output(Bool())
    val statRxErrFraming = Output(Bool())
    val statRxErrOversize = Output(Bool())
    val statRxPktFragment = Output(Bool())
  })

  p.bbFiles.foreach(f => addResource(s"/org/chiselware/cores/o01/t001/mac/$f"))
}