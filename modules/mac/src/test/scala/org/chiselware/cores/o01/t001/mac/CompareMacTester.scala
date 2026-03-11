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
  private val TotalTests = 16 // update this if you add/remove tests

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
  private case class AxisBeat(
    data: BigInt,
    keep: Int,
    last: Boolean,
    user: BigInt,
    tid: BigInt
  )

  private case class TxFrameEvent(
    startLane: Int,
    termLane: Int,
    frameBytes: Int, // number of DATA bytes seen between S and T
    lastDataLane: Int // lane of the last DATA byte right before T
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

  private def bytesToHex(bs: Array[Byte]): String =
    bs.iterator.map(b => f"${b & 0xff}%02x").mkString

  private def bytesToHex(bs: Seq[Byte]): String =
    bs.iterator.map(b => f"${b & 0xff}%02x").mkString

  // --------------------------------------------------------------------------
  // TX helpers (AXI4-Stream -> XGMII):
  // - stepTx: advances the simulation while sampling BOTH XGMII TX monitors and statuses
  //   (Chisel + Verilog) so we don’t miss /S/ (0xFB) or /T/ (0xFD) control chars.
  // - sendAxisFrame: streams one complete AXIS frame into DUT:
  //     * holds tvalid across beats (no gaps between beats)
  //     * waits for txTready before each handshake
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

  private def sendAxisFrame(
    dut: DualWrapperMac,
    b: Bfms,
    bytes: Array[Byte],
    tid: Int
  ): Unit = {
    val beats = bytesToAxisBeats(bytes, id = tid)

    beats.foreach { beat =>
      b.axisTx.driveBeat(beat)
      while (!dut.io.txTready.peek().litToBoolean) {
        stepTx(dut, b, 1)
      }
      stepTx(dut, b, 1)
    }

    b.axisTx.idle()
    stepTx(dut, b, 1)
  }

  // -------------------------------------------------------------------------
  // Negative TX test helpers
  // -------------------------------------------------------------------------
  private def sendAxisBeats(
    dut: DualWrapperMac,
    b: Bfms,
    beats: Seq[AxisBeat]
  ): Unit = {
    beats.foreach { beat =>
      b.axisTx.driveBeat(beat)
      while (!dut.io.txTready.peek().litToBoolean) {
        stepTx(dut, b, 1)
      }
      stepTx(dut, b, 1)
    }
    b.axisTx.idle()
    stepTx(dut, b, 1)
  }

  private def bytesToAxisBeatsOverrideLastKeep(
    bytes: Array[Byte],
    id: Int,
    lastKeep: Int
  ): Seq[AxisBeat] = {
    require(
      bytes.length % 8 == 0,
      s"Need multiple-of-8 bytes to override last keep safely, got ${bytes.length}"
    )

    val beats = bytesToAxisBeats(bytes, id = id).toBuffer
    val last = beats.last
    beats(beats.length - 1) = last.copy(keep = lastKeep, last = true)
    beats.toSeq
  }

  private val MinFrameLen = 64
  private val MinNoFcs = MinFrameLen - 4

  private def expectedTermLaneFromPayloadBytes(payloadNoFcsBytes: Int): Int = {
    val rem = payloadNoFcsBytes % 8
    val empty = (8 - rem) % 8

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

  private def keepMask(nBytes: Int): Int = (1 << nBytes) - 1

  private def beatsWithUser(
    beats: Seq[AxisBeat],
    userOnBeatIdx: Set[Int]
  ): Seq[AxisBeat] = {
    beats.zipWithIndex.map { case (b, i) =>
      if (userOnBeatIdx.contains(i)) b.copy(user = BigInt(1))
      else b.copy(user = BigInt(0))
    }
  }

  // -------------------------------------------------------------------------
  // TX coverage test helpers
  // -------------------------------------------------------------------------
  /** Keep stepping until BOTH models capture a new TX frame event. */
  private def waitForOneFrameEvent(
    dut: DualWrapperMac,
    b: Bfms,
    beforeChisel: Int,
    beforeVerilog: Int,
    maxCycles: Int = 20000,
    tn: Int = -1
  ): Unit = {
    var cycles = 0
    while (
      (b.chiselTxMon.gotEvents.size == beforeChisel ||
        b.verilogTxMon.gotEvents.size == beforeVerilog) &&
      cycles < maxCycles
    ) {
      stepTx(dut, b, 1)
      cycles += 1
      if (tn >= 0 && cycles % 2000 == 0) {
        progInfo(
          tn,
          s"waiting for TX event... cycles=$cycles ch=${b.chiselTxMon.gotEvents.size} v=${b.verilogTxMon.gotEvents.size}"
        )
      }
    }

    assert(
      b.chiselTxMon.gotEvents.size > beforeChisel,
      s"Timed out waiting for Chisel TX frame event after $cycles cycles"
    )
    assert(
      b.verilogTxMon.gotEvents.size > beforeVerilog,
      s"Timed out waiting for Verilog TX frame event after $cycles cycles"
    )
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
        crc =
          if ((crc & 1) != 0) (crc >>> 1) ^ poly
          else (crc >>> 1)
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

    val header =
      dst ++ src ++ Array(
        ((ethType >>> 8) & 0xff).toByte,
        (ethType & 0xff).toByte
      )

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
  // XGMII encode for 64-bit
  // --------------------------------------------------------------------------
  private def packWordLittle(bytes8: Array[Byte]): BigInt = {
    bytes8.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (b, i)) =>
      acc | (BigInt(b & 0xff) << (8 * i))
    }
  }

  private def xgmiiEncode64(
    fullFrameBytes: Array[Byte],
    startOverride: Option[Array[Byte]] = None
  ): Seq[(BigInt, Int)] = {
    require(fullFrameBytes.length >= 8)

    val startCycleBytes = startOverride.getOrElse(
      Array[Byte](
        0xFB.toByte,
        0x55.toByte,
        0x55.toByte,
        0x55.toByte,
        0x55.toByte,
        0x55.toByte,
        0x55.toByte,
        0xD5.toByte
      )
    )
    require(startCycleBytes.length == 8)

    val startData = packWordLittle(startCycleBytes)
    val startCtrl = 0x01

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

        val termLane = remaining
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
        data |= BigInt(bytes(idx + i) & 0xff) << (8 * i)
        keep |= (1 << i)
      }
      val last = (idx + n) >= bytes.length
      beats += AxisBeat(
        data = data,
        keep = keep,
        last = last,
        user = BigInt(0),
        tid = BigInt(id)
      )
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
    private var termLane = -1
    private var frameBytes = 0
    private var lastDataLane = -1

    private var afterTerm = false
    private var idleBytesSinceTerm = 0
    private var lastIfgIdleBytes = 0

    def clear(): Unit = {
      frames.clear()
      events.clear()
      inFrame = false
      cur.clear()
      startLane = -1
      termLane = -1
      frameBytes = 0
      lastDataLane = -1
      afterTerm = false
      idleBytesSinceTerm = 0
      lastIfgIdleBytes = 0
    }

    def gotFrames: Seq[Array[Byte]] = frames.toSeq
    def gotEvents: Seq[TxFrameEvent] = events.toSeq
    def lastIfgBytes: Int = lastIfgIdleBytes

    def sample(): Unit = {
      if (!txValid.peek().litToBoolean) return

      val d = txd.peek().litValue
      val c = txc.peek().litValue.toInt

      def laneByte(i: Int): Int = ((d >> (8 * i)) & 0xff).toInt
      def isCtrl(i: Int): Boolean = ((c >> i) & 1) == 1
      def isIdle(i: Int): Boolean = isCtrl(i) && laneByte(i) == 0x07

      if (afterTerm) {
        for (i <- 0 until 8) if (isIdle(i)) idleBytesSinceTerm += 1
      }

      if (!inFrame) {
        for (i <- 0 until 8) {
          if (isCtrl(i) && laneByte(i) == 0xFB) {
            inFrame = true
            startLane = i
            termLane = -1
            frameBytes = 0
            lastDataLane = -1

            if (afterTerm) {
              lastIfgIdleBytes = idleBytesSinceTerm
              afterTerm = false
              idleBytesSinceTerm = 0
            }

            for (j <- i + 1 until 8) {
              if (!isCtrl(j)) {
                cur += laneByte(j).toByte
                frameBytes += 1
                lastDataLane = j
              }
              if (isCtrl(j) && laneByte(j) == 0xFD) {
                termLane = j
                frames += cur.toArray
                events += TxFrameEvent(startLane, termLane, frameBytes, lastDataLane)
                cur.clear()
                inFrame = false

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

      for (i <- 0 until 8) {
        if (isCtrl(i)) {
          val b = laneByte(i)
          if (b == 0xFD) {
            termLane = i
            frames += cur.toArray
            events += TxFrameEvent(startLane, termLane, frameBytes, lastDataLane)
            cur.clear()
            inFrame = false

            afterTerm = true
            idleBytesSinceTerm = 0
            for (k <- (i + 1) until 8) {
              if (isIdle(k)) idleBytesSinceTerm += 1
            }
            return
          }
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
      dut.io.txTdata.poke(0.U)
      dut.io.txTkeep.poke(0.U)
      dut.io.txTvalid.poke(false.B)
      dut.io.txTlast.poke(false.B)
      dut.io.txTuser.poke(0.U)
      dut.io.txTid.poke(0.U)
    }

    def driveBeat(b: AxisBeat): Unit = {
      dut.io.txTdata.poke(b.data.U(64.W))
      dut.io.txTkeep.poke(b.keep.U(8.W))
      dut.io.txTvalid.poke(true.B)
      dut.io.txTlast.poke(b.last.B)
      dut.io.txTuser.poke(b.user.U(1.W))
      dut.io.txTid.poke(b.tid.U(8.W))
    }
  }

  private class XgmiiRxDriverBfm(dut: DualWrapperMac) {
    private val idleWord: BigInt = BigInt("0707070707070707", 16)

    def drive(data: BigInt, ctrl: Int, rxValid: Boolean = true): Unit = {
      dut.io.xgmiiRxd.poke(data.U(64.W))
      dut.io.xgmiiRxc.poke(ctrl.U(8.W))
      dut.io.xgmiiRxValid.poke(rxValid.B)
    }

    def driveIdle(): Unit = drive(idleWord, 0xFF, rxValid = true)
  }

  private class AxisCollectorBfm(
    tvalid: Bool,
    tdata: UInt,
    tkeep: UInt,
    tlast: Bool,
    tuser: UInt,
    tid: UInt
  ) {
    val beats: ArrayBuffer[AxisBeat] = ArrayBuffer.empty
    def clear(): Unit = beats.clear()
    def sample(): Unit = {
      if (tvalid.peek().litToBoolean) {
        beats += AxisBeat(
          data = tdata.peek().litValue,
          keep = tkeep.peek().litValue.toInt,
          last = tlast.peek().litToBoolean,
          user = tuser.peek().litValue,
          tid = tid.peek().litValue
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
        good = s.good || pktGood.peek().litToBoolean,
        bad = s.bad || pktBad.peek().litToBoolean,
        badFcs = s.badFcs || errBadFcs.peek().litToBoolean,
        preamble = s.preamble || errPreamble.peek().litToBoolean,
        framing = s.framing || errFraming.peek().litToBoolean,
        oversize = s.oversize || errOversize.peek().litToBoolean,
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
        pktGood = s.pktGood || pktGood.peek().litToBoolean,
        pktBad = s.pktBad || pktBad.peek().litToBoolean,
        errUser = s.errUser || errUser.peek().litToBoolean,
        errOversize = s.errOversize || errOversize.peek().litToBoolean,
        errUnderflow = s.errUnderflow || errUnderflow.peek().litToBoolean
      )
    }
    def snapshot: TxStatus = s
  }

  // --------------------------------------------------------------------------
  // Bundle BFMs so tests don’t repeat wiring boilerplate
  // --------------------------------------------------------------------------
  private case class Bfms(
    xgmii: XgmiiRxDriverBfm,
    chiselAxis: AxisCollectorBfm,
    verilogAxis: AxisCollectorBfm,
    chiselStatus: StatusCollectorBfm,
    verilogStatus: StatusCollectorBfm,
    axisTx: AxisTxDriverBfm,
    chiselTxMon: XgmiiTxMonitorBfm,
    verilogTxMon: XgmiiTxMonitorBfm,
    chiselTxStatus: TxStatusCollectorBfm,
    verilogTxStatus: TxStatusCollectorBfm
  )

  private def mkBfms(dut: DualWrapperMac): Bfms = {
    val xgmii = new XgmiiRxDriverBfm(dut)

    val chiselAxis = new AxisCollectorBfm(
      dut.io.chiselRxTvalid,
      dut.io.chiselRxTdata,
      dut.io.chiselRxTkeep,
      dut.io.chiselRxTlast,
      dut.io.chiselRxTuser,
      dut.io.chiselRxTid
    )

    val verilogAxis = new AxisCollectorBfm(
      dut.io.verilogRxTvalid,
      dut.io.verilogRxTdata,
      dut.io.verilogRxTkeep,
      dut.io.verilogRxTlast,
      dut.io.verilogRxTuser,
      dut.io.verilogRxTid
    )

    val chiselStatus = new StatusCollectorBfm(
      dut.io.chiselStatRxPktGood,
      dut.io.chiselStatRxPktBad,
      dut.io.chiselStatRxErrBadFcs,
      dut.io.chiselStatRxErrPreamble,
      dut.io.chiselStatRxErrFraming,
      dut.io.chiselStatRxErrOversize,
      dut.io.chiselStatRxPktFragment
    )

    val verilogStatus = new StatusCollectorBfm(
      dut.io.verilogStatRxPktGood,
      dut.io.verilogStatRxPktBad,
      dut.io.verilogStatRxErrBadFcs,
      dut.io.verilogStatRxErrPreamble,
      dut.io.verilogStatRxErrFraming,
      dut.io.verilogStatRxErrOversize,
      dut.io.verilogStatRxPktFragment
    )

    val axisTx = new AxisTxDriverBfm(dut)

    val chiselTxMon = new XgmiiTxMonitorBfm(
      dut.io.chiselXgmiiTxd,
      dut.io.chiselXgmiiTxc,
      dut.io.chiselXgmiiTxValid
    )
    val verilogTxMon = new XgmiiTxMonitorBfm(
      dut.io.verilogXgmiiTxd,
      dut.io.verilogXgmiiTxc,
      dut.io.verilogXgmiiTxValid
    )

    val chiselTxStatus = new TxStatusCollectorBfm(
      dut.io.chiselStatTxPktGood,
      dut.io.chiselStatTxPktBad,
      dut.io.chiselStatTxErrUser,
      dut.io.chiselStatTxErrOversize,
      dut.io.chiselStatTxErrUnderflow
    )

    val verilogTxStatus = new TxStatusCollectorBfm(
      dut.io.verilogStatTxPktGood,
      dut.io.verilogStatTxPktBad,
      dut.io.verilogStatTxErrUser,
      dut.io.verilogStatTxErrOversize,
      dut.io.verilogStatTxErrUnderflow
    )

    axisTx.idle()

    Bfms(
      xgmii,
      chiselAxis,
      verilogAxis,
      chiselStatus,
      verilogStatus,
      axisTx,
      chiselTxMon,
      verilogTxMon,
      chiselTxStatus,
      verilogTxStatus
    )
  }

  // --------------------------------------------------------------------------
  // Shared test runner
  // --------------------------------------------------------------------------
  private def drainTxUntilFrame(
    dut: DualWrapperMac,
    b: Bfms,
    maxCycles: Int = 5000
  ): Unit = {
    var cycles = 0
    while (
      (b.chiselTxMon.gotFrames.isEmpty || b.verilogTxMon.gotFrames.isEmpty) &&
      cycles < maxCycles
    ) {
      b.chiselTxMon.sample()
      b.verilogTxMon.sample()
      dut.clock.step(1)
      cycles += 1
    }
  }

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
      while (
        !(chiselAxis.beats.exists(_.last) && verilogAxis.beats.exists(_.last)) &&
        cycles < maxCycles
      ) {
        chiselStatus.sample()
        verilogStatus.sample()
        chiselAxis.sample()
        verilogAxis.sample()
        dut.clock.step(1)
        cycles += 1
      }
      chiselAxis.beats.exists(_.last) shouldBe true
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
      stepSample(drainMaxCycles)
    }

    val chiselFrames = beatsToFrames(chiselAxis.beats.toSeq)
    val verilogFrames = beatsToFrames(verilogAxis.beats.toSeq)

    if (requireLast) {
      withClue(
        s"Chisel frames:  ${chiselFrames.map(bytesToHex).mkString(",")}\n" +
          s"Verilog frames: ${verilogFrames.map(bytesToHex).mkString(",")}\n"
      ) {
        chiselFrames.map(_.toSeq) shouldBe verilogFrames.map(_.toSeq)
      }
    } else {
      chiselFrames.map(_.toSeq) shouldBe verilogFrames.map(_.toSeq)
    }

    prog(s"chiselFrames=${chiselFrames.size}, verilogFrames=${verilogFrames.size}")

    (chiselFrames, verilogFrames)
  }

  // --------------------------------------------------------------------------
  // Test cases
  // --------------------------------------------------------------------------
  it should "MAC RX: valid frame passes (tuser==0) and matches expected bytes" in {
    progInfo(0, "start of MAC testing")
    val tn = 0
    val tname = "MAC RX: valid frame passes (tuser==0) and matches expected bytes"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, expectedAxisBytes) =
          buildEthernetFrame(dst, src, ethType, payload)
        val xgmiiWords = xgmiiEncode64(fullBytes)

        val (_, verilogFrames) = runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          prog = msg => progInfo(tn, msg)
        )

        verilogFrames.size shouldBe 1
        verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

        b.verilogAxis.beats.last.user shouldBe 0
        b.chiselAxis.beats.last.user shouldBe 0
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: bad FCS is flagged (tuser==1)" in {
    val tn = 1
    val tname = "MAC RX: bad FCS is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullBad = fullBytes.clone()
        fullBad(fullBad.length - 1) = (fullBad.last ^ 0x01).toByte

        val xgmiiWords = xgmiiEncode64(fullBad)

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          prog = msg => progInfo(tn, msg)
        )

        b.verilogAxis.beats.nonEmpty shouldBe true
        b.verilogAxis.beats.last.user shouldBe 1
        b.chiselAxis.beats.last.user shouldBe 1
        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.badFcs shouldBe true
        vs.good shouldBe false
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: runt (truncated FCS) is flagged (tuser==1)" in {
    val tn = 2
    val tname = "MAC RX: runt (truncated FCS) is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullRunt = fullBytes.dropRight(2)

        val xgmiiWords = xgmiiEncode64(fullRunt)

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false,
          prog = msg => progInfo(tn, msg)
        )

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        (vs.badFcs || vs.pktFragment) shouldBe true
        vs.good shouldBe false

        if (b.verilogAxis.beats.nonEmpty) b.verilogAxis.beats.last.user shouldBe 1
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: bad preamble/SFD (corrupted) is detected" in {
    val tn = 3
    val tname = "MAC RX: bad preamble/SFD (corrupted) - detected"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)

        val badStart = Array[Byte](
          0xFB.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x00.toByte
        )

        val xgmiiWords = xgmiiEncode64(fullBytes, startOverride = Some(badStart))

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false,
          prog = msg => progInfo(tn, msg)
        )

        val vs = b.verilogStatus.snapshot
        vs.good shouldBe true
        vs.preamble shouldBe true
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: framing error (control inside payload) is flagged (tuser==1)" in {
    val tn = 4
    val tname = "MAC RX: framing error (control inside payload) is flagged (tuser==1)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val words = xgmiiEncode64(fullBytes).toBuffer

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

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          words.toSeq,
          prog = msg => progInfo(tn, msg)
        )

        b.verilogAxis.beats.nonEmpty shouldBe true
        b.verilogAxis.beats.last.user shouldBe 1
        b.chiselAxis.beats.last.user shouldBe 1
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: oversize is flagged when cfg_rx_max_pkt_len is small" in {
    val tn = 5
    val tname = "MAC RX: oversize is flagged when cfg_rx_max_pkt_len is small"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(64.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(100)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val xgmiiWords = xgmiiEncode64(fullBytes)

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false,
          prog = msg => progInfo(tn, msg)
        )

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.oversize shouldBe true
        vs.good shouldBe false

        if (b.verilogAxis.beats.nonEmpty) b.verilogAxis.beats.last.user shouldBe 1
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: terminate-lane coverage hits T0..T7 on good frames (tuser==0), Chisel matches Verilog" in {
    val tn = 6
    val tname =
      "MAC RX: terminate-lane coverage hits T0..T7 on good frames (tuser==0), Chisel matches Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800

        val termSeen = Array.fill(8)(false)

        for (k <- 0 until 8) {
          val payloadLen = payloadLenForRxTerminateLane(k)
          val payload =
            Array.tabulate(payloadLen)(i => ((0xA0 + (i & 0x0F)) & 0xFF).toByte)

          val (fullBytes, expectedAxisBytes) =
            buildEthernetFrame(dst, src, ethType, payload)
          val xgmiiWords = xgmiiEncode64(fullBytes)

          val termLane = findTerminateLane(xgmiiWords)
          withClue(
            s"Could not find /T/ in generated xgmiiWords for target k=$k (payloadLen=$payloadLen)\n"
          ) {
            termLane shouldBe k
          }
          termSeen(termLane) = true

          progInfo(
            tn,
            s"RX /T/ sweep: target=$k payloadLen=$payloadLen termLaneFound=$termLane"
          )

          val (_, verilogFrames) =
            runCase(
              dut,
              b.xgmii,
              b.chiselAxis,
              b.verilogAxis,
              b.chiselStatus,
              b.verilogStatus,
              xgmiiWords,
              requireLast = true,
              prog = msg => progInfo(tn, msg)
            )

          verilogFrames.size shouldBe 1
          verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

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

    progEnd(tn + 1, tname)
  }

  it should "MAC RX: 500 random frames (payload/len/data) match expected and Chisel==Verilog" in {
    val tn = 7
    val tname = "MAC RX: 500 random frames (payload/len/data) match expected and Chisel==Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800

        val r = new Random(20260224)

        for (i <- 0 until 500) {
          val payloadLen = r.nextInt(512)
          val payload = randBytes(r, payloadLen)

          val (fullBytes, expectedAxisBytes) =
            buildEthernetFrame(dst, src, ethType, payload)
          val xgmiiWords = xgmiiEncode64(fullBytes)

          val (_, verilogFrames) =
            runCase(
              dut,
              b.xgmii,
              b.chiselAxis,
              b.verilogAxis,
              b.chiselStatus,
              b.verilogStatus,
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

    progEnd(tn + 1, tname)
    progInfo(tn + 1, s"RX tests completed, TX tests starting...")
  }

  it should "MAC TX: AXIS -> XGMII produces matching frames (Chisel vs Verilog)" in {
    val tn = 8
    val tname = "MAC TX: AXIS -> XGMII produces matching frames (Chisel vs Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(64)(0x11.toByte)

        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)

        b.axisTx.idle()
        stepTx(dut, b, 5)
        sendAxisFrame(dut, b, axisBytes, tid = 1)

        b.axisTx.idle()
        stepTx(dut, b, 2000)

        withClue(
          s"Chisel TX frames=${b.chiselTxMon.gotFrames.size}, Verilog TX frames=${b.verilogTxMon.gotFrames.size}\n"
        ) {
          b.chiselTxMon.gotFrames.nonEmpty shouldBe true
          b.verilogTxMon.gotFrames.nonEmpty shouldBe true
        }

        val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
        val vFrames = b.verilogTxMon.gotFrames.map(_.toSeq)

        withClue(
          s"Chisel TX:  ${chFrames.map(bytesToHex).mkString(",")}\n" +
            s"Verilog TX: ${vFrames.map(bytesToHex).mkString(",")}\n"
        ) {
          chFrames shouldBe vFrames
        }

        progInfo(
          tn,
          s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
        )
        progInfo(
          tn,
          s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
        )
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: back-to-back frames preserve ordering and match (Chisel vs Verilog)" in {
    val tn = 9
    val tname = "MAC TX: back-to-back frames preserve ordering and match (Chisel vs Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        def mkAxisBytes(payloadLen: Int): Array[Byte] = {
          val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
          val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
          val ethType = 0x0800
          val payload = Array.fill(payloadLen)(0x22.toByte)
          val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
          axisBytes
        }

        sendAxisFrame(dut, b, mkAxisBytes(10), 1)
        sendAxisFrame(dut, b, mkAxisBytes(64), 2)
        sendAxisFrame(dut, b, mkAxisBytes(200), 3)

        var cycles = 0
        while (
          (b.chiselTxMon.gotFrames.size < 3 || b.verilogTxMon.gotFrames.size < 3) &&
          cycles < 20000
        ) {
          stepTx(dut, b, 1)
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

        progInfo(
          tn,
          s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
        )
        progInfo(
          tn,
          s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
        )
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: terminate-lane coverage hits T0..T7 (Chisel vs Verilog match)" in {
    val tn = 10
    val tname = "MAC TX: terminate-lane coverage hits T0..T7 (Chisel vs Verilog match)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)
        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        def mkAxisBytes(payloadLen: Int): Array[Byte] = {
          val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
          val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
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

        val termSeen = Array.fill(8)(false)
        val startSeen = Array.fill(8)(false)
        val lastDataSeen = Array.fill(8)(false)

        def updateCoverageFrom(ev: TxFrameEvent): Unit = {
          if (ev.termLane >= 0) termSeen(ev.termLane) = true
          if (ev.startLane >= 0) startSeen(ev.startLane) = true
          if (ev.lastDataLane >= 0) lastDataSeen(ev.lastDataLane) = true
        }

        b.axisTx.idle()
        stepTx(dut, b, 10)

        val baselineLen = 100
        val before0Ch = b.chiselTxMon.gotEvents.size
        val before0V = b.verilogTxMon.gotEvents.size
        sendAxisFrame(dut, b, mkAxisBytes(baselineLen), tid = 1)

        waitForOneFrameEvent(dut, b, before0Ch, before0V, tn = tn)

        val ev0 = b.chiselTxMon.gotEvents.last
        updateCoverageFrom(ev0)

        val n0 = ev0.frameBytes
        val s0 = ev0.startLane

        for (k <- 0 until 8) {
          val d = deltaForTargetTermLane(n0, s0, k)
          val len = baselineLen + d

          progInfo(tn, s"terminate-lane sweep: target k=$k (len=$len)")

          val beforeCh = b.chiselTxMon.gotEvents.size
          val beforeV = b.verilogTxMon.gotEvents.size
          sendAxisFrame(dut, b, mkAxisBytes(len), tid = 10 + k)
          waitForOneFrameEvent(dut, b, beforeCh, beforeV, tn = tn)

          val evCh = b.chiselTxMon.gotEvents.last
          val evV = b.verilogTxMon.gotEvents.last

          updateCoverageFrom(evCh)

          withClue(s"Event mismatch at target lane $k:\nChisel=$evCh\nVerilog=$evV\n") {
            evCh.termLane shouldBe evV.termLane
          }

          assert(
            evCh.termLane == k,
            s"Expected /T/ in lane $k but got ${evCh.termLane} (len=$len, N0=$n0, startLane=$s0)"
          )
        }

        assert(termSeen.forall(_ == true), s"Not all terminate lanes seen: ${termSeen.toList}")
        assert(startSeen.contains(true), "Never saw any /S/ lane? monitor not triggering?")

        val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
        val vFrames = b.verilogTxMon.gotFrames.map(_.toSeq)
        chFrames shouldBe vFrames

        progInfo(
          tn,
          s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
        )
        progInfo(
          tn,
          s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
        )
      }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: IFG between frames is >= cfg_tx_ifg (bytes)" in {
    val tn = 11
    val tname = "MAC TX: IFG between frames is >= cfg_tx_ifg (bytes)"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(
      Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(
          Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
        ),
        WriteVcdAnnotation
      )
    ) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rxReady.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      val b = mkBfms(dut)
      b.chiselTxMon.clear()
      b.verilogTxMon.clear()
      b.chiselTxStatus.clear()
      b.verilogTxStatus.clear()

      b.axisTx.idle()
      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x44.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val before0Ch = b.chiselTxMon.gotEvents.size
      val before0V = b.verilogTxMon.gotEvents.size
      sendAxisFrame(dut, b, mkAxisBytes(120), tid = 1)
      waitForOneFrameEvent(dut, b, before0Ch, before0V, tn = tn)

      val before1Ch = b.chiselTxMon.gotEvents.size
      val before1V = b.verilogTxMon.gotEvents.size
      sendAxisFrame(dut, b, mkAxisBytes(120), tid = 2)
      waitForOneFrameEvent(dut, b, before1Ch, before1V, tn = tn)

      withClue(
        s"IFG check failed.\n" +
          s"IFG bytes observed (lastIfgBytes) = ${b.chiselTxMon.lastIfgBytes}\n" +
          s"Last chisel TX event              = ${b.chiselTxMon.gotEvents.lastOption}\n" +
          s"Last verilog TX event             = ${b.verilogTxMon.gotEvents.lastOption}\n" +
          s"chisel tx_valid=${dut.io.chiselXgmiiTxValid.peek().litToBoolean}, " +
          s"verilog tx_valid=${dut.io.verilogXgmiiTxValid.peek().litToBoolean}\n"
      ) {
        val ifgBytes = b.chiselTxMon.lastIfgBytes
        ifgBytes should be >= 12
      }

      progInfo(
        tn,
        s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
      )
      progInfo(
        tn,
        s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
      )
    }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: partial tkeep on last beat (1..7) changes length and terminate lane correctly" in {
    val tn = 12
    val tname = "MAC TX: partial tkeep on last beat (1..7) changes length and terminate lane correctly"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(
      Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(
          Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
        ),
        WriteVcdAnnotation
      )
    ) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rxReady.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      val b = mkBfms(dut)
      b.chiselTxMon.clear()
      b.verilogTxMon.clear()
      b.chiselTxStatus.clear()
      b.verilogTxStatus.clear()

      b.axisTx.idle()
      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x55.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val axisBytes = mkAxisBytes(payloadLen = 50)
      require(
        axisBytes.length % 8 == 0,
        s"axisBytes length must be multiple of 8, got ${axisBytes.length}"
      )

      val before0Ch = b.chiselTxMon.gotEvents.size
      val before0V = b.verilogTxMon.gotEvents.size
      sendAxisFrame(dut, b, axisBytes, tid = 1)
      waitForOneFrameEvent(dut, b, before0Ch, before0V, tn = tn)

      val evFullCh = b.chiselTxMon.gotEvents.last
      val evFullV = b.verilogTxMon.gotEvents.last

      withClue(s"Baseline event mismatch:\nChisel=$evFullCh\nVerilog=$evFullV\n") {
        evFullCh shouldBe evFullV
      }

      val inputBytesFull = axisBytes.length
      val overhead = evFullCh.frameBytes - inputBytesFull

      for (m <- 1 to 7) {
        progInfo(tn, s"tkeep-last sweep: m=$m keep=0x${keepMask(m).toHexString}")
        val lastKeep = keepMask(m)
        val beats =
          bytesToAxisBeatsOverrideLastKeep(axisBytes, id = 10 + m, lastKeep = lastKeep)

        val beforeCh = b.chiselTxMon.gotEvents.size
        val beforeV = b.verilogTxMon.gotEvents.size
        sendAxisBeats(dut, b, beats)
        waitForOneFrameEvent(dut, b, beforeCh, beforeV, tn = tn)

        val evCh = b.chiselTxMon.gotEvents.last
        val evV = b.verilogTxMon.gotEvents.last

        withClue(s"Event mismatch for lastKeepBytes=$m:\nChisel=$evCh\nVerilog=$evV\n") {
          evCh shouldBe evV
        }

        val inputBytesKept = inputBytesFull - (8 - m)
        val payloadNoFcsBytes = math.max(inputBytesKept, MinNoFcs)
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

      progInfo(
        tn,
        s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
      )
      progInfo(
        tn,
        s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
      )
    }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: tuser=0 produces a good packet (tx status all good)" in {
    val tn = 13
    val tname = "MAC TX: tuser=0 produces a good packet (tx status all good)"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(
      Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(
          Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
        ),
        WriteVcdAnnotation
      )
    ) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rxReady.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      val b = mkBfms(dut)
      b.axisTx.idle()

      b.chiselTxMon.clear()
      b.verilogTxMon.clear()
      b.chiselTxStatus.clear()
      b.verilogTxStatus.clear()

      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x66.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val axisBytes = mkAxisBytes(64)

      val beforeCh = b.chiselTxMon.gotEvents.size
      val beforeV = b.verilogTxMon.gotEvents.size
      sendAxisFrame(dut, b, axisBytes, tid = 1)
      waitForOneFrameEvent(dut, b, beforeCh, beforeV, tn = tn)

      stepTx(dut, b, 50)

      val stCh = b.chiselTxStatus.snapshot
      val stV = b.verilogTxStatus.snapshot

      withClue(s"TX status mismatch:\nChisel=$stCh\nVerilog=$stV\n") {
        stCh shouldBe stV
      }

      withClue(s"Expected good TX packet but got:\n$stCh\n") {
        stCh.pktGood shouldBe true
        stCh.pktBad shouldBe false
        stCh.errUser shouldBe false
      }

      progInfo(
        tn,
        s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
      )
      progInfo(
        tn,
        s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
      )
    }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: tuser=1 triggers user-error behavior (drop or mark bad), Chisel matches Verilog" in {
    val tn = 14
    val tname = "MAC TX: tuser=1 triggers user-error behavior (drop or mark bad), Chisel matches Verilog"
    progStart(tn, tname)

    test(new DualWrapperMac).withAnnotations(
      Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(
          Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
        ),
        WriteVcdAnnotation
      )
    ) { dut =>

      dut.clock.setTimeout(0)
      dut.io.rxReady.poke(true.B)
      dut.io.cfgRxMaxPktLen.poke(1518.U)

      val b = mkBfms(dut)
      b.axisTx.idle()

      b.chiselTxMon.clear()
      b.verilogTxMon.clear()
      b.chiselTxStatus.clear()
      b.verilogTxStatus.clear()

      stepTx(dut, b, 10)

      def mkAxisBytes(payloadLen: Int): Array[Byte] = {
        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(payloadLen)(0x77.toByte)
        val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)
        axisBytes
      }

      val axisBytes = mkAxisBytes(64)
      val beats0 = bytesToAxisBeats(axisBytes, id = 2)

      val beatsBad = beats0.zipWithIndex.map { case (b0, i) =>
        if (i == beats0.length - 1) b0.copy(user = BigInt(1)) else b0
      }

      val beforeEvCh = b.chiselTxMon.gotEvents.size
      val beforeEvV = b.verilogTxMon.gotEvents.size

      sendAxisBeats(dut, b, beatsBad)

      var cycles = 0
      while (
        (b.chiselTxMon.gotEvents.size == beforeEvCh ||
          b.verilogTxMon.gotEvents.size == beforeEvV) &&
        cycles < 20000
      ) {
        stepTx(dut, b, 1)
        cycles += 1
      }

      stepTx(dut, b, 200)

      val stCh = b.chiselTxStatus.snapshot
      val stV = b.verilogTxStatus.snapshot

      withClue(s"MAC TX status mismatch:\nChisel=$stCh\nVerilog=$stV\n") {
        stCh shouldBe stV
      }

      val chEmitted = b.chiselTxMon.gotEvents.size > beforeEvCh
      val vEmitted = b.verilogTxMon.gotEvents.size > beforeEvV

      withClue(s"Emission mismatch: chiselEmitted=$chEmitted verilogEmitted=$vEmitted\n") {
        chEmitted shouldBe vEmitted
      }

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
          stCh.pktBad shouldBe true
        }
      }

      progInfo(
        tn,
        s"chiselTxFrames=${b.chiselTxMon.gotFrames.size}, verilogTxFrames=${b.verilogTxMon.gotFrames.size}"
      )
      progInfo(
        tn,
        s"chiselTxEvents=${b.chiselTxMon.gotEvents.size}, verilogTxEvents=${b.verilogTxMon.gotEvents.size}"
      )
    }

    progEnd(tn + 1, tname)
  }

  it should "MAC TX: 500 random AXIS frames (len/data) produce identical XGMII (Chisel==Verilog)" in {
    val tn = 15
    val tname = "MAC TX: 500 random AXIS frames (len/data) produce identical XGMII (Chisel==Verilog)"
    progStart(tn, tname)

    test(new DualWrapperMac)
      .withAnnotations(
        Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(
            Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
          ),
          WriteVcdAnnotation
        )
      ) { dut =>

        dut.clock.setTimeout(0)
        dut.io.rxReady.poke(true.B)
        dut.io.cfgRxMaxPktLen.poke(1518.U)

        val b = mkBfms(dut)
        b.axisTx.idle()

        b.chiselTxMon.clear()
        b.verilogTxMon.clear()
        b.chiselTxStatus.clear()
        b.verilogTxStatus.clear()

        stepTx(dut, b, 10)

        val r = new Random(20260224)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800

        for (i <- 0 until 500) {
          val payloadLen = r.nextInt(512)
          val payload = randBytes(r, payloadLen)
          val (_, axisBytes) = buildEthernetFrame(dst, src, ethType, payload)

          val beforeCh = b.chiselTxMon.gotEvents.size
          val beforeV = b.verilogTxMon.gotEvents.size
          sendAxisFrame(dut, b, axisBytes, tid = (i & 0xff))

          waitForOneFrameEvent(dut, b, beforeCh, beforeV, tn = tn)

          stepTx(dut, b, 50)

          val chFrames = b.chiselTxMon.gotFrames.map(_.toSeq)
          val vFrames = b.verilogTxMon.gotFrames.map(_.toSeq)

          withClue(s"[TX rand $i] payloadLen=$payloadLen axisBytesLen=${axisBytes.length}\n") {
            chFrames shouldBe vFrames
          }

          if (i % 25 == 0) {
            progInfo(
              tn,
              s"TX random progress: i=$i payloadLen=$payloadLen axisBytesLen=${axisBytes.length}"
            )
          }
        }
      }

    progEnd(tn + 1, tname)
    progInfo(tn + 1, s"TX tests completed, MAC testing complete!")
  }
}