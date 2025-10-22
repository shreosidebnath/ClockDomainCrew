// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See Vaxis_loopback.h for the primary calling header

#include "Vaxis_loopback__pch.h"
#include "Vaxis_loopback__Syms.h"
#include "Vaxis_loopback___024root.h"

extern "C" void socket_init(const char* ip, int port);

VL_INLINE_OPT void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_init_TOP(std::string ip, IData/*31:0*/ port) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_init_TOP\n"); );
    // Body
    const char* ip__Vcvt;
    for (size_t ip__Vidx = 0; ip__Vidx < 1; ++ip__Vidx) ip__Vcvt = ip.c_str();
    int port__Vcvt;
    for (size_t port__Vidx = 0; port__Vidx < 1; ++port__Vidx) port__Vcvt = port;
    socket_init(ip__Vcvt, port__Vcvt);
}

extern "C" void socket_send(const svBitVecVal* data, int len);

VL_INLINE_OPT void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_send_TOP(QData/*63:0*/ data, IData/*31:0*/ len) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_send_TOP\n"); );
    // Body
    svBitVecVal data__Vcvt[2];
    for (size_t data__Vidx = 0; data__Vidx < 1; ++data__Vidx) VL_SET_SVBV_Q(64, data__Vcvt + 2 * data__Vidx, data);
    int len__Vcvt;
    for (size_t len__Vidx = 0; len__Vidx < 1; ++len__Vidx) len__Vcvt = len;
    socket_send(data__Vcvt, len__Vcvt);
}

extern "C" int socket_recv(svBitVecVal* data, int len);

VL_INLINE_OPT void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_recv_TOP(QData/*63:0*/ &data, IData/*31:0*/ len, IData/*31:0*/ &socket_recv__Vfuncrtn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_recv_TOP\n"); );
    // Body
    svBitVecVal data__Vcvt[2];
    int len__Vcvt;
    for (size_t len__Vidx = 0; len__Vidx < 1; ++len__Vidx) len__Vcvt = len;
    int socket_recv__Vfuncrtn__Vcvt;
    socket_recv__Vfuncrtn__Vcvt = socket_recv(data__Vcvt, len__Vcvt);
    data = VL_SET_Q_SVBV(data__Vcvt);
    socket_recv__Vfuncrtn = socket_recv__Vfuncrtn__Vcvt;
}

extern "C" void socket_close();

VL_INLINE_OPT void Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_close_TOP() {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root____Vdpiimwrap_axis_loopback__DOT__socket_close_TOP\n"); );
    // Body
    socket_close();
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__ico(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG

void Vaxis_loopback___024root___eval_triggers__ico(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_triggers__ico\n"); );
    // Body
    vlSelf->__VicoTriggered.set(0U, (IData)(vlSelf->__VicoFirstIteration));
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        Vaxis_loopback___024root___dump_triggers__ico(vlSelf);
    }
#endif
}

#ifdef VL_DEBUG
VL_ATTR_COLD void Vaxis_loopback___024root___dump_triggers__act(Vaxis_loopback___024root* vlSelf);
#endif  // VL_DEBUG

void Vaxis_loopback___024root___eval_triggers__act(Vaxis_loopback___024root* vlSelf) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root___eval_triggers__act\n"); );
    // Body
    vlSelf->__VactTriggered.set(0U, ((IData)(vlSelf->clk156) 
                                     & (~ (IData)(vlSelf->__Vtrigprevexpr___TOP__clk156__0))));
    vlSelf->__Vtrigprevexpr___TOP__clk156__0 = vlSelf->clk156;
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        Vaxis_loopback___024root___dump_triggers__act(vlSelf);
    }
#endif
}
