// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Tracing implementation internals
#include "verilated_vcd_c.h"
#include "Vaxis_loopback__Syms.h"


void Vaxis_loopback___024root__trace_chg_0_sub_0(Vaxis_loopback___024root* vlSelf, VerilatedVcd::Buffer* bufp);

void Vaxis_loopback___024root__trace_chg_0(void* voidSelf, VerilatedVcd::Buffer* bufp) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root__trace_chg_0\n"); );
    // Init
    Vaxis_loopback___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<Vaxis_loopback___024root*>(voidSelf);
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    if (VL_UNLIKELY(!vlSymsp->__Vm_activity)) return;
    // Body
    Vaxis_loopback___024root__trace_chg_0_sub_0((&vlSymsp->TOP), bufp);
}

void Vaxis_loopback___024root__trace_chg_0_sub_0(Vaxis_loopback___024root* vlSelf, VerilatedVcd::Buffer* bufp) {
    if (false && vlSelf) {}  // Prevent unused
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root__trace_chg_0_sub_0\n"); );
    // Init
    uint32_t* const oldp VL_ATTR_UNUSED = bufp->oldp(vlSymsp->__Vm_baseCode + 1);
    // Body
    bufp->chgBit(oldp+0,(vlSelf->clk156));
    bufp->chgBit(oldp+1,(vlSelf->resetn));
    bufp->chgQData(oldp+2,(vlSelf->tx_axis_tdata),64);
    bufp->chgCData(oldp+4,(vlSelf->tx_axis_tkeep),8);
    bufp->chgBit(oldp+5,(vlSelf->tx_axis_tvalid));
    bufp->chgBit(oldp+6,(vlSelf->tx_axis_tready));
    bufp->chgBit(oldp+7,(vlSelf->tx_axis_tlast));
    bufp->chgQData(oldp+8,(vlSelf->rx_axis_tdata),64);
    bufp->chgCData(oldp+10,(vlSelf->rx_axis_tkeep),8);
    bufp->chgBit(oldp+11,(vlSelf->rx_axis_tvalid));
    bufp->chgBit(oldp+12,(vlSelf->rx_axis_tready));
    bufp->chgBit(oldp+13,(vlSelf->rx_axis_tlast));
}

void Vaxis_loopback___024root__trace_cleanup(void* voidSelf, VerilatedVcd* /*unused*/) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    Vaxis_loopback___024root__trace_cleanup\n"); );
    // Init
    Vaxis_loopback___024root* const __restrict vlSelf VL_ATTR_UNUSED = static_cast<Vaxis_loopback___024root*>(voidSelf);
    Vaxis_loopback__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    VlUnpacked<CData/*0:0*/, 1> __Vm_traceActivity;
    for (int __Vi0 = 0; __Vi0 < 1; ++__Vi0) {
        __Vm_traceActivity[__Vi0] = 0;
    }
    // Body
    vlSymsp->__Vm_activity = false;
    __Vm_traceActivity[0U] = 0U;
}
