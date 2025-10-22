// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Model implementation (design independent parts)

#include "Vaxis_loopback__pch.h"

//============================================================
// Constructors

Vaxis_loopback::Vaxis_loopback(VerilatedContext* _vcontextp__, const char* _vcname__)
    : VerilatedModel{*_vcontextp__}
    , vlSymsp{new Vaxis_loopback__Syms(contextp(), _vcname__, this)}
    , clk156{vlSymsp->TOP.clk156}
    , resetn{vlSymsp->TOP.resetn}
    , tx_axis_tkeep{vlSymsp->TOP.tx_axis_tkeep}
    , tx_axis_tvalid{vlSymsp->TOP.tx_axis_tvalid}
    , tx_axis_tready{vlSymsp->TOP.tx_axis_tready}
    , tx_axis_tlast{vlSymsp->TOP.tx_axis_tlast}
    , rx_axis_tkeep{vlSymsp->TOP.rx_axis_tkeep}
    , rx_axis_tvalid{vlSymsp->TOP.rx_axis_tvalid}
    , rx_axis_tready{vlSymsp->TOP.rx_axis_tready}
    , rx_axis_tlast{vlSymsp->TOP.rx_axis_tlast}
    , tx_axis_tdata{vlSymsp->TOP.tx_axis_tdata}
    , rx_axis_tdata{vlSymsp->TOP.rx_axis_tdata}
    , rootp{&(vlSymsp->TOP)}
{
    // Register model with the context
    contextp()->addModel(this);
}

Vaxis_loopback::Vaxis_loopback(const char* _vcname__)
    : Vaxis_loopback(Verilated::threadContextp(), _vcname__)
{
}

//============================================================
// Destructor

Vaxis_loopback::~Vaxis_loopback() {
    delete vlSymsp;
}

//============================================================
// Evaluation function

#ifdef VL_DEBUG
void Vaxis_loopback___024root___eval_debug_assertions(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG
void Vaxis_loopback___024root___eval_static(Vaxis_loopback___024root* vlSelf);
void Vaxis_loopback___024root___eval_initial(Vaxis_loopback___024root* vlSelf);
void Vaxis_loopback___024root___eval_settle(Vaxis_loopback___024root* vlSelf);
void Vaxis_loopback___024root___eval(Vaxis_loopback___024root* vlSelf);

void Vaxis_loopback::eval_step() {
    VL_DEBUG_IF(VL_DBG_MSGF("+++++TOP Evaluate Vaxis_loopback::eval_step\n"); );
#ifdef VL_DEBUG
    // Debug assertions
    Vaxis_loopback___024root___eval_debug_assertions(&(vlSymsp->TOP));
#endif  // VL_DEBUG
    vlSymsp->__Vm_deleter.deleteAll();
    if (VL_UNLIKELY(!vlSymsp->__Vm_didInit)) {
        vlSymsp->__Vm_didInit = true;
        VL_DEBUG_IF(VL_DBG_MSGF("+ Initial\n"););
        Vaxis_loopback___024root___eval_static(&(vlSymsp->TOP));
        Vaxis_loopback___024root___eval_initial(&(vlSymsp->TOP));
        Vaxis_loopback___024root___eval_settle(&(vlSymsp->TOP));
    }
    VL_DEBUG_IF(VL_DBG_MSGF("+ Eval\n"););
    Vaxis_loopback___024root___eval(&(vlSymsp->TOP));
    // Evaluate cleanup
    Verilated::endOfEval(vlSymsp->__Vm_evalMsgQp);
}

//============================================================
// Events and timing
bool Vaxis_loopback::eventsPending() { return false; }

uint64_t Vaxis_loopback::nextTimeSlot() {
    VL_FATAL_MT(__FILE__, __LINE__, "", "%Error: No delays in the design");
    return 0;
}

//============================================================
// Utilities

const char* Vaxis_loopback::name() const {
    return vlSymsp->name();
}

//============================================================
// Invoke final blocks

void Vaxis_loopback___024root___eval_final(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD void Vaxis_loopback::final() {
    Vaxis_loopback___024root___eval_final(&(vlSymsp->TOP));
}

//============================================================
// Implementations of abstract methods from VerilatedModel

const char* Vaxis_loopback::hierName() const { return vlSymsp->name(); }
const char* Vaxis_loopback::modelName() const { return "Vaxis_loopback"; }
unsigned Vaxis_loopback::threads() const { return 1; }
void Vaxis_loopback::prepareClone() const { contextp()->prepareClone(); }
void Vaxis_loopback::atClone() const {
    contextp()->threadPoolpOnClone();
}

//============================================================
// Trace configuration

VL_ATTR_COLD void Vaxis_loopback::trace(VerilatedVcdC* tfp, int levels, int options) {
    vl_fatal(__FILE__, __LINE__, __FILE__,"'Vaxis_loopback::trace()' called on model that was Verilated without --trace option");
}
