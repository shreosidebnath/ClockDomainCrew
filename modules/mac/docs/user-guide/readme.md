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

```
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
```

The Mac wrapper module instantiates both datapaths and connects the XGMII interface signals.

Main components:

| Module         | Description              |
| -------------- | ------------------------ |
| `Axis2Xgmii64` | Transmit datapath        |
| `Xgmii2Axis64` | Receive datapath         |
| `Mac`          | Top-level wrapper module |


## MAC Interfaces

**AXI Stream Transmit Interface**

The transmit interface receives packet data from the application layer.

| Signal   | Width        | Description               |
| -------- | ------------ | ------------------------- |
| `tdata`  | 64           | Packet payload data       |
| `tkeep`  | 8            | Byte enable mask          |
| `tvalid` | 1            | Indicates valid data      |
| `tready` | 1            | MAC ready to receive data |
| `tlast`  | 1            | End of packet indicator   |
| `tid`    | Configurable | Packet identifier         |
| `tdest`  | Configurable | Destination identifier    |
| `tuser`  | Configurable | User-defined metadata     |

**AXI Stream Receive Interface**

Received packets are transmitted to the application layer through the AXI Stream RX interface.

| Signal   | Description                 |
| -------- | --------------------------- |
| `tdata`  | Received packet data        |
| `tkeep`  | Byte validity mask          |
| `tvalid` | Indicates valid packet data |
| `tready` | Downstream ready signal     |
| `tlast`  | End of packet               |
| `tuser`  | Packet metadata             |


**XGMII Interface**

The MAC communicates with the PCS layer through the XGMII interface.

XGMII Transmit Signals

| Signal         | Width | Description          |
| -------------- | ----- | -------------------- |
| `xgmiiTxd`     | 64    | Transmit data        |
| `xgmiiTxc`     | 8     | Control characters   |
| `xgmiiTxValid` | 1     | Data valid indicator |

XGMII Receive Signals

| Signal         | Width | Description          |
| -------------- | ----- | -------------------- |
| `xgmiiRxd`     | 64    | Receive data         |
| `xgmiiRxc`     | 8     | Control characters   |
| `xgmiiRxValid` | 1     | Valid data indicator |

## Transmit Datapath

The transmit datapath converts AXI Stream packets into Ethernet frames suitable for transmission over XGMII.

Main responsibilities:
- Frame construction
- CRC generation
- Frame padding
- Inter-frame gap enforcement

**Transmit Pipeline**
```
AXI Stream Input
      │
      ▼
Frame Builder
      │
      ▼
CRC Generator
      │
      ▼
Padding Unit
      │
      ▼
IFG Controller
      │
      ▼
XGMII Output
```

## Ethernet Frame Format
```
+----------+-----------+---------+----------+
| Preamble |   Header  | Payload |   FCS    |
+----------+-----------+---------+----------+
   7B         14B       Variable     4B
```
**Preamble**
```
55 55 55 55 55 55 55 D5
```
Where:
- 0x55 represents the preamble pattern
- 0xD5 is the Start Frame Delimiter (SFD)

## CRC Generation

Each Ethernet frame contains a 32-bit Frame Check Sequence.

The MAC computes the CRC using the polynomial:
```
0x04C11DB7
```
The CRC generator is implemented using a Linear Feedback Shift Register (LFSR).

The CRC is calculated incrementally as frame data is transmitted and appended to the end of the frame.

## Frame Padding 

Ethernet frames must be at least 64 bytes long.

If the payload is smaller than the minimum frame size, the MAC automatically inserts padding bytes before transmitting the CRC.

Minimum frame length: 64 bytes

## Inter-Frame Gap
Ethernet requires a minimum delay between consecutive frames.

Default gap:12 bytes
The transmit module inserts idle characters on the XGMII interface to enforce the inter-frame gap.


## Transmit State Machine 
The transmit module operates using a finite state machine.
```
| State   | Description                |
| ------- | -------------------------- |
| Idle    | Wait for packet arrival    |
| Payload | Transmit packet payload    |
| Pad     | Insert padding if required |
| Fcs1    | Transmit first CRC segment |
| Fcs2    | Transmit remaining CRC     |
| Err     | Transmit error frame       |
| Ifg     | Enforce inter-frame gap    |

```

## Receive Datapath
The receive datapath processes Ethernet frames received from the XGMII interface and converts them into AXI Stream packets.

Main functions:
- Frame detection
- Preamble validation
- Payload extraction
- CRC verification
- Packet classification
```
XGMII Input
      │
      ▼
Frame Detection
      │
      ▼
Preamble Validation
      │
      ▼
Frame Parser
      │
      ▼
CRC Checker
      │
      ▼
AXI Stream Output
```

## Frame Detection
The receive module detects the start of frames using XGMII control characters.

```
| Symbol    | Value | Description      |
| --------- | ----- | ---------------- |
| Idle      | 0x07  | Idle character   |
| Start     | 0xFB  | Start of frame   |
| Terminate | 0xFD  | End of frame     |
| Error     | 0xFE  | Error indication |
```

## Preamble Validation
The receiver verifies the Ethernet preamble before accepting frame data.

Expected preamble sequence:

55 55 55 55 55 55 55 D5

Invalid preamble sequences trigger a preamble error flag.

## CRC Verification

The receive datapath computes the CRC of incoming frames using the same polynomial used during transmission.

0x04C11DB7

If the calculated CRC does not match the received FCS, the frame is marked as invalid.

## Packet Classification

The MAC inspects destination MAC addresses to classify packets.
```
| Packet Type | Detection Method                        |
| ----------- | --------------------------------------- |
| Unicast     | LSB of destination address = 0          |
| Multicast   | LSB of destination address = 1          |
| Broadcast   | Destination address = FF:FF:FF:FF:FF:FF |
| VLAN        | EtherType = 0x8100                      |

```

## Packet Statistics
The MAC generates runtime statistics for monitoring and debugging.

Transmit statistics:

```
| Signal          | Description                  |
| --------------- | ---------------------------- |
| `statTxPktLen`  | Length of transmitted packet |
| `statTxPktGood` | Valid transmitted packet     |
| `statTxPktBad`  | Packet transmission error    |

```
Receive statistics:

```
| Signal              | Description               |
| ------------------- | ------------------------- |
| `statRxPktLen`      | Length of received packet |
| `statRxPktGood`     | Valid received packet     |
| `statRxPktBad`      | Packet error              |
| `statRxErrBadFcs`   | CRC error                 |
| `statRxErrOversize` | Oversized frame           |
| `statRxErrFraming`  | Framing error             |

```

## Timestamp Support
The MAC supports optional Precision Time Protocol (PTP) timestamping.

Configuration parameters:
```
| Parameter     | Description         |
| ------------- | ------------------- |
| `ptpTsEn`     | Enable timestamping |
| `ptpTsFmtTod` | Timestamp format    |
| `ptpTsW`      | Timestamp width     |

```
When enabled, timestamps are attached to transmitted or received packets through metadata fields.

## Configuration Parameters
Key configuration parameters include:

```
| Parameter     | Description                 |
| ------------- | --------------------------- |
| `dataW`       | AXI data width              |
| `keepW`       | Byte enable width           |
| `minFrameLen` | Minimum Ethernet frame size |
| `ptpTsEn`     | Enable timestamp support    |
| `txTagW`      | TX tag width                |
| `userW`       | User signal width           |

```

## Integration
To integrate the MAC:
- Connect application logic to the AXI Stream TX interface.
- Connect the MAC XGMII interface to the PCS module.
- Configure clock and reset signals.
- Enable optional features such as timestamping if required.




