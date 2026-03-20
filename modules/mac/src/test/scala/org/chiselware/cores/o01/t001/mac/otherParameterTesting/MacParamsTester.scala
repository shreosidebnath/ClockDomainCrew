package org.chiselware.cores.o01.t001.mac

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MacParamsTester extends AnyFlatSpec with Matchers {

  behavior of "MacParams"

  it should "allow valid 64-bit XGMII parameters" in {
    val p = MacParams(dataW = 64, ctrlW = 8)
    p.dataW shouldBe 64
    p.ctrlW shouldBe 8
  }

  it should "reject dataW values other than 64" in {
    val ex = the[IllegalArgumentException] thrownBy {
      MacParams(dataW = 32, ctrlW = 4)
    }
    ex.getMessage should include("Interface width must be 64")
  }

  it should "reject ctrlW values that are not byte-aligned to dataW" in {
    val ex = the[IllegalArgumentException] thrownBy {
      MacParams(dataW = 64, ctrlW = 7)
    }
    ex.getMessage should include("Interface requires byte (8-bit) granularity")
  }
}