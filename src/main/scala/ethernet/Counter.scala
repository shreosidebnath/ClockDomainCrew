package ethernet

import chisel3._
import chisel3.util._

class Counter(maxCount: Int) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val count = Output(UInt(log2Ceil(maxCount + 1).W))
    val overflow = Output(Bool())
  })
  
  val counter = RegInit(0.U(log2Ceil(maxCount + 1).W))
  
  when(io.enable) {
    when(counter === maxCount.U) {
      counter := 0.U
    }.otherwise {
      counter := counter + 1.U
    }
  }
  
  io.count := counter
  io.overflow := io.enable && (counter === maxCount.U)
}