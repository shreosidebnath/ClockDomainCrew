package org.chiselware.cores.o01.t001
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class Lfsr(
  val lfsrW: Int = 31,
  val lfsrPoly: BigInt = BigInt("10000001", 16),
  val lfsrGalois: Boolean = false,
  val lfsrFeedForward: Boolean = false,
  val reverse: Boolean = false,
  val dataW: Int = 8,
  val dataInEn: Boolean = true,
  val dataOutEn: Boolean = true
) extends RawModule {
  val io = IO(new Bundle {
    val data_in = Input(UInt(dataW.W))
    val state_in = Input(UInt(lfsrW.W))
    val data_out = Output(UInt(dataW.W))
    val state_out = Output(UInt(lfsrW.W))
  })

  // --- Procedural Generation of Next State Logic (Matrix Calculation) ---
  // In Chisel, we perform the "simulation" of the LFSR shifting at elaboration time (Scala)
  // to build the XOR dependencies for the hardware.

  // 1. Initialize dependency trackers
  // v_state[i] tracks which input state bits affect bit i
  // v_data[i] tracks which input data bits affect bit i
  var v_state = Array.tabulate(lfsrW)(i => (BigInt(1) << i)) 
  var v_data  = Array.fill(lfsrW)(BigInt(0)) 
  
  // Output trackers
  var v_out_state = Array.fill(dataW)(BigInt(0))
  var v_out_data  = Array.fill(dataW)(BigInt(0))

  // 2. Simulate the LFSR shifting loop 'dataW' times
  for (k <- 0 until dataW) {
     val data_idx = if (reverse) k else dataW - 1 - k 
     
     // Current MSB used for feedback
     var state_val = v_state(lfsrW - 1)
     var data_val_eq  = v_data(lfsrW - 1)

     // Add input data dependency (XOR)
     data_val_eq = data_val_eq ^ (BigInt(1) << data_idx)

     if (lfsrGalois) {
       // --- Galois Configuration ---
       // Shift registers
       for (j <- lfsrW - 1 until 0 by -1) {
         v_state(j) = v_state(j-1)
         v_data(j)  = v_data(j-1)
       }
       // Shift output capture
       for (j <- dataW - 1 until 0 by -1) {
         v_out_state(j) = v_out_state(j-1)
         v_out_data(j)  = v_out_data(j-1)
       }
       
       // Output logic
       v_out_state(0) = state_val
       v_out_data(0)  = data_val_eq 

       if (lfsrFeedForward) {
         // In Feed Forward, state clears, input drives next state
         state_val = 0
         data_val_eq = (BigInt(1) << data_idx) 
       }

       v_state(0) = state_val
       v_data(0)  = data_val_eq

       // Galois Taps
       for (j <- 1 until lfsrW) {
         if (((lfsrPoly >> j) & 1) == 1) {
           v_state(j) = v_state(j) ^ state_val
           v_data(j)  = v_data(j) ^ data_val_eq
         }
       }

     } else {
       // --- Fibonacci Configuration ---
       // Calculate feedback from taps
       for (j <- 1 until lfsrW) {
         if (((lfsrPoly >> j) & 1) == 1) {
           state_val = state_val ^ v_state(j-1)
           data_val_eq = data_val_eq ^ v_data(j-1)
         }
       }

       // Shift
       for (j <- lfsrW - 1 until 0 by -1) {
         v_state(j) = v_state(j-1)
         v_data(j)  = v_data(j-1)
       }
       for (j <- dataW - 1 until 0 by -1) {
         v_out_state(j) = v_out_state(j-1)
         v_out_data(j)  = v_out_data(j-1)
       }

       v_out_state(0) = state_val
       v_out_data(0)  = data_val_eq

       if (lfsrFeedForward) {
         state_val = 0
         data_val_eq = (BigInt(1) << data_idx)
       }

       v_state(0) = state_val
       v_data(0)  = data_val_eq
     }
  }

  // --- 3. Generate Hardware Logic from Masks ---
  
  // State Output
  val next_state = Wire(Vec(lfsrW, Bool()))
  for (i <- 0 until lfsrW) {
    // If reverse is true, we need to map to the inverted mask index
    val mask_i = if (reverse) lfsrW - 1 - i else i
    
    val state_contrib = (0 until lfsrW)
      .filter(b => ((v_state(mask_i) >> b) & 1) == 1)
      // Mirror the state_in pin mapping if reversed
      .map(b => io.state_in(if (reverse) lfsrW - 1 - b else b))
      
    val data_contrib  = (0 until dataW)
      .filter(b => ((v_data(mask_i) >> b) & 1) == 1)
      // data_in does NOT need mirroring here because data_idx was flipped in the sim loop
      .map(b => io.data_in(b)) 
      
    val all_contribs = state_contrib ++ (if (dataInEn) data_contrib else Seq())
    
    if (all_contribs.nonEmpty) next_state(i) := all_contribs.reduce(_ ^ _) 
    else next_state(i) := false.B
  }
  io.state_out := next_state.asUInt

  // Data Output
  val data_out_wire = Wire(Vec(dataW, Bool()))
  for (i <- 0 until dataW) {
    val mask_idx = if (reverse) dataW - 1 - i else i
    
    val s_mask = v_out_state(mask_idx)
    val d_mask = v_out_data(mask_idx)
    
    val state_contrib = (0 until lfsrW)
      .filter(b => ((s_mask >> b) & 1) == 1)
      // Mirror the state_in pin mapping if reversed
      .map(b => io.state_in(if (reverse) lfsrW - 1 - b else b))
      
    val data_contrib  = (0 until dataW)
      .filter(b => ((d_mask >> b) & 1) == 1)
      .map(b => io.data_in(b))
    
    val all_contribs = state_contrib ++ (if (dataInEn) data_contrib else Seq())
    
    if (dataOutEn) {
       if (all_contribs.nonEmpty) data_out_wire(i) := all_contribs.reduce(_ ^ _) 
       else data_out_wire(i) := false.B
    } else {
       data_out_wire(i) := false.B
    }
  }
  io.data_out := data_out_wire.asUInt
}

object Lfsr {
  def apply(p: LfsrParams): Lfsr = Module(new Lfsr(
    lfsrW = p.lfsrW, lfsrPoly = p.lfsrPoly, lfsrGalois = p.lfsrGalois,
    lfsrFeedForward = p.lfsrFeedForward, reverse = p.reverse, dataW = p.dataW,
    dataInEn = p.dataInEn, dataOutEn = p.dataOutEn
  ))
}

object Main extends App {
  val mainClassName = "Pcs"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  LfsrParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Lfsr(
        lfsrW = p.lfsrW, lfsrPoly = p.lfsrPoly, lfsrGalois = p.lfsrGalois,
        lfsrFeedForward = p.lfsrFeedForward, reverse = p.reverse, dataW = p.dataW,
        dataInEn = p.dataInEn, dataOutEn = p.dataOutEn
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
    RunScriptFile.create(mainClassName, LfsrParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}