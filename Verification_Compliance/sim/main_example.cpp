#include "Vxxv_ethernet_0_exdes_tb.h"
#include "verilated.h"
#if VM_TRACE
#include "verilated_vcd_c.h"
#endif

static vluint64_t main_time = 0;
double sc_time_stamp() { return main_time; }

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);
  auto* top = new Vxxv_ethernet_0_exdes_tb;  // <-- TB top here

#if VM_TRACE
  Verilated::traceEverOn(true);
  VerilatedVcdC* tfp = new VerilatedVcdC;
  top->trace(tfp, 99);
  tfp->open("wave.vcd");
#endif

  while (!Verilated::gotFinish()) {
    top->eval();
#if VM_TRACE
    tfp->dump(main_time);
#endif
    ++main_time;
  }

#if VM_TRACE
  tfp->close();
#endif
  delete top;
  return 0;
}