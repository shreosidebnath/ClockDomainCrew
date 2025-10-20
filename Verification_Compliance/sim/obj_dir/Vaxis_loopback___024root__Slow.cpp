// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See Vaxis_loopback.h for the primary calling header

#include "Vaxis_loopback__pch.h"
#include "Vaxis_loopback__Syms.h"
#include "Vaxis_loopback___024root.h"

void Vaxis_loopback___024root___ctor_var_reset(Vaxis_loopback___024root* vlSelf);

Vaxis_loopback___024root::Vaxis_loopback___024root(Vaxis_loopback__Syms* symsp, const char* v__name)
    : VerilatedModule{v__name}
    , vlSymsp{symsp}
 {
    // Reset structure values
    Vaxis_loopback___024root___ctor_var_reset(this);
}

void Vaxis_loopback___024root::__Vconfigure(bool first) {
    if (false && first) {}  // Prevent unused
}

Vaxis_loopback___024root::~Vaxis_loopback___024root() {
}
