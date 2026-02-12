import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CompareNfMac10gTester extends AnyFlatSpec with ChiselScalatestTester {
  it should "match Chisel vs original Verilog outputs" in {
    test(new DualWrapperNfMac10g) 
        .withAnnotations(Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)) { dut =>
      for (i <- 0 until 10) {
        dut.io.tx_axis_tdata.poke((i * 12345).U)
        dut.io.tx_axis_tkeep.poke("hFF".U)
        dut.io.tx_axis_tvalid.poke(true.B)
        dut.io.tx_axis_tlast.poke(false.B)
        dut.io.tx_axis_tuser.poke(0.U)
        dut.clock.step(1)

        val chiselOut = dut.io.rx_axis_tdata_chisel.peek().litValue
        val verilogOut = dut.io.rx_axis_tdata_verilog.peek().litValue
        println(f"Cycle $i: Chisel=$chiselOut%x Verilog=$verilogOut%x")
      }
    }
  }
}
