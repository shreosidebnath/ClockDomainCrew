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
case class Agmii2XgmiiParams() {
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
object Axis2XgmiiParams {
  val simConfigMap = LinkedHashMap[String, Axis2XgmiiParams](
    "config" -> Axis2XgmiiParams()
  )

  val synConfigMap = LinkedHashMap[String, Axis2XgmiiParams](
    "small_1" -> Axis2XgmiiParams()
  )

  // Extract config names into a space-separated string
  val synConfigs = Axis2XgmiiParams.synConfigMap
    .map { case (configName, config) => s"$configName" }
    .mkString(" ")
}