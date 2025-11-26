package ethernet

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CounterTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Counter"
  
  it should "count up when enabled" in {
    test(new Counter(7)) { dut =>
      dut.io.enable.poke(true.B)
      
      // Test counting from 0 to 7
      for (i <- 0 until 8) {
        dut.io.count.expect(i.U)
        dut.clock.step()
      }
      
      // Should wrap around to 0
      dut.io.count.expect(0.U)
    }
  }
  
  it should "not count when disabled" in {
    test(new Counter(7)) { dut =>
      dut.io.enable.poke(false.B)
      dut.io.count.expect(0.U)
      
      // Step clock several times
      for (_ <- 0 until 5) {
        dut.clock.step()
        dut.io.count.expect(0.U) // Should stay at 0
      }
    }
  }
}