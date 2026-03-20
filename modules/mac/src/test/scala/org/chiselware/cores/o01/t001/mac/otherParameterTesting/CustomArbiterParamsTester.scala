package org.chiselware.cores.o01.t001.mac.stats

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CustomArbiterParamsTester extends AnyFlatSpec with Matchers {

  behavior of "CustomArbiterParams"

  it should "construct with default values" in {
    val p = CustomArbiterParams()
    p.ports shouldBe 2
    p.arbRoundRobin shouldBe true
    p.arbBlock shouldBe true
    p.arbBlockAck shouldBe true
    p.lsbHighPrio shouldBe false
  }

  it should "provide simulation and synthesis configs" in {
    CustomArbiterParams.simConfigMap should contain key "config"
    CustomArbiterParams.synConfigMap should contain key "custom_arbiter_inst"
    CustomArbiterParams.synConfigs should include ("custom_arbiter_inst")
  }
}