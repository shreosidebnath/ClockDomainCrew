package org.chiselware.cores.o01.t001.nfmac10g
import chisel3._
import chisel3.util._

// RX Pause Frame Handler Module
class RxPause extends Module {
  val io = IO(new Bundle {
    // Clock and Reset (implicit in Module)
    val rst = Input(Bool())
    
    // AXIS Input
    val aresetn = Input(Bool())
    val tdata_i = Input(UInt(64.W))
    val tkeep_i = Input(UInt(8.W))
    val tvalid_i = Input(Bool())
    val tlast_i = Input(Bool())
    val tuser_i = Input(UInt(1.W))  // 1 indicates good CRC, 0 bad CRC
    
    // AXIS Output
    val tuser_o = Output(UInt(1.W))
    
    // Configuration
    val cfg_rx_pause_enable = Input(Bool())
    val cfg_sub_quanta_count = Input(UInt(8.W))  // Number of clock cycles per quanta
    
    // Pause control output
    val rx_pause_active = Output(Bool())
  })

  // Control frame constants
  val PAUSE_OPCODE = 0x0001.U(16.W)
  val control_da = Cat(
    0x01.U(8.W), 0x80.U(8.W), 0xC2.U(8.W),
    0x00.U(8.W), 0x00.U(8.W), 0x01.U(8.W)
  )
  val control_et = Cat(0x88.U(8.W), 0x08.U(8.W))

  // FSM State definitions
  val s_idle = 0.U(3.W)
  val s_normal = 1.U(3.W)
  val s_control_1 = 2.U(3.W)
  val s_control_2 = 3.U(3.W)
  val s_control_3 = 4.U(3.W)

  // Registers
  val state = RegInit(s_idle)
  val nxt_state = Wire(UInt(3.W))
  
  val opcode = RegInit(0.U(16.W))
  val nxt_opcode = Wire(UInt(16.W))
  
  val quanta = RegInit(0.U(16.W))
  val nxt_quanta = Wire(UInt(16.W))
  
  val pause_count = RegInit(0.U(16.W))
  val nxt_pause_count = Wire(UInt(16.W))
  
  val sub_count = RegInit(0.U(8.W))
  val nxt_sub_count = Wire(UInt(8.W))
  
  val new_quanta = Wire(Bool())
  val tuser_o_reg = RegInit(0.U(1.W))
  val rx_pause_active_reg = RegInit(false.B)

  // Output assignments
  io.tuser_o := tuser_o_reg
  io.rx_pause_active := rx_pause_active_reg

  // Combinational logic
  nxt_state := state
  tuser_o_reg := io.tuser_i
  nxt_opcode := opcode
  nxt_quanta := quanta
  new_quanta := false.B
  nxt_pause_count := pause_count
  nxt_sub_count := sub_count

  // Count down pause counter until zero
  // Link is paused when count > 0
  when((pause_count > 0.U) && io.cfg_rx_pause_enable) {
    when(sub_count === (io.cfg_sub_quanta_count - 1.U)) {
      nxt_sub_count := 0.U
      nxt_pause_count := pause_count - 1.U
    }.otherwise {
      nxt_sub_count := sub_count + 1.U
    }
  }.otherwise {
    nxt_pause_count := pause_count
    nxt_sub_count := 0.U
  }

  // FSM state machine logic
  switch(state) {
    is(s_idle) {
      when(io.tvalid_i) {
        when(io.tdata_i(47, 0) === control_da) {
          nxt_state := s_control_1
        }.otherwise {
          nxt_state := s_normal
        }
      }
    }

    is(s_control_1) {
      when(io.tvalid_i) {
        when(io.tdata_i(47, 32) === control_et) {
          nxt_opcode := Cat(io.tdata_i(55, 48), io.tdata_i(63, 56))
          nxt_state := s_control_2
        }.otherwise {
          nxt_state := s_normal
        }
      }
    }

    is(s_control_2) {
      when(io.tvalid_i) {
        when(opcode === PAUSE_OPCODE) {
          nxt_quanta := Cat(io.tdata_i(7, 0), io.tdata_i(15, 8))
          nxt_state := s_control_3
        }.otherwise {
          nxt_state := s_normal
        }
      }
    }

    is(s_control_3) {
      when(io.tvalid_i && io.tlast_i) {
        nxt_state := s_idle
        // If frame is valid (good CRC), load new quanta into pause counter
        when(io.tuser_i(0)) {
          nxt_pause_count := quanta
        }
      }
    }

    is(s_normal) {
      when(io.tvalid_i && io.tlast_i) {
        nxt_state := s_idle
      }
    }
  }

  // Default case - return to idle on invalid state
  when(state =/= s_idle && state =/= s_normal && 
       state =/= s_control_1 && state =/= s_control_2 && state =/= s_control_3) {
    nxt_state := s_idle
  }

  // Sequential logic
  when(io.rst) {
    pause_count := 0.U
    sub_count := 0.U
    state := s_idle
    opcode := 0.U
    quanta := 0.U
    rx_pause_active_reg := false.B
  }.otherwise {
    pause_count := nxt_pause_count
    sub_count := nxt_sub_count
    state := nxt_state
    opcode := nxt_opcode
    quanta := nxt_quanta
    rx_pause_active_reg := (pause_count > 0.U)
  }
}

object RxPause {
  def apply(): RxPause = Module(new RxPause)
}
