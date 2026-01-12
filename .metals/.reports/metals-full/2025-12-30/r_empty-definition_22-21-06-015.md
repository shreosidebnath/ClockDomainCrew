error id: file:///C:/Users/benja/ethernet-mac-chisel/src/main/MacDefinitions.scala:`<none>`.
file:///C:/Users/benja/ethernet-mac-chisel/src/main/MacDefinitions.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -chisel3/Cat.
	 -chisel3/Cat#
	 -chisel3/Cat().
	 -chisel3/util/Cat.
	 -chisel3/util/Cat#
	 -chisel3/util/Cat().
	 -Cat.
	 -Cat#
	 -Cat().
	 -scala/Predef.Cat.
	 -scala/Predef.Cat#
	 -scala/Predef.Cat().
offset: 320
uri: file:///C:/Users/benja/ethernet-mac-chisel/src/main/MacDefinitions.scala
text:
```scala
package ethernet

import chisel3._
import chisel3.util._

object MacDefinitions {
  
  val S = 0xFB.U(8.W)    
  val T = 0xFD.U(8.W)    
  val E = 0xFE.U(8.W)
  val I = 0x07.U(8.W)
  
  
  val PREAMBLE_LANE0_D = Cat("hD5555555555555".U(56.W), S)
  val PREAMBLE_LANE0_C = 0x01.U(8.W)
  
  val QW_IDLE_D = @@Cat(Seq.fill(8)(I).reduce(_##_))  
  val QW_IDLE_C = 0xFF.U(8.W)
  
  
  val CRC_802_3_PRESET = "hFFFFFFFF".U(32.W)
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.