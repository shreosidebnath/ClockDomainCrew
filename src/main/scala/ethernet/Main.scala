package ethernet
import chisel3._

object MacMain extends App {
  
  emitVerilog(new EthMacDefinitions(), Array("--target-dir", "generated"))
}