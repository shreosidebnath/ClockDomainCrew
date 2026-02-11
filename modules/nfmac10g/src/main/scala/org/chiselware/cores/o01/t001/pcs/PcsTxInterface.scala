package org.chiselware.cores.o01.t001.pcs
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class PcsTxInterface(
  val dataW: Int = 64,
  val hdrW: Int = 2,
  val gbxIfEn: Boolean = false,
  val bitReverse: Boolean = false,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val serdesPipeline: Int = 0
) extends Module {
  val io = IO(new Bundle {
    val encoded_tx_data = Input(UInt(dataW.W))
    val encoded_tx_data_valid = Input(Bool())
    val encoded_tx_hdr = Input(UInt(hdrW.W))
    val encoded_tx_hdr_valid = Input(Bool())
    val tx_gbx_req_sync = Output(Bool())
    val tx_gbx_req_stall = Output(Bool())
    val tx_gbx_sync = Input(Bool())

    val serdes_tx_data = Output(UInt(dataW.W))
    val serdes_tx_data_valid = Output(Bool())
    val serdes_tx_hdr = Output(UInt(hdrW.W))
    val serdes_tx_hdr_valid = Output(Bool())
    val serdes_tx_gbx_req_sync = Input(Bool())
    val serdes_tx_gbx_req_stall = Input(Bool())
    val serdes_tx_gbx_sync = Output(Bool())

    val cfg_tx_prbs31_enable = Input(Bool())
  })
  

  io.tx_gbx_req_sync := Mux(gbxIfEn.B, io.serdes_tx_gbx_req_sync, false.B)
  io.tx_gbx_req_stall := Mux(gbxIfEn.B, io.serdes_tx_gbx_req_stall, false.B)

  // Scrambler
  val scrambler_state_reg = RegInit("h3FFFFFFFFFFFFFF".U(58.W))
  val scrambler = Module(new Lfsr(
    lfsrW = 58,
    lfsrPoly = BigInt("8000000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = true
  ))
  
  scrambler.io.data_in := io.encoded_tx_data
  scrambler.io.state_in := scrambler_state_reg
  when (!gbxIfEn.B || io.encoded_tx_data_valid) {
    scrambler_state_reg := scrambler.io.state_out
  }

  // PRBS31 Gen
  val prbs31_state_reg = RegInit("h7FFFFFFF".U(31.W))
  val prbs31_gen = Module(new Lfsr(
    lfsrW = 31,
    lfsrPoly = BigInt("10000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW + hdrW,
    dataInEn = false,
    dataOutEn = true
  ))
  prbs31_gen.io.state_in := prbs31_state_reg
  prbs31_gen.io.data_in := 0.U

  // Output Regs
  val serdes_tx_data_reg = Reg(UInt(dataW.W))
  val serdes_tx_hdr_reg = Reg(UInt(hdrW.W))
  
  when (prbs31En.B && io.cfg_tx_prbs31_enable) {
    if (gbxIfEn) {
      when (io.encoded_tx_data_valid) { prbs31_state_reg := prbs31_gen.io.state_out }
    } else {
      prbs31_state_reg := prbs31_gen.io.state_out
    }
    serdes_tx_data_reg := ~prbs31_gen.io.data_out(dataW+hdrW-1, hdrW)
    serdes_tx_hdr_reg  := ~prbs31_gen.io.data_out(hdrW-1, 0)
  } .otherwise {
    serdes_tx_data_reg := Mux(scramblerDisable.B, io.encoded_tx_data, scrambler.io.data_out)
    serdes_tx_hdr_reg  := io.encoded_tx_hdr
  }

  val tx_data_int = if(bitReverse) Reverse(serdes_tx_data_reg) else serdes_tx_data_reg
  val tx_hdr_int  = if(bitReverse) Reverse(serdes_tx_hdr_reg) else serdes_tx_hdr_reg
  
  io.serdes_tx_data := ShiftRegister(tx_data_int, serdesPipeline)
  io.serdes_tx_hdr  := ShiftRegister(tx_hdr_int, serdesPipeline)
  io.serdes_tx_data_valid := ShiftRegister(RegNext(io.encoded_tx_data_valid), serdesPipeline)
  io.serdes_tx_hdr_valid  := ShiftRegister(RegNext(io.encoded_tx_hdr_valid), serdesPipeline)
  io.serdes_tx_gbx_sync   := ShiftRegister(RegNext(io.tx_gbx_sync), serdesPipeline)
}

object PcsTxInterface {
  def apply(p: PcsTxInterfaceParams): PcsTxInterface = Module(new PcsTxInterface(
    dataW = p.dataW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse,
    scramblerDisable = p.scramblerDisable, prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline
  ))
}

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  PcsTxInterfaceParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new PcsTxInterface(
        dataW = p.dataW, hdrW = p.hdrW, gbxIfEn = p.gbxIfEn, bitReverse = p.bitReverse,
        scramblerDisable = p.scramblerDisable, prbs31En = p.prbs31En, serdesPipeline = p.serdesPipeline
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
    RunScriptFile.create(mainClassName, PcsTxInterfaceParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}