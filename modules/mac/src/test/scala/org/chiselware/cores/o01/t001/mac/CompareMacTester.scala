package org.chiselware.cores.o01.t001.mac

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class CompareMacTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  // --------------------------------------------------------------------------
  // Progress printing helpers
  // --------------------------------------------------------------------------
  private val TotalTests = 16  // update this if you add/remove tests

  private def pct(n: Int): Int = (n * 100) / TotalTests

  private def progStart(n: Int, name: String): Unit =
    println(s"\n[${n}/${TotalTests}] (${pct(n)}%)\tSTART:\t$name")

  private def progEnd(n: Int, name: String): Unit =
    println(s"[${n}/${TotalTests}] (${pct(n)}%)\tDONE:\t$name")

  private def progInfo(n: Int, msg: String): Unit =
    println(s"[${n}/${TotalTests}]\t\t\t• $msg")

  // --------------------------------------------------------------------------
  // Types / helpers
  // --------------------------------------------------------------------------
  private case class AxisBeat(data: BigInt, keep: Int, last: Boolean, user: BigInt, tid: BigInt)

  private case class TxFrameEvent(
    startLane: Int,
    termLane: Int,
    frameBytes: Int,       // number of DATA bytes seen between S and T
    lastDataLane: Int      // lane of the last DATA byte right before T
  )

  /** Convert keep mask + 64-bit data into bytes (little-endian lanes: byte0 is LSB). */
  private def beatToBytes(b: AxisBeat, dataBytes: Int = 8): Array[Byte] = {
    val out = ArrayBuffer.empty[Byte]
    for (i <- 0 until dataBytes) {
      val keepBit = (b.keep >> i) & 0x1
      if (keepBit == 1) {
        val byteVal = ((b.data >> (8 * i)) & 0xff).toInt
        out += byteVal.toByte
      }
    }
    out.toArray
  }

  /** Split a stream of beats into frames on last=true, returning each frame as raw bytes. */
  private def beatsToFrames(beats: Seq[AxisBeat]): Seq[Array[Byte]] = {
    val frames = ArrayBuffer.empty[Array[Byte]]
    val cur = ArrayBuffer.empty[Byte]
    beats.foreach { b =>
      cur ++= beatToBytes(b)
      if (b.last) {
        frames += cur.toArray
        cur.clear()
      }
    }
    frames.toSeq
  }

  private def bytesToHex(bs: Array[Byte]): String = bs.iterator.map(b => f"${b & 0xff}%02x").mkString
  private def bytesToHex(bs: Seq[Byte]): String = bs.iterator.map(b => f"${b & 0xff}%02x").mkString
  
  // --------------------------------------------------------------------------
  // TX helpers (AXI4-Stream -> XGMII):
  // - stepTx: advances the simulation while sampling BOTH XGMII TX monitors and statuses
  //   (Chisel + Verilog) so we don’t miss /S/ (0xFB) or /T/ (0xFD) control chars.
  // - sendAxisFrame: streams one complete AXIS frame into DUT:
  //     * holds tvalid across beats (no gaps between beats)
  //     * waits for tx_tready before each handshake
  //     * drops tvalid after the final beat
  //
  // Notes:
  // - Some MAC TX datapaths don’t like “tvalid bubbles” mid-frame; this helper
  //   behaves like a real AXIS master (continuous valid until last beat).
  // - This is used by TX tests that compare Chisel vs Verilog TX outputs.
  // --------------------------------------------------------------------------
  private def stepTx(dut: DualWrapperMac, b: Bfms, n: Int = 1): Unit = {
    for (_ <- 0 until n) {
      b.chiselTxMon.sample()
      b.verilogTxMon.sample()
      b.chiselTxStatus.sample()
      b.verilogTxStatus.sample()
      dut.clock.step(1)
    }
  }

  private def sendAxisFrame(dut: DualWrapperMac, b: Bfms, bytes: Array[Byte], tid: Int): Unit = {
    val beats = bytesToAxisBeats(bytes, id = tid)

    beats.foreach { beat =>
      b.axisTx.driveBeat(beat)
      // wait until ready
      while (!dut.io.tx_tready.peek().litToBoolean) stepTx(dut, b, 1)
      // handshake happens on this cycle (valid already high)
      stepTx(dut, b, 1)
    }

    // drop valid after the whole frame
    b.axisTx.idle()
    stepTx(dut, b, 1)
  }

  // -------------------------------------------------------------------------
  // Negative TX test helpers
  // -------------------------------------------------------------------------
  private def sendAxisBeats(dut: DualWrapperMac, b: Bfms, beats: Seq[AxisBeat]): Unit = {
    beats.foreach { beat =>
      b.axisTx.driveBeat(beat)
      while (!dut.io.tx_tready.peek().litToBoolean) stepTx(dut, b, 1) // harmless if always 1
      stepTx(dut, b, 1)
    }
    b.axisTx.idle()
    stepTx(dut, b, 1)
  }

  private def bytesToAxisBeatsOverrideLastKeep(bytes: Array[Byte], id: Int, lastKeep: Int): Seq[AxisBeat] = {
    require(bytes.length % 8 == 0, s"Need multiple-of-8 bytes to override last keep safely, got ${bytes.length}")

    val beats = bytesToAxisBeats(bytes, id = id).toBuffer
    val last = beats.last
    beats(beats.length - 1) = last.copy(keep = lastKeep, last = true)
    beats.toSeq
  }

  private val MinFrameLen = 64                // module param MIN_FRAME_LEN
  private val MinNoFcs    = MinFrameLen - 4   // = 60 bytes before FCS -> MAC pads anything less to match
  // helper for calculating expected termlane after padding uses both constants from above
  private def expectedTermLaneFromPayloadBytes(payloadNoFcsBytes: Int): Int = {
    val rem = payloadNoFcsBytes % 8
    val empty = (8 - rem) % 8 // 0..7

    // Maps directly to fcs_output_* cases in taxi_axis_xgmii_tx_64
    empty match {
      case 7 => 5
      case 6 => 6
      case 5 => 7
      case 4 => 0
      case 3 => 1
      case 2 => 2
      case 1 => 3
      case 0 => 4
    }
  }

  // helper for termlane checking
  private def keepMask(nBytes: Int): Int = (1 << nBytes) - 1  // nBytes in 1..7 => 0x01..0x7F

  // helper forces tuser = 1
  private def beatsWithUser(beats: Seq[AxisBeat], userOnBeatIdx: Set[Int]): Seq[AxisBeat] = {
    beats.zipWithIndex.map { case (b, i) =>
      if (userOnBeatIdx.contains(i)) b.copy(user = BigInt(1)) else b.copy(user = BigInt(0))
    }
  }

  // -------------------------------------------------------------------------
  // TX coverage test helpers 
  // -------------------------------------------------------------------------
  /** Keep stepping until frame event is captured (the event contains which lane the terminate was seen) */
  private def waitForOneFrameEvent(dut: DualWrapperMac, b: Bfms, before: Int, maxCycles: Int = 20000, tn: Int = -1): Unit = {
    var cycles = 0
    while (b.chiselTxMon.gotEvents.size == before && cycles < maxCycles) {
      stepTx(dut, b, 1)   // samples + steps
      cycles += 1
      if (tn >= 0 && cycles % 2000 == 0) progInfo(tn, s"waiting for TX event... cycles=$cycles")
    }
    assert(b.chiselTxMon.gotEvents.size > before, s"Timed out waiting for TX frame event after $cycles cycles")
  }

  // -------------------------------------------------------------------------
  // RX coverage helpers
  // -------------------------------------------------------------------------
  /** Find the /T/ lane in an encoded XGMII stream (looks for 0xFD where ctrl bit is 1). */
  private def findTerminateLane(xgmiiWords: Seq[(BigInt, Int)]): Int = {
    def laneByte(d: BigInt, i: Int): Int = ((d >> (8 * i)) & 0xff).toInt
    xgmiiWords.foreach { case (d, c) =>
      for (i <- 0 until 8) {
        val isCtrl = ((c >> i) & 1) == 1
        if (isCtrl && laneByte(d, i) == 0xFD) return i
      }
    }
    -1
  }

  /**
    * Pick a payload length >= 46 such that the RX XGMII encoding ends with /T/ in lane targetLane.
    *
    * Reason:
    * - buildEthernetFrame pads payload to at least 46 (we choose >=46 so no surprise padding).
    * - xgmiiEncode64 puts preamble+SFD in the first 8 bytes, then sends (noPreambleNoFcs + FCS).
    * - /T/ lane depends on (noPreambleNoFcs + 4) % 8, where noPreambleNoFcs = 14 + payloadLen.
    * - So /T/ lane depends on (18 + payloadLen) % 8.
    */
  private def payloadLenForRxTerminateLane(targetLane: Int): Int = {
    require(targetLane >= 0 && targetLane < 8)
    val base = 46
    val chosen = (0 to 7).map(d => base + d).find { pl =>
      ((18 + pl) % 8) == targetLane
    }.getOrElse(base)
    chosen
  }

  // -------------------------------------------------------------------------
  // Random helpers
  // -------------------------------------------------------------------------
  private def randBytes(r: Random, n: Int): Array[Byte] = {
    val a = Array.ofDim[Byte](n)
    r.nextBytes(a)
    a
  }

  // --------------------------------------------------------------------------
  // Ethernet CRC32 (IEEE 802.3 / reflected)
  // init=0xFFFFFFFF, poly=0xEDB88320, xorout=0xFFFFFFFF
  // FCS transmitted little-endian (LSB first)
  // --------------------------------------------------------------------------
  private def crc32Ethernet(data: Array[Byte]): Int = {
    var crc = 0xFFFFFFFF
    val poly = 0xEDB88320

    data.foreach { b =>
      crc ^= (b & 0xff)
      for (_ <- 0 until 8) {
        crc = if ((crc & 1) != 0) (crc >>> 1) ^ poly else (crc >>> 1)
      }
    }
    crc ^ 0xFFFFFFFF
  }

  private def intToLeBytes32(x: Int): Array[Byte] = Array(
    (x & 0xff).toByte,
    ((x >>> 8) & 0xff).toByte,
    ((x >>> 16) & 0xff).toByte,
    ((x >>> 24) & 0xff).toByte
  )

  // --------------------------------------------------------------------------
  // Build a full Ethernet frame as bytes:
  // preamble(7*55) + SFD(D5) + dst(6) + src(6) + ethType(2) + payload(+pad) + FCS(4)
  // Returns:
  //   fullBytes (preamble+SFD+...+FCS)
  //   expectedAxisBytes (dst+src+ethType+payload(+pad))  (RX usually strips FCS)
  // --------------------------------------------------------------------------
  private def buildEthernetFrame(
    dst: Array[Byte],
    src: Array[Byte],
    ethType: Int,
    payload: Array[Byte]
  ): (Array[Byte], Array[Byte]) = {
    require(dst.length == 6)
    require(src.length == 6)

    val header = dst ++ src ++ Array(((ethType >>> 8) & 0xff).toByte, (ethType & 0xff).toByte)

    // Ensure minimum payload:
    // header(14) + payload(>=46) + fcs(4) = 64
    val minPayload = 46
    val payloadPadded =
      if (payload.length >= minPayload) payload
      else payload ++ Array.fill[Byte](minPayload - payload.length)(0)

    val noPreambleNoFcs = header ++ payloadPadded
    val crc = crc32Ethernet(noPreambleNoFcs)
    val fcs = intToLeBytes32(crc)

    val preamble = Array.fill(7)(0x55.toByte)
    val sfd = Array[Byte](0xD5.toByte)

    val full = preamble ++ sfd ++ noPreambleNoFcs ++ fcs
    (full, noPreambleNoFcs)
  }

  // --------------------------------------------------------------------------
  // XGMII encode for 64-bit:
  // - First cycle: lane0=FB (ctrl=1), lanes1..6=55, lane7=D5
  // - Then raw frame bytes (dst..payload..fcs) as DATA cycles (ctrl=0)
  // - Terminate with FD on first unused lane, remaining lanes = 07 with ctrl=1
  //
  // Optional: override the START cycle bytes (for bad preamble tests)
  // --------------------------------------------------------------------------
  // helper for xgmiiEncode64
  private def packWordLittle(bytes8: Array[Byte]): BigInt = {
    bytes8.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) =>
    acc | (BigInt(b & 0xff) << (8 * i))
    }
  }

  private def xgmiiEncode64(fullFrameBytes: Array[Byte], startOverride: Option[Array[Byte]] = None): Seq[(BigInt, Int)] = {
    require(fullFrameBytes.length >= 8)

    val startCycleBytes = startOverride.getOrElse(Array[Byte](
      0xFB.toByte, // lane0 START (control)
      0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte,
      0xD5.toByte  // lane7 SFD
    ))
    require(startCycleBytes.length == 8)

    val startData = packWordLittle(startCycleBytes)
    val startCtrl = 0x01

    // Send bytes starting at dst (skip preamble+SFD already encoded)
    val payloadBytes = fullFrameBytes.drop(8)

    val out = ArrayBuffer.empty[(BigInt, Int)]
    out += ((startData, startCtrl))

    var idx = 0
    while (idx < payloadBytes.length) {
      val remaining = payloadBytes.length - idx
      if (remaining >= 8) {
        val chunk = payloadBytes.slice(idx, idx + 8)
        out += ((packWordLittle(chunk), 0x00))
        idx += 8
      } else {
        val bytes8 = Array.fill(8)(0x07.toByte)
        Array.copy(payloadBytes, idx, bytes8, 0, remaining)

        val termLane = remaining // 0..7
        bytes8(termLane) = 0xFD.toByte

        var ctrl = 0
        for (lane <- termLane until 8) ctrl |= (1 << lane)

        out += ((packWordLittle(bytes8), ctrl))
        idx += remaining
      }
    }

    if ((payloadBytes.length % 8) == 0) {
      val bytes8 = Array.fill(8)(0x07.toByte)
      bytes8(0) = 0xFD.toByte
      out += ((packWordLittle(bytes8), 0xFF))
    }

    out.toSeq
  }

  private def bytesToAxisBeats(bytes: Array[Byte], id: Int = 0): Seq[AxisBeat] = {
    val beats = ArrayBuffer.empty[AxisBeat]
    var idx = 0
    while (idx < bytes.length) {
      val remaining = bytes.length - idx
      val n = math.min(8, remaining)
      var data = BigInt(0)
      var keep = 0
      for (i <- 0 until n) {
        data |= BigInt(bytes(idx + i) & 0xff) << (8*i)
        keep |= (1 << i)
      }
      val last = (idx + n) >= bytes.length
      beats += AxisBeat(data=data, keep=keep, last=last, user=BigInt(0), tid=BigInt(id))
      idx += n
    }
    beats.toSeq
  }

  // --------------------------------------------------------------------------
  // BFMs
  // --------------------------------------------------------------------------
  private class XgmiiTxMonitorBfm(txd: UInt, txc: UInt, txValid: Bool) {
    private val frames = ArrayBuffer.empty[Array[Byte]]
    private val events = ArrayBuffer.empty[TxFrameEvent]

    private var inFrame = false
    private val cur = ArrayBuffer.empty[Byte]

    private var startLane = -1
    private var termLane  = -1
    private var frameBytes = 0
    private var lastDataLane = -1

    // IFG
    private var afterTerm = false
    private var idleBytesSinceTerm = 0
    private var lastIfgIdleBytes = 0

    def clear(): Unit = {
      frames.clear(); events.clear()
      inFrame = false; cur.clear()
      startLane = -1; termLane = -1; frameBytes = 0; lastDataLane = -1
      afterTerm = false; idleBytesSinceTerm = 0; lastIfgIdleBytes = 0
    }

    def gotFrames: Seq[Array[Byte]] = frames.toSeq
    def gotEvents: Seq[TxFrameEvent] = events.toSeq
    def lastIfgBytes: Int = lastIfgIdleBytes

    def sample(): Unit = {
      if (!txValid.peek().litToBoolean) return

      val d = txd.peek().litValue
      val c = txc.peek().litValue.toInt

      def laneByte(i: Int): Int = ((d >> (8*i)) & 0xff).toInt
      def isCtrl(i: Int): Boolean = ((c >> i) & 1) == 1
      def isIdle(i: Int): Boolean = isCtrl(i) && laneByte(i) == 0x07

      // IFG counting between /T/ and next /S/
      if (afterTerm) {
        for (i <- 0 until 8) if (isIdle(i)) idleBytesSinceTerm += 1
      }

      // find start if not in frame
      if (!inFrame) {
        for (i <- 0 until 8) {
          if (isCtrl(i) && laneByte(i) == 0xFB) {
            inFrame = true
            startLane = i
            termLane = -1
            frameBytes = 0
            lastDataLane = -1

            // latch IFG at the moment a new frame starts
            if (afterTerm) {
              lastIfgIdleBytes = idleBytesSinceTerm
              afterTerm = false
              idleBytesSinceTerm = 0
            }

            // bytes after start in same word (lanes i+1..7) that are data (ctrl=0)
            for (j <- i+1 until 8) {
              if (!isCtrl(j)) {
                cur += laneByte(j).toByte
                frameBytes += 1
                lastDataLane = j
              }
              // if terminate appears in same word (rare but possible), close
              if (isCtrl(j) && laneByte(j) == 0xFD) {
                termLane = j
                frames += cur.toArray
                events += TxFrameEvent(startLane, termLane, frameBytes, lastDataLane)
                cur.clear()
                inFrame = false

                // Start IFG counting *including* idles in the same XGMII word after /T/
                afterTerm = true
                idleBytesSinceTerm = 0
                for (k <- (j + 1) until 8) {
                  if (isIdle(k)) idleBytesSinceTerm += 1
                }
                return
              }
            }
            return
          }
        }
        return
      }

      // in frame: consume lanes until terminate
      for (i <- 0 until 8) {
        if (isCtrl(i)) {
          val b = laneByte(i)
          if (b == 0xFD) {
            termLane = i
            frames += cur.toArray
            events += TxFrameEvent(startLane, termLane, frameBytes, lastDataLane)
            cur.clear()
            inFrame = false

            // Start IFG counting *including* idles in the same XGMII word after /T/
            afterTerm = true
            idleBytesSinceTerm = 0
            for (k <- (i + 1) until 8) {
              if (isIdle(k)) idleBytesSinceTerm += 1
            }
            return
          }
          // ignore idles / other controls
        } else {
          cur += laneByte(i).toByte
          frameBytes += 1
          lastDataLane = i
        }
      }
    }
  }

  private class AxisTxDriverBfm(dut: DualWrapperMac) {
    def idle(): Unit = {
      dut.io.tx_tdata.poke(0.U)
      dut.io.tx_tkeep.poke(0.U)
      dut.io.tx_tvalid.poke(false.B)
      dut.io.tx_tlast.poke(false.B)
      dut.io.tx_tuser.poke(0.U)
      dut.io.tx_tid.poke(0.U)
    }

    def driveBeat(b: AxisBeat): Unit = {
      dut.io.tx_tdata.poke(b.data.U(64.W))
      dut.io.tx_tkeep.poke(b.keep.U(8.W))
      dut.io.tx_tvalid.poke(true.B)
      dut.io.tx_tlast.poke(b.last.B)
      dut.io.tx_tuser.poke(b.user.U(1.W))
      dut.io.tx_tid.poke(b.tid.U(8.W))
    }
  }

  private class XgmiiRxDriverBfm(dut: DualWrapperMac) {
    private val idleWord: BigInt = BigInt("0707070707070707", 16)

    def drive(data: BigInt, ctrl: Int, rxValid: Boolean = true): Unit = {
      dut.io.xgmii_rxd.poke(data.U(64.W))
      dut.io.xgmii_rxc.poke(ctrl.U(8.W))
      dut.io.xgmii_rx_valid.poke(rxValid.B)
    }

    def driveIdle(): Unit = drive(idleWord, 0xFF, rxValid = true)
  }

  private class AxisCollectorBfm(tvalid: Bool, tdata: UInt, tkeep: UInt, tlast: Bool, tuser: UInt, tid: UInt) {
    val beats: ArrayBuffer[AxisBeat] = ArrayBuffer.empty
    def clear(): Unit = beats.clear()
    def sample(): Unit = {
      if (tvalid.peek().litToBoolean) {
        beats += AxisBeat(
          data = tdata.peek().litValue,
          keep = tkeep.peek().litValue.toInt,
          last = tlast.peek().litToBoolean,
          user = tuser.peek().litValue,
          tid  = tid.peek().litValue
        )
      }
    }
  }

  private case class RxStatus(
    good: Boolean = false,
    bad: Boolean = false,
    badFcs: Boolean = false,
    preamble: Boolean = false,
    framing: Boolean = false,
    oversize: Boolean = false,
    pktFragment: Boolean = false
  )

  private class StatusCollectorBfm(
    pktGood: Bool,
    pktBad: Bool,
    errBadFcs: Bool,
    errPreamble: Bool,
    errFraming: Bool,
    errOversize: Bool,
    pktFragment: Bool
  ) {
    private var s = RxStatus()

    def clear(): Unit = s = RxStatus()

    def sample(): Unit = {
      s = s.copy(
        good        = s.good        || pktGood.peek().litToBoolean,
        bad         = s.bad         || pktBad.peek().litToBoolean,
        badFcs      = s.badFcs      || errBadFcs.peek().litToBoolean,
        preamble    = s.preamble    || errPreamble.peek().litToBoolean,
        framing     = s.framing     || errFraming.peek().litToBoolean,
        oversize    = s.oversize    || errOversize.peek().litToBoolean,
        pktFragment = s.pktFragment || pktFragment.peek().litToBoolean
      )
    }

    def snapshot: RxStatus = s
  }

  private case class TxStatus(
    pktGood: Boolean = false,
    pktBad: Boolean = false,
    errUser: Boolean = false,
    errOversize: Boolean = false,
    errUnderflow: Boolean = false
  )

  private class TxStatusCollectorBfm(
    pktGood: Bool,
    pktBad: Bool,
    errUser: Bool,
    errOversize: Bool,
    errUnderflow: Bool
  ) {
    private var s = TxStatus()
    def clear(): Unit = s = TxStatus()
    def sample(): Unit = {
      s = s.copy(
        pktGood     = s.pktGood     || pktGood.peek().litToBoolean,
        pktBad      = s.pktBad      || pktBad.peek().litToBoolean,
        errUser     = s.errUser     || errUser.peek().litToBoolean,
        errOversize = s.errOversize || errOversize.peek().litToBoolean,
        errUnderflow= s.errUnderflow|| errUnderflow.peek().litToBoolean
      )
    }
    def snapshot: TxStatus = s
  }

  // --------------------------------------------------------------------------
  // Bundle BFMs so tests don’t repeat wiring boilerplate
  // --------------------------------------------------------------------------
  private case class Bfms(
    // RX side
    xgmii: XgmiiRxDriverBfm,
    chiselAxis: AxisCollectorBfm,
    verilogAxis: AxisCollectorBfm,
    chiselStatus: StatusCollectorBfm,
    verilogStatus: StatusCollectorBfm,

    // TX side
    axisTx: AxisTxDriverBfm,
    chiselTxMon: XgmiiTxMonitorBfm,
    verilogTxMon: XgmiiTxMonitorBfm,
    chiselTxStatus: TxStatusCollectorBfm,
    verilogTxStatus: TxStatusCollectorBfm
  )

  private def mkBfms(dut: DualWrapperMac): Bfms = {
    // RX BFMs
    val xgmii = new XgmiiRxDriverBfm(dut)

    val chiselAxis = new AxisCollectorBfm(
      dut.io.chisel_rx_tvalid, dut.io.chisel_rx_tdata, dut.io.chisel_rx_tkeep,
      dut.io.chisel_rx_tlast, dut.io.chisel_rx_tuser, dut.io.chisel_rx_tid
    )

    val verilogAxis = new AxisCollectorBfm(
      dut.io.verilog_rx_tvalid, dut.io.verilog_rx_tdata, dut.io.verilog_rx_tkeep,
      dut.io.verilog_rx_tlast, dut.io.verilog_rx_tuser, dut.io.verilog_rx_tid
    )

    val chiselStatus = new StatusCollectorBfm(
      dut.io.chisel_stat_rx_pkt_good,
      dut.io.chisel_stat_rx_pkt_bad,
      dut.io.chisel_stat_rx_err_bad_fcs,
      dut.io.chisel_stat_rx_err_preamble,
      dut.io.chisel_stat_rx_err_framing,
      dut.io.chisel_stat_rx_err_oversize,
      dut.io.chisel_stat_rx_pkt_fragment
    )

    val verilogStatus = new StatusCollectorBfm(
      dut.io.verilog_stat_rx_pkt_good,
      dut.io.verilog_stat_rx_pkt_bad,
      dut.io.verilog_stat_rx_err_bad_fcs,
      dut.io.verilog_stat_rx_err_preamble,
      dut.io.verilog_stat_rx_err_framing,
      dut.io.verilog_stat_rx_err_oversize,
      dut.io.verilog_stat_rx_pkt_fragment
    )

    // TX BFMs
    val axisTx = new AxisTxDriverBfm(dut)
    val chiselTxMon  = new XgmiiTxMonitorBfm(dut.io.chisel_xgmii_txd, dut.io.chisel_xgmii_txc, dut.io.chisel_xgmii_tx_valid)
    val verilogTxMon = new XgmiiTxMonitorBfm(dut.io.verilog_xgmii_txd, dut.io.verilog_xgmii_txc, dut.io.verilog_xgmii_tx_valid)

    val chiselTxStatus = new TxStatusCollectorBfm(
      dut.io.chisel_stat_tx_pkt_good,
      dut.io.chisel_stat_tx_pkt_bad,
      dut.io.chisel_stat_tx_err_user,
      dut.io.chisel_stat_tx_err_oversize,
      dut.io.chisel_stat_tx_err_underflow
    )

    val verilogTxStatus = new TxStatusCollectorBfm(
      dut.io.verilog_stat_tx_pkt_good,
      dut.io.verilog_stat_tx_pkt_bad,
      dut.io.verilog_stat_tx_err_user,
      dut.io.verilog_stat_tx_err_oversize,
      dut.io.verilog_stat_tx_err_underflow
    )

    // ensure TX inputs start idle
    axisTx.idle()

    Bfms(xgmii, chiselAxis, verilogAxis, chiselStatus, verilogStatus, axisTx, chiselTxMon, verilogTxMon, chiselTxStatus, verilogTxStatus)
  }

  // --------------------------------------------------------------------------
  // Shared test runner
  // --------------------------------------------------------------------------

  // Helper for tx test case running
  private def drainTxUntilFrame(
    dut: DualWrapperMac,
    b: Bfms,
    maxCycles: Int = 5000
  ): Unit = {
    var cycles = 0
    while ((b.chiselTxMon.gotFrames.isEmpty || b.verilogTxMon.gotFrames.isEmpty) && cycles < maxCycles) {
      b.chiselTxMon.sample()
      b.verilogTxMon.sample()
      dut.clock.step(1)
      cycles += 1
    }
  }

  // Shared test runner for RX side
  private def runCase(
    dut: DualWrapperMac,
    xgmii: XgmiiRxDriverBfm,
    chiselAxis: AxisCollectorBfm,
    verilogAxis: AxisCollectorBfm,
    chiselStatus: StatusCollectorBfm,
    verilogStatus: StatusCollectorBfm,
    xgmiiWords: Seq[(BigInt, Int)],
    drainMaxCycles: Int = 800,
    requireLast: Boolean = true,
    prog: String => Unit = _ => ()
  ): (Seq[Array[Byte]], Seq[Array[Byte]]) = {

    chiselAxis.clear()
    verilogAxis.clear()
    chiselStatus.clear()
    verilogStatus.clear()

    def stepSample(n: Int): Unit = {
      for (_ <- 0 until n) {
        chiselStatus.sample()
        verilogStatus.sample()
        chiselAxis.sample()
        verilogAxis.sample()
        dut.clock.step(1)
      }
    }

    def stepUntilLast(maxCycles: Int): Unit = {
      var cycles = 0
      while (!verilogAxis.beats.exists(_.last) && cycles < maxCycles) {
        chiselStatus.sample()
        verilogStatus.sample()
        chiselAxis.sample()
        verilogAxis.sample()
        dut.clock.step(1)
        cycles += 1
      }
      verilogAxis.beats.exists(_.last) shouldBe true
    }

    xgmii.driveIdle()
    stepSample(8)

    xgmiiWords.foreach { case (d, c) =>
      xgmii.drive(d, c, rxValid = true)
      stepSample(1)
    }

    xgmii.driveIdle()
    if (requireLast) {
      stepUntilLast(drainMaxCycles)
    } else {
      // just drain fixed cycles so status can settle even if frame is dropped
      stepSample(drainMaxCycles)
    }

    val chiselFrames  = beatsToFrames(chiselAxis.beats.toSeq)
    val verilogFrames = beatsToFrames(verilogAxis.beats.toSeq)

    if (requireLast) {
      withClue(
        s"Chisel frames:  ${chiselFrames.map(bytesToHex).mkString(",")}\n" +
        s"Verilog frames: ${verilogFrames.map(bytesToHex).mkString(",")}\n"
      ) {
        chiselFrames.map(_.toSeq) shouldBe verilogFrames.map(_.toSeq)
      }
    } else {
      // In drop-frame cases, allow 0 frames. If frames exist, still ensure they match.
      chiselFrames.map(_.toSeq) shouldBe verilogFrames.map(_.toSeq)
    }

    prog(s"chiselFrames=${chiselFrames.size}, verilogFrames=${verilogFrames.size}")

    (chiselFrames, verilogFrames)
  }

  // --------------------------------------------------------------------------
  // Test cases
  // --------------------------------------------------------------------------
  it should "RX: valid frame passes (tuser==0) and matches expected bytes" in {
    progInfo(0, "start of MAC testing")
    val tn = 0
    val tname = "RX: valid frame passes (tuser==0) and matches expected bytes"
    progStart(tn, tname)
    
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, expectedAxisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        val xgmiiWords = xgmiiEncode64(fullBytes)

        val (_, verilogFrames) = runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, xgmiiWords, prog = msg => progInfo(tn, msg))

        verilogFrames.size shouldBe 1
        verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

        b.verilogAxis.beats.last.user shouldBe 0
        b.chiselAxis.beats.last.user shouldBe 0
      }

    progEnd(tn+1, tname)
  }

  it should "RX: bad FCS is flagged (tuser==1)" in {
    val tn = 1
    val tname = "RX: bad FCS is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullBad = fullBytes.clone()
        fullBad(fullBad.length - 1) = (fullBad.last ^ 0x01).toByte // flip bit in FCS

        val xgmiiWords = xgmiiEncode64(fullBad)

        runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, xgmiiWords, prog = msg => progInfo(tn, msg))

        b.verilogAxis.beats.nonEmpty shouldBe true
        b.verilogAxis.beats.last.user shouldBe 1
        b.chiselAxis.beats.last.user shouldBe 1
        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.badFcs shouldBe true
        vs.good shouldBe false
      }
    
    progEnd(tn+1, tname)
  }

  it should "RX: runt (truncated FCS) is flagged (tuser==1)" in {
    val tn = 2
    val tname = "RX: runt (truncated FCS) is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullRunt = fullBytes.dropRight(2) // missing 2 bytes of FCS => CRC check should fail

        val xgmiiWords = xgmiiEncode64(fullRunt)

        runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, xgmiiWords, requireLast = false, prog = msg => progInfo(tn, msg))

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        (vs.badFcs || vs.pktFragment) shouldBe true
        vs.good shouldBe false

        // if any output exists, it must be marked bad.
        if (b.verilogAxis.beats.nonEmpty) b.verilogAxis.beats.last.user shouldBe 1
      }

    progEnd(tn+1, tname)
  }

  it should "RX: bad preamble/SFD (corrupted) is detected" in {
    val tn = 3
    val tname = "RX: bad preamble/SFD (corrupted) - detected"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)

        val badStart = Array[Byte](
          0xFB.toByte,
          0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte, 0x55.toByte,
          0x00.toByte // SFD wrong (should be 0xD5)
        )

        val xgmiiWords = xgmiiEncode64(fullBytes, startOverride = Some(badStart))

        val (_, verilogFrames) = runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, xgmiiWords, requireLast = false, prog = msg => progInfo(tn, msg))

        val vs = b.verilogStatus.snapshot

        // This MAC ignores the preamble pattern and SFD byte in the start word,
        // so corrupting them should still be accepted as a good packet.
        vs.good shouldBe true

        vs.preamble shouldBe true
      }

    progEnd(tn+1, tname)
  }

  it should "RX: framing error (control inside payload) is flagged (tuser==1)" in {
    val tn = 4
    val tname = "RX: framing error (control inside payload) is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val words = xgmiiEncode64(fullBytes).toBuffer

        // Inject a control character FE in the middle of the payload stream:
        // pick a word after the start cycle (index 1+), set lane3 to 0xFE and mark that lane as control.
        if (words.length >= 4) {
          val (d, c) = words(2)
          val badByteLane = 3
          val mask = BigInt(0xff) << (8 * badByteLane)
          val d2 = (d & ~mask) | (BigInt(0xFE) << (8 * badByteLane))
          val c2 = c | (1 << badByteLane)
          words(2) = ((d2, c2))
        } else {
          fail("Not enough words to inject framing error")
        }

        runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, words.toSeq, prog = msg => progInfo(tn, msg))

        b.verilogAxis.beats.nonEmpty shouldBe true
        b.verilogAxis.beats.last.user shouldBe 1
        b.chiselAxis.beats.last.user shouldBe 1
      }

    progEnd(tn+1, tname)
  }

  it should "RX: oversize is flagged when cfg_rx_max_pkt_len is small" in {
    val tn = 5
    val tname = "RX: oversize is flagged when cfg_rx_max_pkt_len is small"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)

        // key: make "normal" frame oversize
        dut.io.cfg_rx_max_pkt_len.poke(64.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800

        // payload -> way above 64 max
        val payload = Array.fill(100)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val xgmiiWords = xgmiiEncode64(fullBytes)

        // usually dropped or flagged; don't require tlast
        runCase(dut, b.xgmii, b.chiselAxis, b.verilogAxis, b.chiselStatus, b.verilogStatus, xgmiiWords, requireLast = false, prog = msg => progInfo(tn, msg))

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.oversize shouldBe true
        vs.good shouldBe false

        // Optional: if any output exists, it must be marked bad.
        if (b.verilogAxis.beats.nonEmpty) b.verilogAxis.beats.last.user shouldBe 1
      }

    progEnd(tn+1, tname)
  }
  
  it should "RX: terminate-lane coverage hits T0..T7 on good frames (tuser==0), Chisel matches Verilog" in {
    val tn = 6
    val tname = "RX: terminate-lane coverage hits T0..T7 on good frames (tuser==0), Chisel matches Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800

        val termSeen = Array.fill(8)(false)

        for (k <- 0 until 8) {
          val payloadLen = payloadLenForRxTerminateLane(k)
          val payload = Array.tabulate(payloadLen)(i => ((0xA0 + (i & 0x0F)) & 0xFF).toByte)

          val (fullBytes, expectedAxisBytes) = buildEthernetFrame(dst, src, ethType, payload)
          val xgmiiWords = xgmiiEncode64(fullBytes)

          val termLane = findTerminateLane(xgmiiWords)
          withClue(s"Could not find /T/ in generated xgmiiWords for target k=$k (payloadLen=$payloadLen)\n") {
            termLane shouldBe k
          }
          termSeen(termLane) = true

          progInfo(tn, s"RX /T/ sweep: target=$k payloadLen=$payloadLen termLaneFound=$termLane")

          val (_, verilogFrames) =
            runCase(
              dut,
              b.xgmii,
              b.chiselAxis, b.verilogAxis,
              b.chiselStatus, b.verilogStatus,
              xgmiiWords,
              requireLast = true,
              prog = msg => progInfo(tn, msg)
            )

          // Must pass + match expected (RX strips FCS)
          verilogFrames.size shouldBe 1
          verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

          // Must be good (tuser=0)
          b.verilogAxis.beats.last.user shouldBe 0
          b.chiselAxis.beats.last.user shouldBe 0

          val vs = b.verilogStatus.snapshot
          withClue(s"Expected good RX status for target k=$k but got: $vs\n") {
            vs.good shouldBe true
            vs.bad shouldBe false
          }
        }

        assert(termSeen.forall(_ == true), s"Not all RX terminate lanes seen: ${termSeen.toList}")
      }

    progEnd(tn+1, tname)
  }

  it should "RX: 500 random frames (payload/len/data) match expected and Chisel==Verilog" in {
    val tn = 7
    val tname = "RX: 500 random frames (payload/len/data) match expected and Chisel==Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800

        val r = new Random(20260224) // deterministic

        for (i <- 0 until 500) {
          val payloadLen = r.nextInt(512) // 0..511 (buildEthernetFrame pads to >=46)
          val payload = randBytes(r, payloadLen)

          val (fullBytes, expectedAxisBytes) = buildEthernetFrame(dst, src, ethType, payload)
          val xgmiiWords = xgmiiEncode64(fullBytes)

          val (_, verilogFrames) =
            runCase(
              dut,
              b.xgmii,
              b.chiselAxis, b.verilogAxis,
              b.chiselStatus, b.verilogStatus,
              xgmiiWords,
              requireLast = true,
              prog = _ => ()
            )

          withClue(s"[RX rand $i] payloadLen=$payloadLen\n") {
            verilogFrames.size shouldBe 1
            verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

            b.verilogAxis.beats.last.user shouldBe 0
            b.chiselAxis.beats.last.user shouldBe 0

            val vs = b.verilogStatus.snapshot
            vs.good shouldBe true
            vs.bad shouldBe false
          }

          if (i % 25 == 0) progInfo(tn, s"RX random progress: i=$i payloadLen=$payloadLen")
        }
      }

    progEnd(tn+1, tname)
    progInfo(tn+1, s"RX tests completed, TX tests starting...")
  }
  
  it should "TX: AXIS -> XGMII produces matching frames (Chisel vs Verilog)" in {
    val tn = 8
    val tname = "TX: AXIS -> XGMII produces matching frames (Chisel vs Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)

        // RX doesn't matter here, but keep it stable
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        // Drive a normal Ethernet "payload bytes" into AXIS TX.
        // NOTE: For TX path, MAC may or may not add preamble/FCS, so we only compare Chisel vs Verilog outputs.
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(64)(0x11.toByte)

        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)

        // Send TX
        b.axisTx.idle()
        stepTx(dut, b, 5) // DUT settles
        sendAxisFrame(dut, b, axisBytes, tid = 1)

        // return to idle
        b.axisTx.idle()
        stepTx(dut, b, 2000) // give it time to finish emitting /T/ + idles

        withClue(s"Chisel TX frames=${b.chiselTxMon.gotFrames.size}, Verilog TX frames=${b.verilogTxMon.gotFrames.size}\n") {
          b.chiselTxMon.gotFrames.nonEmpty shouldBe true
          b.verilogTxMon.gotFrames.nonEmpty shouldBe true
        }

        val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
        val vFrames  = b.verilogTxMon.gotFrames.map(_.toSeq)

        withClue(
          s"Chisel TX:  ${chFrames.map(bytesToHex).mkString(",")}\n" +
          s"Verilog TX: ${vFrames.map(bytesToHex).mkString(",")}\n"
        ) {
          chFrames shouldBe vFrames
        }

        progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
        progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
      }

    progEnd(tn+1, tname)
  }

  it should "TX: back-to-back frames preserve ordering and match (Chisel vs Verilog)" in {
    val tn = 9
    val tname = "TX: back-to-back frames preserve ordering and match (Chisel vs Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        def mkAxisBytes(payloadLen: Int): Array[Byte] = {
          val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
          val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
          val ethType = 0x0800
          val payload = Array.fill(payloadLen)(0x22.toByte)
          val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
          axisBytes
        }

        // Send 3 frames with different sizes
        sendAxisFrame(dut, b, mkAxisBytes(10),  1)
        sendAxisFrame(dut, b, mkAxisBytes(64),  2)
        sendAxisFrame(dut, b, mkAxisBytes(200), 3)

        // Drain until we see at least 3 frames on both
        var cycles = 0
        while ((b.chiselTxMon.gotFrames.size < 3 || b.verilogTxMon.gotFrames.size < 3) && cycles < 20000) {
          b.chiselTxMon.sample()
          b.verilogTxMon.sample()
          dut.clock.step(1)
          cycles += 1
        }

        b.chiselTxMon.gotFrames.size shouldBe b.verilogTxMon.gotFrames.size
        b.chiselTxMon.gotFrames.size shouldBe 3

        val ch = b.chiselTxMon.gotFrames.map(_.toSeq)
        val vg = b.verilogTxMon.gotFrames.map(_.toSeq)

        withClue(
          s"Chisel TX:  ${ch.map(bytesToHex).mkString(",")}\n" +
          s"Verilog TX: ${vg.map(bytesToHex).mkString(",")}\n"
        ) {
          ch shouldBe vg
        }

        progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
        progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
      }

    progEnd(tn+1, tname)
  }

  it should "TX: terminate-lane coverage hits T0..T7 (Chisel vs Verilog match)" in {
    val tn = 10
    val tname = "TX: terminate-lane coverage hits T0..T7 (Chisel vs Verilog match)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        def mkAxisBytes(payloadLen: Int): Array[Byte] = {
          val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
          val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
          val ethType = 0x0800
          val payload = Array.fill(payloadLen)(0x33.toByte)
          val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
          axisBytes
        }

        def deltaForTargetTermLane(n0: Int, startLane: Int, targetTermLane: Int): Int = {
          val want = (targetTermLane - startLane - 1) & 7
          val have = n0 & 7
          (want - have) & 7
        }

        // coverage bins
        val termSeen = Array.fill(8)(false)
        val startSeen = Array.fill(8)(false)
        val lastDataSeen = Array.fill(8)(false)

        def updateCoverageFrom(ev: TxFrameEvent): Unit = {
          if (ev.termLane >= 0) termSeen(ev.termLane) = true
          if (ev.startLane >= 0) startSeen(ev.startLane) = true
          if (ev.lastDataLane >= 0) lastDataSeen(ev.lastDataLane) = true
        }

        // settle
        b.axisTx.idle()
        stepTx(dut, b, 10)

        // 1) baseline frame
        val baselineLen = 100
        val before0 = b.chiselTxMon.gotEvents.size
        sendAxisFrame(dut, b, mkAxisBytes(baselineLen), tid = 1)

        // wait for event (don’t use raw clock.step)
        waitForOneFrameEvent(dut, b, before0, tn=tn)

        val ev0 = b.chiselTxMon.gotEvents.last
        updateCoverageFrom(ev0)

        val n0 = ev0.frameBytes
        val s0 = ev0.startLane

        // 2) drive 8 frames targeting each /T/ lane
        for (k <- 0 until 8) {
          val d = deltaForTargetTermLane(n0, s0, k)
          val len = baselineLen + d

          progInfo(tn, s"terminate-lane sweep: target k=$k (len=$len)")

          val before = b.chiselTxMon.gotEvents.size
          sendAxisFrame(dut, b, mkAxisBytes(len), tid = 10 + k)
          waitForOneFrameEvent(dut, b, before, tn=tn)

          val evCh = b.chiselTxMon.gotEvents.last
          val evV  = b.verilogTxMon.gotEvents.last

          // update coverage
          updateCoverageFrom(evCh)

          // sanity: metadata should match too (nice extra check)
          withClue(s"Event mismatch at target lane $k:\nChisel=$evCh\nVerilog=$evV\n") {
            evCh.termLane shouldBe evV.termLane
          }

          assert(evCh.termLane == k,
            s"Expected /T/ in lane $k but got ${evCh.termLane} (len=$len, N0=$n0, startLane=$s0)"
          )
        }

        // 3) coverage asserts
        assert(termSeen.forall(_ == true), s"Not all terminate lanes seen: ${termSeen.toList}")
        assert(startSeen.contains(true), "Never saw any /S/ lane? monitor not triggering?")

        // optional: still ensure frames match (like your existing TX tests)
        val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
        val vFrames  = b.verilogTxMon.gotFrames.map(_.toSeq)
        chFrames shouldBe vFrames

        progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
        progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
      }

    progEnd(tn+1, tname)
  }

  it should "TX: IFG between frames is >= cfg_tx_ifg (bytes)" in {
    val tn = 11
    val tname = "TX: IFG between frames is >= cfg_tx_ifg (bytes)"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(Seq(
      VerilatorBackendAnnotation,
      VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
      WriteVcdAnnotation
    )) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rx_ready.poke(true.B)
      dut.io.cfg_rx_max_pkt_len.poke(1518.U)

      // dut.io.cfg_tx_ifg.poke(8.U) -> did not expose this signal left it as it's default config value

      val b = mkBfms(dut)
      b.chiselTxMon.clear()
      b.verilogTxMon.clear()
      b.chiselTxStatus.clear()
      b.verilogTxStatus.clear()

      b.axisTx.idle()
      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x44.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      // send two frames
      val before0 = b.chiselTxMon.gotEvents.size
      sendAxisFrame(dut, b, mkAxisBytes(120), tid = 1)
      waitForOneFrameEvent(dut, b, before0, tn=tn)

      val before1 = b.chiselTxMon.gotEvents.size
      sendAxisFrame(dut, b, mkAxisBytes(120), tid = 2)
      waitForOneFrameEvent(dut, b, before1, tn=tn)

      withClue(
        s"IFG check failed.\n" +
        s"IFG bytes observed (lastIfgBytes) = ${b.chiselTxMon.lastIfgBytes}\n" +
        s"Last chisel TX event              = ${b.chiselTxMon.gotEvents.lastOption}\n" +
        s"Last verilog TX event             = ${b.verilogTxMon.gotEvents.lastOption}\n" +
        s"chisel tx_valid=${dut.io.chisel_xgmii_tx_valid.peek().litToBoolean}, " +
        s"verilog tx_valid=${dut.io.verilog_xgmii_tx_valid.peek().litToBoolean}\n"
      ) {
        val ifgBytes = b.chiselTxMon.lastIfgBytes
        ifgBytes should be >= 12
      }

      progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
      progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
    }

    progEnd(tn+1, tname)
  }

  it should "TX: partial tkeep on last beat (1..7) changes length and terminate lane correctly" in {
    val tn = 12
    val tname = "TX: partial tkeep on last beat (1..7) changes length and terminate lane correctly"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(Seq(
      VerilatorBackendAnnotation,
      VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
      WriteVcdAnnotation
    )) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rx_ready.poke(true.B)
      dut.io.cfg_rx_max_pkt_len.poke(1518.U)

      val b = mkBfms(dut)
      b.chiselTxMon.clear(); b.verilogTxMon.clear();
      b.chiselTxStatus.clear(); b.verilogTxStatus.clear();

      b.axisTx.idle()
      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x55.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      // Choose an axisBytes length that is a multiple of 8 so we can override last keep.
      val axisBytes = mkAxisBytes(payloadLen = 50) // header+pad makes this stable; verify multiple-of-8 below
      require(axisBytes.length % 8 == 0, s"axisBytes length must be multiple of 8, got ${axisBytes.length}")

      // 1) Baseline full-keep send
      val before0 = b.chiselTxMon.gotEvents.size
      sendAxisFrame(dut, b, axisBytes, tid = 1)
      waitForOneFrameEvent(dut, b, before0, tn=tn)

      val evFullCh = b.chiselTxMon.gotEvents.last
      val evFullV  = b.verilogTxMon.gotEvents.last

      // sanity: both models agree on baseline event
      withClue(s"Baseline event mismatch:\nChisel=$evFullCh\nVerilog=$evFullV\n") {
        evFullCh shouldBe evFullV
      }

      val inputBytesFull = axisBytes.length
      val overhead = evFullCh.frameBytes - inputBytesFull

      // 2) Partial last keep cases: 1..7 bytes valid
      for (m <- 1 to 7) {
        progInfo(tn, s"tkeep-last sweep: m=$m keep=0x${keepMask(m).toHexString}")
        val lastKeep = keepMask(m)
        val beats = bytesToAxisBeatsOverrideLastKeep(axisBytes, id = 10 + m, lastKeep = lastKeep)

        val before = b.chiselTxMon.gotEvents.size
        sendAxisBeats(dut, b, beats)
        waitForOneFrameEvent(dut, b, before, tn=tn)

        val evCh = b.chiselTxMon.gotEvents.last
        val evV  = b.verilogTxMon.gotEvents.last

        // 2a) Chisel vs Verilog must match
        withClue(s"Event mismatch for lastKeepBytes=$m:\nChisel=$evCh\nVerilog=$evV\n") {
          evCh shouldBe evV
        }

        // 2b) Expected length + terminate lane
        val inputBytesKept = inputBytesFull - (8 - m)

        // TX pads to at least 60 bytes before FCS
        val payloadNoFcsBytes = math.max(inputBytesKept, MinNoFcs)

        // Your monitor’s frameBytes includes:
        // 7 bytes (preamble+SFD in start word lanes1..7) + payloadNoFcsBytes + 4 FCS = overhead + payloadNoFcsBytes
        val expectedFrameBytes = overhead + payloadNoFcsBytes

        val expectedTermLane = expectedTermLaneFromPayloadBytes(payloadNoFcsBytes)

        withClue(
          s"tkeep-last test failed for m=$m bytes valid.\n" +
          s"inputBytesFull=$inputBytesFull inputBytesKept=$inputBytesKept overhead=$overhead\n" +
          s"Expected frameBytes=$expectedFrameBytes, got ${evCh.frameBytes}\n" +
          s"Expected termLane=$expectedTermLane, got ${evCh.termLane}\n"
        ) {
          evCh.frameBytes shouldBe expectedFrameBytes
          evCh.termLane shouldBe expectedTermLane
        }
      }

      progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
      progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
    }

    progEnd(tn+1, tname)
  }

  it should "TX: tuser=0 produces a good packet (tx status all good)" in {
    val tn = 13
    val tname = "TX: tuser=0 produces a good packet (tx status all good)"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(Seq(
      VerilatorBackendAnnotation,
      VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
      WriteVcdAnnotation
    )) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rx_ready.poke(true.B)
      dut.io.cfg_rx_max_pkt_len.poke(1518.U)

      val b = mkBfms(dut)
      b.axisTx.idle()

      b.chiselTxMon.clear(); b.verilogTxMon.clear()
      b.chiselTxStatus.clear(); b.verilogTxStatus.clear()

      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x66.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val axisBytes = mkAxisBytes(64)

      val before = b.chiselTxMon.gotEvents.size
      sendAxisFrame(dut, b, axisBytes, tid = 1)
      waitForOneFrameEvent(dut, b, before, tn=tn)

      // Give status pulses a little time to appear
      stepTx(dut, b, 50)

      val stCh = b.chiselTxStatus.snapshot
      val stV  = b.verilogTxStatus.snapshot

      withClue(s"TX status mismatch:\nChisel=$stCh\nVerilog=$stV\n") {
        stCh shouldBe stV
      }

      withClue(s"Expected good TX packet but got:\n$stCh\n") {
        stCh.pktGood shouldBe true
        stCh.pktBad  shouldBe false
        stCh.errUser shouldBe false
      }

      progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
      progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
    }

    progEnd(tn+1, tname)
  }

  it should "TX: tuser=1 triggers user-error behavior (drop or mark bad), Chisel matches Verilog" in {
    val tn = 14
    val tname = "TX: tuser=1 triggers user-error behavior (drop or mark bad), Chisel matches Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(Seq(
      VerilatorBackendAnnotation,
      VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
      WriteVcdAnnotation
    )) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rx_ready.poke(true.B)
      dut.io.cfg_rx_max_pkt_len.poke(1518.U)

      val b = mkBfms(dut)
      b.axisTx.idle()

      b.chiselTxMon.clear(); b.verilogTxMon.clear()
      b.chiselTxStatus.clear(); b.verilogTxStatus.clear()

      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x77.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val axisBytes = mkAxisBytes(64)
      val beats0 = bytesToAxisBeats(axisBytes, id = 2)

      // Put tuser=1 on the last beat (marks whole frame bad)
      val beatsBad = beats0.zipWithIndex.map { case (b, i) =>
        if (i == beats0.length - 1) b.copy(user = BigInt(1)) else b
      }

      val beforeEvCh = b.chiselTxMon.gotEvents.size
      val beforeEvV  = b.verilogTxMon.gotEvents.size

      sendAxisBeats(dut, b, beatsBad)

      // Give it time to either emit or drop
      var cycles = 0
      while ((b.chiselTxMon.gotEvents.size == beforeEvCh || b.verilogTxMon.gotEvents.size == beforeEvV) && cycles < 20000) {
        stepTx(dut, b, 1)
        cycles += 1
        // If it is a drop-style MAC, we might never get events; we'll handle that below.
        if (cycles == 20000) ()
      }

      // Let status settle
      stepTx(dut, b, 200)

      val stCh = b.chiselTxStatus.snapshot
      val stV  = b.verilogTxStatus.snapshot

      withClue(s"TX status mismatch:\nChisel=$stCh\nVerilog=$stV\n") {
        stCh shouldBe stV
      }

      val chEmitted = b.chiselTxMon.gotEvents.size > beforeEvCh
      val vEmitted  = b.verilogTxMon.gotEvents.size > beforeEvV

      withClue(s"Emission mismatch: chiselEmitted=$chEmitted verilogEmitted=$vEmitted\n") {
        chEmitted shouldBe vEmitted
      }

      // Core expectations:
      // 1) errUser should assert for a tuser-marked frame (common), OR at least pktBad should assert.
      // 2) If the MAC did emit a frame, it must not claim pktGood=true.
      withClue(
        s"tuser=1 behavior unexpected.\n" +
        s"Emitted=$chEmitted\n" +
        s"ChiselStatus=$stCh\n" +
        s"ChiselLastEvent=${b.chiselTxMon.gotEvents.lastOption}\n" +
        s"VerilogLastEvent=${b.verilogTxMon.gotEvents.lastOption}\n"
      ) {
        (stCh.errUser || stCh.pktBad) shouldBe true

        if (chEmitted) {
          stCh.pktGood shouldBe false
          stCh.pktBad  shouldBe true
        }
      }

      progInfo(tn, s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}")
      progInfo(tn, s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}")
    }

    progEnd(tn+1, tname)
  }

  it should "TX: 500 random AXIS frames (len/data) produce identical XGMII (Chisel==Verilog)" in {
    val tn = 15
    val tname = "TX: 500 random AXIS frames (len/data) produce identical XGMII (Chisel==Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
        WriteVcdAnnotation
      )) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)
        b.axisTx.idle()

        b.chiselTxMon.clear(); b.verilogTxMon.clear()
        b.chiselTxStatus.clear(); b.verilogTxStatus.clear()

        stepTx(dut, b, 10)

        val r = new Random(20260224) // deterministic

        // We'll send raw AXIS bytes (dst+src+ethType+payload+pad maybe).
        // Easiest: reuse buildEthernetFrame to get "axisBytes" (no preamble/FCS),
        // but randomize payload length + payload data.
        val dst = Array[Byte](1,2,3,4,5,6).map(_.toByte)
        val src = Array[Byte](0x0a,0x0b,0x0c,0x0d,0x0e,0x0f).map(_.toByte)
        val ethType = 0x0800

        for (i <- 0 until 500) {
          val payloadLen = r.nextInt(512) // 0..511
          val payload = randBytes(r, payloadLen)
          val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)

          val beforeEv = b.chiselTxMon.gotEvents.size
          sendAxisFrame(dut, b, axisBytes, tid = (i & 0xff))

          // Wait until we see one more event (frame) on TX side
          waitForOneFrameEvent(dut, b, beforeEv, tn = tn)

          // Drain a bit so both monitors fully close the frame
          stepTx(dut, b, 50)

          val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
          val vFrames  = b.verilogTxMon.gotFrames.map(_.toSeq)

          withClue(s"[TX rand $i] payloadLen=$payloadLen axisBytesLen=${axisBytes.length}\n") {
            chFrames shouldBe vFrames
          }

          if (i % 25 == 0) progInfo(tn, s"TX random progress: i=$i payloadLen=$payloadLen axisBytesLen=${axisBytes.length}")
        }
      }

    progEnd(tn+1, tname)
    progInfo(tn+1, s"TX tests completed, MAC testing complete!")
  }
}
