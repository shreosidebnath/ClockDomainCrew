// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

/** Axis2XgmiiTb is a simple test bench wrapper to hold a single instance of axis2xgmii.
  *
  * @param p
  *   A customization of default parameters contained in xgmii2axisParams case class.
  */

class Axis2XgmiiTb() extends Module {
  val io = IO(new Bundle {
    
}
