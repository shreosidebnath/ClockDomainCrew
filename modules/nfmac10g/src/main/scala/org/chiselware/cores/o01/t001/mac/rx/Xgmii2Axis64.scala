package org.chiselware.cores.o01.t001.mac.rx
import org.chiselware.cores.o01.t001.Lfsr
import org.chiselware.cores.o01.t001.mac.AxisInterface
import org.chiselware.cores.o01.t001.mac.AxisInterfaceParams
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class Xgmii2Axis64(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val gbxIfEn: Boolean = false,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96
) extends Module {

  val keepW = dataW / 8
  val userW = (if (ptpTsEn) ptpTsW else 0) + 1

  // Elaborative configuration checks
  require(dataW == 64, s"Error: Interface width must be 64 (instance dataW=$dataW)")
  require(keepW * 8 == dataW && ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")

  val io = IO(new Bundle {
    // XGMII input
    val xgmii_rxd = Input(UInt(dataW.W))
    val xgmii_rxc = Input(UInt(ctrlW.W))
    val xgmii_rx_valid = Input(Bool())


    val m_axis_rx = new AxisInterface(AxisInterfaceParams(
      dataW = dataW, keepW = keepW, userEn = true, userW = userW
    ))

    // PTP
    val ptp_ts = Input(UInt(ptpTsW.W))

    // Configuration
    val cfg_rx_max_pkt_len = Input(UInt(16.W))
    val cfg_rx_enable = Input(Bool())

    // Status
    val rx_start_packet = Output(UInt(2.W))
    val stat_rx_byte = Output(UInt(4.W))
    val stat_rx_pkt_len = Output(UInt(16.W))
    val stat_rx_pkt_fragment = Output(Bool())
    val stat_rx_pkt_jabber = Output(Bool())
    val stat_rx_pkt_ucast = Output(Bool())
    val stat_rx_pkt_mcast = Output(Bool())
    val stat_rx_pkt_bcast = Output(Bool())
    val stat_rx_pkt_vlan = Output(Bool())
    val stat_rx_pkt_good = Output(Bool())
    val stat_rx_pkt_bad = Output(Bool())
    val stat_rx_err_oversize = Output(Bool())
    val stat_rx_err_bad_fcs = Output(Bool())
    val stat_rx_err_bad_block = Output(Bool())
    val stat_rx_err_framing = Output(Bool())
    val stat_rx_err_preamble = Output(Bool())
  })

  // Constants
  val ETH_PRE = "h55".U(8.W)
  val ETH_SFD = "hD5".U(8.W)
  val XGMII_IDLE = "h07".U(8.W)
  val XGMII_START = "hfb".U(8.W)
  val XGMII_TERM = "hfd".U(8.W)
  val XGMII_ERROR = "hfe".U(8.W)

  val STATE_IDLE = 0.U(2.W)
  val STATE_PAYLOAD = 1.U(2.W)
  val STATE_LAST = 2.U(2.W)

  // Registers
  val state_reg = RegInit(STATE_IDLE)
  val lanes_swapped_reg = RegInit(false.B)
  val lanes_swapped_d1_reg = RegInit(false.B)
  val swap_rxd_reg = RegInit(0.U(32.W))
  val swap_rxc_reg = RegInit(0.U(4.W))
  val swap_rxc_term_reg = RegInit(0.U(4.W))

  val term_present_reg = RegInit(false.B)
  val term_first_cycle_reg = RegInit(false.B)
  val term_lane_reg = RegInit(0.U(3.W))
  val term_lane_d0_reg = RegInit(0.U(3.W))
  val framing_error_reg = RegInit(false.B)
  val framing_error_d0_reg = RegInit(false.B)

  val xgmii_rxd_d0_reg = RegInit(0.U(dataW.W))
  val xgmii_rxd_d1_reg = RegInit(0.U(dataW.W))

  val xgmii_start_swap_reg = RegInit(false.B)
  val xgmii_start_d0_reg = RegInit(false.B)
  val xgmii_start_d1_reg = RegInit(false.B)

  val frame_oversize_reg = RegInit(false.B)
  val pre_ok_reg = RegInit(false.B)
  val hdr_ptr_reg = RegInit(0.U(2.W))
  val is_mcast_reg = RegInit(false.B)
  val is_bcast_reg = RegInit(false.B)
  val is_8021q_reg = RegInit(false.B)
  val frame_len_reg = RegInit(0.U(16.W))
  val frame_len_lim_cyc_reg = RegInit(0.U(13.W))
  val frame_len_lim_last_reg = RegInit(0.U(3.W))
  val frame_len_lim_check_reg = RegInit(false.B)

  val m_axis_rx_tdata_reg = RegInit(0.U(dataW.W))
  val m_axis_rx_tkeep_reg = RegInit(0.U(keepW.W))
  val m_axis_rx_tvalid_reg = RegInit(false.B)
  val m_axis_rx_tlast_reg = RegInit(false.B)
  val m_axis_rx_tuser_reg = RegInit(false.B)

  val start_packet_reg = RegInit(0.U(2.W))

  val stat_rx_byte_reg = RegInit(0.U(4.W))
  val stat_rx_pkt_len_reg = RegInit(0.U(16.W))
  val stat_rx_pkt_fragment_reg = RegInit(false.B)
  val stat_rx_pkt_jabber_reg = RegInit(false.B)
  val stat_rx_pkt_ucast_reg = RegInit(false.B)
  val stat_rx_pkt_mcast_reg = RegInit(false.B)
  val stat_rx_pkt_bcast_reg = RegInit(false.B)
  val stat_rx_pkt_vlan_reg = RegInit(false.B)
  val stat_rx_pkt_good_reg = RegInit(false.B)
  val stat_rx_pkt_bad_reg = RegInit(false.B)
  val stat_rx_err_oversize_reg = RegInit(false.B)
  val stat_rx_err_bad_fcs_reg = RegInit(false.B)
  val stat_rx_err_bad_block_reg = RegInit(false.B)
  val stat_rx_err_framing_reg = RegInit(false.B)
  val stat_rx_err_preamble_reg = RegInit(false.B)

  val ptp_ts_reg = RegInit(0.U(ptpTsW.W))
  val ptp_ts_out_reg = RegInit(0.U(ptpTsW.W))
  
  // Handled differently because it's partially assigned conditionally
  val ptp_ts_adj_reg = RegInit(0.U(ptpTsW.W))
  val ptp_ts_borrow_reg = RegInit(false.B)

  val crc_state_reg = RegInit("hffffffff".U(32.W)) // '1 in SV
  val crc_valid_reg = RegInit(0.U(8.W))

  val last_ts_reg = RegInit(0.U(20.W))
  val ts_inc_reg = RegInit(0.U(20.W))

  // Wires for _next state
  val state_next = WireDefault(STATE_IDLE)
  val frame_oversize_next = WireDefault(frame_oversize_reg)
  val pre_ok_next = WireDefault(pre_ok_reg)
  val hdr_ptr_next = WireDefault(hdr_ptr_reg)
  val is_mcast_next = WireDefault(is_mcast_reg)
  val is_bcast_next = WireDefault(is_bcast_reg)
  val is_8021q_next = WireDefault(is_8021q_reg)
  val frame_len_next = WireDefault(frame_len_reg)
  val frame_len_lim_cyc_next = WireDefault(frame_len_lim_cyc_reg)
  val frame_len_lim_last_next = WireDefault(frame_len_lim_last_reg)
  val frame_len_lim_check_next = WireDefault(frame_len_lim_check_reg)

  val m_axis_rx_tdata_next = WireDefault(xgmii_rxd_d1_reg)
  val m_axis_rx_tkeep_next = WireDefault(((1 << keepW) - 1).U(keepW.W))
  val m_axis_rx_tvalid_next = WireDefault(false.B)
  val m_axis_rx_tlast_next = WireDefault(false.B)
  val m_axis_rx_tuser_next = WireDefault(false.B)

  val ptp_ts_out_next = WireDefault(ptp_ts_out_reg)

  val stat_rx_byte_next = WireDefault(0.U(4.W))
  val stat_rx_pkt_len_next = WireDefault(0.U(16.W))
  val stat_rx_pkt_fragment_next = WireDefault(false.B)
  val stat_rx_pkt_jabber_next = WireDefault(false.B)
  val stat_rx_pkt_ucast_next = WireDefault(false.B)
  val stat_rx_pkt_mcast_next = WireDefault(false.B)
  val stat_rx_pkt_bcast_next = WireDefault(false.B)
  val stat_rx_pkt_vlan_next = WireDefault(false.B)
  val stat_rx_pkt_good_next = WireDefault(false.B)
  val stat_rx_pkt_bad_next = WireDefault(false.B)
  val stat_rx_err_oversize_next = WireDefault(false.B)
  val stat_rx_err_bad_fcs_next = WireDefault(false.B)
  val stat_rx_err_bad_block_next = WireDefault(false.B)
  val stat_rx_err_framing_next = WireDefault(false.B)
  val stat_rx_err_preamble_next = WireDefault(false.B)

  // Mask input data
  val xgmii_rxd_masked_vec = Wire(Vec(ctrlW, UInt(8.W)))
  val xgmii_term_vec = Wire(Vec(ctrlW, Bool()))

  for (n <- 0 until ctrlW) {
    val rxd_byte = io.xgmii_rxd((n * 8) + 7, n * 8)
    if (n > 0) {
      xgmii_rxd_masked_vec(n) := Mux(io.xgmii_rxc(n), 0.U, rxd_byte)
    } else {
      xgmii_rxd_masked_vec(n) := rxd_byte
    }
    xgmii_term_vec(n) := io.xgmii_rxc(n) && (rxd_byte === XGMII_TERM)
  }
  
  val xgmii_rxd_masked = xgmii_rxd_masked_vec.asUInt
  val xgmii_term = xgmii_term_vec.asUInt

  // CRC Instantiation
  val crc_inst = Module(new Lfsr(
    lfsrW = 32,
    lfsrPoly = BigInt("4c11db7", 16),
    lfsrGalois = true,
    lfsrFeedForward = false,
    reverse = true,
    dataW = dataW,
    dataInEn = true,
    dataOutEn = false
  ))
  crc_inst.io.data_in := Mux(xgmii_start_swap_reg, Cat(xgmii_rxd_masked(63, 32), 0.U(32.W)), xgmii_rxd_masked)
  crc_inst.io.state_in := crc_state_reg
  val crc_state = crc_inst.io.state_out

  // CRC valid checks
  val crc_valid = Wire(Vec(8, Bool()))
  crc_valid(7) := crc_state_reg === (~"h2144df1c".U(32.W)).asUInt
  crc_valid(6) := crc_state_reg === (~"hc622f71d".U(32.W)).asUInt
  crc_valid(5) := crc_state_reg === (~"hb1c2a1a3".U(32.W)).asUInt
  crc_valid(4) := crc_state_reg === (~"h9d6cdf7e".U(32.W)).asUInt
  crc_valid(3) := crc_state_reg === (~"h6522df69".U(32.W)).asUInt
  crc_valid(2) := crc_state_reg === (~"he60914ae".U(32.W)).asUInt
  crc_valid(1) := crc_state_reg === (~"he38a6876".U(32.W)).asUInt
  crc_valid(0) := crc_state_reg === (~"h6b87b1ec".U(32.W)).asUInt

  // AXI Output Assignments
  io.m_axis_rx.tdata := m_axis_rx_tdata_reg
  io.m_axis_rx.tkeep := m_axis_rx_tkeep_reg
  io.m_axis_rx.tstrb := m_axis_rx_tkeep_reg
  io.m_axis_rx.tvalid := m_axis_rx_tvalid_reg
  io.m_axis_rx.tlast := m_axis_rx_tlast_reg
  io.m_axis_rx.tid := 0.U
  io.m_axis_rx.tdest := 0.U
  
  if (ptpTsEn) {
    io.m_axis_rx.tuser := Cat(ptp_ts_out_reg, m_axis_rx_tuser_reg)
  } else {
    io.m_axis_rx.tuser := m_axis_rx_tuser_reg
  }

  // Status Output Assignments
  io.rx_start_packet := start_packet_reg
  io.stat_rx_byte := stat_rx_byte_reg
  io.stat_rx_pkt_len := stat_rx_pkt_len_reg
  io.stat_rx_pkt_fragment := stat_rx_pkt_fragment_reg
  io.stat_rx_pkt_jabber := stat_rx_pkt_jabber_reg
  io.stat_rx_pkt_ucast := stat_rx_pkt_ucast_reg
  io.stat_rx_pkt_mcast := stat_rx_pkt_mcast_reg
  io.stat_rx_pkt_bcast := stat_rx_pkt_bcast_reg
  io.stat_rx_pkt_vlan := stat_rx_pkt_vlan_reg
  io.stat_rx_pkt_good := stat_rx_pkt_good_reg
  io.stat_rx_pkt_bad := stat_rx_pkt_bad_reg
  io.stat_rx_err_oversize := stat_rx_err_oversize_reg
  io.stat_rx_err_bad_fcs := stat_rx_err_bad_fcs_reg
  io.stat_rx_err_bad_block := stat_rx_err_bad_block_reg
  io.stat_rx_err_framing := stat_rx_err_framing_reg
  io.stat_rx_err_preamble := stat_rx_err_preamble_reg

  // Combinational State Logic
  when(gbxIfEn.B && !io.xgmii_rx_valid) {
    state_next := state_reg
  }.otherwise {
    // frame len
    when(!frame_len_reg(15, 3).andR) {
      when(term_present_reg) {
        frame_len_next := frame_len_reg + term_lane_reg
      }.otherwise {
        frame_len_next := frame_len_reg + ctrlW.U
      }
    }.otherwise {
      frame_len_next := "hffff".U
    }

    when(frame_len_lim_cyc_reg =/= 0.U) {
      frame_len_lim_cyc_next := frame_len_lim_cyc_reg - 1.U
    }.otherwise {
      frame_len_lim_cyc_next := 0.U
    }

    when(frame_len_lim_cyc_reg === 2.U) {
      frame_len_lim_check_next := true.B
    }

    when(!hdr_ptr_reg.andR) {
      hdr_ptr_next := hdr_ptr_reg + 1.U
    }

    switch(hdr_ptr_reg) {
      is(0.U) {
        is_mcast_next := xgmii_rxd_d1_reg(0)
        is_bcast_next := xgmii_rxd_d1_reg(47, 0).andR
      }
      is(1.U) {
        is_8021q_next := Cat(xgmii_rxd_d1_reg(39, 32), xgmii_rxd_d1_reg(47, 40)) === "h8100".U
      }
    }

    switch(state_reg) {
      is(STATE_IDLE) {
        frame_oversize_next := false.B
        frame_len_next := ctrlW.U
        frame_len_lim_cyc_next := io.cfg_rx_max_pkt_len(15, 3)
        frame_len_lim_last_next := io.cfg_rx_max_pkt_len(2, 0)
        frame_len_lim_check_next := false.B
        hdr_ptr_next := 0.U

        pre_ok_next := xgmii_rxd_d1_reg(63, 8) === "hD5555555555555".U

        when(xgmii_start_d1_reg && io.cfg_rx_enable) {
          stat_rx_byte_next := ctrlW.U
          state_next := STATE_PAYLOAD
        }.otherwise {
          state_next := STATE_IDLE
        }
      }
      is(STATE_PAYLOAD) {
        m_axis_rx_tdata_next := xgmii_rxd_d1_reg
        m_axis_rx_tvalid_next := true.B
        
        if (ptpTsEn) {
          ptp_ts_out_next := Mux(!ptpTsFmtTod.B || ptp_ts_borrow_reg, ptp_ts_reg, ptp_ts_adj_reg)
        }

        when(term_present_reg) {
          stat_rx_byte_next := term_lane_reg
          when(frame_len_lim_check_reg && (frame_len_lim_last_reg < term_lane_reg)) {
            frame_oversize_next := true.B
          }
        }.otherwise {
          stat_rx_byte_next := ctrlW.U
          when(frame_len_lim_check_reg) {
            frame_oversize_next := true.B
          }
        }

        when(framing_error_reg || framing_error_d0_reg) {
          m_axis_rx_tlast_next := true.B
          m_axis_rx_tuser_next := true.B
          stat_rx_pkt_bad_next := true.B
          stat_rx_pkt_len_next := frame_len_next
          stat_rx_pkt_ucast_next := !is_mcast_reg
          stat_rx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
          stat_rx_pkt_bcast_next := is_bcast_reg
          stat_rx_pkt_vlan_next := is_8021q_reg
          stat_rx_err_oversize_next := frame_oversize_next
          stat_rx_err_framing_next := true.B
          stat_rx_err_preamble_next := !pre_ok_reg
          stat_rx_pkt_fragment_next := frame_len_next(15, 6) === 0.U
          stat_rx_pkt_jabber_next := frame_oversize_next
          state_next := STATE_IDLE
        }.elsewhen(term_first_cycle_reg) {
          m_axis_rx_tkeep_next := ((1 << keepW) - 1).U(keepW.W) >> (ctrlW.U - 4.U - term_lane_reg)
          m_axis_rx_tlast_next := true.B

          val crc_is_valid = (term_lane_reg === 0.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(3), crc_valid_reg(7))) ||
                             (term_lane_reg === 1.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(4), crc_valid(0))) ||
                             (term_lane_reg === 2.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(5), crc_valid(1))) ||
                             (term_lane_reg === 3.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(6), crc_valid(2))) ||
                             (term_lane_reg === 4.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(7), crc_valid(3)))

          when(crc_is_valid) {
            when(frame_oversize_next) {
              m_axis_rx_tuser_next := true.B
              stat_rx_pkt_bad_next := true.B
            }.otherwise {
              m_axis_rx_tuser_next := false.B
              stat_rx_pkt_good_next := true.B
            }
          }.otherwise {
            m_axis_rx_tuser_next := true.B
            stat_rx_pkt_fragment_next := frame_len_next(15, 6) === 0.U
            stat_rx_pkt_jabber_next := frame_oversize_next
            stat_rx_pkt_bad_next := true.B
            stat_rx_err_bad_fcs_next := true.B
          }

          stat_rx_pkt_len_next := frame_len_next
          stat_rx_pkt_ucast_next := !is_mcast_reg
          stat_rx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
          stat_rx_pkt_bcast_next := is_bcast_reg
          stat_rx_pkt_vlan_next := is_8021q_reg
          stat_rx_err_oversize_next := frame_oversize_next
          stat_rx_err_preamble_next := !pre_ok_reg
          state_next := STATE_IDLE

        }.elsewhen(term_present_reg) {
          state_next := STATE_LAST
        }.otherwise {
          state_next := STATE_PAYLOAD
        }
      }
      is(STATE_LAST) {
        m_axis_rx_tdata_next := xgmii_rxd_d1_reg
        m_axis_rx_tkeep_next := ((1 << keepW) - 1).U(keepW.W) >> (ctrlW.U + 4.U - term_lane_d0_reg)
        m_axis_rx_tvalid_next := true.B
        m_axis_rx_tlast_next := true.B

        val crc_is_valid = (term_lane_d0_reg === 5.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(0), crc_valid_reg(4))) ||
                           (term_lane_d0_reg === 6.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(1), crc_valid_reg(5))) ||
                           (term_lane_d0_reg === 7.U && Mux(lanes_swapped_d1_reg, crc_valid_reg(2), crc_valid_reg(6)))

        when(crc_is_valid) {
          when(frame_oversize_reg) {
            m_axis_rx_tuser_next := true.B
            stat_rx_pkt_bad_next := true.B
          }.otherwise {
            m_axis_rx_tuser_next := false.B
            stat_rx_pkt_good_next := true.B
          }
        }.otherwise {
          m_axis_rx_tuser_next := true.B
          stat_rx_pkt_fragment_next := frame_len_reg(15, 6) === 0.U
          stat_rx_pkt_jabber_next := frame_oversize_reg
          stat_rx_pkt_bad_next := true.B
          stat_rx_err_bad_fcs_next := true.B
        }

        stat_rx_pkt_len_next := frame_len_reg
        stat_rx_pkt_ucast_next := !is_mcast_reg
        stat_rx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
        stat_rx_pkt_bcast_next := is_bcast_reg
        stat_rx_pkt_vlan_next := is_8021q_reg
        stat_rx_err_oversize_next := frame_oversize_reg
        stat_rx_err_preamble_next := !pre_ok_reg

        when(xgmii_start_d1_reg && io.cfg_rx_enable) {
          state_next := STATE_PAYLOAD
        }.otherwise {
          state_next := STATE_IDLE
        }
      }
    }
  }

  // Sequential Logic Block
  state_reg := state_next
  frame_oversize_reg := frame_oversize_next
  pre_ok_reg := pre_ok_next
  hdr_ptr_reg := hdr_ptr_next
  is_mcast_reg := is_mcast_next
  is_bcast_reg := is_bcast_next
  is_8021q_reg := is_8021q_next
  frame_len_reg := frame_len_next
  frame_len_lim_cyc_reg := frame_len_lim_cyc_next
  frame_len_lim_last_reg := frame_len_lim_last_next
  frame_len_lim_check_reg := frame_len_lim_check_next

  m_axis_rx_tdata_reg := m_axis_rx_tdata_next
  m_axis_rx_tkeep_reg := m_axis_rx_tkeep_next
  m_axis_rx_tvalid_reg := m_axis_rx_tvalid_next
  m_axis_rx_tlast_reg := m_axis_rx_tlast_next
  m_axis_rx_tuser_reg := m_axis_rx_tuser_next

  ptp_ts_out_reg := ptp_ts_out_next
  start_packet_reg := 0.U

  stat_rx_byte_reg := stat_rx_byte_next
  stat_rx_pkt_len_reg := stat_rx_pkt_len_next
  stat_rx_pkt_fragment_reg := stat_rx_pkt_fragment_next
  stat_rx_pkt_jabber_reg := stat_rx_pkt_jabber_next
  stat_rx_pkt_ucast_reg := stat_rx_pkt_ucast_next
  stat_rx_pkt_mcast_reg := stat_rx_pkt_mcast_next
  stat_rx_pkt_bcast_reg := stat_rx_pkt_bcast_next
  stat_rx_pkt_vlan_reg := stat_rx_pkt_vlan_next
  stat_rx_pkt_good_reg := stat_rx_pkt_good_next
  stat_rx_pkt_bad_reg := stat_rx_pkt_bad_next
  stat_rx_err_oversize_reg := stat_rx_err_oversize_next
  stat_rx_err_bad_fcs_reg := stat_rx_err_bad_fcs_next
  stat_rx_err_bad_block_reg := stat_rx_err_bad_block_next
  stat_rx_err_framing_reg := stat_rx_err_framing_next
  stat_rx_err_preamble_reg := stat_rx_err_preamble_next

  when(!gbxIfEn.B || io.xgmii_rx_valid) {
    swap_rxd_reg := xgmii_rxd_masked(63, 32)
    swap_rxc_reg := io.xgmii_rxc(7, 4)
    swap_rxc_term_reg := xgmii_term(7, 4)

    xgmii_start_swap_reg := false.B
    xgmii_start_d0_reg := xgmii_start_swap_reg

    if (ptpTsEn && ptpTsFmtTod) {
      ptp_ts_adj_reg(15, 0) := ptp_ts_reg(15, 0)
      
      // $signed({1'b0, ptp_ts_reg[45:16]}) - $signed(31'd1000000000)
      val ts_sub = Cat(0.U(1.W), ptp_ts_reg(45, 16)).asSInt - 1000000000.S(32.W)
      ptp_ts_borrow_reg := ts_sub(31) // Extract sign bit
      
      // We manually construct the fields since partial assignments to regs aren't direct in Chisel
      val ptp_adj_mid = ts_sub(29, 0).asUInt 
      val ptp_adj_high = ptp_ts_reg(95, 48) + 1.U
      
      ptp_ts_adj_reg := Cat(ptp_adj_high, 0.U(2.W), ptp_adj_mid, ptp_ts_reg(15, 0))
    }

    when(lanes_swapped_reg) {
      xgmii_rxd_d0_reg := Cat(xgmii_rxd_masked(31, 0), swap_rxd_reg)
      term_present_reg := false.B
      term_first_cycle_reg := false.B
      term_lane_reg := 0.U
      framing_error_reg := Cat(io.xgmii_rxc(3, 0), swap_rxc_reg) =/= 0.U

      for (i <- ctrlW - 1 to 0 by -1) {
        val term_cond = Cat(xgmii_term(3, 0), swap_rxc_term_reg)(i)
        when(term_cond) {
          term_present_reg := true.B
          term_first_cycle_reg := (i <= 4).B
          term_lane_reg := i.U
          
          val mask = ((1 << ctrlW) - 1).U >> (ctrlW - i)
          framing_error_reg := (Cat(io.xgmii_rxc(3, 0), swap_rxc_reg) & mask) =/= 0.U
        }
      }
    }.otherwise {
      xgmii_rxd_d0_reg := xgmii_rxd_masked
      term_present_reg := false.B
      term_first_cycle_reg := false.B
      term_lane_reg := 0.U
      framing_error_reg := io.xgmii_rxc =/= 0.U

      for (i <- ctrlW - 1 to 0 by -1) {
        when(xgmii_term(i)) {
          term_present_reg := true.B
          term_first_cycle_reg := (i <= 4).B
          term_lane_reg := i.U
          
          val mask = ((1 << ctrlW) - 1).U >> (ctrlW - i)
          framing_error_reg := (io.xgmii_rxc & mask) =/= 0.U
        }
      }
    }

    crc_state_reg := crc_state
    
    when(io.xgmii_rxc(0) && io.xgmii_rxd(7, 0) === XGMII_START) {
      lanes_swapped_reg := false.B
      xgmii_start_d0_reg := true.B
      xgmii_rxd_d0_reg := xgmii_rxd_masked
      crc_state_reg := "hffffffff".U(32.W)
      framing_error_reg := io.xgmii_rxc(7, 1) =/= 0.U
    }.elsewhen(io.xgmii_rxc(4) && io.xgmii_rxd(39, 32) === XGMII_START) {
      lanes_swapped_reg := true.B
      xgmii_start_swap_reg := true.B
      crc_state_reg := (~"h6dd90a9d".U(32.W)).asUInt
      framing_error_reg := io.xgmii_rxc(7, 5) =/= 0.U
    }

    when(xgmii_start_swap_reg) {
      start_packet_reg := 2.U
      if (ptpTsFmtTod) {
        val ptp_low = io.ptp_ts(45, 0) + (ts_inc_reg >> 1)
        ptp_ts_reg := Cat(io.ptp_ts(95, 48), ptp_low(45, 0))
      } else {
        ptp_ts_reg := io.ptp_ts + (ts_inc_reg >> 1)
      }
    }

    when(xgmii_start_d0_reg && !lanes_swapped_reg) {
      start_packet_reg := 1.U
      ptp_ts_reg := io.ptp_ts
    }

    lanes_swapped_d1_reg := lanes_swapped_reg
    term_lane_d0_reg := term_lane_reg
    framing_error_d0_reg := framing_error_reg
    crc_valid_reg := crc_valid.asUInt

    xgmii_rxd_d1_reg := xgmii_rxd_d0_reg
    xgmii_start_d1_reg := xgmii_start_d0_reg
  }

  last_ts_reg := io.ptp_ts(19, 0) // Truncates to 20 bits safely
  ts_inc_reg := io.ptp_ts(19, 0) - last_ts_reg
}


object Xgmii2Axis64 {
  def apply(p: Xgmii2Axis64Params): Xgmii2Axis64 = Module(new Xgmii2Axis64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW
  ))
}

object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  Xgmii2Axis64Params.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Xgmii2Axis64(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
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
    RunScriptFile.create(mainClassName, Xgmii2Axis64Params.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}