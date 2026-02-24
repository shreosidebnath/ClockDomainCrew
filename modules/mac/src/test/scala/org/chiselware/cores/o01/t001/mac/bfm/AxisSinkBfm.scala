package org.chiselware.cores.o01.t001.mac.bfm

import chisel3._
import chiseltest._

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/** AXI-Stream SINK BFM (acts like a receiver):
  *   - drives tready (optionally with stalls)
  *   - captures beats on handshake
  *   - can return completed frames (based on tlast)
  *
  * This expects DUT signals to look like: tvalid: Bool, tready: Bool, tdata:
  * UInt, tkeep: UInt, tlast: Bool, tuser: UInt
  */
class AxisSinkBfm(
    tvalid: Bool,
    tready: Bool,
    tdata: UInt,
    tkeep: UInt,
    tlast: Bool,
    tuser: UInt,
    clock: Clock,
    stallProbability: Double = 0.0, // 0.0 = always ready
    rng: Random = new Random(0xbeef)) {
  private val completedFrames = ArrayBuffer.empty[AxisFrame]
  private val curBeats = ArrayBuffer.empty[AxisBeat]

  /** Call once at start */
  def init(): Unit = {
    tready.poke(true.B)
  }

  /** Step 1 cycle: drive ready + capture if handshake */
  def step(): Unit = {
    // Drive backpressure (optional)
    val readyNow =
      if (stallProbability <= 0.0)
        true
      else
        rng.nextDouble() >= stallProbability
    tready.poke(readyNow.B)

    // Capture only on handshake
    if (tvalid.peek().litToBoolean && readyNow) {
      val beat = AxisBeat(
        data = tdata.peek().litValue,
        keep = tkeep.peek().litValue.toInt,
        last = tlast.peek().litToBoolean,
        user = tuser.peek().litValue
      )
      curBeats += beat
      if (beat.last) {
        completedFrames += AxisFrame(curBeats.toSeq)
        curBeats.clear()
      }
    }

    clock.step(1)
  }

  /** Step N cycles */
  def step(n: Int): Unit = (0 until n).foreach(_ => step())

  /** Pull all frames captured so far */
  def popAllFrames(): Seq[AxisFrame] = {
    val out = completedFrames.toSeq
    completedFrames.clear()
    out
  }

  /** How many frames are currently buffered */
  def framesAvailable: Int = completedFrames.size
}
