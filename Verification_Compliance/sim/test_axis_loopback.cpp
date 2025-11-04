#include "Vaxis_loopback.h"
#include "verilated.h"
#include "verilated_vcd_c.h"
#include <iostream>

vluint64_t main_time = 0;
double sc_time_stamp() { return main_time; }

int main(int argc, char **argv)
{
    Verilated::commandArgs(argc, argv);
    Verilated::traceEverOn(true);

    Vaxis_loopback *top = new Vaxis_loopback;

    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open("wave.vcd");

    top->resetn = 1;
    top->tx_axis_tkeep = 0xFF;
    top->tx_axis_tlast = 0;
    top->rx_axis_tready = 1;
    top->tx_axis_tvalid = 1;

    printf("[C++] Sim start, expecting Python echo...\n");

    for (int cycle = 0; cycle < 20; cycle++)
    {
        // Rising edge
        top->clk156 = 1;
        top->tx_axis_tdata = 0x11110000AAAABBBB + cycle;
        top->eval();
        tfp->dump(main_time++);

        // Falling edge
        top->clk156 = 0;
        top->eval();
        tfp->dump(main_time++);

        main_time++;
    }

    for (int i = 0; i < 100; i++)
    {
        top->clk156 = !top->clk156;
        top->eval();
        tfp->dump(main_time++);
    }

    top->final();
    tfp->close();
    delete tfp;
    delete top;
    printf("[C++] Simulation complete, waveform written to wave.vcd\n");
    return 0;
}
