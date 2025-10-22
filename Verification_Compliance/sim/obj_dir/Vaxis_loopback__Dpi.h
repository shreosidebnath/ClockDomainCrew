// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Prototypes for DPI import and export functions.
//
// Verilator includes this file in all generated .cpp files that use DPI functions.
// Manually include this file where DPI .c import functions are declared to ensure
// the C functions match the expectations of the DPI imports.

#ifndef VERILATED_VAXIS_LOOPBACK__DPI_H_
#define VERILATED_VAXIS_LOOPBACK__DPI_H_  // guard

#include "svdpi.h"

#ifdef __cplusplus
extern "C" {
#endif


    // DPI IMPORTS
    // DPI import at ../rtl/axis_loopback.sv:23:32
    extern void socket_close();
    // DPI import at ../rtl/axis_loopback.sv:20:32
    extern void socket_init(const char* ip, int port);
    // DPI import at ../rtl/axis_loopback.sv:22:32
    extern int socket_recv(svBitVecVal* data, int len);
    // DPI import at ../rtl/axis_loopback.sv:21:32
    extern void socket_send(const svBitVecVal* data, int len);

#ifdef __cplusplus
}
#endif

#endif  // guard
