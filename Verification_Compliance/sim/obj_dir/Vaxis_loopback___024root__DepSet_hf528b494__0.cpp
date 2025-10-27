// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See Vaxis_loopback.h for the primary calling header

#include "Vaxis_loopback__pch.h"
#include "Vaxis_loopback___024root.h"

VL_INLINE_OPT void Vaxis_loopback___024root___ico_sequent__TOP__0(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___ico_sequent__TOP__0\n"); );
    // Body
    vlSelf->tx_axis_tready = vlSelf->rx_axis_tready;
}

void Vaxis_loopback___024root___eval_ico(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_ico\n"); );
    // Body
    if ((1ULL & vlSelf->__VicoTriggered.word(0U))) {
        Vaxis_loopback___024root___ico_sequent__TOP__0(vlSelf);
    }
}

void Vaxis_loopback___024root___eval_triggers__ico(Vaxis_loopback___024root* vlSelf);

bool Vaxis_loopback___024root___eval_phase__ico(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_phase__ico\n"); );
    // Init
    CData/*0:0*/ __VicoExecute;
    // Body
    Vaxis_loopback___024root___eval_triggers__ico(vlSelf);
    __VicoExecute = vlSelf->__VicoTriggered.any();
    if (__VicoExecute) {
        Vaxis_loopback___024root___eval_ico(vlSelf);
    }
    return (__VicoExecute);
}

void Vaxis_loopback___024root___eval_act(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_act\n"); );
}

void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_send_TOP(QData/*63:0*/ data, IData/*31:0*/ len);
void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_recv_TOP(QData/*63:0*/ &data, IData/*31:0*/ len, IData/*31:0*/ &socket_recv__Vfuncrtn);

VL_INLINE_OPT void Vaxis_loopback___024root___nba_sequent__TOP__0(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___nba_sequent__TOP__0\n"); );
    // Init
    IData/*31:0*/ __Vfunc_axis_loopback__DOT__socket_recv__2__Vfuncout;
    __Vfunc_axis_loopback__DOT__socket_recv__2__Vfuncout = 0;
    QData/*63:0*/ __Vfunc_axis_loopback__DOT__socket_recv__2__data;
    __Vfunc_axis_loopback__DOT__socket_recv__2__data = 0;
    // Body
    if (((IData)(vlSelf->tx_axis_tvalid) & (IData)(vlSelf->tx_axis_tready))) {
        Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_send_TOP(vlSelf->tx_axis_tdata, 8U);
    }
    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_recv_TOP(__Vfunc_axis_loopback__DOT__socket_recv__2__data, 8U, __Vfunc_axis_loopback__DOT__socket_recv__2__Vfuncout);
    vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_data 
        = __Vfunc_axis_loopback__DOT__socket_recv__2__data;
    vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_len 
        = __Vfunc_axis_loopback__DOT__socket_recv__2__Vfuncout;
    if (VL_LTS_III(32, 0U, vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_len)) {
        vlSelf->rx_axis_tdata = vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_data;
        vlSelf->rx_axis_tvalid = 1U;
        vlSelf->rx_axis_tkeep = 0xffU;
        vlSelf->rx_axis_tlast = 1U;
    } else {
        vlSelf->rx_axis_tvalid = 0U;
    }
}

void Vaxis_loopback___024root___eval_nba(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_nba\n"); );
    // Body
    if ((1ULL & vlSelf->__VnbaTriggered.word(0U))) {
        Vaxis_loopback___024root___nba_sequent__TOP__0(vlSelf);
    }
}

void Vaxis_loopback___024root___eval_triggers__act(Vaxis_loopback___024root* vlSelf);

bool Vaxis_loopback___024root___eval_phase__act(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_phase__act\n"); );
    // Init
    VlTriggerVec<1> __VpreTriggered;
    CData/*0:0*/ __VactExecute;
    // Body
    Vaxis_loopback___024root___eval_triggers__act(vlSelf);
    __VactExecute = vlSelf->__VactTriggered.any();
    if (__VactExecute) {
        __VpreTriggered.andNot(vlSelf->__VactTriggered, vlSelf->__VnbaTriggered);
        vlSelf->__VnbaTriggered.thisOr(vlSelf->__VactTriggered);
        Vaxis_loopback___024root___eval_act(vlSelf);
    }
    return (__VactExecute);
}

bool Vaxis_loopback___024root___eval_phase__nba(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_phase__nba\n"); );
    // Init
    CData/*0:0*/ __VnbaExecute;
    // Body
    __VnbaExecute = vlSelf->__VnbaTriggered.any();
    if (__VnbaExecute) {
        Vaxis_loopback___024root___eval_nba(vlSelf);
        vlSelf->__VnbaTriggered.clear();
    }
    return (__VnbaExecute);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__ico(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG
#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__nba(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG
#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__act(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG

void Vaxis_loopback___024root___eval(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval\n"); );
    // Init
    IData/*31:0*/ __VicoIterCount;
    CData/*0:0*/ __VicoContinue;
    IData/*31:0*/ __VnbaIterCount;
    CData/*0:0*/ __VnbaContinue;
    // Body
    __VicoIterCount = 0U;
    vlSelf->__VicoFirstIteration = 1U;
    __VicoContinue = 1U;
    while (__VicoContinue) {
        if (VL_UNLIKELY((0x64U < __VicoIterCount))) {
#ifdef VL_DEBUG
            Vaxis_loopback___024root___dump_triggers__ico(vlSelf);
#endif
            VL_FATAL_MT("../rtl/axis_loopback.sv", 1, "", "Input combinational region did not converge.");
        }
        __VicoIterCount = ((IData)(1U) + __VicoIterCount);
        __VicoContinue = 0U;
        if (Vaxis_loopback___024root___eval_phase__ico(vlSelf)) {
            __VicoContinue = 1U;
        }
        vlSelf->__VicoFirstIteration = 0U;
    }
    __VnbaIterCount = 0U;
    __VnbaContinue = 1U;
    while (__VnbaContinue) {
        if (VL_UNLIKELY((0x64U < __VnbaIterCount))) {
#ifdef VL_DEBUG
            Vaxis_loopback___024root___dump_triggers__nba(vlSelf);
#endif
            VL_FATAL_MT("../rtl/axis_loopback.sv", 1, "", "NBA region did not converge.");
        }
        __VnbaIterCount = ((IData)(1U) + __VnbaIterCount);
        __VnbaContinue = 0U;
        vlSelf->__VactIterCount = 0U;
        vlSelf->__VactContinue = 1U;
        while (vlSelf->__VactContinue) {
            if (VL_UNLIKELY((0x64U < vlSelf->__VactIterCount))) {
#ifdef VL_DEBUG
                Vaxis_loopback___024root___dump_triggers__act(vlSelf);
#endif
                VL_FATAL_MT("../rtl/axis_loopback.sv", 1, "", "Active region did not converge.");
            }
            vlSelf->__VactIterCount = ((IData)(1U) 
                                       + vlSelf->__VactIterCount);
            vlSelf->__VactContinue = 0U;
            if (Vaxis_loopback___024root___eval_phase__act(vlSelf)) {
                vlSelf->__VactContinue = 1U;
            }
        }
        if (Vaxis_loopback___024root___eval_phase__nba(vlSelf)) {
            __VnbaContinue = 1U;
        }
    }
}

#ifdef VL_DEBUG
void Vaxis_loopback___024root___eval_debug_assertions(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_debug_assertions\n"); );
    // Body
    if (VL_UNLIKELY((vlSelf->clk156 & 0xfeU))) {
        Verilated::overWidthError("clk156");}
    if (VL_UNLIKELY((vlSelf->resetn & 0xfeU))) {
        Verilated::overWidthError("resetn");}
    if (VL_UNLIKELY((vlSelf->tx_axis_tvalid & 0xfeU))) {
        Verilated::overWidthError("tx_axis_tvalid");}
    if (VL_UNLIKELY((vlSelf->tx_axis_tlast & 0xfeU))) {
        Verilated::overWidthError("tx_axis_tlast");}
    if (VL_UNLIKELY((vlSelf->rx_axis_tready & 0xfeU))) {
        Verilated::overWidthError("rx_axis_tready");}
}
#endif  // VL_DEBUG
