VERILATOR = verilator
TOP       = axis_loopback
SRC       = ../rtl/axis_loopback.sv
TB        = test_axis_loopback.cpp
DPI_SRC   = dpi_socket.c
BUILD     = obj_dir

all: run

# Step 1: Generate C++ model
$(BUILD)/V$(TOP).mk: $(SRC)
	$(VERILATOR) -Wall --Wno-UNUSEDSIGNAL --cc --sv -Mdir $(BUILD) $(SRC)

# Step 2: Build object files
$(BUILD)/V$(TOP)__ALL.o: $(BUILD)/V$(TOP).mk
	$(MAKE) -C $(BUILD) -f V$(TOP).mk

# Step 3: Link everything together into final executable
V$(TOP): $(BUILD)/V$(TOP)__ALL.o $(TB) $(DPI_SRC)
	g++ -o $@ $(BUILD)/V$(TOP)__ALL.o $(TB) $(DPI_SRC) \
	    -I/usr/share/verilator/include -I/usr/share/verilator/include/vltstd \
	    -pthread -latomic

# Step 4: Run simulation
run: V$(TOP)
	./V$(TOP)

clean:
	rm -rf $(BUILD) V$(TOP) wave.vcd
