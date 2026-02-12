import chisel3._
import chisel3.experimental.BundleLiterals._

class DualWrapperNfMac10g extends Module {
  val io = IO(new Bundle {
    val tx_axis_tdata  = Input(UInt(64.W))
    val tx_axis_tvalid = Input(Bool())
    val tx_axis_tkeep  = Input(UInt(8.W))
    val tx_axis_tlast  = Input(Bool())
    val tx_axis_tuser  = Input(UInt(1.W))

    val rx_axis_tdata_chisel  = Output(UInt(64.W))
    val rx_axis_tdata_verilog = Output(UInt(64.W))
  })

  // Instantiate both versions
  val chiselDut = Module(new NfMac10g)
  val origDut   = Module(new NfMac10gBlackBox)

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
