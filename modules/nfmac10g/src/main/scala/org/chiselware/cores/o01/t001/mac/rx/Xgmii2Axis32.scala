package org.chiselware.cores.o01.t001.mac.rx
import org.chiselware.cores.o01.t001.Lfsr
import org.chiselware.cores.o01.t001.mac.AxisInterface
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}


class Xgmii2Axis32(
  val dataW: Int = 32,
  val ctrlW: Int = 4,
  val gbxIfEn: Boolean = false,
  val ptpTsEn: Boolean = false,
  val ptpTsW: Int = 96
) extends Module {
  // derived parameters
  val keepW = dataW / 8
  val userW = (if (ptpTsEn) ptpTsW else 0) + 1

  val io = IO(new Bundle {

    // XGMII input
    val xgmii_rxd      = Input(UInt(dataW.W))
    val xgmii_rxc      = Input(UInt(ctrlW.W))
    val xgmii_rx_valid = Input(Bool())

    // Receive interface (AXI stream)
    val m_axis_rx = new AxisInterface(dataW, userW)

    // PTP
    val ptp_ts = Input(UInt(ptpTsW.W))

    // Configuration
    val cfg_rx_max_pkt_len = Input(UInt(16.W))
    val cfg_rx_enable      = Input(Bool())

    // Status
    val rx_start_packet      = Output(Bool())
    val stat_rx_byte         = Output(UInt(3.W))
    val stat_rx_pkt_len      = Output(UInt(16.W))
    val stat_rx_pkt_fragment = Output(Bool())
    val stat_rx_pkt_jabber   = Output(Bool())
    val stat_rx_pkt_ucast    = Output(Bool())
    val stat_rx_pkt_mcast    = Output(Bool())
    val stat_rx_pkt_bcast    = Output(Bool())
    val stat_rx_pkt_vlan     = Output(Bool())
    val stat_rx_pkt_good     = Output(Bool())
    val stat_rx_pkt_bad      = Output(Bool())
    val stat_rx_err_oversize = Output(Bool())
    val stat_rx_err_bad_fcs  = Output(Bool())
    val stat_rx_err_bad_block= Output(Bool())
    val stat_rx_err_framing  = Output(Bool())
    val stat_rx_err_preamble = Output(Bool())
  })

  // --- Constants ---
  val ETH_PRE     = "h55".U(8.W)
  val ETH_SFD     = "hD5".U(8.W)
  val XGMII_IDLE  = "h07".U(8.W)
  val XGMII_START = "hfb".U(8.W)
  val XGMII_TERM  = "hfd".U(8.W)
  val XGMII_ERROR = "hfe".U(8.W)

  object State extends ChiselEnum {
    val STATE_IDLE, STATE_PREAMBLE, STATE_PAYLOAD, STATE_LAST = Value
  }
  import State._

  // --- Registers ---
  val state_reg = RegInit(STATE_IDLE)

  // Pipeline registers
  val xgmii_rxd_d0_reg = RegInit(0.U(dataW.W))
  val xgmii_rxd_d1_reg = RegInit(0.U(dataW.W))
  val xgmii_rxd_d2_reg = RegInit(0.U(dataW.W))

  val xgmii_start_d0_reg = RegInit(false.B)
  val xgmii_start_d1_reg = RegInit(false.B)
  val xgmii_start_d2_reg = RegInit(false.B)

  // Logic registers
  val term_present_reg     = RegInit(false.B)
  val term_first_cycle_reg = RegInit(false.B)
  val term_lane_reg        = RegInit(0.U(2.W))
  val term_lane_d0_reg     = RegInit(0.U(2.W))
  val framing_error_reg    = RegInit(false.B)

  val frame_oversize_reg      = RegInit(false.B)
  val pre_ok_reg              = RegInit(false.B)
  val hdr_ptr_reg             = RegInit(0.U(3.W))
  val is_mcast_reg            = RegInit(false.B)
  val is_bcast_reg            = RegInit(false.B)
  val is_8021q_reg            = RegInit(false.B)
  val frame_len_reg           = RegInit(0.U(16.W))
  val frame_len_lim_cyc_reg   = RegInit(0.U(14.W))
  val frame_len_lim_last_reg  = RegInit(0.U(2.W))
  val frame_len_lim_check_reg = RegInit(false.B)

  // AXI Output Registers
  val m_axis_rx_tdata_reg  = RegInit(0.U(dataW.W))
  val m_axis_rx_tkeep_reg  = RegInit(0.U(keepW.W))
  val m_axis_rx_tvalid_reg = RegInit(false.B)
  val m_axis_rx_tlast_reg  = RegInit(false.B)
  val m_axis_rx_tuser_reg  = RegInit(false.B)

  val start_packet_reg     = RegInit(false.B)
  val ptp_ts_out_reg       = RegInit(0.U(ptpTsW.W))

  // Status Registers (Reset enabled)
  val stat_reset_block = (
     RegInit(0.U(3.W)), RegInit(0.U(16.W)), RegInit(false.B), RegInit(false.B),
     RegInit(false.B), RegInit(false.B), RegInit(false.B), RegInit(false.B),
     RegInit(false.B), RegInit(false.B), RegInit(false.B), RegInit(false.B),
     RegInit(false.B), RegInit(false.B), RegInit(false.B)
  )
  val (stat_rx_byte_reg, stat_rx_pkt_len_reg, stat_rx_pkt_fragment_reg, stat_rx_pkt_jabber_reg,
       stat_rx_pkt_ucast_reg, stat_rx_pkt_mcast_reg, stat_rx_pkt_bcast_reg, stat_rx_pkt_vlan_reg,
       stat_rx_pkt_good_reg, stat_rx_pkt_bad_reg, stat_rx_err_oversize_reg, stat_rx_err_bad_fcs_reg,
       stat_rx_err_bad_block_reg, stat_rx_err_framing_reg, stat_rx_err_preamble_reg) = stat_reset_block


  // --- CRC Calculation ---
  val crc_state_reg = RegInit("hFFFFFFFF".U(32.W))
  val crc_valid_reg = RegInit(0.U(4.W))

  // Input masking for LFSR
  val xgmii_rxd_masked_wires = Wire(Vec(ctrlW, UInt(8.W)))
  for (n <- 0 until ctrlW) {
    // SV: (n > 0 && xgmii_rxc[n]) ? 8'd0 : xgmii_rxd[n*8 +: 8]
    if (n > 0) {
      xgmii_rxd_masked_wires(n) := Mux(io.xgmii_rxc(n), 0.U, io.xgmii_rxd(n*8+7, n*8))
    } else {
      xgmii_rxd_masked_wires(n) := io.xgmii_rxd(n*8+7, n*8)
    }
  }
  val xgmii_rxd_masked = xgmii_rxd_masked_wires.asUInt

  val eth_crc = Module(new Lfsr(
    lfsrW = 32, lfsrPoly = BigInt("4c11db7", 16), lfsrGalois = true, 
    lfsrFeedForward = false, reverse = true, dataW = dataW, 
    dataInEn = true, dataOutEn = false
  ))
  eth_crc.io.data_in := xgmii_rxd_masked
  eth_crc.io.state_in := crc_state_reg
  val crc_state_out = eth_crc.io.state_out

  val crc_valid = Wire(Vec(4, Bool()))
  crc_valid(3) := crc_state_reg === ~("h2144df1c".U(32.W))
  crc_valid(2) := crc_state_reg === ~("hc622f71d".U(32.W))
  crc_valid(1) := crc_state_reg === ~("hb1c2a1a3".U(32.W))
  crc_valid(0) := crc_state_reg === ~("h9d6cdf7e".U(32.W))

  // --- Output Assignments ---
  io.m_axis_rx.tdata  := m_axis_rx_tdata_reg
  io.m_axis_rx.tkeep  := m_axis_rx_tkeep_reg
  io.m_axis_rx.tstrb  := m_axis_rx_tkeep_reg // tstrb mirrors tkeep
  io.m_axis_rx.tvalid := m_axis_rx_tvalid_reg
  io.m_axis_rx.tlast  := m_axis_rx_tlast_reg
  io.m_axis_rx.tid    := 0.U
  io.m_axis_rx.tdest  := 0.U
  
  if (ptpTsEn) {
    io.m_axis_rx.tuser := Cat(ptp_ts_out_reg, m_axis_rx_tuser_reg)
  } else {
    io.m_axis_rx.tuser := m_axis_rx_tuser_reg
  }

  io.rx_start_packet      := start_packet_reg
  io.stat_rx_byte         := stat_rx_byte_reg
  io.stat_rx_pkt_len      := stat_rx_pkt_len_reg
  io.stat_rx_pkt_fragment := stat_rx_pkt_fragment_reg
  io.stat_rx_pkt_jabber   := stat_rx_pkt_jabber_reg
  io.stat_rx_pkt_ucast    := stat_rx_pkt_ucast_reg
  io.stat_rx_pkt_mcast    := stat_rx_pkt_mcast_reg
  io.stat_rx_pkt_bcast    := stat_rx_pkt_bcast_reg
  io.stat_rx_pkt_vlan     := stat_rx_pkt_vlan_reg
  io.stat_rx_pkt_good     := stat_rx_pkt_good_reg
  io.stat_rx_pkt_bad      := stat_rx_pkt_bad_reg
  io.stat_rx_err_oversize := stat_rx_err_oversize_reg
  io.stat_rx_err_bad_fcs  := stat_rx_err_bad_fcs_reg
  io.stat_rx_err_bad_block:= stat_rx_err_bad_block_reg
  io.stat_rx_err_framing  := stat_rx_err_framing_reg
  io.stat_rx_err_preamble := stat_rx_err_preamble_reg


  // --- Combinational Logic (Next State) ---
  // Using Wires initialized to current Reg values (mimics 'always_comb' defaults)
  val state_next = WireDefault(STATE_IDLE)
  
  val frame_oversize_next      = WireDefault(frame_oversize_reg)
  val pre_ok_next              = WireDefault(pre_ok_reg)
  val hdr_ptr_next             = WireDefault(hdr_ptr_reg)
  val is_mcast_next            = WireDefault(is_mcast_reg)
  val is_bcast_next            = WireDefault(is_bcast_reg)
  val is_8021q_next            = WireDefault(is_8021q_reg)
  val frame_len_next           = WireDefault(frame_len_reg)
  val frame_len_lim_cyc_next   = WireDefault(frame_len_lim_cyc_reg)
  val frame_len_lim_last_next  = WireDefault(frame_len_lim_last_reg)
  val frame_len_lim_check_next = WireDefault(frame_len_lim_check_reg)

  val m_axis_rx_tdata_next     = WireDefault(xgmii_rxd_d2_reg)
  val m_axis_rx_tkeep_next     = WireDefault(Fill(keepW, 1.U))
  val m_axis_rx_tvalid_next    = WireDefault(false.B)
  val m_axis_rx_tlast_next     = WireDefault(false.B)
  val m_axis_rx_tuser_next     = WireDefault(false.B)
  val ptp_ts_out_next          = WireDefault(ptp_ts_out_reg)
  val start_packet_next        = WireDefault(false.B)

  val stat_rx_byte_next          = WireDefault(0.U(3.W))
  val stat_rx_pkt_len_next       = WireDefault(0.U(16.W))
  val stat_rx_pkt_fragment_next  = WireDefault(false.B)
  val stat_rx_pkt_jabber_next    = WireDefault(false.B)
  val stat_rx_pkt_ucast_next     = WireDefault(false.B)
  val stat_rx_pkt_mcast_next     = WireDefault(false.B)
  val stat_rx_pkt_bcast_next     = WireDefault(false.B)
  val stat_rx_pkt_vlan_next      = WireDefault(false.B)
  val stat_rx_pkt_good_next      = WireDefault(false.B)
  val stat_rx_pkt_bad_next       = WireDefault(false.B)
  val stat_rx_err_oversize_next  = WireDefault(false.B)
  val stat_rx_err_bad_fcs_next   = WireDefault(false.B)
  val stat_rx_err_bad_block_next = WireDefault(false.B)
  val stat_rx_err_framing_next   = WireDefault(false.B)
  val stat_rx_err_preamble_next  = WireDefault(false.B)

  when (gbxIfEn.B && !io.xgmii_rx_valid) {
    state_next := state_reg
  } .otherwise {
    // Frame Length Counter
    when (frame_len_reg(15, 2).andR === 0.U) {
      when (term_present_reg) {
        frame_len_next := frame_len_reg + term_lane_reg
      } .otherwise {
        frame_len_next := frame_len_reg + ctrlW.U
      }
    } .otherwise {
      frame_len_next := "hFFFF".U
    }

    // Max Frame Length Enforcement
    when (frame_len_lim_cyc_reg =/= 0.U) {
      frame_len_lim_cyc_next := frame_len_lim_cyc_reg - 1.U
    } .otherwise {
      frame_len_lim_cyc_next := 0.U
    }

    when (frame_len_lim_cyc_reg === 2.U) {
      frame_len_lim_check_next := true.B
    }

    // Address and Ethertype Checks
    when (hdr_ptr_reg.andR === 0.U) {
      hdr_ptr_next := hdr_ptr_reg + 1.U
    }

    switch (hdr_ptr_reg) {
      is (0.U) {
        is_mcast_next := xgmii_rxd_d2_reg(0)
        is_bcast_next := xgmii_rxd_d2_reg.andR
      }
      is (1.U) {
        is_bcast_next := is_bcast_reg && xgmii_rxd_d2_reg(15, 0).andR
      }
      is (3.U) {
        is_8021q_next := Cat(xgmii_rxd_d2_reg(7, 0), xgmii_rxd_d2_reg(15, 8)) === "h8100".U
      }
    }

    // FSM
    switch (state_reg) {
      is (STATE_IDLE) {
        frame_oversize_next := false.B
        frame_len_next := ctrlW.U
        frame_len_lim_cyc_next := io.cfg_rx_max_pkt_len(15, 2)
        frame_len_lim_last_next := io.cfg_rx_max_pkt_len(1, 0)
        frame_len_lim_check_next := false.B
        hdr_ptr_next := 0.U
        
        pre_ok_next := xgmii_rxd_d2_reg(31, 8) === "h555555".U

        when (xgmii_start_d2_reg && io.cfg_rx_enable) {
          when (framing_error_reg) {
            stat_rx_err_framing_next := true.B
            state_next := STATE_IDLE
          } .otherwise {
            stat_rx_byte_next := ctrlW.U
            state_next := STATE_PREAMBLE
          }
        } .otherwise {
          if (ptpTsEn) ptp_ts_out_next := io.ptp_ts
          state_next := STATE_IDLE
        }
      }

      is (STATE_PREAMBLE) {
        hdr_ptr_next := 0.U
        pre_ok_next := pre_ok_reg && (xgmii_rxd_d2_reg === "hD5555555".U)

        when (framing_error_reg) {
          stat_rx_err_framing_next := true.B
          state_next := STATE_IDLE
        } .otherwise {
          start_packet_next := true.B
          stat_rx_byte_next := ctrlW.U
          state_next := STATE_PAYLOAD
        }
      }

      is (STATE_PAYLOAD) {
        m_axis_rx_tdata_next  := xgmii_rxd_d2_reg
        m_axis_rx_tkeep_next  := Fill(keepW, 1.U)
        m_axis_rx_tvalid_next := true.B

        when (term_present_reg) {
          stat_rx_byte_next := term_lane_reg
          when (frame_len_lim_check_reg && (frame_len_lim_last_reg < term_lane_reg)) {
            frame_oversize_next := true.B
          }
        } .otherwise {
          stat_rx_byte_next := ctrlW.U
          when (frame_len_lim_check_reg) {
            frame_oversize_next := true.B
          }
        }

        when (framing_error_reg) {
          m_axis_rx_tlast_next := true.B
          m_axis_rx_tuser_next := true.B
          stat_rx_pkt_bad_next := true.B
          stat_rx_pkt_len_next := frame_len_next
          
          stat_rx_pkt_ucast_next := !is_mcast_reg
          stat_rx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
          stat_rx_pkt_bcast_next := is_bcast_reg
          stat_rx_pkt_vlan_next  := is_8021q_reg
          stat_rx_err_oversize_next := frame_oversize_next
          stat_rx_err_framing_next  := true.B
          stat_rx_err_preamble_next := !pre_ok_reg
          stat_rx_pkt_fragment_next := frame_len_next(15, 6) === 0.U
          stat_rx_pkt_jabber_next   := frame_oversize_next
          
          state_next := STATE_IDLE
        } .elsewhen (term_first_cycle_reg) {
          m_axis_rx_tkeep_next := "b1111".U 
          m_axis_rx_tlast_next := true.B
          
          // CRC Check for 32-bit specific logic
          if (dataW == 32) {
             when (crc_valid_reg(3)) {
               when (frame_oversize_next) {
                 m_axis_rx_tuser_next := true.B
                 stat_rx_pkt_bad_next := true.B
               } .otherwise {
                 m_axis_rx_tuser_next := false.B
                 stat_rx_pkt_good_next := true.B
               }
             } .otherwise {
               m_axis_rx_tuser_next := true.B
               stat_rx_pkt_fragment_next := frame_len_next(15, 6) === 0.U
               stat_rx_pkt_jabber_next := frame_oversize_next
               stat_rx_pkt_bad_next := true.B
               stat_rx_err_bad_fcs_next := true.B
             }
          }

          stat_rx_pkt_len_next      := frame_len_next
          stat_rx_pkt_ucast_next    := !is_mcast_reg
          stat_rx_pkt_mcast_next    := is_mcast_reg && !is_bcast_reg
          stat_rx_pkt_bcast_next    := is_bcast_reg
          stat_rx_pkt_vlan_next     := is_8021q_reg
          stat_rx_err_oversize_next := frame_oversize_next
          stat_rx_err_preamble_next := !pre_ok_reg

          state_next := STATE_IDLE

        } .elsewhen (term_present_reg) {
           state_next := STATE_LAST
        } .otherwise {
           state_next := STATE_PAYLOAD
        }
      }

      is (STATE_LAST) {
        m_axis_rx_tdata_next := xgmii_rxd_d2_reg
        val shift_amt = (ctrlW.U - term_lane_d0_reg)
        m_axis_rx_tkeep_next := (Fill(keepW, 1.U) >> shift_amt)
        m_axis_rx_tvalid_next := true.B
        m_axis_rx_tlast_next  := true.B

        val crc_ok = (term_lane_d0_reg === 1.U && crc_valid_reg(0)) ||
                     (term_lane_d0_reg === 2.U && crc_valid_reg(1)) ||
                     (term_lane_d0_reg === 3.U && crc_valid_reg(2))

        when (crc_ok) {
           when (frame_oversize_reg) {
             m_axis_rx_tuser_next := true.B
             stat_rx_pkt_bad_next := true.B
           } .otherwise {
             m_axis_rx_tuser_next := false.B
             stat_rx_pkt_good_next := true.B
           }
        } .otherwise {
           m_axis_rx_tuser_next := true.B
           stat_rx_pkt_fragment_next := frame_len_reg(15, 6) === 0.U
           stat_rx_pkt_jabber_next := frame_oversize_reg
           stat_rx_pkt_bad_next := true.B
           stat_rx_err_bad_fcs_next := true.B
        }

        stat_rx_pkt_len_next      := frame_len_reg
        stat_rx_pkt_ucast_next    := !is_mcast_reg
        stat_rx_pkt_mcast_next    := is_mcast_reg && !is_bcast_reg
        stat_rx_pkt_bcast_next    := is_bcast_reg
        stat_rx_pkt_vlan_next     := is_8021q_reg
        stat_rx_err_oversize_next := frame_oversize_reg
        stat_rx_err_preamble_next := !pre_ok_reg

        state_next := STATE_IDLE
      }
    }
  }

  // --- Sequential Updates ---
  // Registers updating from 'next' wires
  state_reg               := state_next
  frame_oversize_reg      := frame_oversize_next
  pre_ok_reg              := pre_ok_next
  hdr_ptr_reg             := hdr_ptr_next
  is_mcast_reg            := is_mcast_next
  is_bcast_reg            := is_bcast_next
  is_8021q_reg            := is_8021q_next
  frame_len_reg           := frame_len_next
  frame_len_lim_cyc_reg   := frame_len_lim_cyc_next
  frame_len_lim_last_reg  := frame_len_lim_last_next
  frame_len_lim_check_reg := frame_len_lim_check_next

  m_axis_rx_tdata_reg     := m_axis_rx_tdata_next
  m_axis_rx_tkeep_reg     := m_axis_rx_tkeep_next
  m_axis_rx_tvalid_reg    := m_axis_rx_tvalid_next
  m_axis_rx_tlast_reg     := m_axis_rx_tlast_next
  m_axis_rx_tuser_reg     := m_axis_rx_tuser_next

  ptp_ts_out_reg          := ptp_ts_out_next
  start_packet_reg        := start_packet_next

  stat_rx_byte_reg          := stat_rx_byte_next
  stat_rx_pkt_len_reg       := stat_rx_pkt_len_next
  stat_rx_pkt_fragment_reg  := stat_rx_pkt_fragment_next
  stat_rx_pkt_jabber_reg    := stat_rx_pkt_jabber_next
  stat_rx_pkt_ucast_reg     := stat_rx_pkt_ucast_next
  stat_rx_pkt_mcast_reg     := stat_rx_pkt_mcast_next
  stat_rx_pkt_bcast_reg     := stat_rx_pkt_bcast_next
  stat_rx_pkt_vlan_reg      := stat_rx_pkt_vlan_next
  stat_rx_pkt_good_reg      := stat_rx_pkt_good_next
  stat_rx_pkt_bad_reg       := stat_rx_pkt_bad_next
  stat_rx_err_oversize_reg  := stat_rx_err_oversize_next
  stat_rx_err_bad_fcs_reg   := stat_rx_err_bad_fcs_next
  stat_rx_err_bad_block_reg := stat_rx_err_bad_block_next
  stat_rx_err_framing_reg   := stat_rx_err_framing_next
  stat_rx_err_preamble_reg  := stat_rx_err_preamble_next

  // --- Input Data Path & Termination Check Logic ---
  // Matches the SV always_ff block for input processing
  when (!gbxIfEn.B || io.xgmii_rx_valid) {
    xgmii_start_d0_reg := false.B
    term_present_reg := false.B
    term_first_cycle_reg := false.B
    term_lane_reg := 0.U
    framing_error_reg := io.xgmii_rxc =/= 0.U

    // Replicate SV loop priority: "for (integer i = CTRL_W-1; i >= 0; i = i - 1)"
    // Loop runs high down to low. Last match wins. So i=0 has highest priority.
    // In Chisel `when` blocks, the *last* `when` statement executed wins.
    // So we iterate 3 down to 0, which means 0 comes last and overrides 3.
    for (i <- (ctrlW - 1) to 0 by -1) {
      val is_term = io.xgmii_rxc(i) && (io.xgmii_rxd(i*8+7, i*8) === XGMII_TERM)
      when (is_term) {
        term_present_reg := true.B
        term_first_cycle_reg := (i == 0).B
        term_lane_reg := i.U(2.W)
        val mask = ((1 << i) - 1).U(ctrlW.W)
        framing_error_reg := (io.xgmii_rxc & mask) =/= 0.U
      }
    }

    when (io.xgmii_rxc(0) && io.xgmii_rxd(7, 0) === XGMII_START) {
      xgmii_start_d0_reg := true.B
    }

    when (xgmii_start_d0_reg) {
      crc_state_reg := "hFFFFFFFF".U
    } .otherwise {
      crc_state_reg := crc_state_out
    }

    term_lane_d0_reg   := term_lane_reg
    crc_valid_reg      := crc_valid.asUInt
    
    xgmii_rxd_d0_reg   := xgmii_rxd_masked
    xgmii_rxd_d1_reg   := xgmii_rxd_d0_reg
    xgmii_rxd_d2_reg   := xgmii_rxd_d1_reg
    
    xgmii_start_d1_reg := xgmii_start_d0_reg
    xgmii_start_d2_reg := xgmii_start_d1_reg
  }
}



object Xgmii2Axis32 {
  def apply(p: Xgmii2Axis32Params): Xgmii2Axis32 = Module(new Xgmii2Axis32(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    ptpTsEn = p.ptpTsEn,
    ptpTsW = p.ptpTsW
  ))
}

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  Xgmii2Axis32Params.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Xgmii2Axis32(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        ptpTsEn = p.ptpTsEn,
        ptpTsW = p.ptpTsW
      ),
      firtoolOpts = Array(
        "--lowering-options=disallowLocalVariables,disallowPackedArrays",
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        s"-o=${coreDir}/generated/synTestCases/$configName"
      )
    )
    // Synthesis collateral generation
    sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
    YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
    RunScriptFile.create(mainClassName, Xgmii2Axis32Params.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}