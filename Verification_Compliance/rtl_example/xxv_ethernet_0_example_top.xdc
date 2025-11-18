
## ------------------------------------------------------------------------------
##   (c) Copyright 2020-2021 Advanced Micro Devices, Inc. All rights reserved.
## 
##   This file contains confidential and proprietary information
##   of Advanced Micro Devices, Inc. and is protected under U.S. and
##   international copyright and other intellectual property
##   laws.
## 
##   DISCLAIMER
##   This disclaimer is not a license and does not grant any
##   rights to the materials distributed herewith. Except as
##   otherwise provided in a valid license issued to you by
##   AMD, and to the maximum extent permitted by applicable
##   law: (1) THESE MATERIALS ARE MADE AVAILABLE \"AS IS\" AND
##   WITH ALL FAULTS, AND AMD HEREBY DISCLAIMS ALL WARRANTIES
##   AND CONDITIONS, EXPRESS, IMPLIED, OR STATUTORY, INCLUDING
##   BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, NON-
##   INFRINGEMENT, OR FITNESS FOR ANY PARTICULAR PURPOSE; and
##   (2) AMD shall not be liable (whether in contract or tort,
##   including negligence, or under any other theory of
##   liability) for any loss or damage of any kind or nature
##   related to, arising under or in connection with these
##   materials, including for any direct, or any indirect,
##   special, incidental, or consequential loss or damage
##   (including loss of data, profits, goodwill, or any type of
##   loss or damage suffered as a result of any action brought
##   by a third party) even if such damage or loss was
##   reasonably foreseeable or AMD had been advised of the
##   possibility of the same.
## 
##   CRITICAL APPLICATIONS
##   AMD products are not designed or intended to be fail-
##   safe, or for use in any application requiring fail-safe
##   performance, such as life-support or safety devices or
##   systems, Class III medical devices, nuclear facilities,
##   applications related to the deployment of airbags, or any
##   other applications that could lead to death, personal
##   injury, or severe property or environmental damage
##   (individually and collectively, \"Critical
##   Applications\"). Customer assumes the sole risk and
##   liability of any use of AMD products in Critical
##   Applications, subject only to applicable laws and
##   regulations governing limitations on product liability.
## 
##   THIS COPYRIGHT NOTICE AND DISCLAIMER MUST BE RETAINED AS
##   PART OF THIS FILE AT ALL TIMES.
##
## 
##
##       Owner:          
##       Revision:       $Id: $
##                       $Author: $
##                       $DateTime: $
##                       $Change: $
##       Description:
##
##////////////////////////////////////////////////////////////////////////////
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# XXV_ETHERNET example design-level XDC file
# ----------------------------------------------------------------------------------------------------------------------
## init_clk should be lesser or equal to reference clock.
### Transceiver Reference Clock Placement
### Transceivers should be adjacent to allow timing constraints to be met easily. 
### Full details of available transceiver locations can be found
### in the appropriate transceiver User Guide, or use the Transceiver Wizard.
create_clock -period 10.000 [get_ports dclk]
set_property IOSTANDARD LVCMOS18 [get_ports dclk]
### These are sample constraints, please use correct constraints for your device 
### update the gt_refclk pin location accordingly and un-comment the below two lines 
  #set_property PACKAGE_PIN AK38 [get_ports gt_refclk_p]
  #set_property PACKAGE_PIN AK39 [get_ports gt_refclk_n]

###Board constraints to be added here
### Below XDC constraints are for VCU108 board with xcvu095-ffva2104-2-e-es2 device
### Change these constraints as per your board and device
#### Push Buttons
###set_property PACKAGE_PIN D9 [get_ports sys_reset]
set_property IOSTANDARD LVCMOS18 [get_ports sys_reset]

#set_property LOC A10 [get_ports restart_tx_rx_0]
set_property IOSTANDARD LVCMOS18 [get_ports restart_tx_rx_0]

### LEDs
#set_property PACKAGE_PIN AT32 [get_ports rx_gt_locked_led_0]
set_property IOSTANDARD LVCMOS18 [get_ports rx_gt_locked_led_0]
##
#set_property PACKAGE_PIN AV34 [get_ports rx_block_lock_led_0]
set_property IOSTANDARD LVCMOS18 [get_ports rx_block_lock_led_0]
##
#set_property PACKAGE_PIN AY30 [get_ports completion_status[0]]
set_property IOSTANDARD LVCMOS18 [get_ports completion_status[0]]
##
#set_property PACKAGE_PIN BB32 [get_ports completion_status[1]]
set_property IOSTANDARD LVCMOS18 [get_ports completion_status[1]]
##
#set_property PACKAGE_PIN BF32 [get_ports completion_status[2]]
set_property IOSTANDARD LVCMOS18 [get_ports completion_status[2]]
##
#set_property PACKAGE_PIN AV36 [get_ports completion_status[3]]
set_property IOSTANDARD LVCMOS18 [get_ports completion_status[3]]
##
#set_property PACKAGE_PIN AY35 [get_ports completion_status[4]]
set_property IOSTANDARD LVCMOS18 [get_ports completion_status[4]]







### Any other Constraints  
set_false_path -to [get_cells -hierarchical -filter {NAME =~ */i_*_axi_if_top/*/i_*_syncer/*meta_reg*}]
set_false_path -to [get_cells -hierarchical -filter {NAME =~ */i_*_SYNC*/*stretch_reg*}]

set_false_path -to [get_cells -hierarchical -filter {NAME=~ */i*syncer/*d2_cdc_to*}]












set_false_path -from [get_clocks -of_objects [get_pins */inst/i_core_gtwiz_userclk_rx_inst_*/gen_gtwiz_userclk_rx_main.bufg_gt_usrclk*_inst/*]] -to [get_clocks -of_objects [get_pins */inst/i_core_gtwiz_userclk_tx_inst_*/gen_gtwiz_userclk_tx_main.bufg_gt_usrclk*_inst/*]] -quiet
set_false_path -from [get_clocks -of_objects [get_pins */inst/i_core_gtwiz_userclk_tx_inst_0/gen_gtwiz_userclk_tx_main.bufg_gt_usrclk*_inst/*]] -to [get_clocks -of_objects [get_pins */inst/i_core_gtwiz_userclk_rx_inst_0/gen_gtwiz_userclk_rx_main.bufg_gt_usrclk2_inst/*]] -quiet





set_max_delay  10.000 -datapath_only -from [get_pins -of [get_cells -hier -filter { name =~ */pktgen_enable_int_reg*}] -filter { name =~ *C } ]\
-to [get_pins -of [get_cells -hier -filter { name =~ */*_sync_pkt_gen_enable/s_out_d2_cdc_to_reg*}] -filter { name =~ *D } ] -quiet
set_max_delay  10.000 -datapath_only -from [get_pins -of [get_cells -hier -filter { name =~ */i_RX_WD_ALIGN/align_status_reg[*]*}] -filter { name =~ *C } ]\
-to [get_pins -of [get_cells -hier -filter { name =~ */s_out_d2_cdc_to_reg*}] -filter { name =~ *D } ] -quiet
set_max_delay -from [get_pins -of [get_cells -hier -filter { name =~ */rx_errors_int_reg*}] -filter { name =~ *C } ]\
-to [get_pins -of [get_cells -hier -filter { name =~ */s_out_d2_cdc_to_reg[*]*}] -filter { name =~ *D } ] 10.000 -datapath_only -quiet


set_max_delay -from [get_pins -of [get_cells -hier -filter { name =~ */mode_switch_reg*}] -filter { name =~ *C } ] 10.000 -datapath_only -quiet
set_max_delay -from [get_pins -of [get_cells -hier -filter { name =~ */pipe_reg*}] -filter { name =~ *C } ] 2.5 -datapath_only -quiet
set_max_delay 10.000 -datapath_only -from [get_pins -of [get_cells -hier -filter { name =~ */i_pif_registers/ctl_*x_opcode_*}] -filter { name =~ *C } ]  -quiet






create_waiver -quiet -type CDC -id {CDC-11} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */s_out_d2_cdc_to_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-11} -user "xxv_ethernet" -desc "The reset signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */rx_reset_done_async_r*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */s_out_d2_cdc_to_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-11} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAFFIC_GENERATOR/i_*_PKT_CHK/rx_block_lock_led_*d_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-2} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAFFIC_GENERATOR/i_*_PKT_CHK/rx_block_lock_led_*d_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-1} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_DELETE_FCS/*_d*_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAFFIC_GENERATOR/*_reg*}] -filter {name =~ *}]

create_waiver -quiet -type CDC -id {CDC-11} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAF_CHK*/rx_block_lock_led_*d_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-1} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAF_CHK*/rx_block_lock_led_*d_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-2} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_WD_ALIGN/align_status_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAF_CHK*/rx_block_lock_led_*d_reg*}] -filter {name =~ *D}]

create_waiver -quiet -type CDC -id {CDC-1} -user "xxv_ethernet" -desc "The align status signal is synced with different syncers where fan-out is expected and so can be waived" -tags "11999"\
-from [get_pins -of [get_cells -hier -filter {name =~ */i_RX_DECODER/data_*_reg*}] -filter {name =~ *C}]\
-to [get_pins -of [get_cells -hier -filter {name =~ */i_*_TRAF_CHK*/*_reg*}] -filter {name =~ *}]






set_max_delay -datapath_only -from [get_pins -of [get_cells -hier -filter { name =~ */*_axi_if_top/i_pif_registers/ctl_rsfec_enable_r_reg*}] -filter { name =~ *C }] 10.000 -quiet

set_max_delay -datapath_only -from [get_pins -of [get_cells -hier -filter { name =~ */*_axi_if_top/i_pif_registers/ctl_*x_max_packet_len_out_reg*}] -filter { name =~ *C }] 10.000 -quiet

set_max_delay -from [get_pins -of [get_cells -hier -filter { name =~ */inst/*x_*bit_gt_pipeline_serdes*/data_out_*d*}] -filter { name =~ *C } ] 10.000 -datapath_only -quiet
