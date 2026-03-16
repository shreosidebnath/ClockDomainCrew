# PCS User Guide

## Overview
The Physical Coding Sublayer (PCS) implements the encoding and decoding logic between the Media Access Control (MAC) layer and the Physical Medium Attachment (PMA).

This PCS implements the functionality required for a 10GBASE-R Ethernet interface. It converts XGMII data from the MAC into encoded blocks transmitted over a high-speed serial interface and performs the reverse operation on receive.

The PCS contains separate transmit and receive datapaths.

## PCS Architecture
The PCS sits between the MAC and the SERDES interface.

            +-------------------+
            |        MAC        |
            |   (XGMII IF)      |
            +---------+---------+
                      |
                      v
              +---------------+
              |      PCS      |
              |  TX / RX Path |
              +-------+-------+
                      |
                      v
            +-------------------+
            |      SERDES       |
            |   (Serial PHY)    |
            +-------------------+

The PCS performs block encoding, scrambling, synchronization, and error detection.


## Interfaces

** XGMII Interface

The PCS connects to the MAC through an XGMII interface.

Signal   	Direction	Description
xgmiiTxd	Input	transmit data
xgmiiTxc	Input	transmit control
xgmiiTxValid	Input	TX data valid
xgmiiRxd	Output	receive data
xgmiiRxc	Output	receive control
xgmiiRxValid	Output	RX data valid


## Transmit Path (TX PCS)

The transmit path converts XGMII data from the MAC into encoded PCS blocks for transmission over the SERDES interface.

### TX Architecture

XGMII TX | v +----------------+ | XGMII Encoder | +----------------+ | v +----------------+ | Scrambler | +----------------+ | v +----------------+ | PCS TX IF | +----------------+ | v SERDES TX

### XGMII Encoder
The encoder converts XGMII data and control characters into 64b/66b blocks.

Functions include:
- mapping XGMII control characters to PCS control codes
- inserting block headers
- detecting invalid blocks
- generating control block formats

Supported XGMII characters include:

- Idle
- Start
- Terminate
- Error
- Ordered sets

Each block is transmitted with a 2-bit synchronization header:

Header	Meaning
10	data block
01	control block

### Scrambler

A polynomial scrambler randomizes transmitted data to ensure adequate transition density for clock recovery.

The scrambler is implemented using a Linear Feedback Shift Register (LFSR).

Polynomial:

x^58 + x^39 + 1

The scrambler may be disabled through configuration.

### PRBS31 Generator

For test and validation purposes, the TX PCS supports generation of a PRBS31 test pattern.

When enabled:

cfgTxPrbs31Enable = 1

the PCS transmits PRBS31 data instead of normal Ethernet traffic.




SERDES Interface

The PCS connects to the SERDES using a 64-bit datapath with a 2-bit synchronization header.

Signal	Direction	Description
serdesTxData	Output	encoded transmit data
serdesTxHdr	Output	block header
serdesTxDataValid	Output	transmit valid
serdesRxData	Input	received encoded data
serdesRxHdr	Input	received header
serdesRxDataValid	Input	receive valid


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
