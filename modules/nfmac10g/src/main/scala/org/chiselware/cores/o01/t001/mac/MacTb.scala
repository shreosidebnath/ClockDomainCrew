package org.chiselware.cores.o01.t001.mac
import chisel3._
import chisel3.util._

class MacTb(val p: MacParams) extends Module {

  val io = IO(new Bundle {
    val rx_clk = Input(Clock())
    val rx_rst = Input(Bool())
    val tx_clk = Input(Clock())
    val tx_rst = Input(Bool())

    // fill in rest
   
  })

  val dut = Module(new Mac(
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
    pauseEn = p.pauseEn,
    statEn = p.statEn,
    statTxLevel = p.statTxLevel,
    statRxLevel = p.statRxLevel,
    statIdBase  = p.statIdBase,
    statUpdatePeriod = p.statUpdatePeriod,
    statStrEn = p.statStrEn,
    statPrefixStr = p.statPrefixStr
  ))

    dut.io.rx_clk := io.rx_clk
    dut.rx_rst := io.rx_rst
    dut.io.tx_clk := io.tx_clk
    dut.tx_rst := io.tx_rst
    // fill in rest of IO
}