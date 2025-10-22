#include "Vaxis_loopback.h"
#include "verilated.h"
#include "verilated_vcd_c.h"

int main(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
    Verilated::traceEverOn(true);

    Vaxis_loopback top;
    VerilatedVcdC trace;
    top.trace(&trace, 99);
    trace.open("wave.vcd");

    top.resetn = 0;
    top.clk156 = 0;

    // 10 cycles reset
    for (int i = 0; i < 20; ++i) {
        top.clk156 = !top.clk156;
        top.eval();
        trace.dump(i);
    }

    top.resetn = 1;

    // Simulate 200 cycles
    for (int i = 0; i < 200; ++i) {
        top.clk156 = !top.clk156;
        top.tx_axis_tdata  = 0xABCD1234;
        top.tx_axis_tkeep  = 0xFF;
        top.tx_axis_tvalid = 1;
        top.tx_axis_tlast  = 0;

        top.eval();
        trace.dump(20 + i);
    }

    trace.close();
    return 0;
}
