package org.chiselware.cores.o01.t001.pcs
import chisel3._

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
  // Simulate the LFSR shifting at elaboration time (Scala)
  // to build the XOR dependencies for the hardware.

  // 1. Initialize dependency trackers using immutable vals via scanLeft/foldLeft
  // vState(i) tracks which input state bits affect bit i
  // vData(i) tracks which input data bits affect bit i

  var vState = Array.tabulate(lfsrW)(i => (BigInt(1) << i))
  var vData = Array.fill(lfsrW)(BigInt(0))

  // Output trackers
  var vOutState = Array.fill(dataW)(BigInt(0))
  var vOutData = Array.fill(dataW)(BigInt(0))

  // 2. Simulate the LFSR shifting loop 'dataW' times
  for (k <- 0 until dataW) {
    val dataIdx =
      if (reverse)
        k
      else
        dataW - 1 - k

    // Current MSB used for feedback
    var stateVal = vState(lfsrW - 1)
    var dataValEq = vData(lfsrW - 1)

    // Add input data dependency (XOR)
    dataValEq = dataValEq ^ (BigInt(1) << dataIdx)

    if (lfsrGalois) {
      // --- Galois Configuration ---
      for (j <- lfsrW - 1 until 0 by -1) {
        vState(j) = vState(j - 1)
        vData(j) = vData(j - 1)
      }
      for (j <- dataW - 1 until 0 by -1) {
        vOutState(j) = vOutState(j - 1)
        vOutData(j) = vOutData(j - 1)
      }

      vOutState(0) = stateVal
      vOutData(0) = dataValEq

      if (lfsrFeedForward) {
        stateVal = 0
        dataValEq = (BigInt(1) << dataIdx)
      }

      vState(0) = stateVal
      vData(0) = dataValEq

      for (j <- 1 until lfsrW) {
        if (((lfsrPoly >> j) & 1) == 1) {
          vState(j) = vState(j) ^ stateVal
          vData(j) = vData(j) ^ dataValEq
        }
      }

    } else {
      // --- Fibonacci Configuration ---
      for (j <- 1 until lfsrW) {
        if (((lfsrPoly >> j) & 1) == 1) {
          stateVal = stateVal ^ vState(j - 1)
          dataValEq = dataValEq ^ vData(j - 1)
        }
      }

      for (j <- lfsrW - 1 until 0 by -1) {
        vState(j) = vState(j - 1)
        vData(j) = vData(j - 1)
      }
      for (j <- dataW - 1 until 0 by -1) {
        vOutState(j) = vOutState(j - 1)
        vOutData(j) = vOutData(j - 1)
      }

      vOutState(0) = stateVal
      vOutData(0) = dataValEq

      if (lfsrFeedForward) {
        stateVal = 0
        dataValEq = (BigInt(1) << dataIdx)
      }

      vState(0) = stateVal
      vData(0) = dataValEq
    }
  }

  // --- 3. Generate Hardware Logic from Masks ---

  // State Output
  val nextState = Wire(Vec(lfsrW, Bool()))
  for (i <- 0 until lfsrW) {
    val maskI =
      if (reverse)
        lfsrW - 1 - i
      else
        i

    val stateContrib = (0 until lfsrW)
      .filter(b => ((vState(maskI) >> b) & 1) == 1)
      .map(b =>
        io.stateIn(if (reverse)
          lfsrW - 1 - b
        else
          b)
      )

    val dataContrib = (0 until dataW)
      .filter(b => ((vData(maskI) >> b) & 1) == 1)
      .map(b => io.dataIn(b))

    val allContribs =
      stateContrib ++
        (if (dataInEn)
           dataContrib
         else
           Seq())

    if (allContribs.nonEmpty)
      nextState(i) := allContribs.reduce(_ ^ _)
    else
      nextState(i) := false.B
  }
  io.stateOut := nextState.asUInt

  // Data Output
  val dataOutWire = Wire(Vec(dataW, Bool()))
  for (i <- 0 until dataW) {
    val maskIdx =
      if (reverse)
        dataW - 1 - i
      else
        i

    val sMask = vOutState(maskIdx)
    val dMask = vOutData(maskIdx)

    val stateContrib = (0 until lfsrW)
      .filter(b => ((sMask >> b) & 1) == 1)
      .map(b =>
        io.stateIn(if (reverse)
          lfsrW - 1 - b
        else
          b)
      )

    val dataContrib = (0 until dataW)
      .filter(b => ((dMask >> b) & 1) == 1)
      .map(b => io.dataIn(b))

    val allContribs =
      stateContrib ++
        (if (dataInEn)
           dataContrib
         else
           Seq())

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

object Lfsr {
  def apply(p: LfsrParams): Lfsr = Module(new Lfsr(
    lfsrW = p.lfsrW,
    lfsrPoly = p.lfsrPoly,
    lfsrGalois = p.lfsrGalois,
    lfsrFeedForward = p.lfsrFeedForward,
    reverse = p.reverse,
    dataW = p.dataW,
    dataInEn = p.dataInEn,
    dataOutEn = p.dataOutEn
  ))
}

// object Main extends App {
//   val MainClassName = "Pcs"
//   val coreDir = s"modules/${MainClassName.toLowerCase()}"
//   LfsrParams.SynConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new Lfsr(
//         lfsrW = p.lfsrW, lfsrPoly = p.lfsrPoly, lfsrGalois = p.lfsrGalois,
//         lfsrFeedForward = p.lfsrFeedForward, reverse = p.reverse, dataW = p.dataW,
//         dataInEn = p.dataInEn, dataOutEn = p.dataOutEn
//       ),
//       firtoolOpts = Array(
//         "--lowering-options=disallowLocalVariables,disallowPackedArrays",
//         "--disable-all-randomization",
//         "--strip-debug-info",
//         "--split-verilog",
//         s"-o=${coreDir}/generated/synTestCases/$configName"
//       )
//     )
//     SdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(mainClassName = MainClassName, outputDir = s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(mainClassName = MainClassName, outputDir = s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(mainClassName = MainClassName, synConfigs = LfsrParams.SynConfigs, outputDir = s"${coreDir}/generated/synTestCases")
//   }
// }