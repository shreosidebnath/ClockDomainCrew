package org.chiselware.cores.o01.t001.mac

case class AxisInterfaceParams(
    dataW: Int,
    keepW: Int,
    keepEn: Boolean,
    strbEn: Boolean,
    lastEn: Boolean,
    idEn: Boolean,
    idW: Int,
    destEn: Boolean,
    destW: Int,
    userEn: Boolean,
    userW: Int)

object AxisInterfaceParams {
  def apply(
      dataW: Int = 8,
      keepW: Int = -1, // -1 means auto-calculate
      keepEn: Boolean = true,
      strbEn: Boolean = false,
      lastEn: Boolean = true,
      idEn: Boolean = false,
      idW: Int = 8,
      destEn: Boolean = false,
      destW: Int = 8,
      userEn: Boolean = false,
      userW: Int = 1
    ): AxisInterfaceParams = {
    // Calculate keepW before actually creating the object
    val calculatedKeepW =
      if (keepW == -1)
        ((dataW + 7) / 8)
      else
        keepW

    // Return the new case class with the finalized values
    new AxisInterfaceParams(
      dataW  = dataW,
      keepW  = calculatedKeepW,
      keepEn = keepEn,
      strbEn = strbEn,
      lastEn = lastEn,
      idEn   = idEn,
      idW    = idW,
      destEn = destEn,
      destW  = destW,
      userEn = userEn,
      userW  = userW
    )
  }
}