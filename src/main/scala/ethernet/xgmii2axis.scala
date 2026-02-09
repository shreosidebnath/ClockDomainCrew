package ethernet
import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import ethernet.MacDefinitions._

class XGMII2AXIS extends Module {
  val io = IO(new Bundle {
  val clk   = Input(Clock())
  val rst   = Input(Bool())
  val aresetn = Input(Bool())

  val xgmiiD = Input(UInt(64.W))
  val xgmiiC = Input(UInt(8.W))

  val configurationVector = Input(UInt(80.W))

  val tdata  = Output(UInt(64.W))
  val tkeep  = Output(UInt(8.W))
  val tvalid = Output(Bool())
  val tlast  = Output(Bool())
  val tuser  = Output(UInt(1.W))

  val goodFrames = Output(UInt(32.W))
  val badFrames  = Output(UInt(32.W))

  val rxStatisticsVector = Output(UInt(30.W))
  val rxStatisticsValid  = Output(Bool())
})

  val macDefs = Module(new EthMacDefinitions)
    val xgmiiStart = macDefs.S  
    val xgmiiTerm = macDefs.T   
    val xgmiiError = macDefs.E  
    val xgmiiIdle = macDefs.I   

  def sofLane0(d: UInt, c: UInt): Bool = macDefs.SofLane0(d, c)
  def sofLane4(d: UInt, c: UInt): Bool = macDefs.SofLane4(d, c)
  def isTchar(data: UInt): Bool = macDefs.IsTchar(data)
  def crc8B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc8B(crc, data)
  def crc7B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc7B(crc, data)
  def crc6B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc6B(crc, data)
  def crc5B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc5B(crc, data)
  def crc4B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc4B(crc, data)
  def crc3B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc3B(crc, data)
  def crc2B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc2B(crc, data)
  def crc1B(crc: UInt, data: UInt): UInt = macDefs.EthernetCrc.crc1B(crc, data)

  def crcRev(crc: UInt): UInt = macDefs.CrcRev(crc)

    
  
 


def setStats(byteCount: UInt, crcOk: Bool): Unit = {
  // Set statistics valid flag
  rxStatisticsValidReg := true.B

  // Initialize statistics vector to all zeros
  val statsVec = WireInit(0.U(30.W))

  // Determine frame size category (per IEEE 802.3 statistics)
  when(byteCount < 64.U) {
    // Small frame (< 64 bytes)
    statsVec := statsVec | (1.U << STAT_RX_SMALL)
    when(crcOk) {
      // Good small frame = undersize
      statsVec := statsVec | (1.U << STAT_RX_UNDERSIZE)
    }.otherwise {
      // Bad small frame = fragment
      statsVec := statsVec | (1.U << STAT_RX_FRAGMENT)
    }
    }.elsewhen(byteCount === 64.U) {
      statsVec := statsVec | (1.U << STAT_RX_64B)
    }.elsewhen(byteCount <= 127.U) {
      statsVec := statsVec | (1.U << STAT_RX_65_127B)
    }.elsewhen(byteCount <= 255.U) {
      statsVec := statsVec | (1.U << STAT_RX_128_255B)
    }.elsewhen(byteCount <= 511.U) {
      statsVec := statsVec | (1.U << STAT_RX_256_511B)
    }.elsewhen(byteCount <= 1023.U) {
      statsVec := statsVec | (1.U << STAT_RX_512_1023B)
    }.elsewhen(byteCount <= 1518.U) {
      statsVec := statsVec | (1.U << STAT_RX_1024_1518B)
    }.elsewhen(byteCount <= 1522.U) {
      statsVec := statsVec | (1.U << STAT_RX_1519_1522B)
    }.elsewhen(byteCount <= 1548.U) {
      statsVec := statsVec | (1.U << STAT_RX_1523_1548B)
    }.elsewhen(byteCount <= 2047.U) {
      statsVec := statsVec | (1.U << STAT_RX_1549_2047B)
    }.elsewhen(byteCount > 2047.U) {
      when(byteCount <= RX_MTU) {
        statsVec := statsVec | (1.U << STAT_RX_2048_MAX)
      }
    }

  // Set good packet flag if CRC is OK
  when(crcOk) {
    statsVec := statsVec | (1.U << STAT_RX_GOOD_PKT)
  }

  // Handle oversize/jabber classification
  when(byteCount > 1518.U) {
    when(crcOk) {
      statsVec := statsVec | (1.U << STAT_RX_OVERSIZE)
    }.otherwise {
      statsVec := statsVec | (1.U << STAT_RX_JABBER)
    }
  }

  // Store octet count in bits [29:16] of statistics vector
  val octetCount = byteCount(13, 0)
  statsVec := statsVec | (octetCount << STAT_RX_OCTETS_HIGH)

  // Update the statistics vector register
  rxStatisticsVectorReg := statsVec //u
}
  }
  

    val tvalidReg = RegInit(false.B)
    val tdataReg = RegInit(0.U(64.W))
    val tkeepReg = RegInit(0.U(8.W))
    val tlastReg = RegInit(false.B)
    val tuserReg = RegInit(0.U(1.W))
    val synch = RegInit(false.B)

    val goodFramesReg = RegInit(0.U(32.W))
    val badFramesReg = RegInit(0.U(32.W))
    val rxStatisticsVectorReg = RegInit(0.U(30.W))
    val rxStatisticsValidReg = RegInit(false.B)

    val stateReg = RegInit(State.SRES) //fsn is stateReg
    val tvalidI = RegInit(false.B)
    val tdataI = RegInit(0.U(64.W))
    val tkeepI = RegInit(0.U(8.W))
    val lastTkeepI = RegInit(0.U(8.W))
    val tlastI = RegInit(false.B)
    val tuserI = RegInit(0.U(1.W))

    val tdataD0 = RegInit(0.U(64.W))
    val tvalidD0 = RegInit(false.B)
    val dReg = RegInit(0.U(64.W))
    val cReg = RegInit(0.U(8.W))
    val inboundFrame = RegInit(false.B)
    val len = RegInit(0.U(16.W))
    val auxDw = RegInit(0.U(32.W))
    val chkTchar = RegInit(false.B)

    val crc32_6B = RegInit(0.U(32.W))
    val crc32_5B = RegInit(0.U(32.W))
    val crc32_4B = RegInit(0.U(32.W))
    val crc32_3B = RegInit(0.U(32.W))
    val crc32_2B = RegInit(0.U(32.W))
    val crc32_1B = RegInit(0.U(32.W))
    val receivedCrc = RegInit(0.U(32.W))
    val calctedCrc = RegInit(0.U(32.W))

    val invAresetn = !io.aresetn
 
    when(invAresetn) {
      tvalidReg := false.B
      synch := false.B
    }.otherwise {
      when(!inboundFrame || synch) {
        synch := true.B
        tdataReg := tdataI
        tkeepReg := tkeepI
        tvalidReg := tvalidI
        tlastReg := tlastI
        tuserReg := tuserI
      }.otherwise {
        tvalidReg := false.B
      }
    }

    when(io.rst) {
     
      tvalidI := false.B
      stateReg := State.SRES
      rxStatisticsValidReg := false.B
      rxStatisticsVectorReg := 0.U
      
      auxDw := 0.U
      badFramesReg := 0.U
      cReg := 0.U
      calctedCrc := 0.U
      chkTchar := false.B
      crc32 := 0.U
      crc324B := 0.U
      crc325B := 0.U
      crc326B := 0.U
      crc327B := 0.U
      dReg := 0.U
      goodFramesReg := 0.U
      inboundFrame := false.B
      lastTkeepI := 0.U
      len := 0.U
      receivedCrc := 0.U
      tdataD0 := 0.U
      tdataI := 0.U
      tkeepI := 0.U
      tlastI := false.B
      tuserI := 0.U
      tvalidD0 := false.B
    }.otherwise {
     
      when(tvalidReg && tlastReg && tuserReg(0) === 0.U) {
        goodFramesReg := goodFramesReg + 1.U
      }
      
      when(tvalidReg && tlastReg && tuserReg(0) === 1.U) {
        badFramesReg := badFramesReg + 1.U
      }
        
   
tdataI := tdataD0
tvalidI := tvalidD0
rxStatisticsValidReg := false.B

// FSM is stateReg
switch(stateReg) {
  is(State.SRES) {
    goodFramesReg := 0.U
    badFramesReg := 0.U
    stateReg := State.IDLE
  }
  
  is(State.IDLE) {
    tvalidD0 := false.B
    tlastI := false.B
    tuserI := 0.U
    crc32 := CRC802_3_PRESET
    inboundFrame := false.B
    dReg := io.xgmii_d
    cReg := io.xgmii_c
    len := 0.U
    rxStatisticsVectorReg := 0.U
    
    when(sof_lane0(io.xgmii_d, io.xgmii_c)) {
      inboundFrame := true.B
      stateReg := State.ST_LANE0
    }.elsewhen(sof_lane4(io.xgmii_d, io.xgmii_c)) {
      inboundFrame := true.B
      stateReg := State.ST_LANE4
    }
  }
  
  is(State.ST_LANE0) {
    tdataD0 := io.xgmii_d
    tvalidD0 := true.B
    tkeepI := "hFF".U(8.W)
    tlastI := false.B
    tuserI := 0.U
    dReg := io.xgmii_d
    cReg := io.xgmii_c
    crc32 := crc8B(crc32, io.xgmii_d)
    crc32_7B := crc7B(crc32, io.xgmii_d(55, 0))
    crc32_6B := crc6B(crc32, io.xgmii_d(47, 0))
    crc32_5B := crc5B(crc32, io.xgmii_d(39, 0))
    crc32_4B := crc4B(crc32, io.xgmii_d(31, 0))
    
    switch(io.xgmii_c) {
      is("h00".U(8.W)) {
        len := len + 8.U
      }
      
      is("hFF".U(8.W)) {
        len := len  
        tkeepI := "h0F".U(8.W)  
        tvalidD0 := false.B
        tlastI := true.B
        
        // Check CRC and termination character
        val crcOk = (~crc_rev(crc32_4B) === dReg(63, 32)) && is_tchar(io.xgmii_d(7, 0))
        when(crcOk) {
          tuserI := 1.U(1.W)
          // Update stats
          rxStatisticsVectorReg := Cat(len, 1.U(14.W))  // Simplified
          rxStatisticsValidReg := true.B
        }.otherwise {
          // Update stats differently for bad frame
          rxStatisticsVectorReg := Cat(len, 0.U(14.W))
          rxStatisticsValidReg := true.B
        }
        
        stateReg := State.IDLE
      }
          
          // Termination in lane 1 (byte 1) - T in lane 1
          is("hFE".U(8.W)) {
            len := len + 1.U
            tkeepI := "h1F".U(8.W)  // First 5 bytes valid
            tvalidPipe0 := false.B
            tlastNext := true.B
            
            val crcOk = (~crc_rev(crc_32_5B) === Cat(io.xgmii_d(7, 0), dataReg(63, 40))) && 
                         is_tchar(io.xgmii_d(15, 8))
            when(crcOk) {
              tuserNext:= 1.U(1.W)
              rxStatisticsVectorReg := Cat(len, 1.U(14.W))
            }.otherwise {
              rxStatisticsVectorReg := Cat(len, 0.U(14.W))
            }
            rxStatisticsValidReg := true.B
            
            stateReg := State.IDLE
          }
          
          // Termination in lane 2 (byte 2) - T in lane 2
          is("hFC".U(8.W)) {
            len := len + 2.U
            tkeepI := "h3F".U(8.W)  // First 6 bytes valid
            tvalidPipe0 := false.B
            tlastNext := true.B
            
            val crcOk = (~crc_rev(crc_32_6B) === Cat(io.xgmii_d(15, 0), d_reg(63, 48))) && 
                         is_tchar(io.xgmii_d(23, 16))
            when(crcOk) {
              tuserNext:= 1.U(1.W)
              rxStatisticsVectorReg := Cat(len, 1.U(14.W))
            }.otherwise {
              rxStatisticsVectorReg := Cat(len, 0.U(14.W))
            }
            rxStatisticsValidReg := true.B
            
            stateReg := State.IDLE
          }
          
          // Termination in lane 3 (byte 3) - T in lane 3
          is("hF8".U(8.W)) {
            len := len + 3.U
            tkeepI:= "h7F".U(8.W)  // First 7 bytes valid
            tvalidPipe0 := false.B
            tlastNext := true.B
            
            val crcOk = (~crc_rev(crc_32_7B) === Cat(io.xgmii_d(23, 0), d_reg(63, 56))) && 
                         is_tchar(io.xgmii_d(31, 24))
            when(crcOk) {
              tuserNext:= 1.U(1.W)
              rxStatisticsVectorReg := Cat(len, 1.U(14.W))
            }.otherwise {
              rxStatisticsVectorReg := Cat(len, 0.U(14.W))
            }
            rxStatisticsValidReg := true.B
            
            stateReg := State.IDLE
          }
          
          // Termination in lane 4 (byte 4) - T in lane 4
          is("hF0".U(8.W)) {
            len := len + 4.U
            tkeepI:= "hFF".U(8.W)  // All 8 bytes valid
            tvalidPipe0 := false.B
            tlastNext := true.B
            
            val crcOk = (~crc_rev(crc_32) === io.xgmii_d(31, 0)) && 
                         is_tchar(io.xgmii_d(39, 32))
            when(crcOk) {
              tuserNext:= 1.U(1.W)
              rxStatisticsVectorReg := Cat(len, 1.U(14.W))
            }.otherwise {
              rxStatisticsVectorReg := Cat(len, 0.U(14.W))
            }
            rxStatisticsValidReg := true.B
            
            stateReg := State.IDLE
          }
          
          // Termination in lane 5 (byte 5) - T in lane 5
          is("hE0".U(8.W)) {
            len := len + 5.U
            lastTkeepI := "h01".U(8.W)  // Only byte 0 valid in next cycle?
            receivedCrc := io.xgmii_d(39, 8)
            calculatedCrc := crc1B(crc_32, io.xgmii_d(7, 0))
            termCharValid := is_tchar(io.xgmii_d(47, 40))
            stateReg := State.FIN
          }
          
          // Termination in lane 6 (byte 6) - T in lane 6
          is("hC0".U(8.W)) {
            len := len + 6.U
            lastTkeepI := "h03".U(8.W)  // First 2 bytes valid
            receivedCrc := io.xgmii_d(47, 16)
            calculatedCrc := crc2B(crc_32, io.xgmii_d(15, 0))
            termCharValid := is_tchar(io.xgmii_d(55, 48))
            stateReg := State.FIN
          }
          
          // Termination in lane 7 (byte 7) - T in lane 7
          is("h80".U(8.W)) {
            receivedCrc    := io.xgmiiD(55, 24)
            calculatedCrc  := crc3B(crc32, io.xgmiiD(23, 0))
            termCharValid  := isTChar(io.xgmiiD(63, 56))
            stateReg       := State.FIN
          }
          
          // Default case (error or unexpected control pattern)
          default {
            tlastNext := true.B
            tvalidPipe0:= false.B
            tvalid_i := true.B
            stateReg := State.IDLE
            // Update stats with current tuser value
            rxStatisticsVectorReg := Cat(len, tuserNext(0))
            rxStatisticsValidReg := true.B
          }
        }
      }
      
      // Note: Other states (ST_LANE4, FIN, etc.) would be implemented similarly
      // For brevity, showing only the states from the provided code
      
  
      // need to add the remaining sates here 
    }
  }
  
  ////////////////////////////////////////////////
  // Output connections
  ////////////////////////////////////////////////
  
  io.goodFrames          := goodFramesReg
  io.badFrames           := badFramesReg
  io.rxStatisticsVector  := rxStatisticsVectorReg
  io.rxStatisticsValid   := rxStatisticsValidReg
  io.tdata               := tdataReg
  io.tkeep               := tkeepReg
  io.tvalid              := tvalidReg
  io.tlast               := tlastReg
  io.tuser               := tuserReg


  
  is(State.D_LANE4) {
    // Process data for lane 4 start frames
    // Realign data: take current lower 4 bytes and previous upper 4 bytes
    tdata_d0 := Cat(io.xgmiiD(31, 0), auxDw)
    tvalidPipe0 := true.B
    tkeepI := "hFF".U(8.W)
    auxDw := io.xgmii_d(63, 32)
    dataReg := io.xgmiiD
    ctrlReg := io.xgmiiC
    
    // Update CRC with full 64-bit word
    crc32 := crc8B(crc32, io.xgmiiD)
    crc32_4B := crc4B(crc32, io.xgmiiD(31, 0))
    crc32_5B := crc5B(crc32, io.xgmiiD(39, 0))
    crc32_6B := crc6B(crc32, io.xgmiiD(47, 0))
    crc32_7B := crc7B(crc32, io.xgmiiD(55, 0))
    
    // Handle termination based on control character pattern
    switch(io.xgmiiC) {
      // No termination - full 8 bytes of data
      is("h00".U(8.W)) {
        len := len + 8.U
        // Stay in same state
      }
      
      // Termination in lane 0 (byte 0) - T in lane 0
      is("hFF".U(8.W)) {
        len := len  // No increment for termination byte
        tvalidPipe0 := false.B
        tlastNext := true.B
        
        // Check CRC and termination character
        val crcOk = (~crc_rev(crc32_4B) === dataReg(63, 32)) && is_tchar(io.xgmiiD(7, 0))
        when(crcOk) {
          tuserNext:= 1.U(1.W)
          setStats(len, true.B)
        }.otherwise {
          setStats(len, false.B)
        }
        
        stateReg := State.IDLE
      }
      
      // Termination in lane 1 (byte 1) - T in lane 1
      is("hFE".U(8.W)) {
        len := len + 1.U
        lastTkeepI := "h01".U(8.W)
        receivedCrc := Cat(io.xgmiiD(7, 0), auxDw(31, 8))
        calculatedCrc := crc32_5B
        termCharValid := isTchar(io.xgmiiD(15, 8))
        stateReg := State.FINL4
      }
      
      // Termination in lane 2 (byte 2) - T in lane 2
      is("hFC".U(8.W)) {
        len := len + 2.U
        lastTkeepI := "h03".U(8.W)
        receivedCrc := Cat(io.xgmiiD(15, 0), auxDw(31, 16))
        calculatedCrc := crc32_6B
        termCharValid := is_tchar(io.xgmiiD(23, 16))
        stateReg := State.FINL4
      }
      
      // Termination in lane 3 (byte 3) - T in lane 3
      is("hF8".U(8.W)) {
        len := len + 3.U
        lastTkeepI := "h07".U(8.W)
        receivedCrc := Cat(io.xgmiiD(23, 0), auxDw(31, 24))
        calculatedCrc := crc32_7B
        termCharValid := is_tchar(io.xgmiiD(31, 24))
        stateReg := State.FINL4
      }
      
      // Termination in lane 4 (byte 4) - T in lane 4
      is("hF0".U(8.W)) {
        len := len + 4.U
        lastTkeepI := "h0F".U(8.W)
        receivedCrc := io.xgmiiD(31, 0)
        calculatedCrc := crc32
        termCharValid := is_tchar(io.xgmiiD(39, 32))
        stateReg := State.FIN
      }
      
      // Termination in lane 5 (byte 5) - T in lane 5
      is("hE0".U(8.W)) {
        len := len + 5.U
        lastTkeepI := "h1F".U(8.W)
        receivedCrc := io.xgmiiD(39, 8)
        calculatedCrc := crc1B(crc32, io.xgmiiD(7, 0))
        termCharValid := is_tchar(io.xgmiiD(47, 40))
        stateReg := State.FIN
      }
      
      // Termination in lane 6 (byte 6) - T in lane 6
      is("hC0".U(8.W)) {
        len := len + 6.U
        lastTkeepI := "h3F".U(8.W)
        receivedCrc := io.xgmiiD(47, 16)
        calculatedCrc := crc2B(crc32, io.xgmiiD(15, 0))
        termCharValid := is_tchar(io.xgmiiD(55, 48))
        stateReg := State.FIN
      }
      
      // Termination in lane 7 (byte 7) - T in lane 7
      is("h80".U(8.W)) {
        receivedCrc    := io.xgmiiD(55, 24)
        calculatedCrc  := crc3B(crc32, io.xgmiiD(23, 0))
        termCharValid  := isTChar(io.xgmiiD(63, 56))
        stateReg       := State.FIN
      }
      
      // Default case (error or unexpected control pattern)
      default {
        tlastNext := true.B
        tvalidPipe0 := false.B
        tvalid_i := true.B
        stateReg := State.IDLE
        setStats(len, tuserNext(0).asBool)
      }
    }
  }
  

  
  // Default case for unexpected state
  is(State.s7) {
    stateReg := State.IDLE
  }
  
  // Add default case for completeness
  default {
    stateReg := State.IDLE
  }
}


def setStats(bcount: UInt, crcOk: Bool): Unit = {
 // Register indicating RX statistics are valid
  rxStatisticsValidReg := true.B

  // RX statistics bit positions
  val StatRxSmall        = 0
  val StatRxUndersize    = 1
  val StatRxFragment     = 2
  val StatRx64B          = 3
  val StatRx65to127B     = 4
  val StatRx128to255B    = 5
  val StatRx256to511B    = 6
  val StatRx512to1023B   = 7
  val StatRx1024to1518B  = 8
  val StatRx1519to1522B  = 9
  val StatRx1523to1548B  = 10
  val StatRx1549to2047B  = 11
  val StatRx2048toMax    = 12
  val StatRxGoodPkt      = 13
  val StatRxOversize     = 14
  val StatRxJabber       = 15
  val StatRxOctetsHigh   = 16
  val StatRxOctetsLow    = 30

  // Working statistics vector
  val newStatsVec = WireInit(0.U(30.W))

  when(bcount < 64.U) {
    newStatsVec := newStatsVec | (1.U << StatRxSmall)
    when(crcOk) {
      newStatsVec := newStatsVec | (1.U << StatRxUndersize)
    }.otherwise {
      newStatsVec := newStatsVec | (1.U << StatRxFragment)
    }

  }.elsewhen(bcount === 64.U) {
    newStatsVec := newStatsVec | (1.U << StatRx64B)

  }.elsewhen(bcount <= 127.U) {
    newStatsVec := newStatsVec | (1.U << StatRx65to127B)

  }.elsewhen(bcount <= 255.U) {
    newStatsVec := newStatsVec | (1.U << StatRx128to255B)

  }.elsewhen(bcount <= 511.U) {
    newStatsVec := newStatsVec | (1.U << StatRx256to511B)

  }.elsewhen(bcount <= 1023.U) {
    newStatsVec := newStatsVec | (1.U << StatRx512to1023B)

  }.elsewhen(bcount <= 1518.U) {
    newStatsVec := newStatsVec | (1.U << StatRx1024to1518B)

  }.elsewhen(bcount <= 1522.U) {
    newStatsVec := newStatsVec | (1.U << StatRx1519to1522B)

  }.elsewhen(bcount <= 1548.U) {
    newStatsVec := newStatsVec | (1.U << StatRx1523to1548B)

  }.elsewhen(bcount <= 2047.U) {
    newStatsVec := newStatsVec | (1.U << StatRx1549to2047B)

  }.otherwise {
    val rxMtu = 16384.U
    when(bcount <= rxMtu) {
      newStatsVec := newStatsVec | (1.U << StatRx2048toMax)
    }
  }

  // Good packet indicator
  when(crcOk) {
    newStatsVec := newStatsVec | (1.U << StatRxGoodPkt)
  }

  // Oversize / jabber classification
  when(bcount > 1518.U) {
    when(crcOk) {
      newStatsVec := newStatsVec | (1.U << StatRxOversize)
    }.otherwise {
      newStatsVec := newStatsVec | (1.U << StatRxJabber)
    }
  }

  // Octet count (lower 14 bits)
  val octetBits = bcount(13, 0)
  newStatsVec := newStatsVec | (octetBits << StatRxOctetsHigh)

 
  rxStatisticsVectorReg := newStatsVec
 val _macImportTest = MAC_PREAMBLE

}