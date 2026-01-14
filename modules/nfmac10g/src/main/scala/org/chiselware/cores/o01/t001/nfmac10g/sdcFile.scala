package org.chiselware.cores.o01.t001.nfmac10g

import java.io.{File, PrintWriter}

/** Customize this companion object with your port list and desired synthesis
  * contraints.
  */
object SdcFile {
  def create(sdcDirPath: String, topName: String): Unit = {
    // Default constraints, tighten or loosen as necessary
    val period = 6.400 // ns
    val dutyCycle = 0.50
    val inputDelayPct = 0.2
    val outputDelayPct = 0.2

    // Calculated constraints, customize as needed in SdcFileData
    val inputDelay = period * inputDelayPct
    val outputDelay = period * outputDelayPct
    val fallingEdge = period * dutyCycle

    val sdcFileData =
      s"""
         |create_clock -period $period -waveform {0 $fallingEdge} clock
         |# TODO: replace with real port names for $topName
         |# set_input_delay  -clock clock $inputDelay  [all_inputs]
         |# set_output_delay -clock clock $outputDelay [all_outputs]
         |""".stripMargin.trim

    val dir = new File(sdcDirPath)
    dir.mkdirs()

    val outFile = new File(dir, s"$topName.sdc")
    println(s"Writing SDC file to ${outFile.getAbsolutePath}")

    val pw = new PrintWriter(outFile)
    try pw.write(sdcFileData) finally pw.close()
  }
}
