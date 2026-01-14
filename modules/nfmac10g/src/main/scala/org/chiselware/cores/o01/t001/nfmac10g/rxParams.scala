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
case class RxParams() {
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
object RxParams {
  val simConfigMap = LinkedHashMap[String, RxParams](
    "config" -> RxParams()
  )

  val synConfigMap = LinkedHashMap[String, RxParams](
    "small_1" -> RxParams()
  )

  // Extract config names into a space-separated string
  val synConfigs = RxParams.synConfigMap
    .map { case (configName, config) => s"$configName" }
    .mkString(" ")
}
