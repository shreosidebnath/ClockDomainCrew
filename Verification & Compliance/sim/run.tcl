# sim/run.tcl
# Robust Vivado Tcl for DPI + xsim on Windows

proc find_prog {name} {
    # Try PATH
    if {![catch {exec where $name} out]} {
        # 'where' can return multiple lines; pick the first
        set lines [split $out \n]
        return [string trim [lindex $lines 0]]
    }
    # Try XILINX_VIVADO
    if {[info exists ::env(XILINX_VIVADO)]} {
        set cand [file join $::env(XILINX_VIVADO) bin ${name}.bat]
        if {[file exists $cand]} { return $cand }
        set cand [file join $::env(XILINX_VIVADO) bin ${name}.exe]
        if {[file exists $cand]} { return $cand }
    }
    return ""
}

# Paths (adjust if your tree differs)
set proj_root [file normalize [file join [pwd] ".."]]
set sim_dir   [file normalize [file join $proj_root "sim"]]
set rtl_dir   [file normalize [file join $proj_root "rtl"]]

# Tools
set xsc_path  [find_prog xsc]
set xelab_path [find_prog xelab]
set xsim_path [find_prog xsim]
if {$xsc_path eq ""}  { error "Could not find xsc. Ensure Vivado is on PATH or XILINX_VIVADO is set." }
if {$xelab_path eq ""} { error "Could not find xelab. Ensure Vivado is on PATH." }
if {$xsim_path eq ""}  { error "Could not find xsim. Ensure Vivado is on PATH." }

# 1) Compile DPI C -> dll (library stem will be 'dpi')
cd $sim_dir
puts "Compiling DPI with: $xsc_path"
catch { exec -- $xsc_path [file normalize [file join $sim_dir "dpi_sock_win.c"]] } result
puts $result

# 2) Read RTL
cd $rtl_dir
read_verilog [file normalize [file join $rtl_dir "dpi_axis_bridge.sv"]]
read_verilog [file normalize [file join $rtl_dir "top_tb.sv"]]

# 3) Elaborate: -sv_lib wants the library stem created by xsc
set libstem dpi
set top     top_tb

exec -- $xelab_path $top -sv_lib $libstem -timescale 1ns/1ps -debug typical -s top_sim

puts "Elaborating with: $xelab_path"
exec -- $xelab_path $top -sv_lib $libstem -timescale 1ns/1ps -debug typical -s top_sim

# 4) Run simulation
cd $sim_dir
puts "Running xsim with: $xsim_path"
exec -- $xsim_path top_sim -tclbatch [file normalize [file join $sim_dir "xsim_run.tcl"]]
