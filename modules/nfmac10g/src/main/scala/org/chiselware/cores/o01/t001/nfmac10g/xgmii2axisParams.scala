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
case class Xgmii2AxisParams() {
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
object Xgmii2AxisParams {
  val simConfigMap = LinkedHashMap[String, Xgmii2AxisParams](
    "config" -> Xgmii2AxisParams()
  )

  val synConfigMap = LinkedHashMap[String, Xgmii2AxisParams](
    "small_1" -> Xgmii2AxisParams()
  )

  // Extract config names into a space-separated string
  val synConfigs = Xgmii2AxisParams.synConfigMap
    .map { case (configName, config) => s"$configName" }
    .mkString(" ")
}