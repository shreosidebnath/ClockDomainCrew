import chisel3._
import chisel3.experimental.BundleLiterals._

class DualWrapperMac extends Module {
  val io = IO(new Bundle {

  })

  // Instantiate both versions
  // val chiselDut = Module(new MAC) currently being implemented
  val origDut   = Module(new MacBlackBox)

  // 👇 Nukes all unconnected inputs with "whatever" to avoid FIRRTL init errors
  chiselDut.io := DontCare
  origDut.io   := DontCare

  // Now reconnect only the meaningful signals for comparison
  chiselDut.io.tx_axis_tdata  := io.tx_axis_tdata
  chiselDut.io.tx_axis_tvalid := io.tx_axis_tvalid
  chiselDut.io.tx_axis_tkeep  := io.tx_axis_tkeep
  chiselDut.io.tx_axis_tlast  := io.tx_axis_tlast
  chiselDut.io.tx_axis_tuser  := io.tx_axis_tuser

  origDut.io.tx_axis_tdata  := io.tx_axis_tdata
  origDut.io.tx_axis_tvalid := io.tx_axis_tvalid
  origDut.io.tx_axis_tkeep  := io.tx_axis_tkeep
  origDut.io.tx_axis_tlast  := io.tx_axis_tlast
  origDut.io.tx_axis_tuser  := io.tx_axis_tuser

  // Outputs to compare
  io.rx_axis_tdata_chisel  := chiselDut.io.rx_axis_tdata
  io.rx_axis_tdata_verilog := origDut.io.rx_axis_tdata
  // can expand with more later
}
