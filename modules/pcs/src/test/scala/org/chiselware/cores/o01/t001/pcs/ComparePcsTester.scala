// package org.chiselware.cores.o01.t001.pcs

// import chisel3._
// import chiseltest._
// import chiseltest.simulator.VerilatorFlags
// import org.scalatest.flatspec.AnyFlatSpec
// import org.scalatest.matchers.should.Matchers

// class ComparePcsTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {
//   // --------------------------------------------------------------------------
//   // Progress printing helpers
//   // --------------------------------------------------------------------------

//   // Update this when you add/remove tests
//   // (Tip: keep this equal to the last test index + 1)
//   private val TotalTests = 17

//   private def pct(n: Int): Int = (n * 100) / TotalTests

//   private def progStart(n: Int, name: String): Unit =
//     println(s"\n[${n}/${TotalTests}] (${pct(n)}%)\tSTART:\t$name")

//   private def progEnd(n: Int, name: String): Unit =
//     println(s"[${n}/${TotalTests}] (${pct(n)}%)\tDONE:\t$name")

//   private def progInfo(n: Int, msg: String): Unit =
//     println(s"[${n}/${TotalTests}]\t\t\t• $msg")

//   // --------------------------------------------------------------------------
//   // Common annotations (keep consistent with MAC)
//   // --------------------------------------------------------------------------

//   private val annos = Seq(
//     VerilatorBackendAnnotation,
//     VerilatorFlags(Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")),
//     WriteVcdAnnotation
//   )

//   private def withDut(tn: Int, tname: String)(body: DualWrapperPcs => Unit): Unit = {
//     progStart(tn, tname)
//     test(new DualWrapperPcs).withAnnotations(annos) { dut =>
//       body(dut)
//     }
//     progEnd(tn + 1, tname)
//   }

//   // --------------------------------------------------------------------------
//   // XGMII constants
//   // --------------------------------------------------------------------------
//   private val ETH_PRE   = 0x55
//   private val ETH_SFD   = 0xD5
//   private val XGMII_IDLE  = 0x07
//   private val XGMII_START = 0xFB
//   private val XGMII_TERM  = 0xFD
//   private val XGMII_ERROR = 0xFE

//   // --------------------------------------------------------------------------
//   // Helpers
//   // --------------------------------------------------------------------------

//   private val idleD = "h0707070707070707".U(64.W)
//   private val idleC = "hFF".U(8.W)

//   // 10GBASE-R sync header encodings (2 bits)
//   // data block header: 01, control block header: 10
//   private val DATA_HDR = 1
//   private val CTRL_HDR = 2

//   private def initDut(dut: DualWrapperPcs, tn: Int): Unit = {
//     dut.clock.setTimeout(0)

//     // Defaults
//     dut.io.tap_enable.poke(false.B)
//     dut.io.tap_serdes_rx_data.poke(0.U)
//     dut.io.tap_serdes_rx_data_valid.poke(false.B)
//     dut.io.tap_serdes_rx_hdr.poke(0.U)
//     dut.io.tap_serdes_rx_hdr_valid.poke(false.B)

//     dut.io.xgmii_tx_valid.poke(true.B)
//     dut.io.xgmii_txd.poke(idleD)
//     dut.io.xgmii_txc.poke(idleC)

//     // Reset
//     dut.io.rst.poke(true.B)
//     dut.clock.step(20)
//     dut.io.rst.poke(false.B)

//     // Let it settle/lock a bit
//     dut.clock.step(200)
//     progInfo(tn, "init complete (reset deasserted, settle done)")
//   }

//   private def driveXgmii(dut: DualWrapperPcs, txd: UInt, txc: UInt, valid: Boolean = true): Unit = {
//     dut.io.xgmii_txd.poke(txd)
//     dut.io.xgmii_txc.poke(txc)
//     dut.io.xgmii_tx_valid.poke(valid.B)
//   }

//   private def expectHdrOnValid(hdr: UInt, dv: Bool, hv: Bool, expected: Int, tn: Int, label: String): Unit = {
//     val dataValid = dv.peek().litToBoolean
//     val hdrValid  = hv.peek().litToBoolean
//     if (dataValid && hdrValid) {
//       val got = hdr.peek().litValue.toInt
//       if (got != expected) {
//         progInfo(tn, s"$label: expected hdr=$expected, got=$got")
//       }
//       hdr.expect(expected.U)
//     }
//   }

//   private def enableTapWithSeed(dut: DualWrapperPcs, data: UInt, dv: Bool, hdr: UInt, hv: Bool): Unit = {
//     dut.io.tap_serdes_rx_data.poke(data)
//     dut.io.tap_serdes_rx_data_valid.poke(dv)
//     dut.io.tap_serdes_rx_hdr.poke(hdr)
//     dut.io.tap_serdes_rx_hdr_valid.poke(hv)
//     dut.io.tap_enable.poke(true.B)
//   }

//   // Convenience: wait until both RX are "usable"
//   private def waitForLock(dut: DualWrapperPcs, tn: Int, maxCycles: Int = 5000): Unit = {
//     var locked = false
//     var cycles = 0
//     while (!locked && cycles < maxCycles) {
//       dut.clock.step(1)
//       cycles += 1
//       val chLock = dut.io.chisel_rx_block_lock.peek().litToBoolean
//       val bbLock = dut.io.bb_rx_block_lock.peek().litToBoolean
//       locked = chLock && bbLock
//     }
//     progInfo(tn, s"waitForLock: cycles=$cycles, locked=$locked")
//     locked shouldBe true
//   }

//   // Pack 8 bytes into a 64-bit word assuming lane0 is LSB (xgmii_txd[7:0])
//   private def packBytesLE(bs: Seq[Int]): UInt = {
//     require(bs.length == 8)
//     val w = bs.zipWithIndex.map { case (b, i) => (BigInt(b & 0xFF) << (8 * i)) }.reduce(_ | _)
//     w.U(64.W)
//   }

//   // Pack 8 control bits into xgmii_txc (bit i corresponds to lane i)
//   private def packCtrlBitsLE(cs: Seq[Int]): UInt = {
//     require(cs.length == 8)
//     val w = cs.zipWithIndex.map { case (c, i) => (BigInt(c & 0x1) << i) }.reduce(_ | _)
//     w.U(8.W)
//   }

//   // Canonical XGMII words
//   private val xgmiiIdleWord: (UInt, UInt) =
//     (packBytesLE(Seq.fill(8)(XGMII_IDLE)), packCtrlBitsLE(Seq.fill(8)(1)))

//   // START in lane0: /S/ then 7 bytes of preamble (data)
//   private val xgmiiStartWord: (UInt, UInt) =
//     (packBytesLE(Seq(XGMII_START, ETH_PRE, ETH_PRE, ETH_PRE, ETH_PRE, ETH_PRE, ETH_PRE, ETH_PRE)),
//     packCtrlBitsLE(Seq(1, 0, 0, 0, 0, 0, 0, 0)))

//   // SFD as data (typically follows preamble)
//   private val xgmiiSfdWord: (UInt, UInt) =
//     (packBytesLE(Seq(ETH_SFD, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)),
//     packCtrlBitsLE(Seq.fill(8)(0)))

//   // TERM in lane0 and then IDLE control for rest of lanes
//   private val xgmiiTermWord: (UInt, UInt) =
//     (packBytesLE(Seq(XGMII_TERM, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE)),
//     packCtrlBitsLE(Seq.fill(8)(1)))

//   // --------------------------------------------------------------------------------------
//   // Build an XGMII frame stream:
//   //   IDLE (optional externally)
//   //   START word (/S/ + 7x preamble)
//   //   then data words starting with [SFD + payload...]
//   //   then one final word containing TERM in some lane (computed from payload length)
//   // NOTE: TERM lane is determined by (1 + payloadLen) mod 8, where the "1" is the SFD byte.
//   // --------------------------------------------------------------------------------------
//   private def buildFrameWords(payloadBytes: Seq[Int]): Seq[(UInt, UInt)] = {
//     // Word 0: START + 7 preamble bytes
//     val start = xgmiiStartWord

//     // Build the contiguous data stream that follows the START word on XGMII:
//     // [SFD] + payload bytes
//     val dataStream: Seq[Int] = Seq(ETH_SFD) ++ payloadBytes

//     // Chunk into 8-byte data words (these have txc=0x00)
//     val fullDataWords: Seq[(UInt, UInt)] = dataStream
//       .grouped(8)
//       .toSeq
//       .dropRight(1) // keep last chunk for TERM word handling
//       .map { chunk =>
//         val bytes = chunk.padTo(8, 0x00)
//         (packBytesLE(bytes), packCtrlBitsLE(Seq.fill(8)(0)))
//       }

//     val lastChunk = dataStream.grouped(8).toSeq.lastOption.getOrElse(Seq.empty[Int])
//     val lastDataCount = lastChunk.length // 0..8

//     // If lastChunk is exactly 8 bytes, then TERM goes at lane0 in a *new* word
//     // Otherwise TERM goes at lane = lastDataCount in the final word.
//     val (termWord, needsExtraTermWord) =
//       if (lastDataCount == 8) {
//         // last data word is "full", emit it as data, then a separate TERM+IDLE control word
//         val lastDataWord =
//           (packBytesLE(lastChunk), packCtrlBitsLE(Seq.fill(8)(0)))
//         val termCtrlWord = xgmiiTermWord // TERM@lane0 + idle controls
//         (Seq(lastDataWord, termCtrlWord), true)
//       } else {
//         // TERM shares the last word: [data bytes][TERM][IDLE...]
//         val termLane = lastDataCount // 0..7
//         val bytes = (lastChunk.padTo(termLane, 0x00) ++ Seq(XGMII_TERM) ++ Seq.fill(7 - termLane)(XGMII_IDLE)).take(8)

//         // Control bits: lanes < termLane are data (0); lane termLane..7 are control (1)
//         val ctrlBits = (Seq.fill(termLane)(0) ++ Seq.fill(8 - termLane)(1)).take(8)

//         (Seq((packBytesLE(bytes), packCtrlBitsLE(ctrlBits))), false)
//       }

//     Seq(start) ++ fullDataWords ++ termWord
//   }

//   // Drive a frame (and check BB vs Chisel RX alignment during valid cycles)
//   private def driveFrameAndCompare(dut: DualWrapperPcs, words: Seq[(UInt, UInt)]): (Int, Int) = {
//     var compared = 0
//     var mismatches = 0

//     for ((txd, txc) <- words) {
//       driveXgmii(dut, txd, txc, valid = true)
//       dut.clock.step(1)

//       val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//       val bbV = dut.io.bb_rx_valid.peek().litToBoolean
//       if (chV && bbV) {
//         compared += 1
//         val chRxd = dut.io.chisel_rxd.peek().litValue
//         val bbRxd = dut.io.bb_rxd.peek().litValue
//         val chRxc = dut.io.chisel_rxc.peek().litValue
//         val bbRxc = dut.io.bb_rxc.peek().litValue
//         if (chRxd != bbRxd || chRxc != bbRxc) mismatches += 1

//         dut.io.chisel_rxd.expect(dut.io.bb_rxd.peek())
//         dut.io.chisel_rxc.expect(dut.io.bb_rxc.peek())
//       }
//     }

//     (compared, mismatches)
//   }

//   // --------------------------------------------------------------------------
//   // Test cases
//   // --------------------------------------------------------------------------

//   it should "PCS: Chisel vs Verilog BlackBox matches on IDLE stream (loopback)" in {
//     progInfo(0, "start of PCS testing")
//     val tn = 0
//     val tname = "PCS: Chisel vs Verilog BlackBox matches on IDLE stream (loopback)"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)

//       for (_ <- 0 until 200) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)

//         val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
//         val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

//         if (chValid && bbValid) {
//           dut.io.chisel_rxd.expect(dut.io.bb_rxd.peek())
//           dut.io.chisel_rxc.expect(dut.io.bb_rxc.peek())
//         }
//       }

//       dut.io.chisel_rx_block_lock.expect(dut.io.bb_rx_block_lock.peek())
//       dut.io.chisel_rx_status.expect(dut.io.bb_rx_status.peek())
//     }
//   }

//   it should "PCS TX: data blocks use sync header 01 (DATA) when txc==0x00" in {
//     val tn = 1
//     val tname = "PCS TX: data blocks use sync header 01 (DATA) when txc==0x00"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)

//       val dataC = 0.U(8.W)
//       val dataD = "h1122334455667788".U(64.W)

//       // Extra warmup so TX stops emitting initial idles/control
//       dut.clock.step(300)

//       val totalCycles = 800
//       val ignoreFirstValidBlocks = 20

//       var seenValid = 0
//       var seenData  = 0
//       var seenCtrl  = 0
//       var seenOther = 0
//       var compared  = 0

//       for (_ <- 0 until totalCycles) {
//         driveXgmii(dut, dataD, dataC, valid = true)
//         dut.clock.step(1)

//         val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
//         val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
//         val chDv = dut.io.ch_tx_dv.peek().litToBoolean
//         val chHv = dut.io.ch_tx_hv.peek().litToBoolean

//         // Count BB headers when BB says the block is valid
//         if (bbDv && bbHv) {
//           seenValid += 1
//           val bbHdr = dut.io.bb_tx_hdr.peek().litValue.toInt

//           if (seenValid > ignoreFirstValidBlocks) {
//             if (bbHdr == DATA_HDR) seenData += 1
//             else if (bbHdr == CTRL_HDR) seenCtrl += 1
//             else seenOther += 1
//           }

//           // After warmup, when both sides are valid, require Chisel matches BB
//           if (seenValid > ignoreFirstValidBlocks && chDv && chHv) {
//             dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
//             compared += 1
//           }
//         }
//       }

//       progInfo(tn, s"bb validBlocks=$seenValid, comparedBlocks=$compared, dataHdrCount=$seenData, ctrlHdrCount=$seenCtrl, otherHdrCount=$seenOther")

//       // Compliance expectations (post-warmup)
//       seenData should be > 0
//       seenOther shouldBe 0

//       // Based on observed behavior: after warmup, bb shouldn't emit control headers in pure data mode.
//       // If you ever see intermittent CTRL later, relax this to "seenCtrl should be < X".
//       seenCtrl shouldBe 0

//       // Ensure we actually compared a meaningful number of blocks
//       compared should be > 50
//     }
//   }

//   it should "PCS TX: control blocks use sync header 10 (CTRL) when txc==0xFF (IDLE)" in {
//     val tn = 2
//     val tname = "PCS TX: control blocks use sync header 10 (CTRL) when txc==0xFF (IDLE)"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)

//       for (i <- 0 until 300) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)

//         expectHdrOnValid(dut.io.bb_tx_hdr, dut.io.bb_tx_dv, dut.io.bb_tx_hv, CTRL_HDR, tn, s"bb@$i")
//         expectHdrOnValid(dut.io.ch_tx_hdr, dut.io.ch_tx_dv, dut.io.ch_tx_hv, CTRL_HDR, tn, s"ch@$i")
//       }
//     }
//   }

//   it should "PCS RX: block lock is acquired on clean IDLE and remains stable" in {
//     val tn = 3
//     val tname = "PCS RX: block lock is acquired on clean IDLE and remains stable"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)

//       // Ensure we actually get lock first
//       waitForLock(dut, tn, maxCycles = 5000)

//       // Now keep sending clean IDLE and ensure lock doesn't drop
//       val stableCycles = 5000
//       var lockDrops = 0
//       var statusMismatches = 0

//       for (_ <- 0 until stableCycles) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)

//         val bbLock = dut.io.bb_rx_block_lock.peek().litToBoolean
//         val chLock = dut.io.chisel_rx_block_lock.peek().litToBoolean
//         if (!bbLock || !chLock) lockDrops += 1

//         // Optional: status should track between implementations
//         val bbStatus = dut.io.bb_rx_status.peek().litToBoolean
//         val chStatus = dut.io.chisel_rx_status.peek().litToBoolean
//         if (bbStatus != chStatus) statusMismatches += 1
//       }

//       progInfo(tn, s"stableCycles=$stableCycles, lockDrops=$lockDrops, statusMismatches=$statusMismatches")

//       lockDrops shouldBe 0
//       statusMismatches shouldBe 0
//     }
//   }

//   it should "PCS RX: no spurious errors on clean IDLE (rx_bad_block/seq_error/error_count)" in {
//     val tn = 4
//     val tname = "PCS RX: no spurious errors on clean IDLE (rx_bad_block/seq_error/error_count)"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Snapshot starting error counters
//       val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
//       progInfo(tn, s"initial error_count: bb=$bbErr0 ch=$chErr0")

//       val checkCycles = 5000
//       var badBlockHits = 0
//       var seqErrHits   = 0
//       var errIncHits   = 0
//       var mismatchHits = 0

//       var bbMaxErr = bbErr0
//       var chMaxErr = chErr0

//       for (_ <- 0 until checkCycles) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)

//         val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
//         val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
//         val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
//         val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean

//         val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
//         val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

//         if (bbBad || chBad) badBlockHits += 1
//         if (bbSeq || chSeq) seqErrHits += 1

//         if (bbErr > bbMaxErr) bbMaxErr = bbErr
//         if (chErr > chMaxErr) chMaxErr = chErr

//         // Strict: Chisel should match BB behavior (even if BB decides to wiggle)
//         if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) mismatchHits += 1
//       }

//       // Count any increase relative to initial snapshot
//       if (bbMaxErr != bbErr0 || chMaxErr != chErr0) errIncHits = 1

//       progInfo(tn, s"checkCycles=$checkCycles badBlockHits=$badBlockHits seqErrHits=$seqErrHits mismatchHits=$mismatchHits")
//       progInfo(tn, s"error_count: bb start=$bbErr0 max=$bbMaxErr, ch start=$chErr0 max=$chMaxErr")

//       badBlockHits shouldBe 0
//       seqErrHits shouldBe 0
//       errIncHits shouldBe 0
//       mismatchHits shouldBe 0
//     }
//   }

//   // --------------------------------------------------------------------------
//   // Control formatting sanity (Start/Terminate/Idle)
//   // --------------------------------------------------------------------------

//   it should "PCS TX: control encoding is non-degenerate (IDLE vs START vs TERM differ) and uses CTRL hdr" in {
//     val tn = 5
//     val tname = "PCS TX: control encoding is non-degenerate (IDLE vs START vs TERM differ) and uses CTRL hdr"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)

//       // Warmup (avoid early transient control)
//       dut.clock.step(200)

//       def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
//         driveXgmii(dut, word._1, word._2, valid)
//         dut.clock.step(1)
//       }

//       // Collect a few SERDES TX control-block payloads (bb/ch) under three scenarios:
//       // 1) steady IDLE, 2) START cycle, 3) TERM cycle
//       def collectCtrlPayloads(scenario: String, steps: Int, driveFn: Int => Unit): Set[BigInt] = {
//         var s = Set.empty[BigInt]
//         for (i <- 0 until steps) {
//           driveFn(i)

//           val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
//           val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
//           val chDv = dut.io.ch_tx_dv.peek().litToBoolean
//           val chHv = dut.io.ch_tx_hv.peek().litToBoolean

//           if (bbDv && bbHv) {
//             // Expect CTRL hdr on control blocks
//             dut.io.bb_tx_hdr.expect(CTRL_HDR.U)
//             if (chDv && chHv) dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())

//             s += dut.io.bb_tx_data.peek().litValue
//           }
//         }
//         progInfo(tn, s"$scenario: collected ${s.size} unique ctrl payload(s)")
//         s
//       }

//       val idleSet = collectCtrlPayloads("IDLE", steps = 40, driveFn = _ => stepXgmii(xgmiiIdleWord))
//       val startSet = collectCtrlPayloads("START", steps = 10, driveFn = i => if (i == 0) stepXgmii(xgmiiStartWord) else stepXgmii(xgmiiIdleWord))
//       val termSet  = collectCtrlPayloads("TERM",  steps = 10, driveFn = i => if (i == 0) stepXgmii(xgmiiTermWord)  else stepXgmii(xgmiiIdleWord))

//       // Non-degenerate: START and TERM encodings should not look identical to steady IDLE
//       // (We don’t hardcode exact IEEE block-type values yet, we just sanity-check mapping exists.)
//       (startSet.diff(idleSet).nonEmpty) shouldBe true
//       (termSet.diff(idleSet).nonEmpty) shouldBe true
//     }
//   }

//   it should "PCS TX: START->DATA transition (CTRL hdr then DATA hdr) behaves correctly" in {
//     val tn = 6
//     val tname = "PCS TX: START->DATA transition (CTRL hdr then DATA hdr) behaves correctly"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       dut.clock.step(200)

//       def driveStep(word: (UInt, UInt)): Unit = {
//         driveXgmii(dut, word._1, word._2, valid = true)
//         dut.clock.step(1)
//       }

//       // We’ll drive a short sequence and watch headers across the *entire* sequence.
//       val totalCycles = 300

//       var sawCtrl = false
//       var sawDataAfterCtrl = false
//       var sawAnyData = false
//       var observedValid = 0

//       for (i <- 0 until totalCycles) {
//         // Stimulus timeline:
//         // 0..19: IDLE
//         // 20: START
//         // 21: SFD/first bytes
//         // 22..: DATA
//         if (i < 20) {
//           driveStep(xgmiiIdleWord)
//         } else if (i == 20) {
//           driveStep(xgmiiStartWord)
//         } else if (i == 21) {
//           driveStep(xgmiiSfdWord)
//         } else if (i < 60) {
//           driveStep((packBytesLE(Seq(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08)),
//                     packCtrlBitsLE(Seq.fill(8)(0))))
//         } else {
//           driveStep((packBytesLE(Seq(0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17)),
//                     packCtrlBitsLE(Seq.fill(8)(0))))
//         }

//         val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
//         val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
//         val chDv = dut.io.ch_tx_dv.peek().litToBoolean
//         val chHv = dut.io.ch_tx_hv.peek().litToBoolean

//         if (bbDv && bbHv) {
//           observedValid += 1
//           val h = dut.io.bb_tx_hdr.peek().litValue.toInt

//           if (h == CTRL_HDR) sawCtrl = true
//           if (h == DATA_HDR) {
//             sawAnyData = true
//             if (sawCtrl) sawDataAfterCtrl = true
//           }

//           // Keep ch aligned with bb when both are valid
//           if (chDv && chHv) {
//             dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
//           }
//         }
//       }

//       progInfo(tn, s"observedValid=$observedValid sawCtrl=$sawCtrl sawAnyData=$sawAnyData sawDataAfterCtrl=$sawDataAfterCtrl")

//       // Expectations
//       sawCtrl shouldBe true
//       sawAnyData shouldBe true
//       sawDataAfterCtrl shouldBe true
//     }
//   }

//   it should "PCS TX: TERM->IDLE transition (CTRL hdr then CTRL hdr) behaves correctly" in {
//     val tn = 7
//     val tname = "PCS TX: TERM->IDLE transition (CTRL hdr then CTRL hdr) behaves correctly"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       dut.clock.step(200)

//       def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
//         driveXgmii(dut, word._1, word._2, valid)
//         dut.clock.step(1)
//       }

//       // Drive a tiny frame then terminate then idle
//       stepXgmii(xgmiiIdleWord)
//       stepXgmii(xgmiiStartWord)
//       stepXgmii(xgmiiSfdWord)
//       stepXgmii((packBytesLE(Seq(0xAA,0xBB,0xCC,0xDD,0xEE,0x01,0x02,0x03)), packCtrlBitsLE(Seq.fill(8)(0))))
//       stepXgmii(xgmiiTermWord)

//       // After TERM, send IDLE for a while
//       val window = 200
//       var sawCtrlAtTermRegion = false
//       var sawCtrlAfterTerm = false

//       for (i <- 0 until window) {
//         stepXgmii(xgmiiIdleWord)

//         val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
//         val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
//         if (bbDv && bbHv) {
//           val h = dut.io.bb_tx_hdr.peek().litValue.toInt
//           if (h == CTRL_HDR && i < 20) sawCtrlAtTermRegion = true
//           if (h == CTRL_HDR && i > 20) sawCtrlAfterTerm = true

//           val chDv = dut.io.ch_tx_dv.peek().litToBoolean
//           val chHv = dut.io.ch_tx_hv.peek().litToBoolean
//           if (chDv && chHv) dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
//         }
//       }

//       progInfo(tn, s"sawCtrlAtTermRegion=$sawCtrlAtTermRegion sawCtrlAfterTerm=$sawCtrlAfterTerm")
//       sawCtrlAtTermRegion shouldBe true
//       sawCtrlAfterTerm shouldBe true
//     }
//   }

//   // --------------------------------------------------------------------------
//   // Mapping / bit ordering consistency via cross-coupled tap
//   // --------------------------------------------------------------------------

//   it should "PCS Mapping: BB TX -> Chisel RX (tap) recovered XGMII matches BB recovered XGMII" in {
//     val tn = 8
//     val tname = "PCS Mapping: BB TX -> Chisel RX (tap) recovered XGMII matches BB recovered XGMII"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Start in normal mode (internal loopback) for a short settle
//       dut.io.tap_enable.poke(false.B)
//       for (_ <- 0 until 50) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)
//       }

//       // Prepare tap inputs before enabling tap (avoid X/garbage on first cycle)
//       dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
//       dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
//       dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
//       dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

//       // Enable tap: now BOTH bb.rx and ch.rx see tap_serdes_rx_*
//       dut.io.tap_enable.poke(true.B)

//       // Run a mixed stimulus while feeding tap from bb_tx (1-cycle staged)
//       val totalCycles = 2000
//       val settleCyclesAfterTap = 50

//       var compared = 0
//       var mismatches = 0

//       for (i <- 0 until totalCycles) {
//         // Stimulus on XGMII TX side (goes into both PCS TX paths)
//         if (i < 200) {
//           // IDLE region
//           driveXgmii(dut, idleD, idleC, valid = true)
//         } else if (i < 1200) {
//           // Pure data region
//           driveXgmii(dut, "h1122334455667788".U(64.W), 0.U(8.W), valid = true)
//         } else {
//           // Back to IDLE
//           driveXgmii(dut, idleD, idleC, valid = true)
//         }

//         // Advance one cycle so bb_tx_* updates for this stimulus
//         dut.clock.step(1)

//         // Feed next-cycle tap inputs from current bb_tx_*
//         dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
//         dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
//         dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
//         dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

//         // Compare recovered XGMII after a small settle window
//         if (i >= settleCyclesAfterTap) {
//           val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//           val bbV = dut.io.bb_rx_valid.peek().litToBoolean

//           if (chV && bbV) {
//             compared += 1
//             val chRxd = dut.io.chisel_rxd.peek()
//             val bbRxd = dut.io.bb_rxd.peek()
//             val chRxc = dut.io.chisel_rxc.peek()
//             val bbRxc = dut.io.bb_rxc.peek()

//             if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) {
//               mismatches += 1
//             }

//             dut.io.chisel_rxd.expect(bbRxd)
//             dut.io.chisel_rxc.expect(bbRxc)
//           }
//         }
//       }

//       progInfo(tn, s"tap compare: totalCycles=$totalCycles compared=$compared mismatches=$mismatches")

//       // Ensure we actually compared a meaningful number of cycles
//       compared should be > 50
//       mismatches shouldBe 0
//     }
//   }

//   it should "PCS Mapping: Chisel TX -> BB RX (tap) recovered XGMII matches Chisel recovered XGMII" in {
//     val tn = 9
//     val tname = "PCS Mapping: Chisel TX -> BB RX (tap) recovered XGMII matches Chisel recovered XGMII"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Start in normal mode (internal loopback) for a short settle
//       dut.io.tap_enable.poke(false.B)
//       for (_ <- 0 until 50) {
//         driveXgmii(dut, idleD, idleC, valid = true)
//         dut.clock.step(1)
//       }

//       // Prepare tap inputs before enabling tap (avoid X/garbage on first cycle)
//       dut.io.tap_serdes_rx_data.poke(dut.io.ch_tx_data.peek())
//       dut.io.tap_serdes_rx_data_valid.poke(dut.io.ch_tx_dv.peek())
//       dut.io.tap_serdes_rx_hdr.poke(dut.io.ch_tx_hdr.peek())
//       dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.ch_tx_hv.peek())

//       // Enable tap: now BOTH bb.rx and ch.rx see tap_serdes_rx_*
//       dut.io.tap_enable.poke(true.B)

//       // Run a mixed stimulus while feeding tap from ch_tx (1-cycle staged)
//       val totalCycles = 2000
//       val settleCyclesAfterTap = 50

//       var compared = 0
//       var mismatches = 0

//       for (i <- 0 until totalCycles) {
//         // Stimulus on XGMII TX side (goes into both PCS TX paths)
//         if (i < 200) {
//           // IDLE region
//           driveXgmii(dut, idleD, idleC, valid = true)
//         } else if (i < 1200) {
//           // Pure data region
//           driveXgmii(dut, "h1122334455667788".U(64.W), 0.U(8.W), valid = true)
//         } else {
//           // Back to IDLE
//           driveXgmii(dut, idleD, idleC, valid = true)
//         }

//         // Advance one cycle so ch_tx_* updates for this stimulus
//         dut.clock.step(1)

//         // Feed next-cycle tap inputs from current ch_tx_*
//         dut.io.tap_serdes_rx_data.poke(dut.io.ch_tx_data.peek())
//         dut.io.tap_serdes_rx_data_valid.poke(dut.io.ch_tx_dv.peek())
//         dut.io.tap_serdes_rx_hdr.poke(dut.io.ch_tx_hdr.peek())
//         dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.ch_tx_hv.peek())

//         // Compare recovered XGMII after a small settle window
//         if (i >= settleCyclesAfterTap) {
//           val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//           val bbV = dut.io.bb_rx_valid.peek().litToBoolean

//           if (chV && bbV) {
//             compared += 1
//             val chRxd = dut.io.chisel_rxd.peek()
//             val bbRxd = dut.io.bb_rxd.peek()
//             val chRxc = dut.io.chisel_rxc.peek()
//             val bbRxc = dut.io.bb_rxc.peek()

//             if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) {
//               mismatches += 1
//             }

//             dut.io.bb_rxd.expect(chRxd)
//             dut.io.bb_rxc.expect(chRxc)
//           }
//         }
//       }

//       progInfo(tn, s"tap compare: totalCycles=$totalCycles compared=$compared mismatches=$mismatches")

//       // Ensure we actually compared a meaningful number of cycles
//       compared should be > 50
//       mismatches shouldBe 0
//     }
//   }

//   it should "PCS Mapping: cross-coupled mixed stream (IDLE + START + DATA + TERM) remains consistent" in {
//     val tn = 10
//     val tname = "PCS Mapping: cross-coupled mixed stream (IDLE + START + DATA + TERM) remains consistent"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // settle in normal mode
//       dut.io.tap_enable.poke(false.B)
//       for (_ <- 0 until 50) {
//         driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
//         dut.clock.step(1)
//       }

//       // seed tap
//       dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
//       dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
//       dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
//       dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())
//       dut.io.tap_enable.poke(true.B)

//       val settleCyclesAfterTap = 50
//       var globalCycle = 0
//       var compared = 0
//       var mismatches = 0

//       def stepAndFeedTap(word: (UInt, UInt), valid: Boolean = true): Unit = {
//         driveXgmii(dut, word._1, word._2, valid)
//         dut.clock.step(1)
//         globalCycle += 1

//         // feed tap from bb_tx
//         dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
//         dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
//         dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
//         dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

//         // compare when both valid
//         if (globalCycle >= settleCyclesAfterTap) {
//           val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//           val bbV = dut.io.bb_rx_valid.peek().litToBoolean
//           if (chV && bbV) {
//             compared += 1
//             val chRxd = dut.io.chisel_rxd.peek()
//             val bbRxd = dut.io.bb_rxd.peek()
//             val chRxc = dut.io.chisel_rxc.peek()
//             val bbRxc = dut.io.bb_rxc.peek()
//             if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) mismatches += 1
//             dut.io.chisel_rxd.expect(bbRxd)
//             dut.io.chisel_rxc.expect(bbRxc)
//           }
//         }
//       }

//       // Sequence: IDLE -> START -> SFD/payload -> payload -> TERM -> IDLE
//       for (_ <- 0 until 100) stepAndFeedTap(xgmiiIdleWord)
//       stepAndFeedTap(xgmiiStartWord)
//       stepAndFeedTap(xgmiiSfdWord)
//       for (_ <- 0 until 20) stepAndFeedTap((packBytesLE(Seq(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08)), packCtrlBitsLE(Seq.fill(8)(0))))
//       stepAndFeedTap(xgmiiTermWord)
//       for (_ <- 0 until 200) stepAndFeedTap(xgmiiIdleWord)

//       progInfo(tn, s"mixed START/TERM tap compare: cycles=$globalCycle compared=$compared mismatches=$mismatches")
//       compared should be > 50
//       mismatches shouldBe 0
//     }
//   }

//   // --------------------------------------------------------------------------
//   // Robustness / negative tests (tap injections)
//   // --------------------------------------------------------------------------

//   it should "PCS RX Negative: invalid sync header (00/11) is detected (status path) and matches BB" in {
//     val tn = 11
//     val tname = "PCS RX Negative: invalid sync header (00/11) is detected (status path) and matches BB"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Baseline error counts
//       val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
//       progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

//       // Switch RX to tap injection (seed first, then enable)
//       enableTapWithSeed(
//         dut,
//         data = 0.U,        // seed harmless values
//         dv   = false.B,
//         hdr  = 0.U(2.W),
//         hv   = false.B
//       )

//       // Helper to inject N cycles of a bad header
//       def injectBadHdr(hdr2b: Int, cycles: Int): (Int, Int, Int) = {
//         var mismatchHits = 0
//         var bbBadHits    = 0
//         var bbSeqHits    = 0

//         for (_ <- 0 until cycles) {
//           // Keep TX side quiet/idle; RX is now driven by tap
//           driveXgmii(dut, idleD, idleC, valid = true)

//           dut.io.tap_serdes_rx_data.poke("hDEADBEEFCAFEBABE".U(64.W))
//           dut.io.tap_serdes_rx_data_valid.poke(true.B)
//           dut.io.tap_serdes_rx_hdr.poke(hdr2b.U(2.W))
//           dut.io.tap_serdes_rx_hdr_valid.poke(true.B)

//           dut.clock.step(1)

//           val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
//           val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
//           val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
//           val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
//           val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
//           val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

//           if (bbBad) bbBadHits += 1
//           if (bbSeq) bbSeqHits += 1

//           // Strict: Chisel must match BB on these status signals
//           if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) mismatchHits += 1
//         }

//         (mismatchHits, bbBadHits, bbSeqHits)
//       }

//       val injCycles = 256

//       // Inject hdr=00
//       val (m0, bad0, seq0) = injectBadHdr(hdr2b = 0, cycles = injCycles)
//       progInfo(tn, s"hdr=00: cycles=$injCycles mismatchHits=$m0 bbBadHits=$bad0 bbSeqHits=$seq0")

//       // Inject hdr=11
//       val (m3, bad3, seq3) = injectBadHdr(hdr2b = 3, cycles = injCycles)
//       progInfo(tn, s"hdr=11: cycles=$injCycles mismatchHits=$m3 bbBadHits=$bad3 bbSeqHits=$seq3")

//       // Final error counts
//       val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt
//       progInfo(tn, s"final error_count: bb=$bbErr1 ch=$chErr1")

//       // Expectations:
//       // 1) Chisel must match BB throughout
//       m0 shouldBe 0
//       m3 shouldBe 0

//       // 2) BB should show *some* sign of detecting bad headers.
//       // We accept any of these mechanisms: bad_block, seq_error, or error_count increment.
//       val bbErrInc = bbErr1 > bbErr0
//       val sawAnyBbFlag = (bad0 + bad3 + seq0 + seq3) > 0
//       (bbErrInc || sawAnyBbFlag) shouldBe true

//       // 3) Ch error count should track BB (already enforced cycle-by-cycle, but also check endpoints)
//       chErr1 shouldBe bbErr1
//     }
//   }

//   it should "PCS RX Negative: hdr_valid/data_valid glitching does not mis-deliver data (match BB)" in {
//     val tn = 13
//     val tname = "PCS RX Negative: hdr_valid/data_valid glitching does not mis-deliver data (match BB)"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Baseline (informational)
//       val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
//       progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

//       // Tap mode (seed first, then enable)
//       enableTapWithSeed(
//         dut,
//         data = BigInt("0123456789ABCDEF", 16).U(64.W),
//         dv   = false.B,
//         hdr  = DATA_HDR.U(2.W),
//         hv   = false.B
//       )

//       // Constant injected payload/header; only valids glitch
//       val payload = BigInt("0123456789ABCDEF", 16)
//       val hdrData = DATA_HDR

//       val cycles = 600

//       // Status alignment check (only on meaningful injected cycles)
//       var statusMismatchHits = 0

//       // XGMII compare: align by arming a short window after a "good" injection (dv&&hv),
//       // then comparing on the first cycle where BOTH outputs claim valid.
//       var xgmiiCompared   = 0
//       var xgmiiMismatches = 0

//       var firstMismatchPrinted = false

//       var pendingCompare = 0
//       val pendingMax = 8 // tolerate small internal latency differences

//       def stepInjected(dv: Boolean, hv: Boolean, stepIdx: Int): Unit = {
//         // TX side benign
//         driveXgmii(dut, idleD, idleC, valid = true)

//         // Drive tap RX signals
//         dut.io.tap_serdes_rx_data.poke(payload.U(64.W))
//         dut.io.tap_serdes_rx_hdr.poke(hdrData.U(2.W))
//         dut.io.tap_serdes_rx_data_valid.poke(dv.B)
//         dut.io.tap_serdes_rx_hdr_valid.poke(hv.B)

//         dut.clock.step(1)

//         // Arm/decay compare window after a "good" injected cycle
//         if (dv && hv) pendingCompare = pendingMax
//         else if (pendingCompare > 0) pendingCompare -= 1

//         // Peek status/counters
//         val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
//         val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
//         val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
//         val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
//         val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
//         val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

//         // Only require strict BB-match on meaningful injected cycles
//         if (dv && hv) {
//           if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) statusMismatchHits += 1
//         }

//         // Compare recovered XGMII at the first opportunity after a good injection
//         val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//         val bbV = dut.io.bb_rx_valid.peek().litToBoolean

//         if (pendingCompare > 0 && chV && bbV) {
//           xgmiiCompared += 1

//           val bbRxd = dut.io.bb_rxd.peek().litValue
//           val bbRxc = dut.io.bb_rxc.peek().litValue
//           val chRxd = dut.io.chisel_rxd.peek().litValue
//           val chRxc = dut.io.chisel_rxc.peek().litValue

//           val mismatch = (bbRxd != chRxd) || (bbRxc != chRxc)
//           if (mismatch) {
//             xgmiiMismatches += 1
//             if (!firstMismatchPrinted) {
//               firstMismatchPrinted = true
//               progInfo(
//                 tn,
//                 f"FIRST MISMATCH @step=$stepIdx dv=$dv hv=$hv pending=$pendingCompare " +
//                 f"bbRxd=0x$bbRxd%016x bbRxc=0x$bbRxc%02x " +
//                 f"chRxd=0x$chRxd%016x chRxc=0x$chRxc%02x"
//               )
//             }
//           }

//           // Consume: one compare per good injection
//           pendingCompare = 0
//         }
//       }

//       for (i <- 0 until cycles) {
//         // Glitch schedule:
//         // 0: good (dv=1 hv=1)
//         // 1: hv dropped (dv=1 hv=0)
//         // 2: dv dropped (dv=0 hv=1)
//         // 3: both dropped (dv=0 hv=0)
//         (i % 4) match {
//           case 0 => stepInjected(dv = true,  hv = true,  stepIdx = i)
//           case 1 => stepInjected(dv = true,  hv = false, stepIdx = i)
//           case 2 => stepInjected(dv = false, hv = true,  stepIdx = i)
//           case _ => stepInjected(dv = false, hv = false, stepIdx = i)
//         }
//       }

//       val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt

//       progInfo(tn, s"statusMismatchHits=$statusMismatchHits")
//       progInfo(tn, s"xgmii: compared=$xgmiiCompared mismatches=$xgmiiMismatches")
//       progInfo(tn, s"end error_count: bb=$bbErr1 ch=$chErr1")

//       // Must-haves:
//       // On meaningful injected cycles, Chisel status/counters track BB
//       statusMismatchHits shouldBe 0

//       // After each good injection, when both outputs claim valid, recovered XGMII must match
//       xgmiiCompared should be > 50
//       xgmiiMismatches shouldBe 0
//       // chErr1 shouldBe bbErr1, too strict different designs can be different
//     }
//   }

//   it should "PCS Robustness: short IPG injection (back-to-back start after term) behavior matches BB" in {
//     val tn = 14
//     val tname = "PCS Robustness: short IPG injection (back-to-back start after term) behavior matches BB"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Helper to drive one cycle of an XGMII word
//       def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
//         driveXgmii(dut, word._1, word._2, valid)
//         dut.clock.step(1)
//       }

//       // Baseline counters
//       val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
//       progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

//       // We'll run a few "frames" back-to-back with intentionally short IPG.
//       // Spec: min IPG is 96 bit-times = 12 bytes (on XGMII that's 12 idle bytes).
//       // We deliberately violate by inserting only 0..1 idle cycles between TERM and next START.
//       val trials = 6

//       // A simple payload word stream (all data)
//       val dataWordA: (UInt, UInt) =
//         (packBytesLE(Seq(0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08)), packCtrlBitsLE(Seq.fill(8)(0)))
//       val dataWordB: (UInt, UInt) =
//         (packBytesLE(Seq(0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17)), packCtrlBitsLE(Seq.fill(8)(0)))

//       var mismatchHits = 0
//       var xgmiiCompared = 0
//       var xgmiiMismatches = 0

//       // Track BB reaction (not required, but informative)
//       var bbBadHits = 0
//       var bbSeqHits = 0
//       var bbMaxErr = bbErr0
//       var chMaxErr = chErr0

//       def checkAlign(): Unit = {
//         val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
//         val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
//         val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
//         val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
//         val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
//         val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

//         if (bbBad) bbBadHits += 1
//         if (bbSeq) bbSeqHits += 1
//         if (bbErr > bbMaxErr) bbMaxErr = bbErr
//         if (chErr > chMaxErr) chMaxErr = chErr

//         // Status must match BB every cycle
//         if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) mismatchHits += 1

//         // When both say output valid, recovered XGMII must match
//         val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//         val bbV = dut.io.bb_rx_valid.peek().litToBoolean
//         if (chV && bbV) {
//           xgmiiCompared += 1
//           val chRxd = dut.io.chisel_rxd.peek().litValue
//           val bbRxd = dut.io.bb_rxd.peek().litValue
//           val chRxc = dut.io.chisel_rxc.peek().litValue
//           val bbRxc = dut.io.bb_rxc.peek().litValue
//           if (chRxd != bbRxd || chRxc != bbRxc) xgmiiMismatches += 1
//         }
//       }

//       // Start with a little clean idle
//       for (_ <- 0 until 40) { stepXgmii(xgmiiIdleWord); checkAlign() }

//       for (t <- 0 until trials) {
//         // --- Frame t ---
//         // Some IDLE leading (not required, but keeps things consistent)
//         for (_ <- 0 until 4) { stepXgmii(xgmiiIdleWord); checkAlign() }

//         // START + SFD + a few payload cycles
//         stepXgmii(xgmiiStartWord); checkAlign()
//         stepXgmii(xgmiiSfdWord);  checkAlign()
//         for (_ <- 0 until 6) { stepXgmii(dataWordA); checkAlign() }
//         for (_ <- 0 until 6) { stepXgmii(dataWordB); checkAlign() }

//         // TERM
//         stepXgmii(xgmiiTermWord); checkAlign()

//         // --- Short IPG violation ---
//         // Intentionally too short: 0 or 1 idle cycles (<< min IPG)
//         val shortIpgIdleCycles = if ((t % 2) == 0) 0 else 1
//         for (_ <- 0 until shortIpgIdleCycles) { stepXgmii(xgmiiIdleWord); checkAlign() }

//         // Immediately start the next frame (violating IPG)
//         stepXgmii(xgmiiStartWord); checkAlign()
//         stepXgmii(xgmiiSfdWord);  checkAlign()
//         for (_ <- 0 until 4) { stepXgmii(dataWordA); checkAlign() }

//         // TERM again to close the "violating" burst cleanly
//         stepXgmii(xgmiiTermWord); checkAlign()

//         // A little idle after each trial to allow any internal settling
//         for (_ <- 0 until 20) { stepXgmii(xgmiiIdleWord); checkAlign() }
//       }

//       val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
//       val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt

//       progInfo(tn, s"trials=$trials mismatchHits=$mismatchHits")
//       progInfo(tn, s"bb flags: badHits=$bbBadHits seqHits=$bbSeqHits")
//       progInfo(tn, s"error_count: bb start=$bbErr0 max=$bbMaxErr end=$bbErr1, ch start=$chErr0 max=$chMaxErr end=$chErr1")
//       progInfo(tn, s"xgmii: compared=$xgmiiCompared mismatches=$xgmiiMismatches")

//       // Requirements:
//       // 1) Chisel must behave exactly like BB (status + counter)
//       mismatchHits shouldBe 0
//       chErr1 shouldBe bbErr1

//       // 2) If both produce valid recovered outputs, they must match
//       xgmiiMismatches shouldBe 0

//       // NOTE: we intentionally do NOT require bbErr1 > bbErr0 or flags asserted.
//       // Some PCS implementations may not police IPG at PCS level.
//     }
//   }

//   it should "PCS Coverage: TERM appears in all possible lanes (0..7) and Chisel matches BB" in {
//     val tn = 15
//     val tname = "PCS Coverage: TERM appears in all possible lanes (0..7) and Chisel matches BB"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       // Small settle
//       for (_ <- 0 until 40) {
//         driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
//         dut.clock.step(1)
//       }

//       // TERM lane is determined by (1 + payloadLen) mod 8 (the "1" is SFD)
//       // Choose payload lengths that force each lane.
//       def payloadLenForLane(lane: Int, base: Int = 32): Int = {
//         val needMod = (lane - 1 + 8) % 8
//         val baseAligned = (base / 8) * 8
//         baseAligned + needMod
//       }

//       var totalCompared = 0
//       var totalMismatches = 0

//       // Track that TERM was actually observed on RX with each lane
//       val seenTermLane = Array.fill(8)(false)

//       for (lane <- 0 until 8) {
//         val plen = payloadLenForLane(lane, base = 32)
//         val payload = (0 until plen).map(i => (i * 7 + lane) & 0xFF) // deterministic bytes
//         val words = buildFrameWords(payload)

//         // Drive the frame
//         val (compared, mismatches) = driveFrameAndCompare(dut, words)
//         totalCompared += compared
//         totalMismatches += mismatches

//         // After driving, add a little idle and scan RX for TERM lane presence
//         for (_ <- 0 until 40) {
//           driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
//           dut.clock.step(1)

//           val bbV = dut.io.bb_rx_valid.peek().litToBoolean
//           val chV = dut.io.chisel_rx_valid.peek().litToBoolean

//           // NEW: count + enforce match during scan-idle too (RX output may appear here)
//           if (bbV && chV) {
//             totalCompared += 1

//             val bbRxd = dut.io.bb_rxd.peek()
//             val bbRxc = dut.io.bb_rxc.peek()

//             dut.io.chisel_rxd.expect(bbRxd)
//             dut.io.chisel_rxc.expect(bbRxc)

//             // scan lanes for TERM (use BB stream as reference)
//             val rxd = bbRxd.litValue
//             val rxc = bbRxc.litValue
//             for (l <- 0 until 8) {
//               val byte = (rxd >> (8 * l)) & 0xFF
//               val ctrl = (rxc >> l) & 0x1
//               if (ctrl == 1 && byte == XGMII_TERM) {
//                 seenTermLane(l) = true
//               }
//             }
//           } else if (bbV != chV) {
//             // Optional: if you want stricter debugging, keep this info print.
//             // Comment out if too noisy.
//             // progInfo(tn, s"scan-idle valid mismatch: bbV=$bbV chV=$chV")
//           }
//         }

//         progInfo(tn, s"termLaneTarget=$lane payloadLen=$plen compared=$compared mismatches=$mismatches")
//       }

//       progInfo(tn, s"TOTAL compared=$totalCompared mismatches=$totalMismatches termSeen=${seenTermLane.mkString("[", ",", "]")}")

//       totalCompared should be > 50
//       totalMismatches shouldBe 0
//       // Ensure we *actually* exercised and observed all TERM lanes
//       seenTermLane.forall(_ == true) shouldBe true
//     }
//   }

//   it should "PCS Coverage: randomized Ethernet frames (var length/IFG) and Chisel matches BB" in {
//     val tn = 16
//     val tname = "PCS Coverage: randomized Ethernet frames (var length/IFG) and Chisel matches BB"
//     withDut(tn, tname) { dut =>
//       initDut(dut, tn)
//       waitForLock(dut, tn)

//       val rnd = new scala.util.Random(0xC0FFEE) // deterministic seed

//       def randByte(): Int = rnd.nextInt(256)

//       // Start with some clean idle
//       for (_ <- 0 until 100) {
//         driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
//         dut.clock.step(1)
//       }

//       val frames = 500
//       var totalCompared = 0
//       var totalMismatches = 0

//       // Optional: track TERM lane coverage from random runs too
//       val seenTermLane = Array.fill(8)(false)

//       for (f <- 0 until frames) {
//         // Payload length: include edge cases and typical sizes.
//         // Keep modest so sim stays fast; tweak upper bound if you want.
//         val plen =
//           if (f < 16) f // tiny frames early
//           else rnd.nextInt(512) // 0..511 bytes

//         val payload = Seq.fill(plen)(randByte())
//         val words = buildFrameWords(payload)

//         val (compared, mismatches) = driveFrameAndCompare(dut, words)
//         totalCompared += compared
//         totalMismatches += mismatches

//         // IFG (idle cycles) random 0..10 to vary spacing (including short/violating)
//         val ifg = rnd.nextInt(11)
//         for (_ <- 0 until ifg) {
//           driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
//           dut.clock.step(1)

//           val bbV = dut.io.bb_rx_valid.peek().litToBoolean
//           val chV = dut.io.chisel_rx_valid.peek().litToBoolean
//           if (bbV && chV) {
//             val rxd = dut.io.bb_rxd.peek().litValue
//             val rxc = dut.io.bb_rxc.peek().litValue
//             for (l <- 0 until 8) {
//               val byte = (rxd >> (8 * l)) & 0xFF
//               val ctrl = (rxc >> l) & 0x1
//               if (ctrl == 1 && byte == XGMII_TERM) {
//                 seenTermLane(l) = true
//               }
//             }
//           }
//         }
//       }

//       progInfo(tn, s"frames=$frames totalCompared=$totalCompared totalMismatches=$totalMismatches")
//       progInfo(tn, s"random term lane coverage=${seenTermLane.mkString("[", ",", "]")}")

//       totalCompared should be > 500 // sanity check that more than 500 ethernet frames were sent
//       totalMismatches shouldBe 0
//     }
//   }
// }
package org.chiselware.cores.o01.t001.pcs

import chisel3._
import chiseltest._
import chiseltest.simulator.VerilatorFlags
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import firrtl2.options.TargetDirAnnotation

class ComparePcsTester extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  // --------------------------------------------------------------------------
  // Progress printing helpers
  // --------------------------------------------------------------------------

  private val totalTests = 16

  private def pct(n: Int): Int = (n * 100) / totalTests

  private def progStart(n: Int, name: String): Unit =
    println(s"\n[$n/$totalTests] (${pct(n)}%)\tSTART:\t$name")

  private def progEnd(n: Int, name: String): Unit =
    println(s"[$n/$totalTests] (${pct(n)}%)\tDONE:\t$name")

  private def progInfo(n: Int, msg: String): Unit =
    println(s"[$n/$totalTests]\t\t\t• $msg")

  // --------------------------------------------------------------------------
  // Common annotations
  // --------------------------------------------------------------------------

  private val annotations = Seq(
    VerilatorBackendAnnotation,
    VerilatorFlags(
      Seq("--compiler", "clang", "-LDFLAGS", "-Wno-unused-command-line-argument")
    ),
    WriteVcdAnnotation,
    TargetDirAnnotation("modules/pcs/generated/PcsTests")
  )

  private def withDut(tn: Int, testName: String)(body: DualWrapperPcs => Unit): Unit = {
    progStart(tn, testName)
    test(new DualWrapperPcs).withAnnotations(annotations) { dut =>
      body(dut)
    }
    progEnd(tn, testName)
  }

  // --------------------------------------------------------------------------
  // XGMII constants
  // --------------------------------------------------------------------------

  private val ethPre = 0x55
  private val ethSfd = 0xD5
  private val xgmiiIdle = 0x07
  private val xgmiiStart = 0xFB
  private val xgmiiTerm = 0xFD

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private val idleData = "h0707070707070707".U(64.W)
  private val idleCtrl = "hFF".U(8.W)

  // 10GBASE-R sync header encodings (2 bits)
  // data block header: 01, control block header: 10
  private val dataHdr = 1
  private val ctrlHdr = 2

  private def initDut(dut: DualWrapperPcs, tn: Int): Unit = {
    dut.clock.setTimeout(0)

    // Defaults
    dut.io.tap_enable.poke(false.B)
    dut.io.tap_serdes_rx_data.poke(0.U)
    dut.io.tap_serdes_rx_data_valid.poke(false.B)
    dut.io.tap_serdes_rx_hdr.poke(0.U)
    dut.io.tap_serdes_rx_hdr_valid.poke(false.B)

    dut.io.xgmii_tx_valid.poke(true.B)
    dut.io.xgmii_txd.poke(idleData)
    dut.io.xgmii_txc.poke(idleCtrl)

    // Reset
    dut.io.rst.poke(true.B)
    dut.clock.step(20)
    dut.io.rst.poke(false.B)

    // Let it settle/lock a bit
    dut.clock.step(200)
    progInfo(tn, "init complete (reset deasserted, settle done)")
  }

  private def driveXgmii(
    dut: DualWrapperPcs,
    txd: UInt,
    txc: UInt,
    valid: Boolean = true
  ): Unit = {
    dut.io.xgmii_txd.poke(txd)
    dut.io.xgmii_txc.poke(txc)
    dut.io.xgmii_tx_valid.poke(valid.B)
  }

  private def expectHdrOnValid(
    hdr: UInt,
    dv: Bool,
    hv: Bool,
    expected: Int,
    tn: Int,
    label: String
  ): Unit = {
    val dataValid = dv.peek().litToBoolean
    val hdrValid = hv.peek().litToBoolean

    if (dataValid && hdrValid) {
      val got = hdr.peek().litValue.toInt
      if (got != expected) {
        progInfo(tn, s"$label: expected hdr=$expected, got=$got")
      }
      hdr.expect(expected.U)
    }
  }

  private def enableTapWithSeed(
    dut: DualWrapperPcs,
    data: UInt,
    dv: Bool,
    hdr: UInt,
    hv: Bool
  ): Unit = {
    dut.io.tap_serdes_rx_data.poke(data)
    dut.io.tap_serdes_rx_data_valid.poke(dv)
    dut.io.tap_serdes_rx_hdr.poke(hdr)
    dut.io.tap_serdes_rx_hdr_valid.poke(hv)
    dut.io.tap_enable.poke(true.B)
  }

  private def waitForLock(dut: DualWrapperPcs, tn: Int, maxCycles: Int = 5000): Unit = {
    var locked = false
    var cycles = 0

    while (!locked && cycles < maxCycles) {
      dut.clock.step(1)
      cycles += 1

      val chLock = dut.io.chisel_rx_block_lock.peek().litToBoolean
      val bbLock = dut.io.bb_rx_block_lock.peek().litToBoolean
      locked = chLock && bbLock
    }

    progInfo(tn, s"waitForLock: cycles=$cycles, locked=$locked")
    locked shouldBe true
  }

  // Pack 8 bytes into a 64-bit word assuming lane0 is LSB (xgmii_txd[7:0])
  private def packBytesLE(bs: Seq[Int]): UInt = {
    require(bs.length == 8)
    val word = bs.zipWithIndex
      .map { case (b, i) => BigInt(b & 0xFF) << (8 * i) }
      .reduce(_ | _)
    word.U(64.W)
  }

  // Pack 8 control bits into xgmii_txc (bit i corresponds to lane i)
  private def packCtrlBitsLE(cs: Seq[Int]): UInt = {
    require(cs.length == 8)
    val word = cs.zipWithIndex
      .map { case (c, i) => BigInt(c & 0x1) << i }
      .reduce(_ | _)
    word.U(8.W)
  }

  // Canonical XGMII words
  private val xgmiiIdleWord: (UInt, UInt) =
    (
      packBytesLE(Seq.fill(8)(xgmiiIdle)),
      packCtrlBitsLE(Seq.fill(8)(1))
    )

  // START in lane0: /S/ then 7 bytes of preamble (data)
  private val xgmiiStartWord: (UInt, UInt) =
    (
      packBytesLE(
        Seq(
          xgmiiStart,
          ethPre,
          ethPre,
          ethPre,
          ethPre,
          ethPre,
          ethPre,
          ethPre
        )
      ),
      packCtrlBitsLE(Seq(1, 0, 0, 0, 0, 0, 0, 0))
    )

  // SFD as data (typically follows preamble)
  private val xgmiiSfdWord: (UInt, UInt) =
    (
      packBytesLE(Seq(ethSfd, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77)),
      packCtrlBitsLE(Seq.fill(8)(0))
    )

  // TERM in lane0 and then IDLE control for rest of lanes
  private val xgmiiTermWord: (UInt, UInt) =
    (
      packBytesLE(
        Seq(
          xgmiiTerm,
          xgmiiIdle,
          xgmiiIdle,
          xgmiiIdle,
          xgmiiIdle,
          xgmiiIdle,
          xgmiiIdle,
          xgmiiIdle
        )
      ),
      packCtrlBitsLE(Seq.fill(8)(1))
    )

  // --------------------------------------------------------------------------------------
  // Build an XGMII frame stream:
  //   IDLE (optional externally)
  //   START word (/S/ + 7x preamble)
  //   then data words starting with [SFD + payload...]
  //   then one final word containing TERM in some lane (computed from payload length)
  // NOTE: TERM lane is determined by (1 + payloadLen) mod 8, where the "1" is the SFD byte.
  // --------------------------------------------------------------------------------------
  private def buildFrameWords(payloadBytes: Seq[Int]): Seq[(UInt, UInt)] = {
    val start = xgmiiStartWord

    // [SFD] + payload bytes
    val dataStream: Seq[Int] = Seq(ethSfd) ++ payloadBytes

    val grouped = dataStream.grouped(8).toSeq
    val fullDataWords = grouped
      .dropRight(1)
      .map { chunk =>
        val bytes = chunk.padTo(8, 0x00)
        (packBytesLE(bytes), packCtrlBitsLE(Seq.fill(8)(0)))
      }

    val lastChunk = grouped.lastOption.getOrElse(Seq.empty[Int])
    val lastDataCount = lastChunk.length

    val termWords: Seq[(UInt, UInt)] =
      if (lastDataCount == 8) {
        val lastDataWord =
          (packBytesLE(lastChunk), packCtrlBitsLE(Seq.fill(8)(0)))
        Seq(lastDataWord, xgmiiTermWord)
      } else {
        val termLane = lastDataCount
        val bytes =
          (
            lastChunk.padTo(termLane, 0x00) ++
              Seq(xgmiiTerm) ++
              Seq.fill(7 - termLane)(xgmiiIdle)
          ).take(8)

        val ctrlBits =
          (Seq.fill(termLane)(0) ++ Seq.fill(8 - termLane)(1)).take(8)

        Seq((packBytesLE(bytes), packCtrlBitsLE(ctrlBits)))
      }

    Seq(start) ++ fullDataWords ++ termWords
  }

  private def driveFrameAndCompare(
    dut: DualWrapperPcs,
    words: Seq[(UInt, UInt)]
  ): (Int, Int) = {
    var compared = 0
    var mismatches = 0

    for ((txd, txc) <- words) {
      driveXgmii(dut, txd, txc, valid = true)
      dut.clock.step(1)

      val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
      val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

      if (chValid && bbValid) {
        compared += 1

        val chRxd = dut.io.chisel_rxd.peek().litValue
        val bbRxd = dut.io.bb_rxd.peek().litValue
        val chRxc = dut.io.chisel_rxc.peek().litValue
        val bbRxc = dut.io.bb_rxc.peek().litValue

        if (chRxd != bbRxd || chRxc != bbRxc) {
          mismatches += 1
        }

        dut.io.chisel_rxd.expect(dut.io.bb_rxd.peek())
        dut.io.chisel_rxc.expect(dut.io.bb_rxc.peek())
      }
    }

    (compared, mismatches)
  }

  // --------------------------------------------------------------------------
  // Test cases
  // --------------------------------------------------------------------------

  it should "PCS: Chisel vs Verilog BlackBox matches on IDLE stream (loopback)" in {
    val tn = 1
    val testName = "PCS: Chisel vs Verilog BlackBox matches on IDLE stream (loopback)"

    withDut(tn, testName) { dut =>
      progInfo(tn, "start of PCS testing")
      initDut(dut, tn)

      for (_ <- 0 until 200) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)

        val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
        val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

        if (chValid && bbValid) {
          dut.io.chisel_rxd.expect(dut.io.bb_rxd.peek())
          dut.io.chisel_rxc.expect(dut.io.bb_rxc.peek())
        }
      }

      dut.io.chisel_rx_block_lock.expect(dut.io.bb_rx_block_lock.peek())
      dut.io.chisel_rx_status.expect(dut.io.bb_rx_status.peek())
    }
  }

  it should "PCS TX: data blocks use sync header 01 (DATA) when txc==0x00" in {
    val tn = 2
    val testName = "PCS TX: data blocks use sync header 01 (DATA) when txc==0x00"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)

      val dataCtrl = 0.U(8.W)
      val dataWord = "h1122334455667788".U(64.W)

      dut.clock.step(300)

      val totalCycles = 800
      val ignoreFirstValidBlocks = 20

      var seenValid = 0
      var seenData = 0
      var seenCtrl = 0
      var seenOther = 0
      var compared = 0

      for (_ <- 0 until totalCycles) {
        driveXgmii(dut, dataWord, dataCtrl, valid = true)
        dut.clock.step(1)

        val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
        val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
        val chDv = dut.io.ch_tx_dv.peek().litToBoolean
        val chHv = dut.io.ch_tx_hv.peek().litToBoolean

        if (bbDv && bbHv) {
          seenValid += 1
          val bbHdr = dut.io.bb_tx_hdr.peek().litValue.toInt

          if (seenValid > ignoreFirstValidBlocks) {
            if (bbHdr == dataHdr) seenData += 1
            else if (bbHdr == ctrlHdr) seenCtrl += 1
            else seenOther += 1
          }

          if (seenValid > ignoreFirstValidBlocks && chDv && chHv) {
            dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
            compared += 1
          }
        }
      }

      progInfo(
        tn,
        s"bb validBlocks=$seenValid, comparedBlocks=$compared, dataHdrCount=$seenData, ctrlHdrCount=$seenCtrl, otherHdrCount=$seenOther"
      )

      seenData should be > 0
      seenOther shouldBe 0
      seenCtrl shouldBe 0
      compared should be > 50
    }
  }

  it should "PCS TX: control blocks use sync header 10 (CTRL) when txc==0xFF (IDLE)" in {
    val tn = 3
    val testName = "PCS TX: control blocks use sync header 10 (CTRL) when txc==0xFF (IDLE)"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)

      for (i <- 0 until 300) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)

        expectHdrOnValid(
          dut.io.bb_tx_hdr,
          dut.io.bb_tx_dv,
          dut.io.bb_tx_hv,
          ctrlHdr,
          tn,
          s"bb@$i"
        )

        expectHdrOnValid(
          dut.io.ch_tx_hdr,
          dut.io.ch_tx_dv,
          dut.io.ch_tx_hv,
          ctrlHdr,
          tn,
          s"ch@$i"
        )
      }
    }
  }

  it should "PCS RX: block lock is acquired on clean IDLE and remains stable" in {
    val tn = 4
    val testName = "PCS RX: block lock is acquired on clean IDLE and remains stable"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn, maxCycles = 5000)

      val stableCycles = 5000
      var lockDrops = 0
      var statusMismatches = 0

      for (_ <- 0 until stableCycles) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)

        val bbLock = dut.io.bb_rx_block_lock.peek().litToBoolean
        val chLock = dut.io.chisel_rx_block_lock.peek().litToBoolean
        if (!bbLock || !chLock) lockDrops += 1

        val bbStatus = dut.io.bb_rx_status.peek().litToBoolean
        val chStatus = dut.io.chisel_rx_status.peek().litToBoolean
        if (bbStatus != chStatus) statusMismatches += 1
      }

      progInfo(
        tn,
        s"stableCycles=$stableCycles, lockDrops=$lockDrops, statusMismatches=$statusMismatches"
      )

      lockDrops shouldBe 0
      statusMismatches shouldBe 0
    }
  }

  it should "PCS RX: no spurious errors on clean IDLE (rx_bad_block/seq_error/error_count)" in {
    val tn = 5
    val testName =
      "PCS RX: no spurious errors on clean IDLE (rx_bad_block/seq_error/error_count)"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
      progInfo(tn, s"initial error_count: bb=$bbErr0 ch=$chErr0")

      val checkCycles = 5000
      var badBlockHits = 0
      var seqErrHits = 0
      var errIncHits = 0
      var mismatchHits = 0

      var bbMaxErr = bbErr0
      var chMaxErr = chErr0

      for (_ <- 0 until checkCycles) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)

        val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
        val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
        val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
        val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean

        val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
        val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

        if (bbBad || chBad) badBlockHits += 1
        if (bbSeq || chSeq) seqErrHits += 1

        if (bbErr > bbMaxErr) bbMaxErr = bbErr
        if (chErr > chMaxErr) chMaxErr = chErr

        if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) {
          mismatchHits += 1
        }
      }

      if (bbMaxErr != bbErr0 || chMaxErr != chErr0) {
        errIncHits = 1
      }

      progInfo(
        tn,
        s"checkCycles=$checkCycles badBlockHits=$badBlockHits seqErrHits=$seqErrHits mismatchHits=$mismatchHits"
      )
      progInfo(
        tn,
        s"error_count: bb start=$bbErr0 max=$bbMaxErr, ch start=$chErr0 max=$chMaxErr"
      )

      badBlockHits shouldBe 0
      seqErrHits shouldBe 0
      errIncHits shouldBe 0
      mismatchHits shouldBe 0
    }
  }

  // --------------------------------------------------------------------------
  // Control formatting sanity (Start/Terminate/Idle)
  // --------------------------------------------------------------------------

  it should "PCS TX: control encoding is non-degenerate (IDLE vs START vs TERM differ) and uses CTRL hdr" in {
    val tn = 6
    val testName =
      "PCS TX: control encoding is non-degenerate (IDLE vs START vs TERM differ) and uses CTRL hdr"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      dut.clock.step(200)

      def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
        driveXgmii(dut, word._1, word._2, valid)
        dut.clock.step(1)
      }

      def collectCtrlPayloads(
        scenario: String,
        steps: Int,
        driveFn: Int => Unit
      ): Set[BigInt] = {
        var payloads = Set.empty[BigInt]

        for (i <- 0 until steps) {
          driveFn(i)

          val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
          val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
          val chDv = dut.io.ch_tx_dv.peek().litToBoolean
          val chHv = dut.io.ch_tx_hv.peek().litToBoolean

          if (bbDv && bbHv) {
            dut.io.bb_tx_hdr.expect(ctrlHdr.U)
            if (chDv && chHv) {
              dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
            }

            payloads += dut.io.bb_tx_data.peek().litValue
          }
        }

        progInfo(tn, s"$scenario: collected ${payloads.size} unique ctrl payload(s)")
        payloads
      }

      val idleSet =
        collectCtrlPayloads("IDLE", steps = 40, driveFn = _ => stepXgmii(xgmiiIdleWord))
      val startSet =
        collectCtrlPayloads(
          "START",
          steps = 10,
          driveFn = i => if (i == 0) stepXgmii(xgmiiStartWord) else stepXgmii(xgmiiIdleWord)
        )
      val termSet =
        collectCtrlPayloads(
          "TERM",
          steps = 10,
          driveFn = i => if (i == 0) stepXgmii(xgmiiTermWord) else stepXgmii(xgmiiIdleWord)
        )

      startSet.diff(idleSet).nonEmpty shouldBe true
      termSet.diff(idleSet).nonEmpty shouldBe true
    }
  }

  it should "PCS TX: START->DATA transition (CTRL hdr then DATA hdr) behaves correctly" in {
    val tn = 7
    val testName = "PCS TX: START->DATA transition (CTRL hdr then DATA hdr) behaves correctly"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      dut.clock.step(200)

      def driveStep(word: (UInt, UInt)): Unit = {
        driveXgmii(dut, word._1, word._2, valid = true)
        dut.clock.step(1)
      }

      val totalCycles = 300

      var sawCtrl = false
      var sawDataAfterCtrl = false
      var sawAnyData = false
      var observedValid = 0

      for (i <- 0 until totalCycles) {
        if (i < 20) {
          driveStep(xgmiiIdleWord)
        } else if (i == 20) {
          driveStep(xgmiiStartWord)
        } else if (i == 21) {
          driveStep(xgmiiSfdWord)
        } else if (i < 60) {
          driveStep(
            (
              packBytesLE(Seq(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)),
              packCtrlBitsLE(Seq.fill(8)(0))
            )
          )
        } else {
          driveStep(
            (
              packBytesLE(Seq(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17)),
              packCtrlBitsLE(Seq.fill(8)(0))
            )
          )
        }

        val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
        val bbHv = dut.io.bb_tx_hv.peek().litToBoolean
        val chDv = dut.io.ch_tx_dv.peek().litToBoolean
        val chHv = dut.io.ch_tx_hv.peek().litToBoolean

        if (bbDv && bbHv) {
          observedValid += 1
          val hdr = dut.io.bb_tx_hdr.peek().litValue.toInt

          if (hdr == ctrlHdr) sawCtrl = true
          if (hdr == dataHdr) {
            sawAnyData = true
            if (sawCtrl) sawDataAfterCtrl = true
          }

          if (chDv && chHv) {
            dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
          }
        }
      }

      progInfo(
        tn,
        s"observedValid=$observedValid sawCtrl=$sawCtrl sawAnyData=$sawAnyData sawDataAfterCtrl=$sawDataAfterCtrl"
      )

      sawCtrl shouldBe true
      sawAnyData shouldBe true
      sawDataAfterCtrl shouldBe true
    }
  }

  it should "PCS TX: TERM->IDLE transition (CTRL hdr then CTRL hdr) behaves correctly" in {
    val tn = 8
    val testName = "PCS TX: TERM->IDLE transition (CTRL hdr then CTRL hdr) behaves correctly"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      dut.clock.step(200)

      def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
        driveXgmii(dut, word._1, word._2, valid)
        dut.clock.step(1)
      }

      stepXgmii(xgmiiIdleWord)
      stepXgmii(xgmiiStartWord)
      stepXgmii(xgmiiSfdWord)
      stepXgmii(
        (
          packBytesLE(Seq(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01, 0x02, 0x03)),
          packCtrlBitsLE(Seq.fill(8)(0))
        )
      )
      stepXgmii(xgmiiTermWord)

      val window = 200
      var sawCtrlAtTermRegion = false
      var sawCtrlAfterTerm = false

      for (i <- 0 until window) {
        stepXgmii(xgmiiIdleWord)

        val bbDv = dut.io.bb_tx_dv.peek().litToBoolean
        val bbHv = dut.io.bb_tx_hv.peek().litToBoolean

        if (bbDv && bbHv) {
          val hdr = dut.io.bb_tx_hdr.peek().litValue.toInt

          if (hdr == ctrlHdr && i < 20) sawCtrlAtTermRegion = true
          if (hdr == ctrlHdr && i > 20) sawCtrlAfterTerm = true

          val chDv = dut.io.ch_tx_dv.peek().litToBoolean
          val chHv = dut.io.ch_tx_hv.peek().litToBoolean
          if (chDv && chHv) {
            dut.io.ch_tx_hdr.expect(dut.io.bb_tx_hdr.peek())
          }
        }
      }

      progInfo(
        tn,
        s"sawCtrlAtTermRegion=$sawCtrlAtTermRegion sawCtrlAfterTerm=$sawCtrlAfterTerm"
      )
      sawCtrlAtTermRegion shouldBe true
      sawCtrlAfterTerm shouldBe true
    }
  }

  // --------------------------------------------------------------------------
  // Mapping / bit ordering consistency via cross-coupled tap
  // --------------------------------------------------------------------------

  it should "PCS Mapping: BB TX -> Chisel RX (tap) recovered XGMII matches BB recovered XGMII" in {
    val tn = 9
    val testName =
      "PCS Mapping: BB TX -> Chisel RX (tap) recovered XGMII matches BB recovered XGMII"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      dut.io.tap_enable.poke(false.B)
      for (_ <- 0 until 50) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)
      }

      dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
      dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
      dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
      dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

      dut.io.tap_enable.poke(true.B)

      val totalCycles = 2000
      val settleCyclesAfterTap = 50

      var compared = 0
      var mismatches = 0

      for (i <- 0 until totalCycles) {
        if (i < 200) {
          driveXgmii(dut, idleData, idleCtrl, valid = true)
        } else if (i < 1200) {
          driveXgmii(dut, "h1122334455667788".U(64.W), 0.U(8.W), valid = true)
        } else {
          driveXgmii(dut, idleData, idleCtrl, valid = true)
        }

        dut.clock.step(1)

        dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
        dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
        dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
        dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

        if (i >= settleCyclesAfterTap) {
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

          if (chValid && bbValid) {
            compared += 1

            val chRxd = dut.io.chisel_rxd.peek()
            val bbRxd = dut.io.bb_rxd.peek()
            val chRxc = dut.io.chisel_rxc.peek()
            val bbRxc = dut.io.bb_rxc.peek()

            if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) {
              mismatches += 1
            }

            dut.io.chisel_rxd.expect(bbRxd)
            dut.io.chisel_rxc.expect(bbRxc)
          }
        }
      }

      progInfo(
        tn,
        s"tap compare: totalCycles=$totalCycles compared=$compared mismatches=$mismatches"
      )

      compared should be > 50
      mismatches shouldBe 0
    }
  }

  it should "PCS Mapping: Chisel TX -> BB RX (tap) recovered XGMII matches Chisel recovered XGMII" in {
    val tn = 10
    val testName =
      "PCS Mapping: Chisel TX -> BB RX (tap) recovered XGMII matches Chisel recovered XGMII"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      dut.io.tap_enable.poke(false.B)
      for (_ <- 0 until 50) {
        driveXgmii(dut, idleData, idleCtrl, valid = true)
        dut.clock.step(1)
      }

      dut.io.tap_serdes_rx_data.poke(dut.io.ch_tx_data.peek())
      dut.io.tap_serdes_rx_data_valid.poke(dut.io.ch_tx_dv.peek())
      dut.io.tap_serdes_rx_hdr.poke(dut.io.ch_tx_hdr.peek())
      dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.ch_tx_hv.peek())

      dut.io.tap_enable.poke(true.B)

      val totalCycles = 2000
      val settleCyclesAfterTap = 50

      var compared = 0
      var mismatches = 0

      for (i <- 0 until totalCycles) {
        if (i < 200) {
          driveXgmii(dut, idleData, idleCtrl, valid = true)
        } else if (i < 1200) {
          driveXgmii(dut, "h1122334455667788".U(64.W), 0.U(8.W), valid = true)
        } else {
          driveXgmii(dut, idleData, idleCtrl, valid = true)
        }

        dut.clock.step(1)

        dut.io.tap_serdes_rx_data.poke(dut.io.ch_tx_data.peek())
        dut.io.tap_serdes_rx_data_valid.poke(dut.io.ch_tx_dv.peek())
        dut.io.tap_serdes_rx_hdr.poke(dut.io.ch_tx_hdr.peek())
        dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.ch_tx_hv.peek())

        if (i >= settleCyclesAfterTap) {
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

          if (chValid && bbValid) {
            compared += 1

            val chRxd = dut.io.chisel_rxd.peek()
            val bbRxd = dut.io.bb_rxd.peek()
            val chRxc = dut.io.chisel_rxc.peek()
            val bbRxc = dut.io.bb_rxc.peek()

            if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) {
              mismatches += 1
            }

            dut.io.bb_rxd.expect(chRxd)
            dut.io.bb_rxc.expect(chRxc)
          }
        }
      }

      progInfo(
        tn,
        s"tap compare: totalCycles=$totalCycles compared=$compared mismatches=$mismatches"
      )

      compared should be > 50
      mismatches shouldBe 0
    }
  }

  it should "PCS Mapping: cross-coupled mixed stream (IDLE + START + DATA + TERM) remains consistent" in {
    val tn = 11
    val testName =
      "PCS Mapping: cross-coupled mixed stream (IDLE + START + DATA + TERM) remains consistent"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      dut.io.tap_enable.poke(false.B)
      for (_ <- 0 until 50) {
        driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
        dut.clock.step(1)
      }

      dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
      dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
      dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
      dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())
      dut.io.tap_enable.poke(true.B)

      val settleCyclesAfterTap = 50
      var globalCycle = 0
      var compared = 0
      var mismatches = 0

      def stepAndFeedTap(word: (UInt, UInt), valid: Boolean = true): Unit = {
        driveXgmii(dut, word._1, word._2, valid)
        dut.clock.step(1)
        globalCycle += 1

        dut.io.tap_serdes_rx_data.poke(dut.io.bb_tx_data.peek())
        dut.io.tap_serdes_rx_data_valid.poke(dut.io.bb_tx_dv.peek())
        dut.io.tap_serdes_rx_hdr.poke(dut.io.bb_tx_hdr.peek())
        dut.io.tap_serdes_rx_hdr_valid.poke(dut.io.bb_tx_hv.peek())

        if (globalCycle >= settleCyclesAfterTap) {
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

          if (chValid && bbValid) {
            compared += 1

            val chRxd = dut.io.chisel_rxd.peek()
            val bbRxd = dut.io.bb_rxd.peek()
            val chRxc = dut.io.chisel_rxc.peek()
            val bbRxc = dut.io.bb_rxc.peek()

            if (chRxd.litValue != bbRxd.litValue || chRxc.litValue != bbRxc.litValue) {
              mismatches += 1
            }

            dut.io.chisel_rxd.expect(bbRxd)
            dut.io.chisel_rxc.expect(bbRxc)
          }
        }
      }

      for (_ <- 0 until 100) stepAndFeedTap(xgmiiIdleWord)
      stepAndFeedTap(xgmiiStartWord)
      stepAndFeedTap(xgmiiSfdWord)
      for (_ <- 0 until 20) {
        stepAndFeedTap(
          (
            packBytesLE(Seq(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)),
            packCtrlBitsLE(Seq.fill(8)(0))
          )
        )
      }
      stepAndFeedTap(xgmiiTermWord)
      for (_ <- 0 until 200) stepAndFeedTap(xgmiiIdleWord)

      progInfo(
        tn,
        s"mixed START/TERM tap compare: cycles=$globalCycle compared=$compared mismatches=$mismatches"
      )

      compared should be > 50
      mismatches shouldBe 0
    }
  }

  // --------------------------------------------------------------------------
  // Robustness / negative tests (tap injections)
  // --------------------------------------------------------------------------

  it should "PCS RX Negative: invalid sync header (00/11) is detected (status path) and matches BB" in {
    val tn = 12
    val testName =
      "PCS RX Negative: invalid sync header (00/11) is detected (status path) and matches BB"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
      progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

      enableTapWithSeed(
        dut,
        data = 0.U,
        dv = false.B,
        hdr = 0.U(2.W),
        hv = false.B
      )

      def injectBadHdr(hdr2b: Int, cycles: Int): (Int, Int, Int) = {
        var mismatchHits = 0
        var bbBadHits = 0
        var bbSeqHits = 0

        for (_ <- 0 until cycles) {
          driveXgmii(dut, idleData, idleCtrl, valid = true)

          dut.io.tap_serdes_rx_data.poke("hDEADBEEFCAFEBABE".U(64.W))
          dut.io.tap_serdes_rx_data_valid.poke(true.B)
          dut.io.tap_serdes_rx_hdr.poke(hdr2b.U(2.W))
          dut.io.tap_serdes_rx_hdr_valid.poke(true.B)

          dut.clock.step(1)

          val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
          val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
          val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
          val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
          val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
          val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

          if (bbBad) bbBadHits += 1
          if (bbSeq) bbSeqHits += 1

          if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) {
            mismatchHits += 1
          }
        }

        (mismatchHits, bbBadHits, bbSeqHits)
      }

      val injCycles = 256

      val (m0, bad0, seq0) = injectBadHdr(hdr2b = 0, cycles = injCycles)
      progInfo(
        tn,
        s"hdr=00: cycles=$injCycles mismatchHits=$m0 bbBadHits=$bad0 bbSeqHits=$seq0"
      )

      val (m3, bad3, seq3) = injectBadHdr(hdr2b = 3, cycles = injCycles)
      progInfo(
        tn,
        s"hdr=11: cycles=$injCycles mismatchHits=$m3 bbBadHits=$bad3 bbSeqHits=$seq3"
      )

      val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt
      progInfo(tn, s"final error_count: bb=$bbErr1 ch=$chErr1")

      m0 shouldBe 0
      m3 shouldBe 0

      val bbErrInc = bbErr1 > bbErr0
      val sawAnyBbFlag = (bad0 + bad3 + seq0 + seq3) > 0
      (bbErrInc || sawAnyBbFlag) shouldBe true

      chErr1 shouldBe bbErr1
    }
  }

  it should "PCS RX Negative: hdr_valid/data_valid glitching does not mis-deliver data (match BB)" in {
    val tn = 13
    val testName =
      "PCS RX Negative: hdr_valid/data_valid glitching does not mis-deliver data (match BB)"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
      progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

      enableTapWithSeed(
        dut,
        data = BigInt("0123456789ABCDEF", 16).U(64.W),
        dv = false.B,
        hdr = dataHdr.U(2.W),
        hv = false.B
      )

      val payload = BigInt("0123456789ABCDEF", 16)
      val hdrData = dataHdr

      val cycles = 600

      var statusMismatchHits = 0
      var xgmiiCompared = 0
      var xgmiiMismatches = 0
      var firstMismatchPrinted = false

      var pendingCompare = 0
      val pendingMax = 8

      def stepInjected(dv: Boolean, hv: Boolean, stepIdx: Int): Unit = {
        driveXgmii(dut, idleData, idleCtrl, valid = true)

        dut.io.tap_serdes_rx_data.poke(payload.U(64.W))
        dut.io.tap_serdes_rx_hdr.poke(hdrData.U(2.W))
        dut.io.tap_serdes_rx_data_valid.poke(dv.B)
        dut.io.tap_serdes_rx_hdr_valid.poke(hv.B)

        dut.clock.step(1)

        if (dv && hv) pendingCompare = pendingMax
        else if (pendingCompare > 0) pendingCompare -= 1

        val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
        val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
        val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
        val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
        val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
        val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

        if (dv && hv) {
          if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) {
            statusMismatchHits += 1
          }
        }

        val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
        val bbValid = dut.io.bb_rx_valid.peek().litToBoolean

        if (pendingCompare > 0 && chValid && bbValid) {
          xgmiiCompared += 1

          val bbRxd = dut.io.bb_rxd.peek().litValue
          val bbRxc = dut.io.bb_rxc.peek().litValue
          val chRxd = dut.io.chisel_rxd.peek().litValue
          val chRxc = dut.io.chisel_rxc.peek().litValue

          val mismatch = (bbRxd != chRxd) || (bbRxc != chRxc)
          if (mismatch) {
            xgmiiMismatches += 1
            if (!firstMismatchPrinted) {
              firstMismatchPrinted = true
              progInfo(
                tn,
                f"FIRST MISMATCH @step=$stepIdx dv=$dv hv=$hv pending=$pendingCompare " +
                  f"bbRxd=0x$bbRxd%016x bbRxc=0x$bbRxc%02x " +
                  f"chRxd=0x$chRxd%016x chRxc=0x$chRxc%02x"
              )
            }
          }

          pendingCompare = 0
        }
      }

      for (i <- 0 until cycles) {
        (i % 4) match {
          case 0 => stepInjected(dv = true, hv = true, stepIdx = i)
          case 1 => stepInjected(dv = true, hv = false, stepIdx = i)
          case 2 => stepInjected(dv = false, hv = true, stepIdx = i)
          case _ => stepInjected(dv = false, hv = false, stepIdx = i)
        }
      }

      val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt

      progInfo(tn, s"statusMismatchHits=$statusMismatchHits")
      progInfo(tn, s"xgmii: compared=$xgmiiCompared mismatches=$xgmiiMismatches")
      progInfo(tn, s"end error_count: bb=$bbErr1 ch=$chErr1")

      statusMismatchHits shouldBe 0
      xgmiiCompared should be > 50
      xgmiiMismatches shouldBe 0
    }
  }

  it should "PCS Robustness: short IPG injection (back-to-back start after term) behavior matches BB" in {
    val tn = 14
    val testName =
      "PCS Robustness: short IPG injection (back-to-back start after term) behavior matches BB"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      def stepXgmii(word: (UInt, UInt), valid: Boolean = true): Unit = {
        driveXgmii(dut, word._1, word._2, valid)
        dut.clock.step(1)
      }

      val bbErr0 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr0 = dut.io.ch_rx_error_count.peek().litValue.toInt
      progInfo(tn, s"baseline error_count: bb=$bbErr0 ch=$chErr0")

      val trials = 6

      val dataWordA =
        (
          packBytesLE(Seq(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)),
          packCtrlBitsLE(Seq.fill(8)(0))
        )
      val dataWordB =
        (
          packBytesLE(Seq(0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17)),
          packCtrlBitsLE(Seq.fill(8)(0))
        )

      var mismatchHits = 0
      var xgmiiCompared = 0
      var xgmiiMismatches = 0

      var bbBadHits = 0
      var bbSeqHits = 0
      var bbMaxErr = bbErr0
      var chMaxErr = chErr0

      def checkAlign(): Unit = {
        val bbBad = dut.io.bb_rx_bad_block.peek().litToBoolean
        val chBad = dut.io.ch_rx_bad_block.peek().litToBoolean
        val bbSeq = dut.io.bb_rx_sequence_error.peek().litToBoolean
        val chSeq = dut.io.ch_rx_sequence_error.peek().litToBoolean
        val bbErr = dut.io.bb_rx_error_count.peek().litValue.toInt
        val chErr = dut.io.ch_rx_error_count.peek().litValue.toInt

        if (bbBad) bbBadHits += 1
        if (bbSeq) bbSeqHits += 1
        if (bbErr > bbMaxErr) bbMaxErr = bbErr
        if (chErr > chMaxErr) chMaxErr = chErr

        if (bbBad != chBad || bbSeq != chSeq || bbErr != chErr) {
          mismatchHits += 1
        }

        val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
        val bbValid = dut.io.bb_rx_valid.peek().litToBoolean
        if (chValid && bbValid) {
          xgmiiCompared += 1

          val chRxd = dut.io.chisel_rxd.peek().litValue
          val bbRxd = dut.io.bb_rxd.peek().litValue
          val chRxc = dut.io.chisel_rxc.peek().litValue
          val bbRxc = dut.io.bb_rxc.peek().litValue

          if (chRxd != bbRxd || chRxc != bbRxc) {
            xgmiiMismatches += 1
          }
        }
      }

      for (_ <- 0 until 40) {
        stepXgmii(xgmiiIdleWord)
        checkAlign()
      }

      for (t <- 0 until trials) {
        for (_ <- 0 until 4) {
          stepXgmii(xgmiiIdleWord)
          checkAlign()
        }

        stepXgmii(xgmiiStartWord)
        checkAlign()

        stepXgmii(xgmiiSfdWord)
        checkAlign()

        for (_ <- 0 until 6) {
          stepXgmii(dataWordA)
          checkAlign()
        }

        for (_ <- 0 until 6) {
          stepXgmii(dataWordB)
          checkAlign()
        }

        stepXgmii(xgmiiTermWord)
        checkAlign()

        val shortIpgIdleCycles = if ((t % 2) == 0) 0 else 1
        for (_ <- 0 until shortIpgIdleCycles) {
          stepXgmii(xgmiiIdleWord)
          checkAlign()
        }

        stepXgmii(xgmiiStartWord)
        checkAlign()

        stepXgmii(xgmiiSfdWord)
        checkAlign()

        for (_ <- 0 until 4) {
          stepXgmii(dataWordA)
          checkAlign()
        }

        stepXgmii(xgmiiTermWord)
        checkAlign()

        for (_ <- 0 until 20) {
          stepXgmii(xgmiiIdleWord)
          checkAlign()
        }
      }

      val bbErr1 = dut.io.bb_rx_error_count.peek().litValue.toInt
      val chErr1 = dut.io.ch_rx_error_count.peek().litValue.toInt

      progInfo(tn, s"trials=$trials mismatchHits=$mismatchHits")
      progInfo(tn, s"bb flags: badHits=$bbBadHits seqHits=$bbSeqHits")
      progInfo(
        tn,
        s"error_count: bb start=$bbErr0 max=$bbMaxErr end=$bbErr1, ch start=$chErr0 max=$chMaxErr end=$chErr1"
      )
      progInfo(tn, s"xgmii: compared=$xgmiiCompared mismatches=$xgmiiMismatches")

      mismatchHits shouldBe 0
      chErr1 shouldBe bbErr1
      xgmiiMismatches shouldBe 0
    }
  }

  it should "PCS Coverage: TERM appears in all possible lanes (0..7) and Chisel matches BB" in {
    val tn = 15
    val testName = "PCS Coverage: TERM appears in all possible lanes (0..7) and Chisel matches BB"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      for (_ <- 0 until 40) {
        driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
        dut.clock.step(1)
      }

      def payloadLenForLane(lane: Int, base: Int = 32): Int = {
        val needMod = (lane - 1 + 8) % 8
        val baseAligned = (base / 8) * 8
        baseAligned + needMod
      }

      var totalCompared = 0
      var totalMismatches = 0

      val seenTermLane = Array.fill(8)(false)

      for (lane <- 0 until 8) {
        val payloadLen = payloadLenForLane(lane, base = 32)
        val payload = (0 until payloadLen).map(i => (i * 7 + lane) & 0xFF)
        val words = buildFrameWords(payload)

        val (compared, mismatches) = driveFrameAndCompare(dut, words)
        totalCompared += compared
        totalMismatches += mismatches

        for (_ <- 0 until 40) {
          driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
          dut.clock.step(1)

          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean

          if (bbValid && chValid) {
            totalCompared += 1

            val bbRxd = dut.io.bb_rxd.peek()
            val bbRxc = dut.io.bb_rxc.peek()

            dut.io.chisel_rxd.expect(bbRxd)
            dut.io.chisel_rxc.expect(bbRxc)

            val rxd = bbRxd.litValue
            val rxc = bbRxc.litValue
            for (l <- 0 until 8) {
              val byte = (rxd >> (8 * l)) & 0xFF
              val ctrl = (rxc >> l) & 0x1
              if (ctrl == 1 && byte == xgmiiTerm) {
                seenTermLane(l) = true
              }
            }
          }
        }

        progInfo(
          tn,
          s"termLaneTarget=$lane payloadLen=$payloadLen compared=$compared mismatches=$mismatches"
        )
      }

      progInfo(
        tn,
        s"TOTAL compared=$totalCompared mismatches=$totalMismatches termSeen=${seenTermLane.mkString("[", ",", "]")}"
      )

      totalCompared should be > 50
      totalMismatches shouldBe 0
      seenTermLane.forall(_ == true) shouldBe true
    }
  }

  it should "PCS Coverage: randomized Ethernet frames (var length/IFG) and Chisel matches BB" in {
    val tn = 16
    val testName = "PCS Coverage: randomized Ethernet frames (var length/IFG) and Chisel matches BB"

    withDut(tn, testName) { dut =>
      initDut(dut, tn)
      waitForLock(dut, tn)

      val rnd = new scala.util.Random(0xC0FFEE)

      def randByte(): Int = rnd.nextInt(256)

      for (_ <- 0 until 100) {
        driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
        dut.clock.step(1)
      }

      val frames = 500
      var totalCompared = 0
      var totalMismatches = 0

      val seenTermLane = Array.fill(8)(false)

      for (f <- 0 until frames) {
        val payloadLen =
          if (f < 16) f
          else rnd.nextInt(512)

        val payload = Seq.fill(payloadLen)(randByte())
        val words = buildFrameWords(payload)

        val (compared, mismatches) = driveFrameAndCompare(dut, words)
        totalCompared += compared
        totalMismatches += mismatches

        val ifg = rnd.nextInt(11)
        for (_ <- 0 until ifg) {
          driveXgmii(dut, xgmiiIdleWord._1, xgmiiIdleWord._2, valid = true)
          dut.clock.step(1)

          val bbValid = dut.io.bb_rx_valid.peek().litToBoolean
          val chValid = dut.io.chisel_rx_valid.peek().litToBoolean
          if (bbValid && chValid) {
            val rxd = dut.io.bb_rxd.peek().litValue
            val rxc = dut.io.bb_rxc.peek().litValue
            for (l <- 0 until 8) {
              val byte = (rxd >> (8 * l)) & 0xFF
              val ctrl = (rxc >> l) & 0x1
              if (ctrl == 1 && byte == xgmiiTerm) {
                seenTermLane(l) = true
              }
            }
          }
        }
      }

      progInfo(
        tn,
        s"frames=$frames totalCompared=$totalCompared totalMismatches=$totalMismatches"
      )
      progInfo(
        tn,
        s"random term lane coverage=${seenTermLane.mkString("[", ",", "]")}"
      )

      totalCompared should be > 500
      totalMismatches shouldBe 0
    }
  }
}