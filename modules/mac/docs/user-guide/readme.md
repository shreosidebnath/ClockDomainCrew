# MAC User Guide

## Overview

The Media Access Control (MAC) module implements the Ethernet MAC layer responsible for transmitting and receiving Ethernet frames. The MAC serves as the interface between packet-based application logic and the physical Ethernet interface.

This MAC design connects an AXI-Stream packet interface to a 64-bit XGMII interface, enabling integration with high-speed Ethernet Physical Coding Sublayer (PCS) modules.

The Mac perfoms the following functions:

- Ethernet frame generation
- Ethernet frame reception
- Frame Check Sequence (FCS) generation and verification
- Frame padding and minimum frame length enforcement
- Inter-frame gap enforcement
- Packet statistics reporting
- Optional Precision Time Protocol (PTP) timestamp support

The design contains two primary datapaths:
- Transmit Path (TX) – Converts AXI Stream packets into Ethernet frames transmitted on XGMII
- Receive Path (RX) – Converts Ethernet frames received from XGMII into AXI Stream packets

## MAC Architecture

The MAC architecture consists of separate transmit and receive modules connected through a wrapper module.

Application Logic
       │
       ▼
 AXI Stream TX
       │
       ▼
+------------------+
| Axis2Xgmii64 TX  |
+------------------+
       │
       ▼
   XGMII Interface
       │
       ▼
+------------------+
| Xgmii2Axis64 RX  |
+------------------+
       │
       ▼
 AXI Stream RX

The Mac wrapper module instantiates both datapaths and connects the XGMII interface signals.

Main components:

| Module         | Description              |
| -------------- | ------------------------ |
| `Axis2Xgmii64` | Transmit datapath        |
| `Xgmii2Axis64` | Receive datapath         |
| `Mac`          | Top-level wrapper module |



