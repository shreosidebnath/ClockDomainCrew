# Use bash shell
SHELL := C:/PROGRA~1/Git/bin/bash.exe

TOPLEVEL_LANG = verilog
SIM = verilator

TOPLEVEL = axis_loopback
MODULE = test_linkup

VERILOG_SOURCES = ../rtl/axis_loopback.sv

# Optional: set wave output
EXTRA_ARGS += --trace

# Use Python from conda env
PYTHON := C:/Users/saaed/anaconda3/envs/capstone-scapy/python.exe
VERILATOR := C:/Users/saaed/OneDrive/Desktop/Capstone/verilator/install/bin/verilator.exe

override PYTHON_ARCH := 64bit
override LIBPYTHON_LOC = C:/Users/saaed/anaconda3/envs/capstone-scapy/python311.dll

override VLT_VERSION := 5.040
override VLT_MIN := 4.106
override MIN_VERSION := $(VLT_MIN)

override MAKE := make

include $(shell cocotb-config --makefiles)/Makefile.sim
