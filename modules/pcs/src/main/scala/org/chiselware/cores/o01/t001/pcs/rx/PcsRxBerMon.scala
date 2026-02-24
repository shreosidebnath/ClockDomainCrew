package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._

class PcsRxBerMon(
    val hdrW: Int = 2,
    val count125Us: Double = 125000.0 / 6.4
  ) extends Module {
  val io = IO(new Bundle {
    // SERDES interface
    val serdesRxHdr = Input(UInt(hdrW.W))
    val serdesRxHdrValid = Input(Bool())

    // Status
    val rxHighBer = Output(Bool())
  })

  require(hdrW == 2, "Error: HDR_W must be 2")

  val count125UsInt = count125Us.toInt
  val countW = log2Ceil(count125UsInt + 1)

  // Registers
  val timeCountReg = RegInit(count125UsInt.U(countW.W))
  val berCountReg = RegInit(0.U(4.W))
  val rxHighBerReg = RegInit(false.B)

  io.rxHighBer := rxHighBerReg

  // 1. Timer Decrement Logic
  when(timeCountReg > 0.U) {
    timeCountReg := timeCountReg - 1.U
  }

  // 2. Main BER Logic
  when(io.serdesRxHdrValid) {
    val isValidHeader =
      io.serdesRxHdr === PcsRxBerMon.SyncCtrl || io.serdesRxHdr === PcsRxBerMon.SyncData

    when(isValidHeader) {
      when(berCountReg =/= 15.U) {
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    }.otherwise {
      when(berCountReg === 15.U) {
        rxHighBerReg := true.B
      }.otherwise {
        berCountReg := berCountReg + 1.U
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    }
  }

  // 3. Timer Expiration / Reset Logic
  when(timeCountReg === 0.U && io.serdesRxHdrValid) {
    berCountReg := 0.U
    timeCountReg := count125UsInt.U
  }
}

object PcsRxBerMon {
  val SyncData = "b10".U(2.W)
  val SyncCtrl = "b01".U(2.W)

  def apply(p: PcsRxBerMonParams): PcsRxBerMon = Module(new PcsRxBerMon(
    hdrW = p.hdrW,
    count125Us = p.count125Us
  ))
}

// object Main extends App {
//   val MainClassName = "Pcs"
//   val coreDir = s"modules/${MainClassName.toLowerCase()}"
//   PcsRxBerMonParams.SynConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new PcsRxBerMon(
//         hdrW = p.hdrW, count125Us = p.count125Us
//       ),
//       firtoolOpts = Array(
//         "--lowering-options=disallowLocalVariables,disallowPackedArrays",
//         "--disable-all-randomization",
//         "--strip-debug-info",
//         "--split-verilog",
//         s"-o=${coreDir}/generated/synTestCases/$configName"
//       )
//     )
//     SdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(mainClassName = MainClassName, outputDir = s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(mainClassName = MainClassName, outputDir = s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(mainClassName = MainClassName, synConfigs = PcsRxBerMonParams.SynConfigs, outputDir = s"${coreDir}/generated/synTestCases")
//   }
// }