package org.chiselware.cores.o01.t001.mac.bfm

import chisel3._
import chiseltest._

import scala.util.Random

/** AXI-Stream SOURCE BFM (acts like a sender):
  *   - drives tvalid/tdata/tkeep/tlast/tuser
  *   - waits for handshake (tvalid && tready) before advancing
  */
class AxisSourceBfm(
    tvalid: Bool,
    tready: Bool,
    tdata: UInt,
    tkeep: UInt,
    tlast: Bool,
    tuser: UInt,
    clock: Clock,
    bubbleProbability: Double = 0.0, // 0.0 = no bubbles
    rng: Random = new Random(0xc0ffee)) {

  /** Call once at start */
  def init(): Unit = {
    tvalid.poke(false.B)
    tdata.poke(0.U)
    tkeep.poke(0.U)
    tlast.poke(false.B)
    tuser.poke(0.U)
  }

  /** Send a single beat (blocking until handshake completes) */
  def sendBeat(b: AxisBeat): Unit = {
    // Optional bubble cycle(s) before sending
    while (bubbleProbability > 0.0 && rng.nextDouble() < bubbleProbability) {
      tvalid.poke(false.B)
      clock.step(1)
    }

    // Drive beat
    tdata.poke(b.data.U(tdata.getWidth.W))
    tkeep.poke(b.keep.U(tkeep.getWidth.W))
    tlast.poke(b.last.B)
    tuser.poke(b.user.U(tuser.getWidth.W))
    tvalid.poke(true.B)

    // Wait for handshake
    while (!tready.peek().litToBoolean) {
      clock.step(1)
    }
    // handshake occurs on this cycle
    clock.step(1)

    // Deassert valid after transfer
    tvalid.poke(false.B)
  }

  /** Send a whole frame */
  def sendFrame(f: AxisFrame): Unit = {
    f.beats.foreach(sendBeat)
  }
}
