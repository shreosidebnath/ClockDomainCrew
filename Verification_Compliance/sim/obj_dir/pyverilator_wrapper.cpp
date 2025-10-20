#include <cstddef>
#include "verilated.h"
#include "verilated_vcd_c.h"
#include "Vaxis_loopback.h"
    
// pyverilator defined values
// first declare variables as extern
extern const char* _pyverilator_module_name;
extern const uint32_t _pyverilator_num_inputs;
extern const char* _pyverilator_inputs[];
extern const uint32_t _pyverilator_input_widths[];
extern const uint32_t _pyverilator_num_outputs;
extern const char* _pyverilator_outputs[];
extern const uint32_t _pyverilator_output_widths[];
extern const uint32_t _pyverilator_num_internal_signals;
extern const char* _pyverilator_internal_signals[];
extern const uint32_t _pyverilator_internal_signal_widths[];
extern const uint32_t _pyverilator_num_rules;
extern const char* _pyverilator_rules[];
extern const char* _pyverilator_json_data;
// now initialize the variables
const char* _pyverilator_module_name = "axis_loopback";
const uint32_t _pyverilator_num_inputs = 7;
const char* _pyverilator_inputs[] = {"&clk156","&resetn","&tx_axis_tkeep","&tx_axis_tvalid","&tx_axis_tlast","&rx_axis_tready","&tx_axis_tdata"};
const uint32_t _pyverilator_input_widths[] = {1,1,8,1,1,1,64};

const uint32_t _pyverilator_num_outputs = 5;
const char* _pyverilator_outputs[] = {"&tx_axis_tready","&rx_axis_tkeep","&rx_axis_tvalid","&rx_axis_tlast","&rx_axis_tdata"};
const uint32_t _pyverilator_output_widths[] = {1,8,1,1,64};

const uint32_t _pyverilator_num_internal_signals = 0;
const char* _pyverilator_internal_signals[] = {};
const uint32_t _pyverilator_internal_signal_widths[] = {};

const char* _pyverilator_json_data = "null";

// this is required by verilator for verilog designs using $time
// main_time is incremented in eval
double main_time = 0;

// What to call when $finish is called
typedef void (*vl_finish_callback)(const char* filename, int line, const char* hier);
vl_finish_callback vl_user_finish = NULL;
    
double sc_time_stamp() {
return main_time;
}
void vl_finish (const char* filename, int linenum, const char* hier) VL_MT_UNSAFE {
    if (vl_user_finish) {
       (*vl_user_finish)(filename, linenum, hier);
    } else {
        // Default implementation
        VL_PRINTF("- %s:%d: Verilog $finish\n", filename, linenum);  // Not VL_PRINTF_MT, already on main thread
        if (Verilated::gotFinish()) {
            VL_PRINTF("- %s:%d: Second verilog $finish, exiting\n", filename, linenum);  // Not VL_PRINTF_MT, already on main thread
            Verilated::flushCall();
            exit(0);
        }
        Verilated::gotFinish(true);
    }
}
// function definitions
// helper functions for basic verilator tasks
extern "C" { //Open an extern C closed in the footer
Vaxis_loopback* construct() {
    Verilated::traceEverOn(true);
    Vaxis_loopback* top = new Vaxis_loopback();
    return top;
}
int eval(Vaxis_loopback* top) {
    top->eval();
    main_time++;
    return 0;
}
int destruct(Vaxis_loopback* top) {
    if (top != nullptr) {
        delete top;
        top = nullptr;
    }
    return 0;
}
VerilatedVcdC* start_vcd_trace(Vaxis_loopback* top, const char* filename) {
    VerilatedVcdC* tfp = new VerilatedVcdC;
    top->trace(tfp, 99);
    tfp->open(filename);
    return tfp;
}
int add_to_vcd_trace(VerilatedVcdC* tfp, int time) {
    tfp->dump(time);
    return 0;
}
int flush_vcd_trace(VerilatedVcdC* tfp) {
    tfp->flush();
    return 0;
}
int stop_vcd_trace(VerilatedVcdC* tfp) {
    tfp->close();
    return 0;
}
bool get_finished() {
    return Verilated::gotFinish();
}
void set_finished(bool b) {
    Verilated::gotFinish(b);
}
void set_vl_finish_callback(vl_finish_callback callback) {
    vl_user_finish = callback;
}
void set_command_args(int argc, char** argv) {
    Verilated::commandArgs(argc, argv);
}

uint32_t get_&tx_axis_tready(Vaxis_loopback* top){return top->&tx_axis_tready;}
uint32_t get_&rx_axis_tkeep(Vaxis_loopback* top){return top->&rx_axis_tkeep;}
uint32_t get_&rx_axis_tvalid(Vaxis_loopback* top){return top->&rx_axis_tvalid;}
uint32_t get_&rx_axis_tlast(Vaxis_loopback* top){return top->&rx_axis_tlast;}
uint64_t get_&rx_axis_tdata(Vaxis_loopback* top){return top->&rx_axis_tdata;}
uint32_t get_&clk156(Vaxis_loopback* top){return top->&clk156;}
uint32_t get_&resetn(Vaxis_loopback* top){return top->&resetn;}
uint32_t get_&tx_axis_tkeep(Vaxis_loopback* top){return top->&tx_axis_tkeep;}
uint32_t get_&tx_axis_tvalid(Vaxis_loopback* top){return top->&tx_axis_tvalid;}
uint32_t get_&tx_axis_tlast(Vaxis_loopback* top){return top->&tx_axis_tlast;}
uint32_t get_&rx_axis_tready(Vaxis_loopback* top){return top->&rx_axis_tready;}
uint64_t get_&tx_axis_tdata(Vaxis_loopback* top){return top->&tx_axis_tdata;}
int set_&clk156(Vaxis_loopback* top, uint32_t new_value){ top->&clk156 = new_value; return 0;}
int set_&resetn(Vaxis_loopback* top, uint32_t new_value){ top->&resetn = new_value; return 0;}
int set_&tx_axis_tkeep(Vaxis_loopback* top, uint32_t new_value){ top->&tx_axis_tkeep = new_value; return 0;}
int set_&tx_axis_tvalid(Vaxis_loopback* top, uint32_t new_value){ top->&tx_axis_tvalid = new_value; return 0;}
int set_&tx_axis_tlast(Vaxis_loopback* top, uint32_t new_value){ top->&tx_axis_tlast = new_value; return 0;}
int set_&rx_axis_tready(Vaxis_loopback* top, uint32_t new_value){ top->&rx_axis_tready = new_value; return 0;}
int set_&tx_axis_tdata(Vaxis_loopback* top, uint64_t new_value){ top->&tx_axis_tdata = new_value; return 0;}
}