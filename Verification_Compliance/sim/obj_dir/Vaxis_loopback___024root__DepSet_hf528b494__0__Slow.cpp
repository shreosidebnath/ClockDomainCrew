// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See Vaxis_loopback.h for the primary calling header

#include "Vaxis_loopback__pch.h"
#include "Vaxis_loopback___024root.h"

VL_ATTR_COLD void Vaxis_loopback___024root___eval_static(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_static\n"); );
}

VL_ATTR_COLD void Vaxis_loopback___024root___eval_initial__TOP(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD void Vaxis_loopback___024root___eval_initial(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_initial\n"); );
    // Body
    Vaxis_loopback___024root___eval_initial__TOP(vlSelf);
    vlSelf->__Vtrigprevexpr___TOP__clk156__0 = vlSelf->clk156;
}

void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_init_TOP(std::string ip, IData/*31:0*/ port);

VL_ATTR_COLD void Vaxis_loopback___024root___eval_initial__TOP(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_initial__TOP\n"); );
    // Body
    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_init_TOP(
                                                                               std::string{"127.0.0.1"}, 0x2328U);
}

VL_ATTR_COLD void Vaxis_loopback___024root___eval_final__TOP(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD void Vaxis_loopback___024root___eval_final(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_final\n"); );
    // Body
    Vaxis_loopback___024root___eval_final__TOP(vlSelf);
}

void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_close_TOP();

VL_ATTR_COLD void Vaxis_loopback___024root___eval_final__TOP(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_final__TOP\n"); );
    // Body
    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_close_TOP();
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__stl(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG
VL_ATTR_COLD bool Vaxis_loopback___024root___eval_phase__stl(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD void Vaxis_loopback___024root___eval_settle(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_settle\n"); );
    // Init
    IData/*31:0*/ __VstlIterCount;
    CData/*0:0*/ __VstlContinue;
    // Body
    __VstlIterCount = 0U;
    vlSelf->__VstlFirstIteration = 1U;
    __VstlContinue = 1U;
    while (__VstlContinue) {
        if (VL_UNLIKELY((0x64U < __VstlIterCount))) {
#ifdef VL_DEBUG
            Vaxis_loopback___024root___dump_triggers__stl(vlSelf);
#endif
            VL_FATAL_MT("../rtl/axis_loopback.sv", 1, "", "Settle region did not converge.");
        }
        __VstlIterCount = ((IData)(1U) + __VstlIterCount);
        __VstlContinue = 0U;
        if (Vaxis_loopback___024root___eval_phase__stl(vlSelf)) {
            __VstlContinue = 1U;
        }
        vlSelf->__VstlFirstIteration = 0U;
    }
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__stl(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___dump_triggers__stl\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VstlTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VstlTriggered.word(0U))) {
        VL_DBG_MSGF("         'stl' region trigger index 0 is active: Internal 'stl' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

void Vaxis_loopback___024root___ico_sequent__TOP__0(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD void Vaxis_loopback___024root___eval_stl(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_stl\n"); );
    // Body
    if ((1ULL & vlSelf->__VstlTriggered.word(0U))) {
        Vaxis_loopback___024root___ico_sequent__TOP__0(vlSelf);
    }
}

VL_ATTR_COLD void Vaxis_loopback___024root___eval_triggers__stl(Vaxis_loopback___024root* vlSelf);

VL_ATTR_COLD bool Vaxis_loopback___024root___eval_phase__stl(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_phase__stl\n"); );
    // Init
    CData/*0:0*/ __VstlExecute;
    // Body
    Vaxis_loopback___024root___eval_triggers__stl(vlSelf);
    __VstlExecute = vlSelf->__VstlTriggered.any();
    if (__VstlExecute) {
        Vaxis_loopback___024root___eval_stl(vlSelf);
    }
    return (__VstlExecute);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__ico(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___dump_triggers__ico\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VicoTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VicoTriggered.word(0U))) {
        VL_DBG_MSGF("         'ico' region trigger index 0 is active: Internal 'ico' trigger - first iteration\n");
    }
}
#endif  // VL_DEBUG

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__act(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___dump_triggers__act\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VactTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VactTriggered.word(0U))) {
        VL_DBG_MSGF("         'act' region trigger index 0 is active: @(posedge clk156)\n");
    }
}
#endif  // VL_DEBUG

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__nba(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___dump_triggers__nba\n"); );
    // Body
    if ((1U & (~ (IData)(vlSelf->__VnbaTriggered.any())))) {
        VL_DBG_MSGF("         No triggers active\n");
    }
    if ((1ULL & vlSelf->__VnbaTriggered.word(0U))) {
        VL_DBG_MSGF("         'nba' region trigger index 0 is active: @(posedge clk156)\n");
    }
}
#endif  // VL_DEBUG

VL_ATTR_COLD void Vaxis_loopback___024root___ctor_var_reset(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___ctor_var_reset\n"); );
    // Body
    vlSelf->clk156 = VL_RAND_RESET_I(1);
    vlSelf->resetn = VL_RAND_RESET_I(1);
    vlSelf->tx_axis_tdata = VL_RAND_RESET_Q(64);
    vlSelf->tx_axis_tkeep = VL_RAND_RESET_I(8);
    vlSelf->tx_axis_tvalid = VL_RAND_RESET_I(1);
    vlSelf->tx_axis_tready = VL_RAND_RESET_I(1);
    vlSelf->tx_axis_tlast = VL_RAND_RESET_I(1);
    vlSelf->rx_axis_tdata = VL_RAND_RESET_Q(64);
    vlSelf->rx_axis_tkeep = VL_RAND_RESET_I(8);
    vlSelf->rx_axis_tvalid = VL_RAND_RESET_I(1);
    vlSelf->rx_axis_tready = VL_RAND_RESET_I(1);
    vlSelf->rx_axis_tlast = VL_RAND_RESET_I(1);
    vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_data = 0;
    vlSelf->axis_loopback__DOT__unnamedblk1__DOT__recv_len = 0;
    vlSelf->__Vtrigprevexpr___TOP__clk156__0 = VL_RAND_RESET_I(1);
}
