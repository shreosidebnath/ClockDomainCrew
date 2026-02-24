import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ComparePcsTester extends AnyFlatSpec with ChiselScalatestTester {
  it should "match Chisel vs original Verilog outputs" in {
    test(new DualWrapperPcs) 
        .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) { dut =>
      // TODO: Add tests for both golden model and our implementation
    }
  }
}
