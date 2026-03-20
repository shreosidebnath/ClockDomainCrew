package org.chiselware.cores.o01.t001.pcs

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PcsParamsTester extends AnyFlatSpec with Matchers {

  behavior of "PcsParams"

  it should "accept valid default parameters" in {
    noException should be thrownBy {
      PcsParams()
    }
  }

  it should "reject invalid dataW" in {
    val ex = intercept[IllegalArgumentException] {
      PcsParams(dataW = 48, ctrlW = 6)
    }
    ex.getMessage should include ("Interface width must be 32 or 64")
  }

  it should "reject ctrlW that does not match dataW byte granularity" in {
    val ex = intercept[IllegalArgumentException] {
      PcsParams(dataW = 64, ctrlW = 7)
    }
    ex.getMessage should include ("Interface requires byte (8-bit) granularity")
  }

  it should "reject invalid hdrW" in {
    val ex = intercept[IllegalArgumentException] {
      PcsParams(dataW = 64, ctrlW = 8, hdrW = 1)
    }
    ex.getMessage should include ("Interface requires 2-bit sync header")
  }

  it should "accept valid 32-bit configuration" in {
    noException should be thrownBy {
      PcsParams(dataW = 32, ctrlW = 4, hdrW = 2)
    }
  }
}