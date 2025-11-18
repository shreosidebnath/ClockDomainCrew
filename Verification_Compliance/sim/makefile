VERILATOR = verilator
TOP       = axis_loopback
SRC       = ../rtl/axis_loopback.sv
TB        = test_axis_loopback.cpp
DPI_SRC   = dpi_socket.cpp
BUILD     = obj_dir

all: run

# Step 1: Generate C++ model
$(BUILD)/V$(TOP).mk: $(SRC)
	$(VERILATOR) -Wall --Wno-UNUSEDSIGNAL --cc --sv --trace -Mdir $(BUILD) $(SRC)

# Step 2: Build object files
$(BUILD)/V$(TOP)__ALL.o: $(BUILD)/V$(TOP).mk
	$(MAKE) -C $(BUILD) -f V$(TOP).mk

# Step 3: Link everything together into final executable
V$(TOP): $(BUILD)/V$(TOP)__ALL.o $(TB) $(DPI_SRC)
	g++ -o $@ $(DPI_SRC) $(TB) \
	    $(BUILD)/V$(TOP)__ALL.o $(BUILD)/libV$(TOP).a $(BUILD)/libverilated.a \
	    -I$(BUILD) \
	    -I/usr/share/verilator/include -I/usr/share/verilator/include/vltstd \
	    -pthread -latomic

# Step 4: Run simulation
run: V$(TOP)
	./V$(TOP)

clean:
	rm -rf $(BUILD) V$(TOP) wave.vcd

# ===== Example (Kria) quick path — uses rtl_example + Verilog TB =====
EX_TOP 		?= xxv_ethernet_0_exdes_tb
EX_DIR      ?= ../rtl_example
EX_BUILD    ?= obj_dir_example
EX_TRACE    ?= 0
EX_PORT     ?= 9000

# Gather all Verilog/SV sources in rtl_example (ignore .xdc constraints)
EX_SRCS := $(filter-out %.xdc, $(wildcard $(EX_DIR)/*.v) $(wildcard $(EX_DIR)/*.sv))

# Verilator flags for example path
EX_VFLAGS := -sv --timing --cc --exe --build \
             -O3 -CFLAGS "-O3 -pthread" \
             --top-module $(EX_TOP) \
             --Mdir $(EX_BUILD) \
             -Wno-UNOPTFLAT -Wno-WIDTH -Wno-CASEINCOMPLETE -Wno-LITENDIAN \
             -Wno-UNUSEDSIGNAL -Wno-UNUSEDPARAM -Wno-DECLFILENAME \
             -Wno-GENUNNAMED -Wno-VARHIDDEN -Wno-IMPLICIT -Wno-SYNCASYNCNET \
             -Wno-UNDRIVEN -Wno-PINCONNECTEMPTY

EX_VFLAGS += -DSIM_SPEED_UP

ifeq ($(EX_TRACE),1)
  EX_VFLAGS += --trace
  EX_DEFS   := -DVM_TRACE
endif

# Build the example (uses a tiny C++ harness that just runs the SV TB)
.PHONY: example_build
example_build:
	verilator $(EX_VFLAGS) \
	  $(EX_SRCS) \
	  ./main_example.cpp

# Run with your existing Python socket server (optional). If your TB is self-contained, use example_run_nopy.
.PHONY: example_run
example_run: example_build
	(set -m; python3 ./server.py --port $(EX_PORT) &)
	./$(EX_BUILD)/V$(EX_TOP)

.PHONY: example_run_nopy
example_run_nopy: example_build
	./$(EX_BUILD)/V$(EX_TOP)

.PHONY: example_clean
example_clean:
	rm -rf $(EX_BUILD) wave.vcd
