package org.chiselware.cores.o01.t001.mac.stats
import chisel3._
import chisel3.util._


class CustomArbiterTb(val p: CustomArbiterParams) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Bool())
    val req = Input(UInt(p.ports.W))
    val ack = Input(UInt(p.ports.W))
    val grantValid = Output(Bool())
    val grant = Output(UInt(p.ports.W))
    val grantIndex = Output(UInt(log2Ceil(p.ports).W))
  })

  val dut = Module(new CustomArbiter(
    ports = p.ports, 
    arbRoundRobin = p.arbRoundRobin,
    arbBlock = p.arbBlock,
    arbBlockAck = p.arbBlockAck,
    lsbHighPrio = p.lsbHighPrio
  ))
    // clock and reset is implicitly created by Chisel (it's always name "clock" and "reset")
    dut.clock := io.clk
    dut.reset := io.rst
    dut.io.req   := io.req
    dut.io.ack   := io.ack
    io.grantValid   := dut.io.grantValid
    io.grant  := dut.io.grant
    io.grantIndex  := dut.io.grantIndex
}