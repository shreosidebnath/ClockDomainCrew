// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/** RstModTb is a simple test bench wrapper to hold a single instance of RstMod.
  *
  * @param p
  *   A customization of default parameters contained in Rst_ModParams case class.
  */

class RstModTb() extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val dcm_locked = Input(Bool())
    val rst = Output(Bool())
  })

  val dut = Module(new RstMod())
    dut.io.reset := io.reset
    dut.io.dcm_locked := io.dcm_locked
    io.rst := dut.io.rst
}
