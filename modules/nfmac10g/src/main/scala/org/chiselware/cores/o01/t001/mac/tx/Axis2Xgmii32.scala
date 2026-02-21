// package org.chiselware.cores.o01.t001.mac.tx
// import org.chiselware.cores.o01.t001.Lfsr
// import org.chiselware.cores.o01.t001.mac.AxisInterface
// import chisel3._
// import chisel3.util._
// import _root_.circt.stage.ChiselStage
// import org.chiselware.syn.{YosysTclFile, StaTclFile, RunScriptFile}
// import java.io.{File, PrintWriter}

// class Axis2Xgmii32(
//   val dataW: Int = 32,
//   val ctrlW: Int = 4,
//   val gbxIfEn: Boolean = false,
//   val gbxCnt: Int = 1,
//   val paddingEn: Boolean = true,
//   val dicEn: Boolean = true,
//   val minFrameLen: Int = 64,
//   val ptpTsEn: Boolean = false,
//   val ptpTsW: Int = 96,
//   val txCplCtrlInTuser: Boolean = true,
//   val idW: Int = 8 // Matches s_axis_tx.ID_W
// ) extends Module {

//   // --- Derived Parameters ---
//   val keepW = dataW / 8
//   val userW = if (txCplCtrlInTuser) 2 else 1
  
//   // Empty width calculation: $clog2(KEEP_W)
//   val emptyW = log2Ceil(keepW)
//   // Min Len width: $clog2(MIN_FRAME_LEN-4-CTRL_W+1)
//   val minLenW = log2Ceil(minFrameLen - 4 - ctrlW + 1)
  
//   // Logic for completion interface width
//   val cplDataW = if (ptpTsEn) ptpTsW else dataW

//   val io = IO(new Bundle {
//     // Transmit interface (AXI stream) - Sink/Slave
//     val s_axis_tx = Flipped(new AxisInterface(dataW, userW, idW))
    
//     // Completion interface (AXI stream) - Source/Master
//     // Note: tuser/tdest width defaults to 1/8 as they are driven to 0
//     val m_axis_tx_cpl = new AxisInterface(cplDataW, 1, idW)

//     // XGMII output
//     val xgmii_txd = Output(UInt(dataW.W))
//     val xgmii_txc = Output(UInt(ctrlW.W))
//     val xgmii_tx_valid = Output(Bool())

//     // Gearbox
//     val tx_gbx_req_sync = Input(UInt(gbxCnt.W))
//     val tx_gbx_req_stall = Input(Bool())
//     val tx_gbx_sync = Output(UInt(gbxCnt.W))

//     // PTP
//     val ptp_ts = Input(UInt(ptpTsW.W))

//     // Configuration
//     val cfg_tx_max_pkt_len = Input(UInt(16.W))
//     val cfg_tx_ifg = Input(UInt(8.W))
//     val cfg_tx_enable = Input(Bool())

//     // Status
//     val tx_start_packet = Output(Bool())
//     val stat_tx_byte = Output(UInt(3.W))
//     val stat_tx_pkt_len = Output(UInt(16.W))
//     val stat_tx_pkt_ucast = Output(Bool())
//     val stat_tx_pkt_mcast = Output(Bool())
//     val stat_tx_pkt_bcast = Output(Bool())
//     val stat_tx_pkt_vlan = Output(Bool())
//     val stat_tx_pkt_good = Output(Bool())
//     val stat_tx_pkt_bad = Output(Bool())
//     val stat_tx_err_oversize = Output(Bool())
//     val stat_tx_err_user = Output(Bool())
//     val stat_tx_err_underflow = Output(Bool())
//   })

//   // --- Constants ---
//   val ETH_PRE     = "h55".U(8.W)
//   val ETH_SFD     = "hD5".U(8.W)
//   val XGMII_IDLE  = "h07".U(8.W)
//   val XGMII_START = "hfb".U(8.W)
//   val XGMII_TERM  = "hfd".U(8.W)
//   val XGMII_ERROR = "hfe".U(8.W)

//   object State extends ChiselEnum {
//     val STATE_IDLE, STATE_PREAMBLE, STATE_PAYLOAD, STATE_PAD, 
//         STATE_FCS_1, STATE_FCS_2, STATE_FCS_3, STATE_ERR, STATE_IFG = Value
//   }
//   import State._

//   // --- Registers ---
//   val state_reg = RegInit(STATE_IDLE)

//   val s_tdata_reg = RegInit(0.U(dataW.W))
//   val s_empty_reg = RegInit(0.U(emptyW.W))

//   val frame_reg = RegInit(false.B)
//   val frame_error_reg = RegInit(false.B)
//   val frame_oversize_reg = RegInit(false.B)
//   val frame_min_count_reg = RegInit(0.U(minLenW.W))
//   val hdr_ptr_reg = RegInit(0.U(3.W))
//   val is_mcast_reg = RegInit(false.B)
//   val is_bcast_reg = RegInit(false.B)
//   val is_8021q_reg = RegInit(false.B)
  
//   val frame_len_reg = RegInit(0.U(16.W))
//   val frame_len_lim_cyc_reg = RegInit(0.U(14.W))
//   val frame_len_lim_last_reg = RegInit(0.U(2.W))
//   val frame_len_lim_check_reg = RegInit(false.B)
  
//   val ifg_cnt_reg = RegInit(0.U(8.W))
//   val ifg_count_reg = RegInit(0.U(8.W))
//   val deficit_idle_count_reg = RegInit(0.U(2.W))

//   val s_axis_tx_tready_reg = RegInit(false.B)

//   val m_axis_tx_cpl_ts_reg = RegInit(0.U(ptpTsW.W))
//   val m_axis_tx_cpl_tag_reg = RegInit(0.U(idW.W))
//   val m_axis_tx_cpl_valid_reg = RegInit(false.B)

//   val crc_state_reg = RegInit(VecInit(Seq.fill(4)("hFFFFFFFF".U(32.W))))

//   val xgmii_txd_reg = RegInit(VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt)
//   val xgmii_txc_reg = RegInit(Fill(ctrlW, 1.U))
//   val xgmii_tx_valid_reg = RegInit(false.B)
//   val tx_gbx_sync_reg = RegInit(0.U(gbxCnt.W))

//   val start_packet_reg = RegInit(false.B)

//   val stat_reset_block = (
//     RegInit(0.U(3.W)), RegInit(0.U(16.W)), 
//     RegInit(false.B), RegInit(false.B), RegInit(false.B), RegInit(false.B),
//     RegInit(false.B), RegInit(false.B), RegInit(false.B), RegInit(false.B),
//     RegInit(false.B)
//   )
//   val (stat_tx_byte_reg, stat_tx_pkt_len_reg, 
//        stat_tx_pkt_ucast_reg, stat_tx_pkt_mcast_reg, stat_tx_pkt_bcast_reg, stat_tx_pkt_vlan_reg,
//        stat_tx_pkt_good_reg, stat_tx_pkt_bad_reg, 
//        stat_tx_err_oversize_reg, stat_tx_err_user_reg, stat_tx_err_underflow_reg) = stat_reset_block

//   // --- Assignments ---
  
//   // Tready logic
//   io.s_axis_tx.tready := s_axis_tx_tready_reg && (!gbxIfEn.B || !io.tx_gbx_req_stall)

//   io.xgmii_txd := xgmii_txd_reg
//   io.xgmii_txc := xgmii_txc_reg
//   io.xgmii_tx_valid := Mux(gbxIfEn.B, xgmii_tx_valid_reg, true.B)
//   io.tx_gbx_sync := Mux(gbxIfEn.B, tx_gbx_sync_reg, 0.U)

//   io.m_axis_tx_cpl.tdata  := Mux(ptpTsEn.B, m_axis_tx_cpl_ts_reg, 0.U)
//   io.m_axis_tx_cpl.tkeep  := 1.U
//   io.m_axis_tx_cpl.tstrb  := 1.U
//   io.m_axis_tx_cpl.tvalid := m_axis_tx_cpl_valid_reg
//   io.m_axis_tx_cpl.tlast  := true.B
//   io.m_axis_tx_cpl.tid    := m_axis_tx_cpl_tag_reg
//   io.m_axis_tx_cpl.tdest  := 0.U
//   io.m_axis_tx_cpl.tuser  := 0.U

//   io.tx_start_packet       := start_packet_reg
//   io.stat_tx_byte          := stat_tx_byte_reg
//   io.stat_tx_pkt_len       := stat_tx_pkt_len_reg
//   io.stat_tx_pkt_ucast     := stat_tx_pkt_ucast_reg
//   io.stat_tx_pkt_mcast     := stat_tx_pkt_mcast_reg
//   io.stat_tx_pkt_bcast     := stat_tx_pkt_bcast_reg
//   io.stat_tx_pkt_vlan      := stat_tx_pkt_vlan_reg
//   io.stat_tx_pkt_good      := stat_tx_pkt_good_reg
//   io.stat_tx_pkt_bad       := stat_tx_pkt_bad_reg
//   io.stat_tx_err_oversize  := stat_tx_err_oversize_reg
//   io.stat_tx_err_user      := stat_tx_err_user_reg
//   io.stat_tx_err_underflow := stat_tx_err_underflow_reg

//   // --- Functions / Helpers ---
//   def keep2empty(k: UInt): UInt = {
//     // k is 4 bits. 
//     // zzz0 -> 3 (0000 -> 3?)
//     // zz01 -> 3 (0001 -> 3)
//     // z011 -> 2 (0011 -> 2)
//     // 0111 -> 1 (0111 -> 1)
//     // 1111 -> 0
//     // PriorityEncoder returns index of LSB set to 1. 
//     // This logic counts zeros from MSB effectively, or just mapping.
//     MuxCase(0.U, Seq(
//       (k === "b1111".U) -> 0.U,
//       (k === "b0111".U) -> 1.U,
//       (k(1,0) === "b11".U) -> 2.U, // z011
//       (k(0) === 1.U)    -> 3.U,    // zz01
//       (k(0) === 0.U)    -> 3.U     // zzz0
//     ))
//   }

//   // --- CRC Modules ---
//   val crc_state = Wire(Vec(4, UInt(32.W)))
  
//   for (n <- 0 until 4) {
//     val lfsr = Module(new Lfsr(
//       lfsrW = 32, lfsrPoly = BigInt("4c11db7", 16), lfsrGalois = true,
//       lfsrFeedForward = false, reverse = true, dataW = 8 * (n + 1),
//       dataInEn = true, dataOutEn = false
//     ))
//     // Input masking for LFSR
//     // SV: s_tdata_reg[0 +: 8*(n+1)]
//     lfsr.io.data_in := s_tdata_reg(8 * (n + 1) - 1, 0)
//     lfsr.io.state_in := crc_state_reg(3)
//     crc_state(n) := lfsr.io.state_out
//   }

//   // --- Combinational Logic ---
  
//   // Mask input data
//   val s_axis_tx_tdata_masked_wires = Wire(Vec(keepW, UInt(8.W)))
//   for (n <- 0 until keepW) {
//     val keepBit = io.s_axis_tx.tkeep(n)
//     s_axis_tx_tdata_masked_wires(n) := Mux(keepBit, io.s_axis_tx.tdata(n*8+7, n*8), 0.U)
//   }
//   val s_axis_tx_tdata_masked = s_axis_tx_tdata_masked_wires.asUInt

//   // FCS Output Mux Logic
//   val fcs_output_txd_0 = Wire(UInt(dataW.W))
//   val fcs_output_txd_1 = Wire(UInt(dataW.W))
//   val fcs_output_txc_0 = Wire(UInt(ctrlW.W))
//   val fcs_output_txc_1 = Wire(UInt(ctrlW.W))
//   val ifg_offset       = Wire(UInt(8.W))
//   val extra_cycle      = Wire(Bool())

//   // Defaults
//   fcs_output_txd_0 := 0.U
//   fcs_output_txd_1 := 0.U
//   fcs_output_txc_0 := 0.U
//   fcs_output_txc_1 := 0.U
//   ifg_offset       := 0.U
//   extra_cycle      := false.B

//   switch (s_empty_reg) {
//     is (3.U) {
//        fcs_output_txd_0 := Cat(~crc_state(0)(23, 0), s_tdata_reg(7, 0))
//        fcs_output_txd_1 := Cat(XGMII_IDLE, XGMII_IDLE, XGMII_TERM, ~crc_state_reg(0)(31, 24))
//        fcs_output_txc_0 := "b0000".U
//        fcs_output_txc_1 := "b1110".U
//        ifg_offset       := 3.U
//        extra_cycle      := false.B
//     }
//     is (2.U) {
//        fcs_output_txd_0 := Cat(~crc_state(1)(15, 0), s_tdata_reg(15, 0))
//        fcs_output_txd_1 := Cat(XGMII_IDLE, XGMII_TERM, ~crc_state_reg(1)(31, 16))
//        fcs_output_txc_0 := "b0000".U
//        fcs_output_txc_1 := "b1100".U
//        ifg_offset       := 2.U
//        extra_cycle      := false.B
//     }
//     is (1.U) {
//        fcs_output_txd_0 := Cat(~crc_state(2)(7, 0), s_tdata_reg(23, 0))
//        fcs_output_txd_1 := Cat(XGMII_TERM, ~crc_state_reg(2)(31, 8))
//        fcs_output_txc_0 := "b0000".U
//        fcs_output_txc_1 := "b1000".U
//        ifg_offset       := 1.U
//        extra_cycle      := false.B
//     }
//     is (0.U) {
//        fcs_output_txd_0 := s_tdata_reg
//        fcs_output_txd_1 := ~crc_state_reg(3)
//        fcs_output_txc_0 := "b0000".U
//        fcs_output_txc_1 := "b0000".U
//        ifg_offset       := 4.U
//        extra_cycle      := true.B
//     }
//   }

//   // --- Next State Logic ---
//   val state_next = WireDefault(STATE_IDLE)
  
//   val reset_crc = WireDefault(false.B)
//   val update_crc = WireDefault(false.B)

//   val frame_next = WireDefault(frame_reg)
//   val frame_error_next = WireDefault(frame_error_reg)
//   val frame_oversize_next = WireDefault(frame_oversize_reg)
//   val frame_min_count_next = WireDefault(frame_min_count_reg)
//   val hdr_ptr_next = WireDefault(hdr_ptr_reg)
//   val is_mcast_next = WireDefault(is_mcast_reg)
//   val is_bcast_next = WireDefault(is_bcast_reg)
//   val is_8021q_next = WireDefault(is_8021q_reg)
//   val frame_len_next = WireDefault(frame_len_reg)
//   val frame_len_lim_cyc_next = WireDefault(frame_len_lim_cyc_reg)
//   val frame_len_lim_last_next = WireDefault(frame_len_lim_last_reg)
//   val frame_len_lim_check_next = WireDefault(frame_len_lim_check_reg)
//   val ifg_cnt_next = WireDefault(ifg_cnt_reg)

//   val ifg_count_next = WireDefault(ifg_count_reg)
//   val deficit_idle_count_next = WireDefault(deficit_idle_count_reg)

//   val s_axis_tx_tready_next = WireDefault(false.B)
//   val s_tdata_next = WireDefault(s_tdata_reg)
//   val s_empty_next = WireDefault(s_empty_reg)

//   val m_axis_tx_cpl_ts_next = WireDefault(m_axis_tx_cpl_ts_reg)
//   val m_axis_tx_cpl_tag_next = WireDefault(m_axis_tx_cpl_tag_reg)
//   val m_axis_tx_cpl_valid_next = WireDefault(false.B)

//   // XGMII Defaults
//   val xgmii_txd_next = WireDefault(VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt)
//   val xgmii_txc_next = WireDefault(Fill(ctrlW, 1.U))

//   val start_packet_next = WireDefault(false.B)
  
//   // Stats defaults
//   val stat_tx_byte_next = WireDefault(0.U(3.W))
//   val stat_tx_pkt_len_next = WireDefault(0.U(16.W))
//   val stat_tx_pkt_ucast_next = WireDefault(false.B)
//   val stat_tx_pkt_mcast_next = WireDefault(false.B)
//   val stat_tx_pkt_bcast_next = WireDefault(false.B)
//   val stat_tx_pkt_vlan_next = WireDefault(false.B)
//   val stat_tx_pkt_good_next = WireDefault(false.B)
//   val stat_tx_pkt_bad_next = WireDefault(false.B)
//   val stat_tx_err_oversize_next = WireDefault(false.B)
//   val stat_tx_err_user_next = WireDefault(false.B)
//   val stat_tx_err_underflow_next = WireDefault(false.B)

//   // --- Logic Body ---

//   when (start_packet_reg) {
//     if (ptpTsEn) m_axis_tx_cpl_ts_next := io.ptp_ts
    
//     if (txCplCtrlInTuser) {
//       m_axis_tx_cpl_valid_next := (io.s_axis_tx.tuser >> 1) === 0.U
//     } else {
//       m_axis_tx_cpl_valid_next := true.B
//     }
//   }

//   when (io.s_axis_tx.tvalid && io.s_axis_tx.tready) {
//     frame_next := !io.s_axis_tx.tlast
//   }

//   when (gbxIfEn.B && io.tx_gbx_req_stall) {
//     state_next := state_reg
//     s_axis_tx_tready_next := s_axis_tx_tready_reg
//   } .otherwise {
    
//     // Min frame length counter
//     when (frame_min_count_reg > ctrlW.U) {
//       frame_min_count_next := frame_min_count_reg - ctrlW.U
//     } .otherwise {
//       frame_min_count_next := 0.U
//     }

//     // Frame length counter
//     when (frame_len_reg(15, 2).andR === 0.U) {
//       frame_len_next := frame_len_reg + ctrlW.U
//     } .otherwise {
//       frame_len_next := "hFFFF".U
//     }

//     // Max frame length
//     when (frame_len_lim_cyc_reg =/= 0.U) {
//       frame_len_lim_cyc_next := frame_len_lim_cyc_reg - 1.U
//     } .otherwise {
//       frame_len_lim_cyc_next := 0.U
//     }

//     when (frame_len_lim_cyc_reg === 2.U) {
//       frame_len_lim_check_next := true.B
//     }

//     // Address / Ethertype checks
//     when (hdr_ptr_reg.andR === 0.U) {
//       hdr_ptr_next := hdr_ptr_reg + 1.U
//     }

//     switch (hdr_ptr_reg) {
//       is (0.U) {
//         is_mcast_next := s_tdata_reg(0)
//         is_bcast_next := s_tdata_reg.andR
//       }
//       is (1.U) {
//         is_bcast_next := is_bcast_reg && s_tdata_reg(15,0).andR
//       }
//       is (3.U) {
//         is_8021q_next := Cat(s_tdata_reg(7,0), s_tdata_reg(15,8)) === "h8100".U
//       }
//     }

//     when (ifg_cnt_reg(7,2) =/= 0.U) {
//       ifg_cnt_next := ifg_cnt_reg - ctrlW.U
//     } .otherwise {
//       ifg_cnt_next := 0.U
//     }

//     switch (state_reg) {
//       is (STATE_IDLE) {
//         frame_error_next := false.B
//         frame_oversize_next := false.B
//         frame_min_count_next := (minFrameLen - 4).U
//         hdr_ptr_next := 0.U
//         frame_len_next := 0.U
//         val max_len = io.cfg_tx_max_pkt_len - 1.U
//         frame_len_lim_cyc_next := max_len(15, 2)
//         frame_len_lim_last_next := max_len(1, 0)
//         frame_len_lim_check_next := false.B
//         reset_crc := true.B

//         xgmii_txd_next := VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt
//         xgmii_txc_next := Fill(ctrlW, 1.U)

//         s_tdata_next := s_axis_tx_tdata_masked
//         s_empty_next := keep2empty(io.s_axis_tx.tkeep)
        
//         m_axis_tx_cpl_tag_next := io.s_axis_tx.tid

//         when (io.s_axis_tx.tvalid && io.cfg_tx_enable) {
//           // XGMII Start
//           xgmii_txd_next := Cat(XGMII_START, ETH_PRE, ETH_PRE, ETH_PRE)
//           xgmii_txc_next := "b0001".U
//           s_axis_tx_tready_next := true.B
//           state_next := STATE_PREAMBLE
//         } .otherwise {
//           ifg_count_next := 0.U
//           deficit_idle_count_next := 0.U
//           state_next := STATE_IDLE
//         }
//       }

//       is (STATE_PREAMBLE) {
//         reset_crc := true.B
//         hdr_ptr_next := 0.U
//         frame_len_next := 0.U

//         s_tdata_next := s_axis_tx_tdata_masked
//         s_empty_next := keep2empty(io.s_axis_tx.tkeep)

//         xgmii_txd_next := Cat(ETH_PRE, ETH_PRE, ETH_PRE, ETH_SFD)
//         xgmii_txc_next := 0.U

//         s_axis_tx_tready_next := true.B
//         start_packet_next := true.B
//         state_next := STATE_PAYLOAD
//       }

//       is (STATE_PAYLOAD) {
//         update_crc := true.B
//         s_axis_tx_tready_next := true.B

//         xgmii_txd_next := s_tdata_reg
//         xgmii_txc_next := 0.U

//         s_tdata_next := s_axis_tx_tdata_masked
//         s_empty_next := keep2empty(io.s_axis_tx.tkeep)

//         stat_tx_byte_next := ctrlW.U

//         when (io.s_axis_tx.tvalid && io.s_axis_tx.tlast) {
//           when (frame_len_lim_check_reg) {
//             val empty_chk = 3.U - keep2empty(io.s_axis_tx.tkeep)
//             when (frame_len_lim_last_reg < empty_chk(1,0)) {
//               frame_oversize_next := true.B
//             }
//           }
//         } .otherwise {
//           when (frame_len_lim_check_reg) {
//             frame_oversize_next := true.B
//           }
//         }

//         // Padding Logic
//         if (paddingEn) {
//           when (frame_min_count_reg =/= 0.U) {
//              when (frame_min_count_reg > ctrlW.U) {
//                s_empty_next := 0.U
//              } .elsewhen (keep2empty(io.s_axis_tx.tkeep) > (ctrlW.U - frame_min_count_reg)) {
//                s_empty_next := (ctrlW.U - frame_min_count_reg)
//              }
//           }
//         }

//         when (!io.s_axis_tx.tvalid || io.s_axis_tx.tlast || frame_oversize_next) {
//           s_axis_tx_tready_next := frame_next
          
//           val user_err = io.s_axis_tx.tuser(0)
//           frame_error_next := !io.s_axis_tx.tvalid || user_err || frame_oversize_next
//           stat_tx_err_user_next := user_err
//           stat_tx_err_underflow_next := !io.s_axis_tx.tvalid

//           if (paddingEn) {
//             when (frame_min_count_reg =/= 0.U && frame_min_count_reg > ctrlW.U) {
//               state_next := STATE_PAD
//             } .otherwise {
//               state_next := STATE_FCS_1
//             }
//           } else {
//             state_next := STATE_FCS_1
//           }
//         } .otherwise {
//           state_next := STATE_PAYLOAD
//         }
//       }

//       is (STATE_PAD) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := s_tdata_reg
//         xgmii_txc_next := 0.U
//         s_tdata_next := 0.U
//         s_empty_next := 0.U
//         stat_tx_byte_next := ctrlW.U
//         update_crc := true.B

//         when (frame_min_count_reg > ctrlW.U) {
//           state_next := STATE_PAD
//         } .otherwise {
//           s_empty_next := (ctrlW.U - frame_min_count_reg)
//           state_next := STATE_FCS_1
//         }
//       }

//       is (STATE_FCS_1) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := fcs_output_txd_0
//         xgmii_txc_next := fcs_output_txc_0
//         stat_tx_byte_next := ctrlW.U
//         update_crc := true.B

//         val ifg_val = Mux(io.cfg_tx_ifg > 12.U, io.cfg_tx_ifg, 12.U)
//         ifg_count_next := (ifg_val - ifg_offset) + deficit_idle_count_reg

//         when (frame_error_reg) {
//           state_next := STATE_ERR
//         } .otherwise {
//           state_next := STATE_FCS_2
//         }
//       }

//       is (STATE_FCS_2) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := fcs_output_txd_1
//         xgmii_txc_next := fcs_output_txc_1
//         stat_tx_byte_next := (4.U - s_empty_reg)
//         frame_len_next := frame_len_reg + (4.U - s_empty_reg)

//         when (extra_cycle) {
//           state_next := STATE_FCS_3
//         } .otherwise {
//           stat_tx_pkt_len_next := frame_len_next
//           stat_tx_pkt_good_next := !frame_error_reg
//           stat_tx_pkt_bad_next := frame_error_reg
//           stat_tx_pkt_ucast_next := !is_mcast_reg
//           stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
//           stat_tx_pkt_bcast_next := is_bcast_reg
//           stat_tx_pkt_vlan_next := is_8021q_reg
//           stat_tx_err_oversize_next := frame_oversize_reg
//           state_next := STATE_IFG
//         }
//       }

//     is (STATE_FCS_3) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := Cat(XGMII_TERM, XGMII_IDLE, XGMII_IDLE, XGMII_IDLE)
//         xgmii_txc_next := Fill(ctrlW, 1.U)

//         stat_tx_pkt_len_next := frame_len_reg
//         stat_tx_pkt_good_next := !frame_error_reg
//         stat_tx_pkt_bad_next := frame_error_reg
//         stat_tx_pkt_ucast_next := !is_mcast_reg
//         stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
//         stat_tx_pkt_bcast_next := is_bcast_reg
//         stat_tx_pkt_vlan_next := is_8021q_reg
//         stat_tx_err_oversize_next := frame_oversize_reg

//         if (dicEn) {
//           // FIX: Check ifg_count_reg instead of ifg_count_next to break the cycle
//           when (ifg_count_reg > 3.U) {
//             state_next := STATE_IFG
//           } .otherwise {
//             deficit_idle_count_next := ifg_count_reg // Capture current value
//             ifg_count_next := 0.U
//             s_axis_tx_tready_next := true.B
//             state_next := STATE_IDLE
//           }
//         } else {
//           // FIX: Check ifg_count_reg here too
//           when (ifg_count_reg > 0.U) {
//             state_next := STATE_IFG
//           } .otherwise {
//             state_next := STATE_IDLE
//           }
//         }
//       }

//       is (STATE_ERR) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := Cat(XGMII_ERROR, XGMII_ERROR, XGMII_ERROR, XGMII_TERM)
//         xgmii_txc_next := Fill(ctrlW, 1.U)
        
//         ifg_count_next := Mux(io.cfg_tx_ifg > 12.U, io.cfg_tx_ifg, 12.U)
        
//         stat_tx_pkt_len_next := frame_len_reg
//         stat_tx_pkt_good_next := !frame_error_reg
//         stat_tx_pkt_bad_next := frame_error_reg
//         stat_tx_pkt_ucast_next := !is_mcast_reg
//         stat_tx_pkt_mcast_next := is_mcast_reg && !is_bcast_reg
//         stat_tx_pkt_bcast_next := is_bcast_reg
//         stat_tx_pkt_vlan_next := is_8021q_reg
//         stat_tx_err_oversize_next := frame_oversize_reg
        
//         state_next := STATE_IFG
//       }

//     is (STATE_IFG) {
//         s_axis_tx_tready_next := frame_next
//         xgmii_txd_next := VecInit(Seq.fill(ctrlW)(XGMII_IDLE)).asUInt
//         xgmii_txc_next := Fill(ctrlW, 1.U)

//         // FIX: Calculate the next value into a temp variable first
//         val ifg_decremented = Mux(ifg_count_reg > 4.U, ifg_count_reg - 4.U, 0.U)
        
//         // Assign default next value
//         ifg_count_next := ifg_decremented

//         if (dicEn) {
//            // FIX: Check the temp variable 'ifg_decremented', not the wire 'ifg_count_next'
//            when (ifg_decremented > 3.U || frame_reg) {
//              state_next := STATE_IFG
//            } .otherwise {
//              deficit_idle_count_next := ifg_decremented
//              ifg_count_next := 0.U // This overwrite is now safe
//              state_next := STATE_IDLE
//            }
//         } else {
//            // FIX: Check the temp variable here too
//            when (ifg_decremented > 0.U || frame_reg) {
//              state_next := STATE_IFG
//            } .otherwise {
//              state_next := STATE_IDLE
//            }
//         }
//       }
//     }
//   }

//   // --- Sequential Logic ---
//   state_reg               := state_next
//   frame_reg               := frame_next
//   frame_error_reg         := frame_error_next
//   frame_oversize_reg      := frame_oversize_next
//   frame_min_count_reg     := frame_min_count_next
//   hdr_ptr_reg             := hdr_ptr_next
//   is_mcast_reg            := is_mcast_next
//   is_bcast_reg            := is_bcast_next
//   is_8021q_reg            := is_8021q_next
//   frame_len_reg           := frame_len_next
//   frame_len_lim_cyc_reg   := frame_len_lim_cyc_next
//   frame_len_lim_last_reg  := frame_len_lim_last_next
//   frame_len_lim_check_reg := frame_len_lim_check_next
//   ifg_cnt_reg             := ifg_cnt_next
//   ifg_count_reg           := ifg_count_next
//   deficit_idle_count_reg  := deficit_idle_count_next

//   s_tdata_reg             := s_tdata_next
//   s_empty_reg             := s_empty_next
//   s_axis_tx_tready_reg    := s_axis_tx_tready_next

//   m_axis_tx_cpl_ts_reg    := m_axis_tx_cpl_ts_next
//   m_axis_tx_cpl_tag_reg   := m_axis_tx_cpl_tag_next
//   m_axis_tx_cpl_valid_reg := m_axis_tx_cpl_valid_next

//   when (!gbxIfEn.B || !io.tx_gbx_req_stall) {
//     for (i <- 0 until 3) {
//       crc_state_reg(i) := crc_state(i)
//     }
//   }
  
//   when (update_crc) {
//     crc_state_reg(3) := crc_state(3)
//   }
  
//   when (reset_crc) {
//     crc_state_reg(3) := "hFFFFFFFF".U
//   }

//   xgmii_txd_reg      := xgmii_txd_next
//   xgmii_txc_reg      := xgmii_txc_next
//   xgmii_tx_valid_reg := !io.tx_gbx_req_stall
  
//   start_packet_reg   := start_packet_next
  
//   stat_tx_byte_reg          := stat_tx_byte_next
//   stat_tx_pkt_len_reg       := stat_tx_pkt_len_next
//   stat_tx_pkt_ucast_reg     := stat_tx_pkt_ucast_next
//   stat_tx_pkt_mcast_reg     := stat_tx_pkt_mcast_next
//   stat_tx_pkt_bcast_reg     := stat_tx_pkt_bcast_next
//   stat_tx_pkt_vlan_reg      := stat_tx_pkt_vlan_next
//   stat_tx_pkt_good_reg      := stat_tx_pkt_good_next
//   stat_tx_pkt_bad_reg       := stat_tx_pkt_bad_next
//   stat_tx_err_oversize_reg  := stat_tx_err_oversize_next
//   stat_tx_err_user_reg      := stat_tx_err_user_next
//   stat_tx_err_underflow_reg := stat_tx_err_underflow_next

//   tx_gbx_sync_reg := io.tx_gbx_req_sync
// }




// object Axis2Xgmii32 {
//   def apply(p: Axis2Xgmii32Params): Axis2Xgmii32 = Module(new Axis2Xgmii32(
//     dataW = p.dataW,
//     ctrlW = p.ctrlW,
//     gbxIfEn = p.gbxIfEn,
//     gbxCnt = p.gbxCnt,
//     paddingEn = p.paddingEn,
//     dicEn = p.dicEn,
//     minFrameLen = p.minFrameLen,
//     ptpTsEn = p.ptpTsEn,
//     ptpTsW = p.ptpTsW,
//     txCplCtrlInTuser = p.txCplCtrlInTuser,
//     idW = p.idW
//   ))
// }


// object Main extends App {
//   val mainClassName = "Nfmac10g"
//   val coreDir = s"modules/${mainClassName.toLowerCase()}"
//   Axis2Xgmii32Params.synConfigMap.foreach { case (configName, p) =>
//     println(s"Generating Verilog for config: $configName")
//     ChiselStage.emitSystemVerilog(
//       new Axis2Xgmii32(
//         dataW = p.dataW,
//         ctrlW = p.ctrlW,
//         gbxIfEn = p.gbxIfEn,
//         gbxCnt = p.gbxCnt,
//         paddingEn = p.paddingEn,
//         dicEn = p.dicEn,
//         minFrameLen = p.minFrameLen,
//         ptpTsEn = p.ptpTsEn,
//         ptpTsW = p.ptpTsW,
//         txCplCtrlInTuser = p.txCplCtrlInTuser,
//         idW = p.idW
//       ),
//       firtoolOpts = Array(
//         "--lowering-options=disallowLocalVariables,disallowPackedArrays",
//         "--disable-all-randomization",
//         "--strip-debug-info",
//         "--split-verilog",
//         s"-o=${coreDir}/generated/synTestCases/$configName"
//       )
//     )
//     // Synthesis collateral generation
//     sdcFile.create(s"${coreDir}/generated/synTestCases/$configName")
//     YosysTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     StaTclFile.create(mainClassName, s"${coreDir}/generated/synTestCases/$configName")
//     RunScriptFile.create(mainClassName, Axis2Xgmii32Params.synConfigs, s"${coreDir}/generated/synTestCases")
//   }
// }