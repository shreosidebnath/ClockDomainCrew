import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CompareMacTester extends AnyFlatSpec with ChiselScalatestTester {
  it should "match Chisel vs original Verilog outputs" in {
    test(new DualWrapperMac) 
        .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) { dut =>
      // TODO: Add tests for both golden model and our implementation
      
    }
  }
}
