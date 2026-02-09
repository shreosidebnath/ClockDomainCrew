package ethernet
import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import ethernet.MacDefinitions._

class XGMII2AXIS extends Module {
    
 val clk = Input(Clock())  
val rst = Input(Bool())
val macDefs = Module(new EthMacDefinitions)
val xgmiiStart = macDefs.S  
val xgmiiTerm = macDefs.T   
val xgmiiError = macDefs.E  
val xgmiiIdle = macDefs.I   
val crc8023Preset = macDefs.crc8023Preset

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

    val goodFrames = Output(UInt(32.W))
    val badFrames = Output(UInt(32.W))

    val configurationVector = Input(UInt(80.W))
    val rxStatisticsVector = Output(UInt(30.W))
    val rxStatisticsValid = Output(Bool())

    val xgmiiD = Input(UInt(64.W))
    val xgmiiC = Input(UInt(8.W))

    val aresetn = Input(Bool())
    val tdata = Output(UInt(64.W))
    val tkeep = Output(UInt(8.W))
    val tvalid = Output(Bool())
    val tlast = Output(Bool())
    val tuser = Output(UInt(1.W))
  
  
  val CRC802_3_PRESET = "hFFFFFFFF".U(32.W)
  

  def crc_rev(crc: UInt): UInt = {
    // Reverse CRC bytes (for Ethernet CRC)
    Cat(
      crc(7, 0), crc(15, 8), crc(23, 16), crc(31, 24)
    )
  }
  
  def set_stats(length: UInt, good: Bool): Unit = {
   def set_stats(bcount: UInt, crc_ok: Bool): Unit = {
  // Set statistics valid flag
  rx_statistics_valid_reg := true.B
  
  // Initialize statistics vector to all zeros
  val stats_vec = WireInit(0.U(30.W))
  
  // Calculate which size bin the frame belongs to
  val size_bin = Wire(UInt(5.W))
  
  // Determine frame size category (per IEEE 802.3 statistics)
  when(bcount < 64.U) {
    // Small frame (< 64 bytes)
    stats_vec := stats_vec | (1.U << STAT_RX_SMALL)
    when(crc_ok) {
      // Good small frame = undersize
      stats_vec := stats_vec | (1.U << STAT_RX_UNDERSIZE)
    }.otherwise {
      // Bad small frame = fragment
      stats_vec := stats_vec | (1.U << STAT_RX_FRAGMENT)
    }
  }.elsewhen(bcount === 64.U) {
    // Exactly 64 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_64B)
  }.elsewhen(bcount > 64.U && bcount <= 127.U) {
    // 65-127 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_65_127B)
  }.elsewhen(bcount > 127.U && bcount <= 255.U) {
    // 128-255 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_128_255B)
  }.elsewhen(bcount > 255.U && bcount <= 511.U) {
    // 256-511 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_256_511B)
  }.elsewhen(bcount > 511.U && bcount <= 1023.U) {
    // 512-1023 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_512_1023B)
  }.elsewhen(bcount > 1023.U && bcount <= 1518.U) {
    // 1024-1518 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_1024_1518B)
  }.elsewhen(bcount > 1518.U && bcount <= 1522.U) {
    // 1519-1522 bytes (VLAN tagged max)
    stats_vec := stats_vec | (1.U << STAT_RX_1519_1522B)
  }.elsewhen(bcount > 1522.U && bcount <= 1548.U) {
    // 1523-1548 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_1523_1548B)
  }.elsewhen(bcount > 1548.U && bcount <= 2047.U) {
    // 1549-2047 bytes
    stats_vec := stats_vec | (1.U << STAT_RX_1549_2047B)
  }.elsewhen(bcount > 2047.U) {
    // 2048+ bytes (jumbo frames up to MTU)
    // RX_MTU should be defined in MACDefinitions
    when(bcount <= RX_MTU) {
      stats_vec := stats_vec | (1.U << STAT_RX_2048_MAX)
    }
  }
  
  // Set good packet flag if CRC is OK (regardless of size)
  when(crc_ok) {
    stats_vec := stats_vec | (1.U << STAT_RX_GOOD_PKT)
  }
  
  // Handle oversize/jabber classification for frames > 1518 bytes
  when(bcount > 1518.U) {
    when(crc_ok) {
      // Good oversize frame
      stats_vec := stats_vec | (1.U << STAT_RX_OVERSIZE)
    }.otherwise {
      // Bad oversize frame = jabber
      stats_vec := stats_vec | (1.U << STAT_RX_JABBER)
    }
  }
  
  // Store octet count in bits [29:16] of statistics vector
  // bcount is 16 bits, but we only store lower 14 bits (max 16383)
  val octet_count = bcount(13, 0)  // Take lower 14 bits
  stats_vec := stats_vec | (octet_count << STAT_RX_OCTETS_HIGH)
  
  // Update the statistics vector register
  rx_statistics_vector_reg := stats_vec
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

    val fsm = RegInit(State.SRES)
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
    val rcvedCrc = RegInit(0.U(32.W))
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
      fsm := State.SRES
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
      rcvedCrc := 0.U
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
        
   
    tdata_i := tdata_d0
    tvalid_i := tvalid_d0
    rx_statistics_valid_reg := false.B
    
    // FSM
    switch(fsm) {
      is(State.SRES) {
        good_frames_reg := 0.U
        bad_frames_reg := 0.U
        fsm := State.IDLE
      }
      
      is(State.IDLE) {
        tvalid_d0 := false.B
        tlast_i := false.B
        tuser_i := 0.U
        crc_32 := CRC802_3_PRESET
        inbound_frame := false.B
        d_reg := io.xgmii_d
        c_reg := io.xgmii_c
        len := 0.U
        rx_statistics_vector_reg := 0.U
        
        when(sof_lane0(io.xgmii_d, io.xgmii_c)) {
          inbound_frame := true.B
          fsm := State.ST_LANE0
        }.elsewhen(sof_lane4(io.xgmii_d, io.xgmii_c)) {
          inbound_frame := true.B
          fsm := State.ST_LANE4
        }
      }
      
      is(State.ST_LANE0) {
        tdata_d0 := io.xgmii_d
        tvalid_d0 := true.B
        tkeep_i := "hFF".U(8.W)
        tlast_i := false.B
        tuser_i := 0.U
        d_reg := io.xgmii_d
        c_reg := io.xgmii_c
        crc_32 := crc8B(crc_32, io.xgmii_d)
        crc_32_7B := crc7B(crc_32, io.xgmii_d(55, 0))
        crc_32_6B := crc6B(crc_32, io.xgmii_d(47, 0))
        crc_32_5B := crc5B(crc_32, io.xgmii_d(39, 0))
        crc_32_4B := crc4B(crc_32, io.xgmii_d(31, 0))
        
        
        switch(io.xgmii_c) {
         
          is("h00".U(8.W)) {
            len := len + 8.U
           
          }
          
          //Still updating 
          is("hFF".U(8.W)) {
            len := len  
            tkeep_i := "h0F".U(8.W)  
            tvalid_d0 := false.B
            tlast_i := true.B
            
            // Check CRC and termination character
            val crc_ok = (~crc_rev(crc_32_4B) === d_reg(63, 32)) && is_tchar(io.xgmii_d(7, 0))
            when(crc_ok) {
              tuser_i := 1.U(1.W)
              // Update stats
              rx_statistics_vector_reg := Cat(len, 1.U(14.W))  // Simplified
              rx_statistics_valid_reg := true.B
            }.otherwise {
              // Update stats differently for bad frame
              rx_statistics_vector_reg := Cat(len, 0.U(14.W))
              rx_statistics_valid_reg := true.B
            }
            
            fsm := State.IDLE
          }
          
          // Termination in lane 1 (byte 1) - T in lane 1
          is("hFE".U(8.W)) {
            len := len + 1.U
            tkeep_i := "h1F".U(8.W)  // First 5 bytes valid
            tvalid_d0 := false.B
            tlast_i := true.B
            
            val crc_ok = (~crc_rev(crc_32_5B) === Cat(io.xgmii_d(7, 0), d_reg(63, 40))) && 
                         is_tchar(io.xgmii_d(15, 8))
            when(crc_ok) {
              tuser_i := 1.U(1.W)
              rx_statistics_vector_reg := Cat(len, 1.U(14.W))
            }.otherwise {
              rx_statistics_vector_reg := Cat(len, 0.U(14.W))
            }
            rx_statistics_valid_reg := true.B
            
            fsm := State.IDLE
          }
          
          // Termination in lane 2 (byte 2) - T in lane 2
          is("hFC".U(8.W)) {
            len := len + 2.U
            tkeep_i := "h3F".U(8.W)  // First 6 bytes valid
            tvalid_d0 := false.B
            tlast_i := true.B
            
            val crc_ok = (~crc_rev(crc_32_6B) === Cat(io.xgmii_d(15, 0), d_reg(63, 48))) && 
                         is_tchar(io.xgmii_d(23, 16))
            when(crc_ok) {
              tuser_i := 1.U(1.W)
              rx_statistics_vector_reg := Cat(len, 1.U(14.W))
            }.otherwise {
              rx_statistics_vector_reg := Cat(len, 0.U(14.W))
            }
            rx_statistics_valid_reg := true.B
            
            fsm := State.IDLE
          }
          
          // Termination in lane 3 (byte 3) - T in lane 3
          is("hF8".U(8.W)) {
            len := len + 3.U
            tkeep_i := "h7F".U(8.W)  // First 7 bytes valid
            tvalid_d0 := false.B
            tlast_i := true.B
            
            val crc_ok = (~crc_rev(crc_32_7B) === Cat(io.xgmii_d(23, 0), d_reg(63, 56))) && 
                         is_tchar(io.xgmii_d(31, 24))
            when(crc_ok) {
              tuser_i := 1.U(1.W)
              rx_statistics_vector_reg := Cat(len, 1.U(14.W))
            }.otherwise {
              rx_statistics_vector_reg := Cat(len, 0.U(14.W))
            }
            rx_statistics_valid_reg := true.B
            
            fsm := State.IDLE
          }
          
          // Termination in lane 4 (byte 4) - T in lane 4
          is("hF0".U(8.W)) {
            len := len + 4.U
            tkeep_i := "hFF".U(8.W)  // All 8 bytes valid
            tvalid_d0 := false.B
            tlast_i := true.B
            
            val crc_ok = (~crc_rev(crc_32) === io.xgmii_d(31, 0)) && 
                         is_tchar(io.xgmii_d(39, 32))
            when(crc_ok) {
              tuser_i := 1.U(1.W)
              rx_statistics_vector_reg := Cat(len, 1.U(14.W))
            }.otherwise {
              rx_statistics_vector_reg := Cat(len, 0.U(14.W))
            }
            rx_statistics_valid_reg := true.B
            
            fsm := State.IDLE
          }
          
          // Termination in lane 5 (byte 5) - T in lane 5
          is("hE0".U(8.W)) {
            len := len + 5.U
            last_tkeep_i := "h01".U(8.W)  // Only byte 0 valid in next cycle?
            rcved_crc := io.xgmii_d(39, 8)
            calcted_crc := crc1B(crc_32, io.xgmii_d(7, 0))
            chk_tchar := is_tchar(io.xgmii_d(47, 40))
            fsm := State.FIN
          }
          
          // Termination in lane 6 (byte 6) - T in lane 6
          is("hC0".U(8.W)) {
            len := len + 6.U
            last_tkeep_i := "h03".U(8.W)  // First 2 bytes valid
            rcved_crc := io.xgmii_d(47, 16)
            calcted_crc := crc2B(crc_32, io.xgmii_d(15, 0))
            chk_tchar := is_tchar(io.xgmii_d(55, 48))
            fsm := State.FIN
          }
          
          // Termination in lane 7 (byte 7) - T in lane 7
          is("h80".U(8.W)) {
            len := len + 7.U
            last_tkeep_i := "h07".U(8.W)  // First 3 bytes valid
            rcved_crc := io.xgmii_d(55, 24)
            calcted_crc := crc3B(crc_32, io.xgmii_d(23, 0))
            chk_tchar := is_tchar(io.xgmii_d(63, 56))
            fsm := State.FIN
          }
          
          // Default case (error or unexpected control pattern)
          default {
            tlast_i := true.B
            tvalid_d0 := false.B
            tvalid_i := true.B
            fsm := State.IDLE
            // Update stats with current tuser value
            rx_statistics_vector_reg := Cat(len, tuser_i(0))
            rx_statistics_valid_reg := true.B
          }
        }
      }
      
      // Note: Other states (ST_LANE4, FIN, etc.) would be implemented similarly
      // For brevity, showing only the states from the provided code
      
      is(State.FIN) {
        // FIN state implementation would go here
        // Check CRC and update statistics
        val crc_ok = (rcved_crc === calcted_crc) && chk_tchar
        when(crc_ok) {
          tuser_i := 1.U(1.W)
        }
        tlast_i := true.B
        tkeep_i := last_tkeep_i
        tvalid_d0 := false.B
        // Update statistics
        rx_statistics_vector_reg := Cat(len, crc_ok.asUInt, 0.U(13.W))
        rx_statistics_valid_reg := true.B
        fsm := State.IDLE
      }
      
      // Other states would be defined here...
    }
  }
  
  ////////////////////////////////////////////////
  // Output connections
  ////////////////////////////////////////////////
  
  io.good_frames := good_frames_reg
  io.bad_frames := bad_frames_reg
  io.rx_statistics_vector := rx_statistics_vector_reg
  io.rx_statistics_valid := rx_statistics_valid_reg
  io.tdata := tdata_reg
  io.tkeep := tkeep_reg
  io.tvalid := tvalid_reg
  io.tlast := tlast_reg
  io.tuser := tuser_reg
  // Add to the existing FSM switch statement
switch(fsm) {
  // ... existing states (SRES, IDLE, ST_LANE0) ...
  
  is(State.FIN) {
    tkeep_i := last_tkeep_i
    tlast_i := true.B
    tvalid_d0 := false.B
    crc_32 := CRC802_3_PRESET
    
    // Check CRC and termination character
    val crc_ok = (~crc_rev(calcted_crc) === rcved_crc) && chk_tchar
    when(crc_ok) {
      tuser_i := 1.U(1.W)
      set_stats(len, true.B)
    }.otherwise {
      set_stats(len, false.B)
    }
    
    // Check for next frame start
    when(sof_lane4(io.xgmii_d, io.xgmii_c)) {
      fsm := State.ST_LANE4
    }.otherwise {
      fsm := State.IDLE
    }
  }
  
  is(State.ST_LANE4) {
    // Frame starts at lane 4 (4-byte shifted)
    len := 4.U
    tlast_i := false.B
    tuser_i := 0.U
    
    // Process the first 4 bytes from lanes 4-7
    crc_32 := crc4B(crc_32, io.xgmii_d(63, 32))
    aux_dw := io.xgmii_d(63, 32)
    
    // Check if there are more bytes to process
    when(io.xgmii_c === 0.U) {
      // No control characters, continue processing
      fsm := State.D_LANE4
    }.otherwise {
      fsm := State.IDLE
    }
  }
  
  is(State.D_LANE4) {
    // Process data for lane 4 start frames
    // Realign data: take current lower 4 bytes and previous upper 4 bytes
    tdata_d0 := Cat(io.xgmii_d(31, 0), aux_dw)
    tvalid_d0 := true.B
    tkeep_i := "hFF".U(8.W)
    aux_dw := io.xgmii_d(63, 32)
    d_reg := io.xgmii_d
    c_reg := io.xgmii_c
    
    // Update CRC with full 64-bit word
    crc_32 := crc8B(crc_32, io.xgmii_d)
    crc_32_4B := crc4B(crc_32, io.xgmii_d(31, 0))
    crc_32_5B := crc5B(crc_32, io.xgmii_d(39, 0))
    crc_32_6B := crc6B(crc_32, io.xgmii_d(47, 0))
    crc_32_7B := crc7B(crc_32, io.xgmii_d(55, 0))
    
    // Handle termination based on control character pattern
    switch(io.xgmii_c) {
      // No termination - full 8 bytes of data
      is("h00".U(8.W)) {
        len := len + 8.U
        // Stay in same state
      }
      
      // Termination in lane 0 (byte 0) - T in lane 0
      is("hFF".U(8.W)) {
        len := len  // No increment for termination byte
        tvalid_d0 := false.B
        tlast_i := true.B
        
        // Check CRC and termination character
        val crc_ok = (~crc_rev(crc_32_4B) === d_reg(63, 32)) && is_tchar(io.xgmii_d(7, 0))
        when(crc_ok) {
          tuser_i := 1.U(1.W)
          set_stats(len, true.B)
        }.otherwise {
          set_stats(len, false.B)
        }
        
        fsm := State.IDLE
      }
      
      // Termination in lane 1 (byte 1) - T in lane 1
      is("hFE".U(8.W)) {
        len := len + 1.U
        last_tkeep_i := "h01".U(8.W)
        rcved_crc := Cat(io.xgmii_d(7, 0), aux_dw(31, 8))
        calcted_crc := crc_32_5B
        chk_tchar := is_tchar(io.xgmii_d(15, 8))
        fsm := State.FINL4
      }
      
      // Termination in lane 2 (byte 2) - T in lane 2
      is("hFC".U(8.W)) {
        len := len + 2.U
        last_tkeep_i := "h03".U(8.W)
        rcved_crc := Cat(io.xgmii_d(15, 0), aux_dw(31, 16))
        calcted_crc := crc_32_6B
        chk_tchar := is_tchar(io.xgmii_d(23, 16))
        fsm := State.FINL4
      }
      
      // Termination in lane 3 (byte 3) - T in lane 3
      is("hF8".U(8.W)) {
        len := len + 3.U
        last_tkeep_i := "h07".U(8.W)
        rcved_crc := Cat(io.xgmii_d(23, 0), aux_dw(31, 24))
        calcted_crc := crc_32_7B
        chk_tchar := is_tchar(io.xgmii_d(31, 24))
        fsm := State.FINL4
      }
      
      // Termination in lane 4 (byte 4) - T in lane 4
      is("hF0".U(8.W)) {
        len := len + 4.U
        last_tkeep_i := "h0F".U(8.W)
        rcved_crc := io.xgmii_d(31, 0)
        calcted_crc := crc_32
        chk_tchar := is_tchar(io.xgmii_d(39, 32))
        fsm := State.FIN
      }
      
      // Termination in lane 5 (byte 5) - T in lane 5
      is("hE0".U(8.W)) {
        len := len + 5.U
        last_tkeep_i := "h1F".U(8.W)
        rcved_crc := io.xgmii_d(39, 8)
        calcted_crc := crc1B(crc_32, io.xgmii_d(7, 0))
        chk_tchar := is_tchar(io.xgmii_d(47, 40))
        fsm := State.FIN
      }
      
      // Termination in lane 6 (byte 6) - T in lane 6
      is("hC0".U(8.W)) {
        len := len + 6.U
        last_tkeep_i := "h3F".U(8.W)
        rcved_crc := io.xgmii_d(47, 16)
        calcted_crc := crc2B(crc_32, io.xgmii_d(15, 0))
        chk_tchar := is_tchar(io.xgmii_d(55, 48))
        fsm := State.FIN
      }
      
      // Termination in lane 7 (byte 7) - T in lane 7
      is("h80".U(8.W)) {
        len := len + 7.U
        last_tkeep_i := "h7F".U(8.W)
        rcved_crc := io.xgmii_d(55, 24)
        calcted_crc := crc3B(crc_32, io.xgmii_d(23, 0))
        chk_tchar := is_tchar(io.xgmii_d(63, 56))
        fsm := State.FIN
      }
      
      // Default case (error or unexpected control pattern)
      default {
        tlast_i := true.B
        tvalid_d0 := false.B
        tvalid_i := true.B
        fsm := State.IDLE
        set_stats(len, tuser_i(0).asBool)
      }
    }
  }
  
  is(State.FINL4) {
    len := 0.U
    tkeep_i := last_tkeep_i
    tlast_i := true.B
    tvalid_d0 := false.B
    crc_32 := CRC802_3_PRESET
    
    // Check CRC and termination character for lane 4 start frames
    val crc_ok = (~crc_rev(calcted_crc) === rcved_crc) && chk_tchar
    when(crc_ok) {
      tuser_i := 1.U(1.W)
      set_stats(len, true.B)
    }.otherwise {
      set_stats(len, false.B)
    }
    
    // Check for next frame start
    when(sof_lane0(io.xgmii_d, io.xgmii_c)) {
      fsm := State.ST_LANE0
    }.elsewhen(sof_lane4(io.xgmii_d, io.xgmii_c)) {
      fsm := State.ST_LANE4
    }.otherwise {
      fsm := State.IDLE
    }
  }
  
  // Default case for unexpected state
  is(State.s7) {
    fsm := State.IDLE
  }
  
  // Add default case for completeness
  default {
    fsm := State.IDLE
  }
}

// Implement the set_stats function as a method
def set_stats(bcount: UInt, crc_ok: Bool): Unit = {
  rx_statistics_valid_reg := true.B
  
  // Define statistics bit positions (from xgmii_includes.vh)
  // These would typically be in a companion object or separate file
  val STAT_RX_SMALL = 0
  val STAT_RX_UNDERSIZE = 1
  val STAT_RX_FRAGMENT = 2
  val STAT_RX_64B = 3
  val STAT_RX_65_127B = 4
  val STAT_RX_128_255B = 5
  val STAT_RX_256_511B = 6
  val STAT_RX_512_1023B = 7
  val STAT_RX_1024_1518B = 8
  val STAT_RX_1519_1522B = 9
  val STAT_RX_1523_1548B = 10
  val STAT_RX_1549_2047B = 11
  val STAT_RX_2048_MAX = 12
  val STAT_RX_GOOD_PKT = 13
  val STAT_RX_OVERSIZE = 14
  val STAT_RX_JABBER = 15
  val STAT_RX_OCTETS_HIGH = 16
  val STAT_RX_OCTETS_LOW = 30  // Actually bits 16:29 for octet count
  
  // Clear the vector first
  val new_stats_vec = WireInit(0.U(30.W))
  
  // Set statistics based on byte count
  when(bcount < 64.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_SMALL)
    when(crc_ok) {
      new_stats_vec := new_stats_vec | (1.U << STAT_RX_UNDERSIZE)
    }.otherwise {
      new_stats_vec := new_stats_vec | (1.U << STAT_RX_FRAGMENT)
    }
  }.elsewhen(bcount === 64.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_64B)
  }.elsewhen(bcount > 64.U && bcount <= 127.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_65_127B)
  }.elsewhen(bcount > 127.U && bcount <= 255.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_128_255B)
  }.elsewhen(bcount > 255.U && bcount <= 511.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_256_511B)
  }.elsewhen(bcount > 511.U && bcount <= 1023.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_512_1023B)
  }.elsewhen(bcount > 1023.U && bcount <= 1518.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_1024_1518B)
  }.elsewhen(bcount > 1518.U && bcount <= 1522.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_1519_1522B)
  }.elsewhen(bcount > 1522.U && bcount <= 1548.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_1523_1548B)
  }.elsewhen(bcount > 1548.U && bcount <= 2047.U) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_1549_2047B)
  }.elsewhen(bcount > 2047.U) {
    // Using RX_MTU constant (would be defined elsewhere)
    val RX_MTU = 16384.U  // Example value
    when(bcount <= RX_MTU) {
      new_stats_vec := new_stats_vec | (1.U << STAT_RX_2048_MAX)
    }
  }
  
  // Set good packet flag if CRC is OK
  when(crc_ok) {
    new_stats_vec := new_stats_vec | (1.U << STAT_RX_GOOD_PKT)
  }
  
  // Handle oversize/jabber for packets > 1518 bytes
  when(bcount > 1518.U) {
    when(crc_ok) {
      new_stats_vec := new_stats_vec | (1.U << STAT_RX_OVERSIZE)
    }.otherwise {
      new_stats_vec := new_stats_vec | (1.U << STAT_RX_JABBER)
    }
  }
  
  // Set octet count (14 bits in the vector)
  val octet_bits = bcount(13, 0)  // Use lower 14 bits of byte count
  new_stats_vec := new_stats_vec | (octet_bits << STAT_RX_OCTETS_HIGH)
  
  // Update the statistics vector register
  rx_statistics_vector_reg := new_stats_vec
}
}