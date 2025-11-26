package ethernet 

import chisel3._
import chisel3.util._

class axis2Xgmii extends Module {
    val good_frames = Input(UInt(32.W))
    val bad_frames = Output(UInt(32.W))
    val configuration_vector = Input(UInt(80.W))
    val lane4_start = Wire(UInt(80.W))
    val dic_o = Output(UInt(2.W))
    val xgmii_d = Output(UInt(64.W))
    val xgmii_c = Output(UInt(8.W))
    val tdata = Input(UInt(64.W))
    val tkeep = Input(UInt(8.W))
    val tvalid = Input(Bool())
    val tready = Output(Bool())
    val tlast = Input(Bool())
    val tuser = Input(UInt(0.W))




}