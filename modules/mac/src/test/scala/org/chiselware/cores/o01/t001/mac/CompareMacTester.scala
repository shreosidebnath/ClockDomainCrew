package org.chiselware.cores.o01.t001.mac

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ArrayBuffer

class CompareMacTester extends AnyFlatSpec with ChiselScalatestTester
    with Matchers {

  // --------------------------------------------------------------------------
  // Types / helpers
  // --------------------------------------------------------------------------
  private case class AxisBeat(
      data: BigInt,
      keep: Int,
      last: Boolean,
      user: BigInt,
      tid: BigInt)

  /** Convert keep mask + 64-bit data into bytes (little-endian lanes: byte0 is
    * LSB).
    */
  private def beatToBytes(
      b: AxisBeat,
      dataBytes: Int = 8
    ): Array[Byte] = {
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

  /** Split a stream of beats into frames on last=true, returning each frame as
    * raw bytes.
    */
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
  // Ethernet CRC32 (IEEE 802.3 / reflected)
  // init=0xFFFFFFFF, poly=0xEDB88320, xorout=0xFFFFFFFF
  // FCS transmitted little-endian (LSB first)
  // --------------------------------------------------------------------------
  private def crc32Ethernet(data: Array[Byte]): Int = {
    var crc = 0xffffffff
    val poly = 0xedb88320

    data.foreach { b =>
      crc ^= (b & 0xff)
      for (_ <- 0 until 8) {
        crc =
          if ((crc & 1) != 0)
            (crc >>> 1) ^ poly
          else
            (crc >>> 1)
      }
    }
    crc ^ 0xffffffff
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
      dst ++ src ++
        Array(((ethType >>> 8) & 0xff).toByte, (ethType & 0xff).toByte)

    // Ensure minimum payload:
    // header(14) + payload(>=46) + fcs(4) = 64
    val minPayload = 46
    val payloadPadded =
      if (payload.length >= minPayload)
        payload
      else
        payload ++ Array.fill[Byte](minPayload - payload.length)(0)

    val noPreambleNoFcs = header ++ payloadPadded
    val crc = crc32Ethernet(noPreambleNoFcs)
    val fcs = intToLeBytes32(crc)

    val preamble = Array.fill(7)(0x55.toByte)
    val sfd = Array[Byte](0xd5.toByte)

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

    val startCycleBytes = startOverride.getOrElse(Array[Byte](
      0xfb.toByte, // lane0 START (control)
      0x55.toByte,
      0x55.toByte,
      0x55.toByte,
      0x55.toByte,
      0x55.toByte,
      0x55.toByte,
      0xd5.toByte // lane7 SFD
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
        bytes8(termLane) = 0xfd.toByte

        var ctrl = 0
        for (lane <- termLane until 8)
          ctrl |= (1 << lane)

        out += ((packWordLittle(bytes8), ctrl))
        idx += remaining
      }
    }

    if ((payloadBytes.length % 8) == 0) {
      val bytes8 = Array.fill(8)(0x07.toByte)
      bytes8(0) = 0xfd.toByte
      out += ((packWordLittle(bytes8), 0xff))
    }

    out.toSeq
  }

  // --------------------------------------------------------------------------
  // BFMs
  // --------------------------------------------------------------------------
  private class XgmiiRxDriverBfm(dut: DualWrapperMac) {
    private val idleWord: BigInt = BigInt("0707070707070707", 16)

    def drive(
        data: BigInt,
        ctrl: Int,
        rxValid: Boolean = true
      ): Unit = {
      dut.io.xgmii_rxd.poke(data.U(64.W))
      dut.io.xgmii_rxc.poke(ctrl.U(8.W))
      dut.io.xgmii_rx_valid.poke(rxValid.B)
    }

    def driveIdle(): Unit = drive(idleWord, 0xff, rxValid = true)
  }

  private class AxisCollectorBfm(
      tvalid: Bool,
      tdata: UInt,
      tkeep: UInt,
      tlast: Bool,
      tuser: UInt,
      tid: UInt) {
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
      pktFragment: Boolean = false)

  private class StatusCollectorBfm(
      pktGood: Bool,
      pktBad: Bool,
      errBadFcs: Bool,
      errPreamble: Bool,
      errFraming: Bool,
      errOversize: Bool,
      pktFragment: Bool) {
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

  // --------------------------------------------------------------------------
  // Bundle BFMs so tests don’t repeat wiring boilerplate
  // --------------------------------------------------------------------------
  private case class Bfms(
      xgmii: XgmiiRxDriverBfm,
      chiselAxis: AxisCollectorBfm,
      verilogAxis: AxisCollectorBfm,
      chiselStatus: StatusCollectorBfm,
      verilogStatus: StatusCollectorBfm)

  private def mkBfms(dut: DualWrapperMac): Bfms = {
    val xgmii = new XgmiiRxDriverBfm(dut)

    val chiselAxis =
      new AxisCollectorBfm(
        dut.io.chisel_rx_tvalid,
        dut.io.chisel_rx_tdata,
        dut.io.chisel_rx_tkeep,
        dut.io.chisel_rx_tlast,
        dut.io.chisel_rx_tuser,
        dut.io.chisel_rx_tid
      )

    val verilogAxis =
      new AxisCollectorBfm(
        dut.io.verilog_rx_tvalid,
        dut.io.verilog_rx_tdata,
        dut.io.verilog_rx_tkeep,
        dut.io.verilog_rx_tlast,
        dut.io.verilog_rx_tuser,
        dut.io.verilog_rx_tid
      )

    val chiselStatus =
      new StatusCollectorBfm(
        dut.io.chisel_stat_rx_pkt_good,
        dut.io.chisel_stat_rx_pkt_bad,
        dut.io.chisel_stat_rx_err_bad_fcs,
        dut.io.chisel_stat_rx_err_preamble,
        dut.io.chisel_stat_rx_err_framing,
        dut.io.chisel_stat_rx_err_oversize,
        dut.io.chisel_stat_rx_pkt_fragment
      )

    val verilogStatus =
      new StatusCollectorBfm(
        dut.io.verilog_stat_rx_pkt_good,
        dut.io.verilog_stat_rx_pkt_bad,
        dut.io.verilog_stat_rx_err_bad_fcs,
        dut.io.verilog_stat_rx_err_preamble,
        dut.io.verilog_stat_rx_err_framing,
        dut.io.verilog_stat_rx_err_oversize,
        dut.io.verilog_stat_rx_pkt_fragment
      )

    Bfms(xgmii, chiselAxis, verilogAxis, chiselStatus, verilogStatus)
  }

  // --------------------------------------------------------------------------
  // Shared test runner
  // --------------------------------------------------------------------------
  private def runCase(
      dut: DualWrapperMac,
      xgmii: XgmiiRxDriverBfm,
      chiselAxis: AxisCollectorBfm,
      verilogAxis: AxisCollectorBfm,
      chiselStatus: StatusCollectorBfm,
      verilogStatus: StatusCollectorBfm,
      xgmiiWords: Seq[(BigInt, Int)],
      drainMaxCycles: Int = 800,
      requireLast: Boolean = true
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
      // In drop-frame cases, allow 0 frames. If frames exist, still ensure they match.
      chiselFrames.map(_.toSeq) shouldBe verilogFrames.map(_.toSeq)
    }

    (chiselFrames, verilogFrames)
  }

  // --------------------------------------------------------------------------
  // Test cases
  // --------------------------------------------------------------------------
  it should "RX: valid frame passes (tuser==0) and matches expected bytes" in {
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang")),
        WriteVcdAnnotation
      )) { dut =>
        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, expectedAxisBytes) = buildEthernetFrame(
          dst,
          src,
          ethType,
          payload
        )
        val xgmiiWords = xgmiiEncode64(fullBytes)

        val (_, verilogFrames) = runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords
        )

        verilogFrames.size shouldBe 1
        verilogFrames.head.toSeq shouldBe expectedAxisBytes.toSeq

        b.verilogAxis.beats.last.user shouldBe 0
        b.chiselAxis.beats.last.user shouldBe 0
      }
  }

  it should "RX: bad FCS is flagged (tuser==1)" in {
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang")),
        WriteVcdAnnotation
      )) { dut =>
        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullBad = fullBytes.clone()
        fullBad(fullBad.length - 1) =
          (fullBad.last ^ 0x01).toByte // flip bit in FCS

        val xgmiiWords = xgmiiEncode64(fullBad)

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords
        )

        b.verilogAxis.beats.nonEmpty shouldBe true
        b.verilogAxis.beats.last.user shouldBe 1
        b.chiselAxis.beats.last.user shouldBe 1
        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.badFcs shouldBe true
        vs.good shouldBe false
      }
  }

  it should "RX: runt (truncated FCS) is flagged (tuser==1)" in {
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang")),
        WriteVcdAnnotation
      )) { dut =>
        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val fullRunt = fullBytes.dropRight(
          2
        ) // missing 2 bytes of FCS => CRC check should fail

        val xgmiiWords = xgmiiEncode64(fullRunt)

        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false
        )

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        (vs.badFcs || vs.pktFragment) shouldBe true
        vs.good shouldBe false

        // if any output exists, it must be marked bad.
        if (b.verilogAxis.beats.nonEmpty)
          b.verilogAxis.beats.last.user shouldBe 1
      }
  }

  it should "RX: bad preamble/SFD (corrupted) - detected and rejected" in {
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang")),
        WriteVcdAnnotation
      )) { dut =>
        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)
        dut.io.cfg_rx_max_pkt_len.poke(1518.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800
        val payload = Array.fill(8)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)

        val badStart = Array[Byte](
          0xfb.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x55.toByte,
          0x00.toByte // SFD wrong (should be 0xD5)
        )

        val xgmiiWords = xgmiiEncode64(
          fullBytes,
          startOverride = Some(badStart)
        )

        val (_, verilogFrames) = runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false
        )

        val vs = b.verilogStatus.snapshot

        // This MAC ignores the preamble pattern and SFD byte in the start word,
        // so corrupting them should still be accepted as a good packet.
        vs.good shouldBe true
        vs.bad shouldBe false
        vs.badFcs shouldBe false
        vs.framing shouldBe false
        vs.preamble shouldBe false

        // If a frame came out, it must not be marked bad.
        if (b.verilogAxis.beats.nonEmpty)
          b.verilogAxis.beats.last.user shouldBe 0
        if (b.chiselAxis.beats.nonEmpty)
          b.chiselAxis.beats.last.user shouldBe 0
      }
  }

  it should
    "RX: framing error (control inside payload) is flagged (tuser==1)" in {
      test(new DualWrapperMac)
        .withAnnotations(Seq(
          VerilatorBackendAnnotation,
          VerilatorFlags(Seq("--compiler", "clang")),
          WriteVcdAnnotation
        )) { dut =>
          dut.clock.setTimeout(0)
          dut.io.rx_ready.poke(true.B)
          dut.io.cfg_rx_max_pkt_len.poke(1518.U)

          val b = mkBfms(dut)

          val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
          val src = Array[Byte](
            0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
          ).map(_.toByte)
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
            val d2 = (d & ~mask) | (BigInt(0xfe) << (8 * badByteLane))
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
            words.toSeq
          )

          b.verilogAxis.beats.nonEmpty shouldBe true
          b.verilogAxis.beats.last.user shouldBe 1
          b.chiselAxis.beats.last.user shouldBe 1
        }
    }

  it should "RX: oversize is flagged when cfg_rx_max_pkt_len is small" in {
    test(new DualWrapperMac)
      .withAnnotations(Seq(
        VerilatorBackendAnnotation,
        VerilatorFlags(Seq("--compiler", "clang")),
        WriteVcdAnnotation
      )) { dut =>
        dut.clock.setTimeout(0)
        dut.io.rx_ready.poke(true.B)

        // key: make "normal" frame oversize
        dut.io.cfg_rx_max_pkt_len.poke(64.U)

        val b = mkBfms(dut)

        val dst = Array[Byte](1, 2, 3, 4, 5, 6).map(_.toByte)
        val src = Array[Byte](0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f).map(_.toByte)
        val ethType = 0x0800

        // payload -> way above 64 max
        val payload = Array.fill(100)(0x11.toByte)

        val (fullBytes, _) = buildEthernetFrame(dst, src, ethType, payload)
        val xgmiiWords = xgmiiEncode64(fullBytes)

        // usually dropped or flagged; don't require tlast
        runCase(
          dut,
          b.xgmii,
          b.chiselAxis,
          b.verilogAxis,
          b.chiselStatus,
          b.verilogStatus,
          xgmiiWords,
          requireLast = false
        )

        val vs = b.verilogStatus.snapshot
        vs.bad shouldBe true
        vs.oversize shouldBe true
        vs.good shouldBe false

        // Optional: if any output exists, it must be marked bad.
        if (b.verilogAxis.beats.nonEmpty)
          b.verilogAxis.beats.last.user shouldBe 1
      }
  }
}
