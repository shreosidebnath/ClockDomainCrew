package org.chiselware.cores.o01.t001.nfmac10g
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}


/** * RstMod is a reset module that generates a synchronized reset signal based on
  * an asynchronous reset input and a DCM locked signal. It uses a finite state
  * machine (FSM) to manage the reset sequence.
  *
  */
class RstMod extends Module {
  val io = IO(new Bundle {
    // Inputs
    val reset = Input(Bool())      // Async reset input
    val dcm_locked = Input(Bool()) // DCM locked signal
    
    // Output
    val rst = Output(Bool())       // Synchronized reset output
  })

  // FSM state encoding (one-hot)
  val s0 = 0.U(8.W)
  val s1 = 1.U(8.W)
  val s2 = 2.U(8.W)
  val s3 = 3.U(8.W)
  val s4 = 4.U(8.W)
  val s5 = 5.U(8.W)
  val s6 = 6.U(8.W)
  val s7 = 7.U(8.W)

  // State machine register
  val fsm = RegInit(s0)
  
  // Reset output register
  val rst_reg = RegInit(true.B)
  
  // Connect output
  io.rst := rst_reg

  // State machine logic
  when(io.reset) {
    // Reset state
    rst_reg := true.B
    fsm := s0
  }.otherwise {
    // Normal operation
    switch(fsm) {
      is(s0) {
        rst_reg := true.B
        fsm := s1
      }

      is(s1) {
        fsm := s2
      }

      is(s2) {
        fsm := s3
      }

      is(s3) {
        fsm := s4
      }

      is(s4) {
        when(io.dcm_locked) {
          fsm := s5
        }
      }

      is(s5) {
        rst_reg := false.B
      }
    }

    // Default case - go back to s0
    when(fsm =/= s0 && fsm =/= s1 && fsm =/= s2 && 
         fsm =/= s3 && fsm =/= s4 && fsm =/= s5) {
      fsm := s0
    }
  }
}

// Companion object for easy instantiation
object RstMod {
  def apply(): RstMod = Module(new RstMod)
}


/** Generate Verilog and related collateral for each configuration to be used as
  * part of the regression framework.
  */
object Main extends App {
  val mainClassName = "RstMod"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  RstModParams.synConfigMap.foreach { case (configName, configParams) =>
    println()
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new RstMod(),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )

    // Generate synthesis files and scripts
    sdcFile.create(
      s"${coreDir}/generated/synTestCases/$configName"
    )
    YosysTclFile.create(
      mainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    StaTclFile.create(
      mainClassName,
      s"${coreDir}/generated/synTestCases/$configName"
    )
    RunScriptFile.create(
      mainClassName,
      RstModParams.synConfigs,
      s"${coreDir}/generated/synTestCases"
    )
  }
}
