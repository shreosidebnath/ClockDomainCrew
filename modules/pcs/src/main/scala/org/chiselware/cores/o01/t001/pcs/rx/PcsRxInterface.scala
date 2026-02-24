package org.chiselware.cores.o01.t001.pcs.rx
import org.chiselware.cores.o01.t001.pcs.Lfsr
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


/**
 * 10G Ethernet PHY RX IF
 */
class PcsRxInterface(
  val dataW: Int = 64,
  val hdrW: Int = 2,
  val gbxIfEn: Boolean = false,
  val bitReverse: Boolean = false,
  val scramblerDisable: Boolean = false,
  val prbs31En: Boolean = false,
  val serdesPipeline: Int = 0,
  val bitslipHighCycles: Int = 1,
  val bitslipLowCycles: Int = 7,
  val count125Us: Double = 125000 / 6.4
) extends Module {

  // Validation Checks
  require(dataW == 32 || dataW == 64, "Error: Interface width must be 32 or 64")
  require(hdrW == 2, "Error: HDR_W must be 2")

  val io = IO(new Bundle {

    /* 10GBASE-R encoded interface */
    val encoded_rx_data       = Output(UInt(dataW.W))
    val encoded_rx_data_valid = Output(Bool())
    val encoded_rx_hdr        = Output(UInt(hdrW.W))
    val encoded_rx_hdr_valid  = Output(Bool())

    /* SERDES interface */
    val serdes_rx_data        = Input(UInt(dataW.W))
    val serdes_rx_data_valid  = Input(Bool())
    val serdes_rx_hdr         = Input(UInt(hdrW.W))
    val serdes_rx_hdr_valid   = Input(Bool())
    val serdes_rx_bitslip     = Output(Bool())
    val serdes_rx_reset_req   = Output(Bool())

    /* Status */
    val rx_bad_block          = Input(Bool())
    val rx_sequence_error     = Input(Bool())
    val rx_error_count        = Output(UInt(7.W))
    val rx_block_lock         = Output(Bool())
    val rx_high_ber           = Output(Bool())
    val rx_status             = Output(Bool())

    /* Configuration */
    val cfg_rx_prbs31_enable  = Input(Bool())
  })

  // Local Parameter
  val useHdrVld = gbxIfEn || dataW != 64

  // --- Bit Reversal ---
  val serdesRxDataRev = Wire(UInt(dataW.W))
  val serdesRxHdrRev  = Wire(UInt(hdrW.W))

  if (bitReverse) {
    serdesRxDataRev := Reverse(io.serdes_rx_data)
    serdesRxHdrRev  := Reverse(io.serdes_rx_hdr)
  } else {
    serdesRxDataRev := io.serdes_rx_data
    serdesRxHdrRev  := io.serdes_rx_hdr
  }

  // --- Pipeline ---
  val serdesRxDataInt       = Wire(UInt(dataW.W))
  val serdesRxDataValidInt  = Wire(Bool())
  val serdesRxHdrInt        = Wire(UInt(hdrW.W))
  val serdesRxHdrValidInt   = Wire(Bool())

  // Note: Chisel's ShiftRegister creates a pipeline of registers.
  // The SV code creates a shift register logic[...][Pipeline-1:0] and takes index [Pipeline-1].
  // Chisel's ShiftRegister(sig, n) does exactly this.
  if (serdesPipeline > 0) {
    serdesRxDataInt := ShiftRegister(serdesRxDataRev, serdesPipeline)
    
    // Logic for valid signals depends on GBX_IF_EN/USE_HDR_VLD
    val dataValidPipe = ShiftRegister(io.serdes_rx_data_valid, serdesPipeline)
    serdesRxDataValidInt := Mux(gbxIfEn.B, dataValidPipe, true.B)
    
    val hdrPipe = ShiftRegister(serdesRxHdrRev, serdesPipeline)
    serdesRxHdrInt := hdrPipe
    
    val hdrValidPipe = ShiftRegister(io.serdes_rx_hdr_valid, serdesPipeline)
    serdesRxHdrValidInt := Mux(useHdrVld.B, hdrValidPipe, true.B)
  } else {
    serdesRxDataInt := serdesRxDataRev
    serdesRxDataValidInt := Mux(gbxIfEn.B, io.serdes_rx_data_valid, true.B)
    serdesRxHdrInt := serdesRxHdrRev
    serdesRxHdrValidInt := Mux(useHdrVld.B, io.serdes_rx_hdr_valid, true.B)
  }

  // --- Descrambler ---
  val descrambledRxData = Wire(UInt(dataW.W))
  val scramblerStateReg = RegInit(VecInit(Seq.fill(58)(true.B)).asUInt) // '1 in SV means all 1s
  val scramblerState    = Wire(UInt(58.W))

  // Instantiate Descrambler BlackBox
  val descrambler = Module(new Lfsr(
    lfsrW = 58,
    lfsrPoly = BigInt("8000000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = true,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = true
  ))

  descrambler.io.data_in  := serdesRxDataInt
  descrambler.io.state_in := scramblerStateReg
  descrambledRxData       := descrambler.io.data_out
  scramblerState          := descrambler.io.state_out

  // Update Scrambler State
  when(!gbxIfEn.B || serdesRxDataValidInt) {
    scramblerStateReg := scramblerState
  }

  // --- PRBS31 Check ---
  val prbs31StateReg = RegInit(VecInit(Seq.fill(31)(true.B)).asUInt) // '1
  val prbs31State    = Wire(UInt(31.W))
  val prbs31Data     = Wire(UInt((dataW + hdrW).W))
  val prbs31DataReg  = RegInit(0.U((dataW + hdrW).W))

  val prbs31Check = Module(new Lfsr(
    lfsrW = 31,
    lfsrPoly = BigInt("10000001", 16),
    lfsrGalois = false,
    lfsrFeedForward = true,
    reverse = true,
    dataW = dataW + hdrW,
    dataInEn = true,
    dataOutEn = true
  ))

  // SV: ~{serdes_rx_data_int, serdes_rx_hdr_int}
  val prbsInputConcat = Cat(serdesRxDataInt, serdesRxHdrInt)
  prbs31Check.io.data_in  := ~prbsInputConcat
  prbs31Check.io.state_in := prbs31StateReg
  prbs31Data              := prbs31Check.io.data_out
  prbs31State             := prbs31Check.io.state_out

  // --- Error Counting Logic ---
  // Split bit counting (Odd/Even indices)
  val rxErrorCount1Temp = Wire(UInt(6.W))
  val rxErrorCount2Temp = Wire(UInt(6.W))

  // In SV: loop i=0 to W. if(i[0]) add to count1 else count2.
  // i[0] checks if index is odd.
  val prbsBits = prbs31DataReg.asBools // LSB is index 0
  
  // Filter bits based on odd/even index
  val oddBits  = prbsBits.zipWithIndex.filter(_._2 % 2 != 0).map(_._1)
  val evenBits = prbsBits.zipWithIndex.filter(_._2 % 2 == 0).map(_._1)

  rxErrorCount1Temp := PopCount(oddBits)
  rxErrorCount2Temp := PopCount(evenBits)

  // --- Output Registers ---
  val encodedRxDataReg      = RegInit(0.U(dataW.W))
  val encodedRxDataValidReg = RegInit(false.B)
  val encodedRxHdrReg       = RegInit(0.U(hdrW.W))
  val encodedRxHdrValidReg  = RegInit(false.B)

  val rxErrorCountReg   = RegInit(0.U(7.W))
  val rxErrorCount1Reg  = RegInit(0.U(6.W))
  val rxErrorCount2Reg  = RegInit(0.U(6.W))

  encodedRxDataReg      := Mux(scramblerDisable.B, serdesRxDataInt, descrambledRxData)
  encodedRxDataValidReg := serdesRxDataValidInt
  encodedRxHdrReg       := serdesRxHdrInt
  encodedRxHdrValidReg  := serdesRxHdrValidInt

  if (prbs31En) {
    when(io.cfg_rx_prbs31_enable && (!gbxIfEn.B || serdesRxDataValidInt)) {
      prbs31StateReg := prbs31State
      prbs31DataReg  := prbs31Data
    }.otherwise {
      prbs31DataReg  := 0.U
    }

    rxErrorCount1Reg := rxErrorCount1Temp
    rxErrorCount2Reg := rxErrorCount2Temp
    rxErrorCountReg  := rxErrorCount1Reg +& rxErrorCount2Reg
  } else {
    rxErrorCountReg := 0.U
  }

  // --- Final Assignments ---
  io.encoded_rx_data       := encodedRxDataReg
  io.encoded_rx_data_valid := Mux(gbxIfEn.B, encodedRxDataValidReg, true.B)
  io.encoded_rx_hdr        := encodedRxHdrReg
  io.encoded_rx_hdr_valid  := Mux(useHdrVld.B, encodedRxHdrValidReg, true.B)
  
  io.rx_error_count        := rxErrorCountReg

  val serdesRxBitslipInt   = Wire(Bool())
  val serdesRxResetReqInt  = Wire(Bool())

  io.serdes_rx_bitslip     := serdesRxBitslipInt && !(prbs31En.B && io.cfg_rx_prbs31_enable)
  io.serdes_rx_reset_req   := serdesRxResetReqInt && !(prbs31En.B && io.cfg_rx_prbs31_enable)

  // --- Submodule Instantiations ---
  
  val frameSync = Module(new PcsRxFrameSync(hdrW, bitslipHighCycles, bitslipLowCycles))
  frameSync.clock := clock
  frameSync.reset := reset
  frameSync.io.serdesRxHdr := serdesRxHdrInt
  frameSync.io.serdesRxHdrValid := serdesRxHdrValidInt
  serdesRxBitslipInt := frameSync.io.serdesRxBitslip
  io.rx_block_lock := frameSync.io.rxBlockLock

  val berMon = Module(new PcsRxBerMon(hdrW, count125Us))
  berMon.clock := clock
  berMon.reset := reset
  berMon.io.serdes_rx_hdr := serdesRxHdrInt
  berMon.io.serdes_rx_hdr_valid := serdesRxHdrValidInt
  io.rx_high_ber := berMon.io.rx_high_ber

  val watchdog = Module(new PcsRxWatchdog(hdrW, count125Us))
  watchdog.clock := clock
  watchdog.reset := reset
  watchdog.io.serdes_rx_hdr := serdesRxHdrInt
  watchdog.io.serdes_rx_hdr_valid := serdesRxHdrValidInt
  serdesRxResetReqInt := watchdog.io.serdes_rx_reset_req
  watchdog.io.rx_bad_block := io.rx_bad_block
  watchdog.io.rx_sequence_error := io.rx_sequence_error
  watchdog.io.rx_block_lock := io.rx_block_lock 
  watchdog.io.rx_high_ber := io.rx_high_ber
  io.rx_status := watchdog.io.rx_status

}


object PcsRxInterface {
  def apply(p: PcsRxInterfaceParams): PcsRxInterface = Module(new PcsRxInterface(
        dataW = p.dataW,
        hdrW = p.hdrW,
        gbxIfEn = p.gbxIfEn,
        bitReverse = p.bitReverse,
        scramblerDisable = p.scramblerDisable,
        prbs31En = p.prbs31En,
        serdesPipeline = p.serdesPipeline,
        bitslipHighCycles = p.bitslipHighCycles,
        bitslipLowCycles = p.bitslipLowCycles,
        count125Us = p.count125Us
  ))
}

// object Main extends App {
//   val mainClassName = "Pcs"
//   val coreDir = s"modules/${mainClassName.toLowerCase()}"
//   PcsRxInterfaceParams.synConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new PcsRxInterface(
//         dataW = p.dataW,
//         hdrW = p.hdrW,
//         gbxIfEn = p.gbxIfEn,
//         bitReverse = p.bitReverse,
//         scramblerDisable = p.scramblerDisable,
//         prbs31En = p.prbs31En,
//         serdesPipeline = p.serdesPipeline,
//         bitslipHighCycles = p.bitslipHighCycles,
//         bitslipLowCycles = p.bitslipLowCycles,
//         count125Us = p.count125Us
//       ),
//       firtoolOpts = Array(
//         "--lowering-options=disallowLocalVariables,disallowPackedArrays",
//         "--disable-all-randomization",
//         "--strip-debug-info",
//         "--split-verilog",
//         s"-o=${coreDir}/generated/synTestCases/$configName"
//       )
//     )
//     // Synthesis collateral generation
//     sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(mainClassName, PcsRxInterfaceParams.synConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }