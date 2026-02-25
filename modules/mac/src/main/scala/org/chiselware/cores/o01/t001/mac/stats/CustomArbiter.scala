package org.chiselware.cores.o01.t001.mac.stats
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class CustomArbiter(
  val ports: Int = 4,
  val arbRoundRobin: Boolean = true,
  val arbBlock: Boolean = true,
  val arbBlockAck: Boolean = false,
  val lsbHighPrio: Boolean = false
) extends Module {
  val io = IO(new Bundle {
    val req = Input(UInt(ports.W))
    val ack = Input(UInt(ports.W))
    
    val grantValid = Output(Bool())
    val grant = Output(UInt(ports.W))
    val grantIndex = Output(UInt(log2Ceil(ports).W))
  })

  // Synchronous Registers (Equivalent to always_ff @(posedge clk) with rst)
  val grant_reg = RegInit(0.U(ports.W))
  val grant_valid_reg = RegInit(false.B)
  val grant_index_reg = RegInit(0.U(log2Ceil(ports).W))
  val mask_reg = RegInit(0.U(ports.W))

  // Assign Outputs
  io.grant := grant_reg
  io.grantValid := grant_valid_reg
  io.grantIndex := grant_index_reg

  // Helper function to dynamically create the priority encoder module
  def getPenc(in: UInt): (Bool, UInt, UInt) = {
    val valid = in.orR
    val index = Wire(UInt(log2Ceil(ports).W))
    
    if (lsbHighPrio) {
      index := PriorityEncoder(in)
    } else {
      // For MSB priority, reverse the bits, encode, and invert the index
      index := (ports - 1).U - PriorityEncoder(Reverse(in))
    }
    
    // UIntToOH handles the shifting for the one-hot mask, we just truncate it to ports width
    val mask = Mux(valid, UIntToOH(index)(ports - 1, 0), 0.U(ports.W))
    (valid, index, mask)
  }

  // Request Evaluation
  val (req_valid, req_index, req_mask) = getPenc(io.req)
  
  val masked_req = io.req & mask_reg
  val (masked_req_valid, masked_req_index, masked_req_mask) = 
    if (arbRoundRobin) getPenc(masked_req) else (false.B, 0.U, 0.U)

  // Next State Logic Wires (Equivalent to always_comb assignments)
  val grant_next = WireDefault(0.U(ports.W))
  val grant_valid_next = WireDefault(false.B)
  val grant_index_next = WireDefault(0.U(log2Ceil(ports).W))
  val mask_next = WireDefault(mask_reg)

  // Full bitmask of 1s
  val allOnes = ((BigInt(1) << ports) - 1).U(ports.W)

  // Pre-calculate block states for clean "when" logic
  val isBlockUnack = arbBlock && !arbBlockAck
  val isBlockAck = arbBlock && arbBlockAck

  // Arbitration Logic
  when(isBlockUnack.B && (grant_reg & io.req).orR) {
    // Granted req still asserted; hold it
    grant_valid_next := grant_valid_reg
    grant_next := grant_reg
    grant_index_next := grant_index_reg
    
  }.elsewhen(isBlockAck.B && grant_valid_reg && (grant_reg & io.ack) === 0.U) {
    // Granted req not yet acknowledged; hold it
    grant_valid_next := grant_valid_reg
    grant_next := grant_reg
    grant_index_next := grant_index_reg
    
  }.elsewhen(req_valid) {
    if (arbRoundRobin) {
      when(masked_req_valid) {
        grant_valid_next := true.B
        grant_next := masked_req_mask
        grant_index_next := masked_req_index
        
        if (lsbHighPrio) {
          mask_next := (allOnes << (masked_req_index + 1.U))(ports - 1, 0)
        } else {
          mask_next := (allOnes >> (ports.U - masked_req_index))(ports - 1, 0)
        }
      }.otherwise {
        grant_valid_next := true.B
        grant_next := req_mask
        grant_index_next := req_index
        
        if (lsbHighPrio) {
          mask_next := (allOnes << (req_index + 1.U))(ports - 1, 0)
        } else {
          mask_next := (allOnes >> (ports.U - req_index))(ports - 1, 0)
        }
      }
    } else {
      grant_valid_next := true.B
      grant_next := req_mask
      grant_index_next := req_index
    }
  }

  // Register Updates
  grant_reg := grant_next
  grant_valid_reg := grant_valid_next
  grant_index_reg := grant_index_next
  mask_reg := mask_next
}



object CustomArbiter {
  def apply(p: CustomArbiterParams): CustomArbiter = Module(new CustomArbiter(
    ports = p.ports, 
    arbRoundRobin = p.arbRoundRobin,
    arbBlock = p.arbBlock,
    arbBlockAck = p.arbBlockAck,
    lsbHighPrio = p.lsbHighPrio
  ))
}

object Main extends App {
  val mainClassName = "Mac"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  CustomArbiterParams.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new CustomArbiter(
        ports = p.ports, 
        arbRoundRobin = p.arbRoundRobin,
        arbBlock = p.arbBlock,
        arbBlockAck = p.arbBlockAck,
        lsbHighPrio = p.lsbHighPrio
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
    RunScriptFile.create(mainClassName, CustomArbiterParams.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}