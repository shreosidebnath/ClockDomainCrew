# PCS User Guide

## Overview

Physical Coding Sublayer for 10 Gigabit Ethernet.

## Purpose

Implements IEEE 802.3 Clause 49 PCS functionality:
- 64b/66b encoding/decoding
- Scrambling/descrambling
- Lane alignment and deskew

## Interfaces

- **Input**: XGMII (from MAC layer)
- **Output**: Serialized data (to PMD/Transceiver)

## Documentation Structure

- `images/` - Block diagrams and architecture
- `draw.io/` - Editable diagram sources
- `wavedrom/` - Timing diagrams
- `pdf/` - Compiled documentation
