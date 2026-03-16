# ClockDomainCrew – 10G Ethernet MAC + PCS

This repository contains a hardware implementation of a 10 Gigabit Ethernet Media Access Control (MAC) and Physical Coding Sublayer (PCS) written in Chisel.

The project was developed as a capstone design project and implements the digital logic required to transmit and receive Ethernet frames using a 10GBASE-R style architecture.

The design converts Ethernet frames from the MAC layer into encoded data suitable for high-speed serial transmission and performs the reverse operation on receive.

## Project Overview

The Ethernet datapath is composed of two major components:

### MAC (Media Access Control)
Handles Ethernet frame formatting, control characters, and the XGMII interface.

### PCS (Physical Coding Sublayer)
Implements 64b/66b encoding, scrambling, synchronization, and decoding between the MAC and SERDES.

Together these modules form the digital portion of a 10G Ethernet network interface.

## License 
![License: CERN-OHL-S-2.0](https://img.shields.io/badge/license-CERN--OHL--S--2.0-blue)

This project is licensed under the **CERN Open Hardware Licence v2 – Strongly Reciprocal (CERN-OHL-S-2.0)**.

Under this license you are free to:
- Use the hardware design and documentation
- Modify the source and create derivative works
- Manufacture and distribute products based on the design
- Share the design publicly

However, the following conditions apply:
- Any modifications or derivative works must also be released under **CERN-OHL-S-2.0**
- Copyright and license notices must be retained
- When distributing products based on this design, the **complete corresponding source** must be made available

This design is provided **“as is” without warranty**. The authors are not liable for any damages resulting from its use.

See the `LICENSE.md` file or the official license text for full details:
https://ohwr.org/cern_ohl_s_v2.txt

### Upstream Work

This project is based in part on the taxi repository:

https://github.com/fpganinja/taxi

Original work:
Copyright (c) 2015–2025 FPGA Ninja, LLC  
Author: Alex Forencich

Modifications and additional development:
Copyright (c) 2026 ClockDomainCrew  
University of Calgary – Schulich School of Engineering

Project Sponsor:
ChiselWare

## System Architecture

```
+----------------------+
|   Application Logic  |
+----------+-----------+
           │
           ▼
+----------------------+
|         MAC          |
|    (XGMII Interface) |
+----------+-----------+
           │
           ▼
+----------------------+
|         PCS          |
|    TX / RX Encoding  |
+----------+-----------+
           │
           ▼
+----------------------+
|        SERDES        |
|    Serializer / PHY  |
+----------+-----------+
           │
           ▼
        Ethernet Link
```

The MAC exchanges data with application logic while the PCS prepares the data for transmission over a high-speed serial physical interface.

## Repository Structure

```
ClockDomainCrew/
├── build.sbt
├── Makefile.base          # Primary build + verification engine
├── Makefile.pcs           # Injects PCS variables into Makefile.base
├── Makefile.mac           # Injects MAC variables into Makefile.base
├── docs/                  # Project-level reports and documentation
├── modules/
│   ├── mac/               # MAC layer (standalone)
│   └── pcs/               # PCS layer (standalone)
├── Scapy-Tests/           # Loopback tests using Scapy
└── project/               # sbt plugins and build configuration
```

### modules/mac
Contains the implementation of the Ethernet Media Access Control layer, including:

- AXI-Stream interfaces
- XGMII transmit and receive logic
- frame processing

### modules/pcs

Contains the Physical Coding Sublayer, including:
- 64b/66b encoding and decoding
- scrambling and descrambling
- block synchronization
- error detection

## Reference Design 

The implementation uses concepts and structure inspired by the open-source Ethernet core:
- Ninja Taxi FPGA Ethernet Core

This project was used as a golden reference for architecture and module organization.

## Running the Project
The primary way to interact with the project is through the provided Makefiles. The build system is split to support the MAC and PCS cores independently.

_(Note: You can substitute `Makefile.mac` with `Makefile.pcs` in any of the Make commands below to target the other module.)_

### Building (via sbt)
```bash
# Compile all modules
sbt compile

# Compile specific module
sbt "project mac" compile
sbt "project pcs" compile
```

### Testing

Run the following command to execute the test suite for the MAC core:
```bash
make -f Makefile.mac test
```

This is equivalent to running the following under the hood:
```bash
sbt "project mac" test
```

and writes output to:
```bash
modules/mac/generated/test.rpt
```

### Generating Verilog

Generate SystemVerilog and synthesis collateral for the MAC core:
```bash
make -f Makefile.mac verilog
```

This is equivalent to running the following under the hood:
```bash
sbt "project mac" run
```

Entrypoint:
```bash
org.chiselware.cores.o01.t001.mac.Main
```


### Full regression Flow
Run everything end-to-end (cleans the directory, runs tests with coverage, generates Verilog, synthesizes with Yosys, builds documentation, and checks for errors):

```bash
make -f Makefile.mac all
```


## Simulation

Simulations can be executed using several supported Verilog simulators:
- Verilator
- Icarus Verilog
- Synopsys VCS

Run the included tests with:

sbt
project core
test

## Documentation

Detailed documentation for each module can be found in the user guides:

modules/mac/docs/user-guide

modules/pcs/docs/user-guide

These guides describe the internal architecture, interfaces, and configuration parameters of each subsystem.

## Authors

ClockDomainCrew
University of Calgary
2026
Electrical Engineering Capstone Project