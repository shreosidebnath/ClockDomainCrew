package ethernet

import chisel3._

object CounterMain extends App {
  // Generate Verilog for an 8-bit counter
  emitVerilog(new Counter(255), Array("--target-dir", "generated"))
}