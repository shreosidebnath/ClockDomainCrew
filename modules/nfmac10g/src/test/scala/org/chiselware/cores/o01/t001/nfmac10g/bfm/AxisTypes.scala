package org.chiselware.cores.o01.t001.nfmac10g.bfm

// One transfer on AXI-Stream (one cycle handshake)
case class AxisBeat(
  data: BigInt,
  keep: Int,
  last: Boolean,
  user: BigInt = 0
)

// A full frame is multiple beats, ending with last=true
case class AxisFrame(beats: Seq[AxisBeat]) {
  require(beats.nonEmpty, "AxisFrame must contain at least 1 beat")
  require(beats.last.last, "AxisFrame must end with last=true on final beat")
}
