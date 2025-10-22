// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See Vaxis_loopback.h for the primary calling header

#ifndef VERILATED_VAXIS_LOOPBACK___024ROOT_H_
#define VERILATED_VAXIS_LOOPBACK___024ROOT_H_  // guard

#include "verilated.h"


class Vaxis_loopback__Syms;

class alignas(VL_CACHE_LINE_BYTES) Vaxis_loopback___024root final : public VerilatedModule {
  public:

    // DESIGN SPECIFIC STATE
    VL_IN8(clk156,0,0);
    VL_IN8(resetn,0,0);
    VL_IN8(tx_axis_tkeep,7,0);
    VL_IN8(tx_axis_tvalid,0,0);
    VL_OUT8(tx_axis_tready,0,0);
    VL_IN8(tx_axis_tlast,0,0);
    VL_OUT8(rx_axis_tkeep,7,0);
    VL_OUT8(rx_axis_tvalid,0,0);
    VL_IN8(rx_axis_tready,0,0);
    VL_OUT8(rx_axis_tlast,0,0);
    CData/*0:0*/ __VstlFirstIteration;
    CData/*0:0*/ __VicoFirstIteration;
    CData/*0:0*/ __Vtrigprevexpr___TOP__clk156__0;
    CData/*0:0*/ __VactContinue;
    IData/*31:0*/ __VactIterCount;
    VL_IN64(tx_axis_tdata,63,0);
    VL_OUT64(rx_axis_tdata,63,0);
    VlTriggerVec<1> __VstlTriggered;
    VlTriggerVec<1> __VicoTriggered;
    VlTriggerVec<1> __VactTriggered;
    VlTriggerVec<1> __VnbaTriggered;

    // INTERNAL VARIABLES
    Vaxis_loopback__Syms* const vlSymsp;

    // CONSTRUCTORS
    Vaxis_loopback___024root(Vaxis_loopback__Syms* symsp, const char* v__name);
    ~Vaxis_loopback___024root();
    VL_UNCOPYABLE(Vaxis_loopback___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
