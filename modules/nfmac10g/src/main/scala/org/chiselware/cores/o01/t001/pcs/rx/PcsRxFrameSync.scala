package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class PcsRxFrameSync(
  hdrW: Int = 2, // correct
  bitslipHighCycles: Int = 1, // Should be 0
  bitslipLowCycles: Int = 7 // correct
) extends Module {
  
  // --------------------------------------------------------
  // Check Configuration
  // --------------------------------------------------------
  // SystemVerilog: if (HDR_W != 2) $fatal(0, "Error: HDR_W must be 2");
  require(hdrW == 2, "Error: HDR_W must be 2")

  // --------------------------------------------------------
  // IO Interface
  // --------------------------------------------------------
  val io = IO(new Bundle {
    /*
     * SERDES interface
     */
    val serdesRxHdr       = Input(UInt(hdrW.W))
    val serdesRxHdrValid  = Input(Bool())
    val serdesRxBitslip   = Output(Bool())

    /*
     * Status
     */
    val rxBlockLock       = Output(Bool())
  })

  // --------------------------------------------------------
  // Local Constants & Parameters
  // --------------------------------------------------------
  val bitslipMaxCycles = if (bitslipHighCycles > bitslipLowCycles) bitslipHighCycles else bitslipLowCycles
  
  // SystemVerilog: localparam BITSLIP_COUNT_W = $clog2(BITSLIP_MAX_CYCLES);
  // We add 1 to allow strictly 'bitslipMaxCycles' value storage without overflow if it's a power of 2
  val bitslipCountW    = log2Ceil(bitslipMaxCycles + 1) 

  val syncData = "b10".U(2.W)
  val syncCtrl = "b01".U(2.W)

  // --------------------------------------------------------
  // Registers
  // --------------------------------------------------------
  // SystemVerilog: logic [5:0] sh_count_reg = 6'd0;
  val shCountReg        = RegInit(0.U(6.W))
  
  // SystemVerilog: logic [3:0] sh_invalid_count_reg = 4'd0;
  val shInvalidCountReg = RegInit(0.U(4.W))
  
  // SystemVerilog: logic [BITSLIP_COUNT_W-1:0] bitslip_count_reg = '0;
  val bitslipCountReg   = RegInit(0.U(bitslipCountW.W))
  
  // SystemVerilog: logic serdes_rx_bitslip_reg = 1'b0;
  val serdesRxBitslipReg = RegInit(false.B)
  
  // SystemVerilog: logic rx_block_lock_reg = 1'b0;
  val rxBlockLockReg     = RegInit(false.B)

  // --------------------------------------------------------
  // Next State Logic (Combinational)
  // --------------------------------------------------------
  // Initialize 'next' signals to current register values (default behavior)
  val shCountNext         = WireDefault(shCountReg)
  val shInvalidCountNext  = WireDefault(shInvalidCountReg)
  val bitslipCountNext    = WireDefault(bitslipCountReg)
  val serdesRxBitslipNext = WireDefault(serdesRxBitslipReg)
  val rxBlockLockNext     = WireDefault(rxBlockLockReg)

  // Helper values for reduction operators (&sh_count_reg)
  val shCountAllOnes        = shCountReg.andR
  val shInvalidCountAllOnes = shInvalidCountReg.andR

  // Logic
  when(bitslipCountReg =/= 0.U) {
    bitslipCountNext := bitslipCountReg - 1.U
  }.elsewhen(serdesRxBitslipReg) {
    serdesRxBitslipNext := false.B
    bitslipCountNext    := bitslipLowCycles.U(bitslipCountW.W)
  }.elsewhen(!io.serdesRxHdrValid) {
    // wait for header - do nothing (defaults hold)
  }.elsewhen(io.serdesRxHdr === syncCtrl || io.serdesRxHdr === syncData) {
    // valid header
    shCountNext := shCountReg + 1.U
    
    // Check for &sh_count_reg (Overflow)
    when(shCountAllOnes) {
      // valid count overflow, reset
      shCountNext        := 0.U
      shInvalidCountNext := 0.U
      when(shInvalidCountReg === 0.U) {
        rxBlockLockNext := true.B
      }
    }
  }.otherwise {
    // invalid header
    shCountNext        := shCountReg + 1.U
    shInvalidCountNext := shInvalidCountReg + 1.U

    when(!rxBlockLockReg || shInvalidCountAllOnes) {
      // invalid count overflow, lost block lock
      shCountNext        := 0.U
      shInvalidCountNext := 0.U
      rxBlockLockNext    := false.B

      // slip one bit
      serdesRxBitslipNext := true.B
      bitslipCountNext    := bitslipHighCycles.U(bitslipCountW.W)
    }.elsewhen(shCountAllOnes) {
      // valid count overflow, reset
      shCountNext        := 0.U
      shInvalidCountNext := 0.U
    }
  }

  // --------------------------------------------------------
  // Register Updates
  // --------------------------------------------------------
  // In Chisel, assigning to a Reg automatically creates the FF behavior.
  // The RegInit handles the reset logic defined in the SV `if(rst)` block.
  shCountReg        := shCountNext
  shInvalidCountReg := shInvalidCountNext
  bitslipCountReg   := bitslipCountNext
  serdesRxBitslipReg := serdesRxBitslipNext
  rxBlockLockReg    := rxBlockLockNext

  // --------------------------------------------------------
  // Output Assignments
  // --------------------------------------------------------
  io.serdesRxBitslip := serdesRxBitslipReg
  io.rxBlockLock     := rxBlockLockReg
}