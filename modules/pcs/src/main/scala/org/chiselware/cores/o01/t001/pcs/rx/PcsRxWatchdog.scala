package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class PcsRxWatchdog(
  val hdrW: Int = 2,
  // FIXED: Force floating point division before casting to Int
  // 125000 / 6.4 = 19531.25 -> 19531
  val count125us: Double = (125000.0 / 6.4)
) extends Module {
  val io = IO(new Bundle {
    val serdes_rx_hdr = Input(UInt(hdrW.W))
    val serdes_rx_hdr_valid = Input(Bool())
    val serdes_rx_reset_req = Output(Bool())

    val rx_bad_block = Input(Bool())
    val rx_sequence_error = Input(Bool())
    val rx_block_lock = Input(Bool())
    val rx_high_ber = Input(Bool()) // Note: Input present in port list but unused in SV logic

    val rx_status = Output(Bool())
  })

  // Constants
  val SYNC_CTRL = "b01".U(2.W)
  val count125UsInt = count125us.toInt
  // Registers
  // We use log2Ceil(count + 1) to ensure the width fits the exact count value
  val time_count_reg = RegInit(count125UsInt.U(log2Ceil(count125UsInt + 1).W))
  val error_count_reg = RegInit(0.U(4.W))
  val status_count_reg = RegInit(0.U(4.W))
  
  val saw_ctrl_sh_reg = RegInit(false.B)
  val block_error_count_reg = RegInit(0.U(10.W))
  
  val serdes_rx_reset_req_reg = RegInit(false.B)
  val rx_status_reg = RegInit(false.B)

  // Outputs
  io.serdes_rx_reset_req := serdes_rx_reset_req_reg
  io.rx_status := rx_status_reg

  // ---------------------------------------------------------
  // Next State Logic (Mimicking SV always_comb "last assignment wins")
  // ---------------------------------------------------------
  
  // 1. Default assignments (hold state)
  val time_count_next = WireDefault(time_count_reg)
  val error_count_next = WireDefault(error_count_reg)
  val status_count_next = WireDefault(status_count_reg)
  val saw_ctrl_sh_next = WireDefault(saw_ctrl_sh_reg)
  val block_error_count_next = WireDefault(block_error_count_reg)
  val rx_status_next = WireDefault(rx_status_reg)
  
  // Default for reset pulse is always 0 (unless triggered below)
  val serdes_rx_reset_req_next = WireDefault(false.B)

  // 2. Monitor Logic
  when (io.rx_block_lock) {
    // Check for Sync Control
    when (io.serdes_rx_hdr === SYNC_CTRL && io.serdes_rx_hdr_valid) {
      saw_ctrl_sh_next := true.B
    }
    // Check for Block Errors (saturating counter)
    when ((io.rx_bad_block || io.rx_sequence_error) && !block_error_count_reg.andR) {
      block_error_count_next := block_error_count_reg + 1.U
    }
  } .otherwise {
    rx_status_next := false.B
    status_count_next := 0.U
  }

  // 3. Timer Logic (Overrides Monitor Logic where conflicts exist)
  when (time_count_reg =/= 0.U) {
    time_count_next := time_count_reg - 1.U
  } .otherwise {
    // Timer Expired: Reset timer
    time_count_next := count125UsInt.U

    // Check interval health
    when (!saw_ctrl_sh_reg || block_error_count_reg.andR) {
      // Bad interval
      error_count_next := error_count_reg + 1.U
      status_count_next := 0.U
    } .otherwise {
      // Good interval
      error_count_next := 0.U
      when (!status_count_reg.andR) {
        status_count_next := status_count_reg + 1.U
      }
    }

    // Error saturation trigger
    when (error_count_reg.andR) {
      error_count_next := 0.U
      serdes_rx_reset_req_next := true.B
    }

    // Status saturation trigger
    when (status_count_reg.andR) {
      rx_status_next := true.B
    }

    // Clear interval flags (Important: Overrides Monitor Logic above)
    saw_ctrl_sh_next := false.B
    block_error_count_next := 0.U
  }

  // ---------------------------------------------------------
  // State Updates
  // ---------------------------------------------------------
  time_count_reg := time_count_next
  error_count_reg := error_count_next
  status_count_reg := status_count_next
  saw_ctrl_sh_reg := saw_ctrl_sh_next
  block_error_count_reg := block_error_count_next
  rx_status_reg := rx_status_next
  serdes_rx_reset_req_reg := serdes_rx_reset_req_next
}

object PcsRxWatchdog {
  def apply(p: PcsRxWatchdogParams): PcsRxWatchdog = Module(new PcsRxWatchdog(
    hdrW = p.hdrW, count125us = p.count125us
  ))
}

// object Main extends App {
//   val mainClassName = "Pcs"
//   val coreDir = s"modules/${mainClassName.toLowerCase()}"
//   PcsRxWatchdogParams.synConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new PcsRxWatchdog(
//         hdrW = p.hdrW, count125us = p.count125us
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
//     RunScriptFile.create(mainClassName, PcsRxWatchdogParams.synConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }