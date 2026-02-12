package org.chiselware.cores.o01.t001.pcs.rx
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class PcsRxBerMon(
  val hdrW: Int = 2,
  val count125Us: Double = 125000.0 / 6.4
) extends Module {
  val io = IO(new Bundle {
    /*
     * SERDES interface
     */
    val serdes_rx_hdr       = Input(UInt(hdrW.W))
    val serdes_rx_hdr_valid = Input(Bool())

    /*
     * Status
     */
    val rx_high_ber         = Output(Bool())
  })

  // Check configuration
  require(hdrW == 2, "Error: HDR_W must be 2")

  // Calculate parameters
  // SystemVerilog $rtoi rounds to integer (usually truncation)
  val count125UsInt = count125Us.toInt 
  val countW = log2Ceil(count125UsInt + 1)

  // Constants
  val SYNC_DATA = "b10".U(2.W)
  val SYNC_CTRL = "b01".U(2.W)

  // Registers
  // Initialized to match the reset block in the SystemVerilog
  val timeCountReg = RegInit(count125UsInt.U(countW.W))
  val berCountReg  = RegInit(0.U(4.W))
  val rxHighBerReg = RegInit(false.B)

  // Default output assignment
  io.rx_high_ber := rxHighBerReg

  // Logic Implementation
  
  // 1. Timer Decrement Logic
  when(timeCountReg > 0.U) {
    timeCountReg := timeCountReg - 1.U
  }

  // 2. Main BER Logic
  // We use "when" blocks. In Chisel, later assignments within the same clock cycle
  // override earlier ones, mimicking the "next = reg" variable updates in SV.
  when(io.serdes_rx_hdr_valid) {
    val isValidHeader = io.serdes_rx_hdr === SYNC_CTRL || io.serdes_rx_hdr === SYNC_DATA

    when(isValidHeader) {
      // Valid header
      when(berCountReg =/= 15.U) {
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    } .otherwise {
      // Invalid header
      when(berCountReg === 15.U) {
        rxHighBerReg := true.B
      } .otherwise {
        berCountReg := berCountReg + 1.U
        when(timeCountReg === 0.U) {
          rxHighBerReg := false.B
        }
      }
    }
  }

  // 3. Timer Expiration / Reset Logic
  // This block comes last to override previous assignments to timeCountReg or berCountReg
  // if the condition is met (matching the priority in the SV always_comb block).
  when(timeCountReg === 0.U && io.serdes_rx_hdr_valid) {
    berCountReg  := 0.U
    timeCountReg := count125UsInt.U
  }
}

// Generate the Verilog to verify (Optional helper object)
object TaxiEthPhy10gRxBerMon extends App {
  emitVerilog(new TaxiEthPhy10gRxBerMon(), Array("--target-dir", "generated"))
}