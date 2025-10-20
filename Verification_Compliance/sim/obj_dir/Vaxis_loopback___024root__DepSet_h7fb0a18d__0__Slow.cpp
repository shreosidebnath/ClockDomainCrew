// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See Vaxis_loopback.h for the primary calling header

#include "Vaxis_loopback__pch.h"
#include "Vaxis_loopback__Syms.h"
#include "Vaxis_loopback___024root.h"

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__stl(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG

VL_ATTR_COLD void Vaxis_loopback___024root___eval_triggers__stl(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_triggers__stl\n"); );
    // Body
    vlSelf->__VstlTriggered.set(0U, (IData)(vlSelf->__VstlFirstIteration));
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        Vaxis_loopback___024root___dump_triggers__stl(vlSelf);
    }
#endif
}
