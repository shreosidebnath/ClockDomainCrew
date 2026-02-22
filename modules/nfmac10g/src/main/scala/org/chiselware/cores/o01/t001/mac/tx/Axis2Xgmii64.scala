package org.chiselware.cores.o01.t001.mac.tx
import org.chiselware.cores.o01.t001.Lfsr
import org.chiselware.cores.o01.t001.mac.AxisInterface
import org.chiselware.cores.o01.t001.mac.AxisInterfaceParams
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
import java.io.{File, PrintWriter}

class Axis2Xgmii64(
  val dataW: Int = 64,
  val ctrlW: Int = 8,
  val gbxIfEn: Boolean = false,
  val gbxCnt: Int = 1,
  val paddingEn: Boolean = true,
  val dicEn: Boolean = true,
  val minFrameLen: Int = 64,
  val ptpTsEn: Boolean = false,
  val ptpTsFmtTod: Boolean = true,
  val ptpTsW: Int = 96,
  val txCplCtrlInTuser: Boolean = true
) extends Module {

  val keepW = dataW / 8
  val userW = if (txCplCtrlInTuser) 2 else 1
  

  val txTagW = 8 

  val emptyW = log2Ceil(keepW + 1)
  val minLenW = log2Ceil(minFrameLen - 4 - ctrlW + 1 + 1)

  require(dataW == 64, s"Error: Interface width must be 64 (instance dataW=$dataW)")
  require(keepW * 8 == dataW && ctrlW * 8 == dataW, "Error: Interface requires byte (8-bit) granularity")

  val io = IO(new Bundle {
    // Transmit interface (AXI stream)
    val s_axis_tx = Flipped(new AxisInterface(AxisInterfaceParams(
      dataW = dataW, keepW = keepW, idEn = true, idW = txTagW, userEn = true, userW = userW
    )))
    val m_axis_tx_cpl = new AxisInterface(AxisInterfaceParams(
      dataW = 96, keepW = 1, idW = 8
    ))

    // XGMII output
    val xgmii_txd = Output(UInt(dataW.W))
    val xgmii_txc = Output(UInt(ctrlW.W))
    val xgmii_tx_valid = Output(Bool())
    
    val tx_gbx_req_sync = Input(UInt(gbxCnt.W))
    val tx_gbx_req_stall = Input(Bool())
    val tx_gbx_sync = Output(UInt(gbxCnt.W))

    // PTP
    val ptp_ts = Input(UInt(ptpTsW.W))

    // Configuration
    val cfg_tx_max_pkt_len = Input(UInt(16.W))
    val cfg_tx_ifg = Input(UInt(8.W))
    val cfg_tx_enable = Input(Bool())

    // Status
    val tx_start_packet = Output(UInt(2.W))
    val stat_tx_byte = Output(UInt(4.W))
    val stat_tx_pkt_len = Output(UInt(16.W))
    val stat_tx_pkt_ucast = Output(Bool())
    val stat_tx_pkt_mcast = Output(Bool())
    val stat_tx_pkt_bcast = Output(Bool())
    val stat_tx_pkt_vlan = Output(Bool())
    val stat_tx_pkt_good = Output(Bool())
    val stat_tx_pkt_bad = Output(Bool())
    val stat_tx_err_oversize = Output(Bool())
    val stat_tx_err_user = Output(Bool())
    val stat_tx_err_underflow = Output(Bool())
  })

  // Constants
  val ETH_PRE = "h55".U(8.W)
  val ETH_SFD = "hD5".U(8.W)
  val XGMII_IDLE = "h07".U(8.W)
  val XGMII_START = "hfb".U(8.W)
  val XGMII_TERM = "hfd".U(8.W)
  val XGMII_ERROR = "hfe".U(8.W)

  val STATE_IDLE = 0.U(3.W)
  val STATE_PAYLOAD = 1.U(3.W)
  val STATE_PAD = 2.U(3.W)
  val STATE_FCS_1 = 3.U(3.W)
  val STATE_FCS_2 = 4.U(3.W)
  val STATE_ERR = 5.U(3.W)
  val STATE_IFG = 6.U(3.W)

  // Registers
  val state_reg = RegInit(STATE_IDLE)

  val swap_lanes_reg = RegInit(false.B)
  val swap_txd_reg = RegInit(0.U(32.W))
  val swap_txc_reg = RegInit(0.U(4.W))

  val s_tdata_reg = RegInit(0.U(dataW.W))
  val s_empty_reg = RegInit(0.U(emptyW.W))

  val frame_start_reg = RegInit(false.B)
  val frame_reg = RegInit(false.B)
  val frame_error_reg = RegInit(false.B)
  val frame_oversize_reg = RegInit(false.B)
  val frame_min_count_reg = RegInit(0.U(minLenW.W))
  val hdr_ptr_reg = RegInit(0.U(2.W))
  val is_mcast_reg = RegInit(false.B)
  val is_bcast_reg = RegInit(false.B)
  val is_8021q_reg = RegInit(false.B)
  val frame_len_reg = RegInit(0.U(16.W))
  val frame_len_lim_cyc_reg = RegInit(0.U(13.W))
  val frame_len_lim_last_reg = RegInit(0.U(3.W))
  val frame_len_lim_check_reg = RegInit(false.B)
  val ifg_cnt_reg = RegInit(0.U(8.W))

  val ifg_count_reg = RegInit(0.U(8.W))
  val deficit_idle_count_reg = RegInit(0.U(2.W))

  val s_axis_tx_tready_reg = RegInit(false.B)

  val m_axis_tx_cpl_ts_reg = RegInit(0.U(ptpTsW.W))
  val m_axis_tx_cpl_ts_adj_reg = RegInit(0.U(ptpTsW.W))
  val m_axis_tx_cpl_tag_reg = RegInit(0.U(txTagW.W))
  val m_axis_tx_cpl_valid_reg = RegInit(false.B)
  val m_axis_tx_cpl_valid_int_reg = RegInit(false.B)
  val m_axis_tx_cpl_ts_borrow_reg = RegInit(false.B)

  val crc_state_reg = RegInit(VecInit(Seq.fill(8)("hffffffff".U(32.W))))

  val last_ts_reg = RegInit(0.U(20.W))
  val ts_inc_reg = RegInit(0.U(20.W))

  val xgmii_txd_reg = RegInit(VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt)
  val xgmii_txc_reg = RegInit(((1 << ctrlW) - 1).U(ctrlW.W))
  val xgmii_tx_valid_reg = RegInit(false.B)
  val tx_gbx_sync_reg = RegInit(0.U(gbxCnt.W))

  val start_packet_reg = RegInit(0.U(2.W))

  val stat_tx_byte_reg = RegInit(0.U(4.W))
  val stat_tx_pkt_len_reg = RegInit(0.U(16.W))
  val stat_tx_pkt_ucast_reg = RegInit(false.B)
  val stat_tx_pkt_mcast_reg = RegInit(false.B)
  val stat_tx_pkt_bcast_reg = RegInit(false.B)
  val stat_tx_pkt_vlan_reg = RegInit(false.B)
  val stat_tx_pkt_good_reg = RegInit(false.B)
  val stat_tx_pkt_bad_reg = RegInit(false.B)
  val stat_tx_err_oversize_reg = RegInit(false.B)
  val stat_tx_err_user_reg = RegInit(false.B)
  val stat_tx_err_underflow_reg = RegInit(false.B)

  // Combinational Fallbacks (_next logic)
  val state_next = WireDefault(STATE_IDLE)
  val reset_crc = WireDefault(false.B)
  val update_crc = WireDefault(false.B)

  val swap_lanes_next = WireDefault(swap_lanes_reg)
  val frame_start_next = WireDefault(false.B)
  val frame_next = WireDefault(frame_reg)
  val frame_error_next = WireDefault(frame_error_reg)
  val frame_oversize_next = WireDefault(frame_oversize_reg)
  val frame_min_count_next = WireDefault(frame_min_count_reg)
  val hdr_ptr_next = WireDefault(hdr_ptr_reg)
  val is_mcast_next = WireDefault(is_mcast_reg)
  val is_bcast_next = WireDefault(is_bcast_reg)
  val is_8021q_next = WireDefault(is_8021q_reg)
  val frame_len_next = WireDefault(frame_len_reg)
  val frame_len_lim_cyc_next = WireDefault(frame_len_lim_cyc_reg)
  val frame_len_lim_last_next = WireDefault(frame_len_lim_last_reg)
  val frame_len_lim_check_next = WireDefault(frame_len_lim_check_reg)
  val ifg_cnt_next = WireDefault(ifg_cnt_reg)

  val ifg_count_next = WireDefault(ifg_count_reg)
  val deficit_idle_count_next = WireDefault(deficit_idle_count_reg)

  val s_axis_tx_tready_next = WireDefault(false.B)
  val s_tdata_next = WireDefault(s_tdata_reg)
  val s_empty_next = WireDefault(s_empty_reg)
  val m_axis_tx_cpl_tag_next = WireDefault(m_axis_tx_cpl_tag_reg)

  val xgmii_txd_next = WireDefault(VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt)
  val xgmii_txc_next = WireDefault(((1 << ctrlW) - 1).U(ctrlW.W))

  val stat_tx_byte_next = WireDefault(0.U(4.W))
  val stat_tx_pkt_len_next = WireDefault(0.U(16.W))
  val stat_tx_pkt_ucast_next = WireDefault(false.B)
  val stat_tx_pkt_mcast_next = WireDefault(false.B)
  val stat_tx_pkt_bcast_next = WireDefault(false.B)
  val stat_tx_pkt_vlan_next = WireDefault(false.B)
  val stat_tx_pkt_good_next = WireDefault(false.B)
  val stat_tx_pkt_bad_next = WireDefault(false.B)
  val stat_tx_err_oversize_next = WireDefault(false.B)
  val stat_tx_err_user_next = WireDefault(false.B)
  val stat_tx_err_underflow_next = WireDefault(false.B)

  // Direct Outputs
  io.s_axis_tx.tready := s_axis_tx_tready_reg && (!gbxIfEn.B || !io.tx_gbx_req_stall)
  io.xgmii_txd := xgmii_txd_reg
  io.xgmii_txc := xgmii_txc_reg
  io.xgmii_tx_valid := Mux(gbxIfEn.B, xgmii_tx_valid_reg, true.B)
  io.tx_gbx_sync := Mux(gbxIfEn.B, tx_gbx_sync_reg, 0.U)

  io.tx_start_packet := start_packet_reg
  io.stat_tx_byte := stat_tx_byte_reg
  io.stat_tx_pkt_len := stat_tx_pkt_len_reg
  io.stat_tx_pkt_ucast := stat_tx_pkt_ucast_reg
  io.stat_tx_pkt_mcast := stat_tx_pkt_mcast_reg
  io.stat_tx_pkt_bcast := stat_tx_pkt_bcast_reg
  io.stat_tx_pkt_vlan := stat_tx_pkt_vlan_reg
  io.stat_tx_pkt_good := stat_tx_pkt_good_reg
  io.stat_tx_pkt_bad := stat_tx_pkt_bad_reg
  io.stat_tx_err_oversize := stat_tx_err_oversize_reg
  io.stat_tx_err_user := stat_tx_err_user_reg
  io.stat_tx_err_underflow := stat_tx_err_underflow_reg

  if (ptpTsEn) {
    io.m_axis_tx_cpl.tdata := Mux(!ptpTsFmtTod.B || m_axis_tx_cpl_ts_borrow_reg, m_axis_tx_cpl_ts_reg, m_axis_tx_cpl_ts_adj_reg)
  } else {
    io.m_axis_tx_cpl.tdata := 0.U
  }
  
  io.m_axis_tx_cpl.tkeep := 1.U
  io.m_axis_tx_cpl.tstrb := 1.U
  io.m_axis_tx_cpl.tvalid := m_axis_tx_cpl_valid_reg
  io.m_axis_tx_cpl.tlast := true.B
  io.m_axis_tx_cpl.tid := m_axis_tx_cpl_tag_reg
  io.m_axis_tx_cpl.tdest := 0.U
  io.m_axis_tx_cpl.tuser := 0.U

  // Helper Function
  def keep2empty(k: UInt): UInt = {
    val out = WireDefault(0.U(3.W))
    val k8 = k(7, 0) // Extract the 8 bits once to keep the code clean
    
    when(k8 === BitPat("b???????0")) { out := 7.U }
    .elsewhen(k8 === BitPat("b??????01")) { out := 7.U }
    .elsewhen(k8 === BitPat("b?????011")) { out := 6.U }
    .elsewhen(k8 === BitPat("b????0111")) { out := 5.U }
    .elsewhen(k8 === BitPat("b???01111")) { out := 4.U }
    .elsewhen(k8 === BitPat("b??011111")) { out := 3.U }
    .elsewhen(k8 === BitPat("b?0111111")) { out := 2.U }
    .elsewhen(k8 === BitPat("b01111111")) { out := 1.U }
    .elsewhen(k8 === BitPat("b11111111")) { out := 0.U }
    
    out
  }

  // Mask input data
  val s_axis_tx_tdata_masked_vec = Wire(Vec(keepW, UInt(8.W)))
  for (n <- 0 until keepW) {
    if (n == 0) {
      s_axis_tx_tdata_masked_vec(n) := io.s_axis_tx.tdata(7, 0)
    } else {
      s_axis_tx_tdata_masked_vec(n) := Mux(io.s_axis_tx.tkeep(n), io.s_axis_tx.tdata((n * 8) + 7, n * 8), 0.U(8.W))
    }
  }
  val s_axis_tx_tdata_masked = s_axis_tx_tdata_masked_vec.asUInt

  // CRC Generation
  val crc_state = Wire(Vec(8, UInt(32.W)))
  for (n <- 0 until 8) {
    val crc_inst = Module(new Lfsr(
      lfsrW = 32, lfsrPoly = BigInt("4c11db7", 16), lfsrGalois = true,
      lfsrFeedForward = false, reverse = true, dataW = 8 * (n + 1),
      dataInEn = true, dataOutEn = false
    ))
    crc_inst.io.data_in := s_tdata_reg(8 * (n + 1) - 1, 0)
    crc_inst.io.state_in := crc_state_reg(7)
    crc_state(n) := crc_inst.io.state_out
  }

  // FCS Cycle Logic
  val fcs_output_txd_0 = WireDefault(0.U(dataW.W))
  val fcs_output_txd_1 = WireDefault(0.U(dataW.W))
  val fcs_output_txc_0 = WireDefault(0.U(ctrlW.W))
  val fcs_output_txc_1 = WireDefault(0.U(ctrlW.W))
  val ifg_offset = WireDefault(0.U(8.W))

  switch(s_empty_reg) {
    is(7.U) {
      fcs_output_txd_0 := Cat(XGMII_IDLE, XGMII_IDLE, XGMII_TERM, ~crc_state(0), s_tdata_reg(7, 0))
      fcs_output_txd_1 := VecInit(Seq.fill(8)(XGMII_IDLE)).asUInt
      fcs_output_txc_0 := "b11100000".U
      fcs_output_txc_1 := "b11111111".U
      ifg_offset := 3.U
    }
    is(6.U) {
      fcs_output_txd_0 := Cat(XGMII_IDLE, XGMII_TERM, ~crc_state(1), s_tdata_reg(15, 0))
      fcs_output_txd_1 := VecInit(Seq.fill(8)(XGMII_IDLE)).asUInt
      fcs_output_txc_0 := "b11000000".U
      fcs_output_txc_1 := "b11111111".U
      ifg_offset := 2.U
    }
    is(5.U) {
      fcs_output_txd_0 := Cat(XGMII_TERM, ~crc_state(2), s_tdata_reg(23, 0))
      fcs_output_txd_1 := VecInit(Seq.fill(8)(XGMII_IDLE)).asUInt
      fcs_output_txc_0 := "b10000000".U
      fcs_output_txc_1 := "b11111111".U
      ifg_offset := 1.U
    }
    is(4.U) {
      fcs_output_txd_0 := Cat(~crc_state(3), s_tdata_reg(31, 0))
      fcs_output_txd_1 := Cat(VecInit(Seq.fill(7)(XGMII_IDLE)).asUInt, XGMII_TERM)
      fcs_output_txc_0 := "b00000000".U
      fcs_output_txc_1 := "b11111111".U
      ifg_offset := 8.U
    }
    is(3.U) {
      fcs_output_txd_0 := Cat(~crc_state(4)(23, 0), s_tdata_reg(39, 0))
      fcs_output_txd_1 := Cat(VecInit(Seq.fill(6)(XGMII_IDLE)).asUInt, XGMII_TERM, ~crc_state_reg(4)(31, 24))
      fcs_output_txc_0 := "b00000000".U
      fcs_output_txc_1 := "b11111110".U
      ifg_offset := 7.U
    }
    is(2.U) {
      fcs_output_txd_0 := Cat(~crc_state(5)(15, 0), s_tdata_reg(47, 0))
      fcs_output_txd_1 := Cat(VecInit(Seq.fill(5)(XGMII_IDLE)).asUInt, XGMII_TERM, ~crc_state_reg(5)(31, 16))
      fcs_output_txc_0 := "b00000000".U
      fcs_output_txc_1 := "b11111100".U
      ifg_offset := 6.U
    }
    is(1.U) {
      fcs_output_txd_0 := Cat(~crc_state(6)(7, 0), s_tdata_reg(55, 0))
      fcs_output_txd_1 := Cat(VecInit(Seq.fill(4)(XGMII_IDLE)).asUInt, XGMII_TERM, ~crc_state_reg(6)(31, 8))
      fcs_output_txc_0 := "b00000000".U
      fcs_output_txc_1 := "b11111000".U
      ifg_offset := 5.U
    }
    is(0.U) {
      fcs_output_txd_0 := s_tdata_reg
      fcs_output_txd_1 := Cat(VecInit(Seq.fill(3)(XGMII_IDLE)).asUInt, XGMII_TERM, ~crc_state_reg(7))
      fcs_output_txc_0 := "b00000000".U
      fcs_output_txc_1 := "b11110000".U
      ifg_offset := 4.U
    }
  }

  // FSM Logic (always_comb mapping)
  when(io.s_axis_tx.tvalid && io.s_axis_tx.tready) {
    frame_next := !io.s_axis_tx.tlast
  }

  when(gbxIfEn.B && io.tx_gbx_req_stall) {
    state_next := state_reg
    frame_start_next := frame_start_reg
    s_axis_tx_tready_next := s_axis_tx_tready_reg
  }.otherwise {

    when(frame_min_count_reg > ctrlW.U) {
      frame_min_count_next := frame_min_count_reg - ctrlW.U
    }.otherwise {
      frame_min_count_next := 0.U
    }

    when(!frame_len_reg(15, 3).andR) {
      frame_len_next := frame_len_reg + ctrlW.U
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
        is_mcast_next := s_tdata_reg(0)
        is_bcast_next := s_tdata_reg(47, 0).andR
      }
      is(1.U) {
        is_8021q_next := Cat(s_tdata_reg(39, 32), s_tdata_reg(47, 40)) === "h8100".U
      }
    }

    when(ifg_cnt_reg(7, 3) =/= 0.U) {
      ifg_cnt_next := ifg_cnt_reg - ctrlW.U
    }.otherwise {
      ifg_cnt_next := 0.U
    }

    switch(state_reg) {
      is(STATE_IDLE) {
        frame_error_next := false.B
        frame_oversize_next := false.B
        frame_min_count_next := (minFrameLen - 4 - ctrlW).U
        hdr_ptr_next := 0.U
        frame_len_next := 0.U
        
        val max_pkt_lim = io.cfg_tx_max_pkt_len - 5.U
        frame_len_lim_cyc_next := max_pkt_lim(15, 3)
        frame_len_lim_last_next := max_pkt_lim(2, 0)
        frame_len_lim_check_next := false.B
        reset_crc := true.B
        s_axis_tx_tready_next := io.cfg_tx_enable

        s_tdata_next := s_axis_tx_tdata_masked
        s_empty_next := keep2empty(io.s_axis_tx.tkeep)
        m_axis_tx_cpl_tag_next := io.s_axis_tx.tid

        when(io.s_axis_tx.tvalid && io.s_axis_tx.tready) {
          xgmii_txd_next := Cat(ETH_SFD, VecInit(Seq.fill(6)(ETH_PRE)).asUInt, XGMII_START)
          xgmii_txc_next := "b00000001".U
          frame_start_next := true.B
          s_axis_tx_tready_next := true.B
          state_next := STATE_PAYLOAD
        }.otherwise {
          swap_lanes_next := false.B
          ifg_count_next := 0.U
          deficit_idle_count_next := 0.U
          state_next := STATE_IDLE
        }
      }

      is(STATE_PAYLOAD) {
        update_crc := true.B
        s_axis_tx_tready_next := true.B
        xgmii_txd_next := s_tdata_reg
        xgmii_txc_next := 0.U
        s_tdata_next := s_axis_tx_tdata_masked
        s_empty_next := keep2empty(io.s_axis_tx.tkeep)
        stat_tx_byte_next := ctrlW.U

        when(io.s_axis_tx.tvalid && io.s_axis_tx.tlast) {
          when(frame_len_lim_check_reg) {
            when(frame_len_lim_last_reg < (7.U - keep2empty(io.s_axis_tx.tkeep))) {
              frame_oversize_next := true.B
            }
          }
        }.otherwise {
          when(frame_len_lim_check_reg) {
            frame_oversize_next := true.B
          }
        }

        when(paddingEn.B && frame_min_count_reg =/= 0.U) {
          when(frame_min_count_reg > ctrlW.U) {
            s_empty_next := 0.U
          }.elsewhen(keep2empty(io.s_axis_tx.tkeep) > (ctrlW.U - frame_min_count_reg)) {
            s_empty_next := ctrlW.U - frame_min_count_reg
          }
        }

        when(!io.s_axis_tx.tvalid || io.s_axis_tx.tlast || frame_oversize_next) {
          s_axis_tx_tready_next := frame_next
          frame_error_next := !io.s_axis_tx.tvalid || io.s_axis_tx.tuser(0) || frame_oversize_next
          stat_tx_err_user_next := io.s_axis_tx.tuser(0)
          stat_tx_err_underflow_next := !io.s_axis_tx.tvalid

          when(paddingEn.B && frame_min_count_reg =/= 0.U && frame_min_count_reg > ctrlW.U) {
            state_next := STATE_PAD
          }.otherwise {
            when(frame_error_next) {
              state_next := STATE_ERR
            }.otherwise {
              state_next := STATE_FCS_1
            }
          }
        }.otherwise {
          state_next := STATE_PAYLOAD
        }
      }

      is(STATE_PAD) {
        s_axis_tx_tready_next := frame_next
        xgmii_txd_next := s_tdata_reg
        xgmii_txc_next := 0.U
        s_tdata_next := 0.U
        s_empty_next := 0.U
        stat_tx_byte_next := ctrlW.U
        update_crc := true.B

        when(frame_min_count_reg > ctrlW.U) {
          state_next := STATE_PAD
        }.otherwise {
          s_empty_next := ctrlW.U - frame_min_count_reg
          when(frame_error_reg) {
            state_next := STATE_ERR
          }.otherwise {
            state_next := STATE_FCS_1
          }
        }
      }

      is(STATE_FCS_1) {
        s_axis_tx_tready_next := frame_next
        xgmii_txd_next := fcs_output_txd_0
        xgmii_txc_next := fcs_output_txc_0
        update_crc := true.B

        val active_ifg = Mux(io.cfg_tx_ifg > 12.U, io.cfg_tx_ifg, 12.U)
        ifg_count_next := active_ifg - ifg_offset + Mux(swap_lanes_reg, 4.U, 0.U) + deficit_idle_count_reg

        when(s_empty_reg <= 4.U) {
          stat_tx_byte_next := ctrlW.U
          state_next := STATE_FCS_2
        }.otherwise {
          stat_tx_byte_next := 12.U - s_empty_reg
          frame_len_next := frame_len_reg + (12.U - s_empty_reg)
          stat_tx_pkt_len_next := frame_len_next
          stat_tx_pkt_good_next := !frame_error_reg
          stat_tx_pkt_bad_next := frame_error_reg
          stat_tx_pkt_ucast_next := !is_mcast_reg
          stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
          stat_tx_pkt_bcast_next := is_bcast_reg
          stat_tx_pkt_vlan_next := is_8021q_reg
          stat_tx_err_oversize_next := frame_oversize_reg
          state_next := STATE_IFG
        }
      }

    is(STATE_FCS_2) {
        s_axis_tx_tready_next := frame_next
        xgmii_txd_next := fcs_output_txd_1
        xgmii_txc_next := fcs_output_txc_1

        stat_tx_byte_next := 4.U - s_empty_reg
        frame_len_next := frame_len_reg + (4.U - s_empty_reg)
        stat_tx_pkt_len_next := frame_len_next
        stat_tx_pkt_good_next := !frame_error_reg
        stat_tx_pkt_bad_next := frame_error_reg
        stat_tx_pkt_ucast_next := !is_mcast_reg
        stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
        stat_tx_pkt_bcast_next := is_bcast_reg
        stat_tx_pkt_vlan_next := is_8021q_reg
        stat_tx_err_oversize_next := frame_oversize_reg

        // Use a temporary val to represent the starting state of ifg_count
        val ifg_temp = ifg_count_reg 

        when(dicEn.B) {
          when(ifg_temp > 7.U) {
            state_next := STATE_IFG
          }.otherwise {
            when(ifg_temp >= 4.U) {
              deficit_idle_count_next := ifg_temp - 4.U
              swap_lanes_next := true.B
            }.otherwise {
              deficit_idle_count_next := ifg_temp(1, 0)
              ifg_count_next := 0.U
              swap_lanes_next := false.B
            }
            s_axis_tx_tready_next := io.cfg_tx_enable
            state_next := STATE_IDLE
          }
        }.otherwise {
          when(ifg_temp > 4.U) {
            state_next := STATE_IFG
          }.otherwise {
            s_axis_tx_tready_next := io.cfg_tx_enable
            swap_lanes_next := ifg_temp =/= 0.U
            state_next := STATE_IDLE
          }
        }
      }

      is(STATE_ERR) {
        s_axis_tx_tready_next := frame_next
        xgmii_txd_next := Cat(XGMII_TERM, VecInit(Seq.fill(7)(XGMII_ERROR)).asUInt)
        xgmii_txc_next := ((1 << ctrlW) - 1).U
        ifg_count_next := Mux(io.cfg_tx_ifg > 12.U, io.cfg_tx_ifg, 12.U)
        
        stat_tx_pkt_len_next := frame_len_reg
        stat_tx_pkt_good_next := !frame_error_reg
        stat_tx_pkt_bad_next := frame_error_reg
        stat_tx_pkt_ucast_next := !is_mcast_reg
        stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
        stat_tx_pkt_bcast_next := is_bcast_reg
        stat_tx_pkt_vlan_next := is_8021q_reg
        stat_tx_err_oversize_next := frame_oversize_reg
        state_next := STATE_IFG
      }

      is(STATE_IFG) {
        s_axis_tx_tready_next := frame_next
        
        // 1. Calculate the intermediate value safely
        val ifg_temp = Mux(ifg_count_reg > 8.U, ifg_count_reg - 8.U, 0.U)
        
        // 2. Assign it to the actual hardware wire (this can be safely overwritten later)
        ifg_count_next := ifg_temp

        // 3. Read the intermediate val to evaluate conditions without creating loops
        when(dicEn.B) {
          when(ifg_temp > 7.U || frame_reg) {
            state_next := STATE_IFG
          }.otherwise {
            when(ifg_temp >= 4.U) {
              deficit_idle_count_next := ifg_temp - 4.U
              swap_lanes_next := true.B
            }.otherwise {
              deficit_idle_count_next := ifg_temp(1, 0)
              ifg_count_next := 0.U // Overwrites safely due to last-connect semantics
              swap_lanes_next := false.B
            }
            s_axis_tx_tready_next := io.cfg_tx_enable
            state_next := STATE_IDLE
          }
        }.otherwise {
          when(ifg_temp > 4.U || frame_reg) {
            state_next := STATE_IFG
          }.otherwise {
            s_axis_tx_tready_next := io.cfg_tx_enable
            swap_lanes_next := ifg_temp =/= 0.U
            state_next := STATE_IDLE
          }
        }
      }
    }
  }

  // Register assignments
  state_reg := state_next
  swap_lanes_reg := swap_lanes_next
  frame_start_reg := frame_start_next
  frame_reg := frame_next
  frame_error_reg := frame_error_next
  frame_oversize_reg := frame_oversize_next
  frame_min_count_reg := frame_min_count_next
  hdr_ptr_reg := hdr_ptr_next
  is_mcast_reg := is_mcast_next
  is_bcast_reg := is_bcast_next
  is_8021q_reg := is_8021q_next
  frame_len_reg := frame_len_next
  frame_len_lim_cyc_reg := frame_len_lim_cyc_next
  frame_len_lim_last_reg := frame_len_lim_last_next
  frame_len_lim_check_reg := frame_len_lim_check_next
  ifg_cnt_reg := ifg_cnt_next

  ifg_count_reg := ifg_count_next
  deficit_idle_count_reg := deficit_idle_count_next

  s_tdata_reg := s_tdata_next
  s_empty_reg := s_empty_next
  s_axis_tx_tready_reg := s_axis_tx_tready_next

  m_axis_tx_cpl_tag_reg := m_axis_tx_cpl_tag_next
  m_axis_tx_cpl_valid_reg := false.B
  m_axis_tx_cpl_valid_int_reg := false.B

  start_packet_reg := 0.U

  stat_tx_byte_reg := stat_tx_byte_next
  stat_tx_pkt_len_reg := stat_tx_pkt_len_next
  stat_tx_pkt_ucast_reg := stat_tx_pkt_ucast_next
  stat_tx_pkt_mcast_reg := stat_tx_pkt_mcast_next
  stat_tx_pkt_bcast_reg := stat_tx_pkt_bcast_next
  stat_tx_pkt_vlan_reg := stat_tx_pkt_vlan_next
  stat_tx_pkt_good_reg := stat_tx_pkt_good_next
  stat_tx_pkt_bad_reg := stat_tx_pkt_bad_next
  stat_tx_err_oversize_reg := stat_tx_err_oversize_next
  stat_tx_err_user_reg := stat_tx_err_user_next
  stat_tx_err_underflow_reg := stat_tx_err_underflow_next

  if (ptpTsEn && ptpTsFmtTod) {
    m_axis_tx_cpl_valid_reg := m_axis_tx_cpl_valid_int_reg
    m_axis_tx_cpl_ts_adj_reg := Cat(
      m_axis_tx_cpl_ts_reg(95, 48) + 1.U,
      0.U(2.W),
      // Manual subtraction to retrieve the borrow bit
      (Cat(0.U(1.W), m_axis_tx_cpl_ts_reg(45, 16)).asSInt - 1000000000.S)(29, 0).asUInt,
      m_axis_tx_cpl_ts_reg(15, 0)
    )
    m_axis_tx_cpl_ts_borrow_reg := (Cat(0.U(1.W), m_axis_tx_cpl_ts_reg(45, 16)).asSInt - 1000000000.S)(31)
  }

  when(gbxIfEn.B && io.tx_gbx_req_stall) {
    xgmii_tx_valid_reg := false.B
  }.otherwise {
    when(frame_start_reg) {
      when(swap_lanes_reg) {
        if (ptpTsEn) {
          if (ptpTsFmtTod) {
            val ptp_low = io.ptp_ts(45, 0) + (ts_inc_reg >> 1)
            m_axis_tx_cpl_ts_reg := Cat(io.ptp_ts(95, 48), ptp_low(45, 0))
          } else {
            m_axis_tx_cpl_ts_reg := io.ptp_ts + (ts_inc_reg >> 1)
          }
        }
        start_packet_reg := "b10".U
      }.otherwise {
        if (ptpTsEn) m_axis_tx_cpl_ts_reg := io.ptp_ts
        start_packet_reg := "b01".U
      }

      if (txCplCtrlInTuser) {
        if (ptpTsFmtTod) m_axis_tx_cpl_valid_int_reg := (io.s_axis_tx.tuser >> 1) === 0.U
        else m_axis_tx_cpl_valid_reg := (io.s_axis_tx.tuser >> 1) === 0.U
      } else {
        if (ptpTsFmtTod) m_axis_tx_cpl_valid_int_reg := true.B
        else m_axis_tx_cpl_valid_reg := true.B
      }
    }

    for (i <- 0 until 7) {
      crc_state_reg(i) := crc_state(i)
    }

    when(update_crc) {
      crc_state_reg(7) := crc_state(7)
    }
    when(reset_crc) {
      crc_state_reg(7) := "hffffffff".U(32.W)
    }

    swap_txd_reg := xgmii_txd_next(63, 32)
    swap_txc_reg := xgmii_txc_next(7, 4)

    when(swap_lanes_reg) {
      xgmii_txd_reg := Cat(xgmii_txd_next(31, 0), swap_txd_reg)
      xgmii_txc_reg := Cat(xgmii_txc_next(3, 0), swap_txc_reg)
    }.otherwise {
      xgmii_txd_reg := xgmii_txd_next
      xgmii_txc_reg := xgmii_txc_next
    }
    xgmii_tx_valid_reg := true.B
  }

  tx_gbx_sync_reg := io.tx_gbx_req_sync
  last_ts_reg := io.ptp_ts(19, 0)
  ts_inc_reg := io.ptp_ts(19, 0) - last_ts_reg

}


object Axis2Xgmii64 {
  def apply(p: Axis2Xgmii64Params): Axis2Xgmii64 = Module(new Axis2Xgmii64(
    dataW = p.dataW,
    ctrlW = p.ctrlW,
    gbxIfEn = p.gbxIfEn,
    gbxCnt = p.gbxCnt,
    paddingEn = p.paddingEn,
    dicEn = p.dicEn,
    minFrameLen = p.minFrameLen,
    ptpTsEn = p.ptpTsEn,
    ptpTsFmtTod = p.ptpTsFmtTod,
    ptpTsW = p.ptpTsW,
    txCplCtrlInTuser = p.txCplCtrlInTuser
  ))
}


object Main extends App {
  val mainClassName = "Nfmac10g"
  val coreDir = s"modules/${mainClassName.toLowerCase()}"
  Axis2Xgmii64Params.synConfigMap.foreach { case (configName, p) =>
    println(s"Generating Verilog for config: $configName")
    ChiselStage.emitSystemVerilog(
      new Axis2Xgmii64(
        dataW = p.dataW,
        ctrlW = p.ctrlW,
        gbxIfEn = p.gbxIfEn,
        gbxCnt = p.gbxCnt,
        paddingEn = p.paddingEn,
        dicEn = p.dicEn,
        minFrameLen = p.minFrameLen,
        ptpTsEn = p.ptpTsEn,
        ptpTsFmtTod = p.ptpTsFmtTod,
        ptpTsW = p.ptpTsW,
        txCplCtrlInTuser = p.txCplCtrlInTuser
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
    RunScriptFile.create(mainClassName, Axis2Xgmii64Params.synConfigs, s"${coreDir}/generated/synTestCases")
  }
}
