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

```
        +----------------------+
        |   Application Logic  |
        +----------+-----------+
                   |
                   v
        +----------------------+
        |         MAC          |
        |    (XGMII Interface) |
        +----------+-----------+
                   |
                   v
        +----------------------+
        |         PCS          |
        |  TX / RX Encoding    |
        +----------+-----------+
                   |
                   v
        +----------------------+
        |        SERDES        |
        |   Serializer/PHY     |
        +----------+-----------+
                   |
                   v
              Ethernet Fiber
```
## Interfaces

### XGMII Interface

The PCS connects to the MAC through an XGMII interface.


| Signal | Direction | Description |
|-------|-----------|-------------|
| xgmiiTxd | Input | transmit data |
| xgmiiTxc | Input | transmit control |
| xgmiiTxValid | Input | TX data valid |
| xgmiiRxd | Output | receive data |
| xgmiiRxc | Output | receive control |
| xgmiiRxValid | Output | RX data valid |


## Transmit Path (TX PCS)

The transmit path converts XGMII data from the MAC into encoded PCS blocks for transmission over the SERDES interface.

### TX Architecture

```
XGMII TX
   │
   ▼
+----------------+
|  XGMII Encoder |
+----------------+
   │
   ▼
+----------------+
|   Scrambler    |
+----------------+
   │
   ▼
+----------------+
|    PCS TX IF   |
+----------------+
   │
   ▼
  SERDES TX
```

```
XGMII TX Interface
        │
        ▼
+----------------------+
|     XGMII Encoder    |
|  Control/Data Encode |
+----------------------+
        │
        ▼
+----------------------+
|      Scrambler       |
|  Polynomial Random   |
+----------------------+
        │
        ▼
+----------------------+
|   64b/66b Formatter  |
|  Add Sync Headers    |
+----------------------+
        │
        ▼
     SERDES TX
```

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

## Receive Path (RX PCS)

The receive datapath converts encoded PCS blocks from the SERDES into XGMII data for the MAC.

### RX Architecture


```
SERDES RX
   │
   ▼
+----------------+
|   Block Sync   |
+----------------+
   │
   ▼
+----------------+
|  Descrambler   |
+----------------+
   │
   ▼
+----------------+
| XGMII Decoder  |
+----------------+
   │
   ▼
  XGMII RX
```

```
      SERDES RX
         │
         ▼
+----------------------+
|   Block Synchronizer |
|  64b/66b Alignment   |
+----------------------+
         │
         ▼
+----------------------+
|     Descrambler      |
| Reverse TX Scramble  |
+----------------------+
         │
         ▼
+----------------------+
|    XGMII Decoder     |
| Control/Data Decode  |
+----------------------+
         │
         ▼
      XGMII RX
```
### Block Lock

The PCS monitors received synchronization headers to determine correct alignment of 64b/66b blocks.

Once sufficient valid blocks are observed, block lock is asserted.

rxBlockLock = 1

Loss of synchronization clears block lock.

### Descrambler

The receive descrambler reverses the scrambling operation applied during transmission.

The descrambler uses the same LFSR polynomial as the transmitter.

### Error Detection

The PCS monitors several error conditions:

| Signal | Description |
|-------|-------------|
| rxBadBlock | invalid block detected |
| rxSequenceError | sequence error |
| rxHighBer | high bit error rate detected |

These signals allow higher layers to detect link problems.


## Configuration Parameters

The PCS is configurable through parameters.

| Parameter | Description |
|----------|-------------|
| dataW | data width |
| ctrlW | control width |
| hdrW | header width |
| bitReverse | bit order reversal |
| scramblerDisable | disable scrambler |
| prbs31En | enable PRBS31 |

These parameters allow the PCS to be adapted for different interface requirements.


## Status Signals

The PCS provides status outputs to monitor link health.

| Signal | Description |
|-------|-------------|
| rxBlockLock | indicates block synchronization |
| rxHighBer | high bit error rate detected |
| rxErrorCount | accumulated errors |
| txBadBlock | invalid TX block |


## Integration with MAC
The PCS connects directly to the MAC through the XGMII interface.

Typical system architecture:
```
Application Logic
       │
       ▼
      MAC
       │
       ▼
      PCS
       │
       ▼
     SERDES
       │
       ▼
   Physical Link
```

The PCS handles all encoding and decoding required by the Ethernet physical layer.


## Summary 
The PCS core implements a complete 10GBASE-R Physical Coding Sublayer including:

- transmit encoding
- receive decoding
- scrambling / descrambling
- block synchronization
- error detection
- PRBS31 test support

The core is fully synthesizable and can be integrated into high-speed Ethernet FPGA or ASIC designs.





