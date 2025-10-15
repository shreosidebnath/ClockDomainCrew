import chisel3._
import chisel3.util._

// XGMII to AXIS Converter Module
class Xgmii2Axis extends Module {
  val io = IO(new Bundle {
    // Clock and Reset (implicit in Module)
    val rst = Input(Bool())
    
    // Stats
    val good_frames = Output(UInt(32.W))
    val bad_frames = Output(UInt(32.W))
    
    // Configuration vectors
    val configuration_vector = Input(UInt(80.W))
    val rx_statistics_vector = Output(UInt(30.W))
    val rx_statistics_valid = Output(Bool())
    
    // XGMII input
    val xgmii_d = Input(UInt(64.W))
    val xgmii_c = Input(UInt(8.W))
    
    // AXIS output
    val aresetn = Input(Bool())
    val tdata = Output(UInt(64.W))
    val tkeep = Output(UInt(8.W))
    val tvalid = Output(Bool())
    val tlast = Output(Bool())
    val tuser = Output(UInt(1.W))
  })

  import XgmiiConstants._
  import XgmiiFunctions._

  // FSM State definitions
  val SRES = 0.U(8.W)
  val IDLE = 1.U(8.W)
  val ST_LANE0 = 2.U(8.W)
  val ST_LANE4 = 3.U(8.W)
  val FIN = 4.U(8.W)
  val D_LANE4 = 5.U(8.W)
  val FINL4 = 6.U(8.W)
  val s7 = 7.U(8.W)

  // Registers
  val synch = RegInit(false.B)
  val fsm = RegInit(SRES)
  val tdata_i = RegInit(0.U(64.W))
  val tkeep_i = RegInit(0.U(8.W))
  val last_tkeep_i = RegInit(0.U(8.W))
  val tvalid_i = RegInit(false.B)
  val tlast_i = RegInit(false.B)
  val tuser_i = RegInit(0.U(1.W))
  val tdata_d0 = RegInit(0.U(64.W))
  val tvalid_d0 = RegInit(false.B)
  val d_reg = RegInit(0.U(64.W))
  val c_reg = RegInit(0.U(8.W))
  val inbound_frame = RegInit(false.B)
  val len = RegInit(0.U(16.W))
  val aux_dw = RegInit(0.U(32.W))
  val chk_tchar = RegInit(false.B)

  // CRC registers
  val crc_32 = RegInit(0.U(32.W))
  val crc_32_7B = RegInit(0.U(32.W))
  val crc_32_6B = RegInit(0.U(32.W))
  val crc_32_5B = RegInit(0.U(32.W))
  val crc_32_4B = RegInit(0.U(32.W))
  val crc_32_3B = RegInit(0.U(32.W))
  val crc_32_2B = RegInit(0.U(32.W))
  val crc_32_1B = RegInit(0.U(32.W))
  val rcved_crc = RegInit(0.U(32.W))
  val calcted_crc = RegInit(0.U(32.W))

  // Statistics
  val good_frames_reg = RegInit(0.U(32.W))
  val bad_frames_reg = RegInit(0.U(32.W))
  val rx_statistics_vector_reg = RegInit(0.U(30.W))
  val rx_statistics_valid_reg = RegInit(false.B)

  // Output registers
  val tdata_reg = RegInit(0.U(64.W))
  val tkeep_reg = RegInit(0.U(8.W))
  val tvalid_reg = RegInit(false.B)
  val tlast_reg = RegInit(false.B)
  val tuser_reg = RegInit(0.U(1.W))

  // Wire assignments
  val d = io.xgmii_d
  val c = io.xgmii_c
  val inv_aresetn = ~io.aresetn

  // Output assignments
  io.tdata := tdata_reg
  io.tkeep := tkeep_reg
  io.tvalid := tvalid_reg
  io.tlast := tlast_reg
  io.tuser := tuser_reg
  io.good_frames := good_frames_reg
  io.bad_frames := bad_frames_reg
  io.rx_statistics_vector := rx_statistics_vector_reg
  io.rx_statistics_valid := rx_statistics_valid_reg

  // set_stats function
  def set_stats(bcount: UInt, crc_ok: Bool): Unit = {
    rx_statistics_valid_reg := true.B
    rx_statistics_vector_reg := 0.U

    when(bcount < 64.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_SMALL).asUInt
      when(crc_ok) {
        rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_UNDERSIZE).asUInt
      }.otherwise {
        rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_FRAGMENT).asUInt
      }
    }.elsewhen(bcount === 64.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_64B).asUInt
    }.elsewhen(bcount > 64.U && bcount <= 127.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_65_127B).asUInt
    }.elsewhen(bcount > 127.U && bcount <= 255.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_128_255B).asUInt
    }.elsewhen(bcount > 255.U && bcount <= 511.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_256_511B).asUInt
    }.elsewhen(bcount > 511.U && bcount <= 1023.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_512_1023B).asUInt
    }.elsewhen(bcount > 1023.U && bcount <= 1518.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_1024_1518B).asUInt
    }.elsewhen(bcount > 1518.U && bcount <= 1522.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_1519_1522B).asUInt
    }.elsewhen(bcount > 1522.U && bcount <= 1548.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_1523_1548B).asUInt
    }.elsewhen(bcount > 1548.U && bcount <= 2047.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_1549_2047B).asUInt
    }.elsewhen(bcount > 2047.U && bcount <= RX_MTU.U) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_2048_MAX).asUInt
    }

    when(crc_ok) {
      rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_GOOD_PKT).asUInt
    }

    when(bcount > 1518.U) {
      when(crc_ok) {
        rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_OVERSIZE).asUInt
      }.otherwise {
        rx_statistics_vector_reg := rx_statistics_vector_reg | (1.U << STAT_RX_JABBER).asUInt
      }
    }

    rx_statistics_vector_reg := rx_statistics_vector_reg | bcount(13, 0)
  }

  // Output synchronization logic
  when(inv_aresetn) {
    tvalid_reg := false.B
    synch := false.B
  }.otherwise {
    when(!inbound_frame || synch) {
      synch := true.B
      tdata_reg := tdata_i
      tkeep_reg := tkeep_i
      tvalid_reg := tvalid_i
      tlast_reg := tlast_i
      tuser_reg := tuser_i
    }.otherwise {
      tvalid_reg := false.B
    }
  }

  // Main adapter FSM
  when(io.rst) {
    // Reset all registers
    tvalid_i := false.B
    fsm := SRES
    rx_statistics_valid_reg := false.B
    rx_statistics_vector_reg := 0.U
    aux_dw := 0.U
    bad_frames_reg := 0.U
    c_reg := 0.U
    calcted_crc := 0.U
    chk_tchar := false.B
    crc_32 := 0.U
    crc_32_4B := 0.U
    crc_32_5B := 0.U
    crc_32_6B := 0.U
    crc_32_7B := 0.U
    d_reg := 0.U
    good_frames_reg := 0.U
    inbound_frame := false.B
    last_tkeep_i := 0.U
    len := 0.U
    rcved_crc := 0.U
    tdata_d0 := 0.U
    tdata_i := 0.U
    tkeep_i := 0.U
    tlast_i := false.B
    tuser_i := 0.U
    tvalid_d0 := false.B
  }.otherwise {
    // Update frame counters
    when(tvalid_reg && tlast_reg && tuser_reg(0)) {
      good_frames_reg := good_frames_reg + 1.U
    }
    when(tvalid_reg && tlast_reg && !tuser_reg(0)) {
      bad_frames_reg := bad_frames_reg + 1.U
    }

    tdata_i := tdata_d0
    tvalid_i := tvalid_d0
    rx_statistics_valid_reg := false.B

    switch(fsm) {
      is(SRES) {
        good_frames_reg := 0.U
        bad_frames_reg := 0.U
        fsm := IDLE
      }

      is(IDLE) {
        tvalid_d0 := false.B
        tlast_i := false.B
        tuser_i := 0.U
        crc_32 := CRC802_3_PRESET
        inbound_frame := false.B
        d_reg := d
        c_reg := c
        len := 0.U
        rx_statistics_vector_reg := 0.U

        when(sof_lane0(d, c)) {
          inbound_frame := true.B
          fsm := ST_LANE0
        }.elsewhen(sof_lane4(d, c)) {
          inbound_frame := true.B
          fsm := ST_LANE4
        }
      }

      is(ST_LANE0) {
        tdata_d0 := d
        tvalid_d0 := true.B
        tkeep_i := 0xFF.U
        tlast_i := false.B
        tuser_i := 0.U
        d_reg := d
        c_reg := c
        crc_32 := crc8B(crc_32, d)
        crc_32_7B := crc7B(crc_32, d(55, 0))
        crc_32_6B := crc6B(crc_32, d(47, 0))
        crc_32_5B := crc5B(crc_32, d(39, 0))
        crc_32_4B := crc4B(crc_32, d(31, 0))

        switch(c) {
          is(0x00.U) {
            len := len + 8.U
          }
          is(0xFF.U) {
            len := len
            tkeep_i := 0x0F.U
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32_4B) === d_reg(63, 32)) && is_tchar(d(7, 0))) {
              tuser_i := 1.U
              set_stats(len, true.B)
            }.otherwise {
              set_stats(len, false.B)
            }
            fsm := IDLE
          }
          is(0xFE.U) {
            len := len + 1.U
            tkeep_i := 0x1F.U
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32_5B) === Cat(d(7, 0), d_reg(63, 40))) && is_tchar(d(15, 8))) {
              tuser_i := 1.U
              set_stats(len + 1.U, true.B)
            }.otherwise {
              set_stats(len + 1.U, false.B)
            }
            fsm := IDLE
          }
          is(0xFC.U) {
            len := len + 2.U
            tkeep_i := 0x3F.U
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32_6B) === Cat(d(15, 0), d_reg(63, 48))) && is_tchar(d(23, 16))) {
              tuser_i := 1.U
              set_stats(len + 2.U, true.B)
            }.otherwise {
              set_stats(len + 2.U, false.B)
            }
            fsm := IDLE
          }
          is(0xF8.U) {
            len := len + 3.U
            tkeep_i := 0x7F.U
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32_7B) === Cat(d(23, 0), d_reg(63, 56))) && is_tchar(d(31, 24))) {
              tuser_i := 1.U
              set_stats(len + 3.U, true.B)
            }.otherwise {
              set_stats(len + 3.U, false.B)
            }
            fsm := IDLE
          }
          is(0xF0.U) {
            len := len + 4.U
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32) === d(31, 0)) && is_tchar(d(39, 32))) {
              tuser_i := 1.U
              set_stats(len + 4.U, true.B)
            }.otherwise {
              set_stats(len + 4.U, false.B)
            }
            fsm := IDLE
          }
          is(0xE0.U) {
            len := len + 5.U
            last_tkeep_i := 0x01.U
            rcved_crc := d(39, 8)
            calcted_crc := crc1B(crc_32, d(7, 0))
            chk_tchar := is_tchar(d(47, 40))
            fsm := FIN
          }
          is(0xC0.U) {
            len := len + 6.U
            last_tkeep_i := 0x03.U
            rcved_crc := d(47, 16)
            calcted_crc := crc2B(crc_32, d(15, 0))
            chk_tchar := is_tchar(d(55, 48))
            fsm := FIN
          }
          is(0x80.U) {
            len := len + 7.U
            last_tkeep_i := 0x07.U
            rcved_crc := d(55, 24)
            calcted_crc := crc3B(crc_32, d(23, 0))
            chk_tchar := is_tchar(d(63, 56))
            fsm := FIN
          }
        }

        // Default case for invalid c values
        when(c =/= 0x00.U && c =/= 0xFF.U && c =/= 0xFE.U && c =/= 0xFC.U && 
             c =/= 0xF8.U && c =/= 0xF0.U && c =/= 0xE0.U && c =/= 0xC0.U && c =/= 0x80.U) {
          tlast_i := true.B
          tvalid_d0 := false.B
          tvalid_i := true.B
          fsm := IDLE
          set_stats(len, tuser_i(0))
        }
      }

      is(FIN) {
        tkeep_i := last_tkeep_i
        tlast_i := true.B
        tvalid_d0 := false.B
        crc_32 := CRC802_3_PRESET
        when((~crc_rev(calcted_crc) === rcved_crc) && chk_tchar) {
          tuser_i := 1.U
          set_stats(len, true.B)
        }.otherwise {
          set_stats(len, false.B)
        }

        when(sof_lane4(d, c)) {
          fsm := ST_LANE4
        }.otherwise {
          fsm := IDLE
        }
      }

      is(ST_LANE4) {
        len := 4.U
        tlast_i := false.B
        tuser_i := 0.U
        crc_32 := crc4B(crc_32, d(63, 32))
        aux_dw := d(63, 32)
        when(!c.orR) {
          fsm := D_LANE4
        }.otherwise {
          fsm := IDLE
        }
      }

      is(D_LANE4) {
        tdata_d0 := Cat(d(31, 0), aux_dw)
        tvalid_d0 := true.B
        tkeep_i := 0xFF.U
        aux_dw := d(63, 32)
        d_reg := d
        c_reg := c
        crc_32 := crc8B(crc_32, d)
        crc_32_4B := crc4B(crc_32, d(31, 0))
        crc_32_5B := crc5B(crc_32, d(39, 0))
        crc_32_6B := crc6B(crc_32, d(47, 0))
        crc_32_7B := crc7B(crc_32, d(55, 0))

        switch(c) {
          is(0x00.U) {
            len := len + 8.U
          }
          is(0xFF.U) {
            len := len
            tvalid_d0 := false.B
            tlast_i := true.B
            when((~crc_rev(crc_32_4B) === d_reg(63, 32)) && is_tchar(d(7, 0))) {
              tuser_i := 1.U
              set_stats(len, true.B)
            }.otherwise {
              set_stats(len, false.B)
            }
            fsm := IDLE
          }
          is(0xFE.U) {
            len := len + 1.U
            last_tkeep_i := 0x01.U
            rcved_crc := Cat(d(7, 0), aux_dw(31, 8))
            calcted_crc := crc_32_5B
            chk_tchar := is_tchar(d(15, 8))
            fsm := FINL4
          }
          is(0xFC.U) {
            len := len + 2.U
            last_tkeep_i := 0x03.U
            rcved_crc := Cat(d(15, 0), aux_dw(31, 16))
            calcted_crc := crc_32_6B
            chk_tchar := is_tchar(d(23, 16))
            fsm := FINL4
          }
          is(0xF8.U) {
            len := len + 3.U
            last_tkeep_i := 0x07.U
            rcved_crc := Cat(d(23, 0), aux_dw(31, 24))
            calcted_crc := crc_32_7B
            chk_tchar := is_tchar(d(31, 24))
            fsm := FINL4
          }
          is(0xF0.U) {
            len := len + 4.U
            last_tkeep_i := 0x0F.U
            rcved_crc := d(31, 0)
            calcted_crc := crc_32
            chk_tchar := is_tchar(d(39, 32))
            fsm := FIN
          }
          is(0xE0.U) {
            len := len + 5.U
            last_tkeep_i := 0x1F.U
            rcved_crc := d(39, 8)
            calcted_crc := crc1B(crc_32, d(7, 0))
            chk_tchar := is_tchar(d(47, 40))
            fsm := FIN
          }
          is(0xC0.U) {
            len := len + 6.U
            last_tkeep_i := 0x3F.U
            rcved_crc := d(47, 16)
            calcted_crc := crc2B(crc_32, d(15, 0))
            chk_tchar := is_tchar(d(55, 48))
            fsm := FIN
          }
          is(0x80.U) {
            len := len + 7.U
            last_tkeep_i := 0x7F.U
            rcved_crc := d(55, 24)
            calcted_crc := crc3B(crc_32, d(23, 0))
            chk_tchar := is_tchar(d(63, 56))
            fsm := FIN
          }
        }

        // Default case
        when(c =/= 0x00.U && c =/= 0xFF.U && c =/= 0xFE.U && c =/= 0xFC.U && 
             c =/= 0xF8.U && c =/= 0xF0.U && c =/= 0xE0.U && c =/= 0xC0.U && c =/= 0x80.U) {
          tlast_i := true.B
          tvalid_d0 := false.B
          tvalid_i := true.B
          fsm := IDLE
          set_stats(len, tuser_i(0))
        }
      }

      is(FINL4) {
        len := 0.U
        tkeep_i := last_tkeep_i
        tlast_i := true.B
        tvalid_d0 := false.B
        crc_32 := CRC802_3_PRESET
        when((~crc_rev(calcted_crc) === rcved_crc) && chk_tchar) {
          tuser_i := 1.U
          set_stats(len, true.B)
        }.otherwise {
          set_stats(len, false.B)
        }

        when(sof_lane0(d, c)) {
          fsm := ST_LANE0
        }.elsewhen(sof_lane4(d, c)) {
          fsm := ST_LANE4
        }.otherwise {
          fsm := IDLE
        }
      }
    }

    // Default case - return to IDLE on invalid state
    when(fsm =/= SRES && fsm =/= IDLE && fsm =/= ST_LANE0 && 
         fsm =/= ST_LANE4 && fsm =/= FIN && fsm =/= D_LANE4 && fsm =/= FINL4) {
      fsm := IDLE
    }
  }
}

object Xgmii2Axis {
  def apply(): Xgmii2Axis = Module(new Xgmii2Axis)
}
