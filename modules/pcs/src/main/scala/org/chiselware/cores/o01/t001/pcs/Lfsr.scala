// SPDX-License-Identifier: CERN-OHL-S-2.0
/*
Copyright (c) 2015-2025 FPGA Ninja, LLC
Authors:
- Alex Forencich

Modifications:
Copyright (c) 2026 ClockDomainCrew
University of Calgary – Schulich School of Engineering
*/
package org.chiselware.cores.o01.t001.pcs
import chisel3._


/** Parametric Linear Feedback Shift Register (LFSR)
  * * This module calculates the LFSR matrix at elaboration time to generate 
  * the optimal XOR-based hardware implementation for the next state.
  *
  * @param lfsrW width of the LFSR state
  * @param lfsrPoly polynomial representation (BigInt)
  * @param lfsrGalois if true, uses Galois configuration; otherwise Fibonacci
  * @param lfsrFeedForward enables feedforward logic for CRC-like operations
  * @param reverse mirrors the bit ordering of the input/output
  * @author ClockDomainCrew
  */
class Lfsr(
    val lfsrW: Int = 31,
    val lfsrPoly: BigInt = BigInt("10000001", 16),
    val lfsrGalois: Boolean = false,
    val lfsrFeedForward: Boolean = false,
    val reverse: Boolean = false,
    val dataW: Int = 8,
    val dataInEn: Boolean = true,
    val dataOutEn: Boolean = true) extends RawModule {
  val io = IO(new Bundle {
    val dataIn = Input(UInt(dataW.W))
    val stateIn = Input(UInt(lfsrW.W))
    val dataOut = Output(UInt(dataW.W))
    val stateOut = Output(UInt(lfsrW.W))
  })

  // --- Procedural Generation of Next State Logic (Matrix Calculation) ---
  // The LFSR shifting is done at elaboration time
  // to build the XOR dependencies for the hardware.

  // Initialize dependency trackers
  // vState[i] tracks which input state bits affect bit i
  // vData[i] tracks which input data bits affect bit i
  case class LfsrSimState(
      vState: Array[BigInt],
      vData: Array[BigInt],
      vOutState: Array[BigInt],
      vOutData: Array[BigInt])

  val initState = LfsrSimState(
    vState = Array.tabulate(lfsrW)(i => (BigInt(1) << i)),
    vData = Array.fill(lfsrW)(BigInt(0)),
    vOutState = Array.fill(dataW)(BigInt(0)),
    vOutData = Array.fill(dataW)(BigInt(0))
  )

  // Simulate the LFSR shifting loop 'dataW' times
  val simResult = (0 until dataW).foldLeft(initState) { (s, k) =>
      val dataIdx = if (reverse) k else (dataW - 1 - k)
      val stateVal0 = s.vState(lfsrW - 1)
      val dataValEq0 = s.vData(lfsrW - 1) ^ (BigInt(1) << dataIdx)

      if (lfsrGalois) {
        // --- Galois Configuration ---
        // Shift registers
        val newVState =
          Array.tabulate(lfsrW)(j =>
            if (j == 0)
              stateVal0
            else
              s.vState(j - 1)
          )
        val newVData =
          Array.tabulate(lfsrW)(j =>
            if (j == 0)
              dataValEq0
            else
              s.vData(j - 1)
          )
        // Shift output capture
        val newVOutState =
          Array.tabulate(dataW)(j =>
            if (j == 0)
              stateVal0
            else
              s.vOutState(j - 1)
          )
        val newVOutData =
          Array.tabulate(dataW)(j =>
            if (j == 0)
              dataValEq0
            else
              s.vOutData(j - 1)
          )

        // Output logic
        val (ffStateVal, ffDataValEq) =
          if (lfsrFeedForward)
            (BigInt(0), BigInt(1) << dataIdx)
          else
            (stateVal0, dataValEq0)

        // Galois Taps
        val tappedVState = newVState.clone()
        val tappedVData = newVData.clone()
        for (j <- 1 until lfsrW) {
          if (((lfsrPoly >> j) & 1) == 1) {
            tappedVState(j) = tappedVState(j) ^ ffStateVal
            tappedVData(j) = tappedVData(j) ^ ffDataValEq
          }
        }
        tappedVState(0) = ffStateVal
        tappedVData(0) = ffDataValEq

        LfsrSimState(tappedVState, tappedVData, newVOutState, newVOutData)

      } else {
        // --- Fibonacci Configuration ---
        // Calculate feedback from taps
        val (
          fbStateVal,
          fbDataValEq
        ) = (1 until lfsrW).foldLeft((stateVal0, dataValEq0)) {
          case ((sv, dv), j) =>
            if (((lfsrPoly >> j) & 1) == 1)
              (sv ^ s.vState(j - 1), dv ^ s.vData(j - 1))
            else
              (sv, dv)
        }

        // Shift
        val newVOutState =
          Array.tabulate(dataW)(j =>
            if (j == 0)
              fbStateVal
            else
              s.vOutState(j - 1)
          )
        val newVOutData =
          Array.tabulate(dataW)(j =>
            if (j == 0)
              fbDataValEq
            else
              s.vOutData(j - 1)
          )

        val (ffStateVal, ffDataValEq) =
          if (lfsrFeedForward)
            (BigInt(0), BigInt(1) << dataIdx)
          else
            (fbStateVal, fbDataValEq)

        val newVState =
          Array.tabulate(lfsrW)(j =>
            if (j == 0)
              ffStateVal
            else
              s.vState(j - 1)
          )
        val newVData =
          Array.tabulate(lfsrW)(j =>
            if (j == 0)
              ffDataValEq
            else
              s.vData(j - 1)
          )

        LfsrSimState(newVState, newVData, newVOutState, newVOutData)
      }
  }

  // Generate Hardware Logic from Masks ---

  // State Output
  val nextState = Wire(Vec(n = lfsrW, gen = Bool()))
  for (i <- 0 until lfsrW) {
    // If reverse is true, we need to map to the inverted mask index
    val maskI = if (reverse) (lfsrW - 1 - i) else i

    val stateContrib = (0 until lfsrW)
      .filter(b => ((simResult.vState(maskI) >> b) & 1) == 1)
      // Mirror the state_in pin mapping if reversed
      .map(b =>
        io.stateIn(if (reverse) (lfsrW - 1 - b) else b)
      )

    val dataContrib = (0 until dataW)
      .filter(b => ((simResult.vData(maskI) >> b) & 1) == 1)
      .map(b => io.dataIn(b))

    val allContribs = stateContrib ++ (if (dataInEn) dataContrib else Seq())

    if (allContribs.nonEmpty)
      nextState(i) := allContribs.reduce(_ ^ _)
    else
      nextState(i) := false.B
  }
  io.stateOut := nextState.asUInt

  // Data Output
  val dataOutWire = Wire(Vec(n = dataW, gen = Bool()))
  for (i <- 0 until dataW) {
    val maskIdx = if (reverse) (dataW - 1 - i) else i
    val sMask = simResult.vOutState(maskIdx)
    val dMask = simResult.vOutData(maskIdx)

    val stateContrib = (0 until lfsrW)
      .filter(b => ((sMask >> b) & 1) == 1)
      // Mirror the state_in pin mapping if reversed
      .map(b =>
        io.stateIn(if (reverse) (lfsrW - 1 - b) else b)
      )

    val dataContrib = (0 until dataW)
      .filter(b => ((dMask >> b) & 1) == 1)
      .map(b => io.dataIn(b))

    val allContribs = stateContrib ++ (if (dataInEn) dataContrib else Seq())

    if (dataOutEn) {
      if (allContribs.nonEmpty)
        dataOutWire(i) := allContribs.reduce(_ ^ _)
      else
        dataOutWire(i) := false.B
    } else {
      dataOutWire(i) := false.B
    }
  }
  io.dataOut := dataOutWire.asUInt
}