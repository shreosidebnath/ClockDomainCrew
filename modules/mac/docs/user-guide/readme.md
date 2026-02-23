# MAC User Guide

## Overview

10 Gigabit Ethernet MAC (Media Access Control) layer implementation.

## Purpose

Implements IEEE 802.3 MAC functionality:
- Ethernet frame transmission and reception
- Frame padding to minimum size
- CRC-32 generation and checking
- Flow control (PAUSE frames)

## Interfaces

- **Input**: AXI-Stream (packet data from application)
- **Output**: XGMII (to Physical Coding Sublayer)

## Documentation Structure

- `images/` - Block diagrams and architecture
- `draw.io/` - Editable diagram sources
- `wavedrom/` - Timing diagrams
- `pdf/` - Compiled documentation
