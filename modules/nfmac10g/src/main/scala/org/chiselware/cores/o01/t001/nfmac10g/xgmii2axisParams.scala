// (c) <year> <your name or company>
// This code is licensed under the <name of license> (see LICENSE.MD)

package org.chiselware.cores.o01.t001.nfmac10g

import chisel3._
import chisel3.util._
import scala.collection.mutable.LinkedHashMap
import java.io.{File, PrintWriter}

/** Default parameter settings for RstMod.
  *
  * @constructor
  *   default parameter settings
  * @author
  *   Warren Savage
  * @see
  *   [[http://www.mycompany.com]] for more information
  */
case class Xgmii2axisParams() {
}

/** Define a companion object to hold a Map of the configurations and the order
  * in which to be tested.
  *
  * Provide a meaningful configuration name for each set of parameter
  * configurations.
  * ```
  *  For sim, use lowerCamel case conventions: myConfig1, etc.
  *  For syn, use snake_case, typical for Verilog: my_config_1
  * ```
  */
object Xgmii2axisParams {
  val simConfigMap = LinkedHashMap[String, Xgmii2AxisParams](
    "config" -> Xgmii2axisParams()
  )

  val synConfigMap = LinkedHashMap[String, Xgmii2AxisParams](
    "small_1" -> Xgmii2AxisParams()
  )

  // Extract config names into a space-separated string
  val synConfigs = Xgmii2AxisParams.synConfigMap
    .map { case (configName, config) => s"$configName" }
    .mkString(" ")
}

/** Customize this companion object with your port list and desired synthesis
  * contraints.
  */

object sdcFile {
  def create(sdcFilePath: String): Unit = {
    // Default constraints, tighten or loosen as necessary
    val period = 6.400 // ns
    val dutyCycle = 0.50
    val inputDelayPct = 0.2
    val outputDelayPct = 0.2

    // Calculated constraints, customize as needed in SdcFileData
    val inputDelay = period * inputDelayPct
    val outputDelay = period * outputDelayPct
    val fallingEdge = period * dutyCycle

    val sdcFileData = s"""
      |create_clock -period $period -waveform {0 $fallingEdge} clock
      |set_input_delay -clock clock $inputDelay {reset}
      |set_input_delay -clock clock $inputDelay {dcm_locked}
      |set_output_delay -clock clock $outputDelay {rst}
    """.stripMargin.trim

    println(s"Writing SDC file to $sdcFilePath")
    val sdcFileDir = new File(sdcFilePath)
    sdcFileDir.mkdirs()
    val sdcFileName = new File(s"$sdcFilePath/RstMod.sdc")
    val sdcFile = new PrintWriter(sdcFileName)
    sdcFile.write(s"${sdcFileData}")
    sdcFile.close()
  }
}
