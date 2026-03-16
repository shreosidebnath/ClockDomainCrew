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

## System Architecture

+----------------------+ | Application Logic | +----------+-----------+ | v +----------------------+ | MAC | | (XGMII Interface) | +----------+-----------+ | v +----------------------+ | PCS | | TX / RX Encoding | +----------+-----------+ | v +----------------------+ | SERDES | | Serializer / PHY | +----------+-----------+ | v Ethernet Link

The MAC exchanges data with application logic while the PCS prepares the data for transmission over a high-speed serial physical interface.

## Repository Structure

ClockDomainCrew/
├── build.sbt
├── Makefile.base         # Primary build + verification engine
├── Makefile.pcs          # Injects PCS variables into Makefile.base
├── Makefile.mac          # Injects MAC variables into Makefile.base
├── docs/                 # Project-level reports and documentation
├── modules/
│   ├── mac/              # MAC layer (standalone)
│   ├── pcs/              # PCS layer (standalone)
├── Scapy-Tests/          # Loopback tests using Scapy
└── project/              # sbt plugins and build config

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
- Taxi FPGA Ethernet Core

This project was used as a golden reference for architecture and module organization.

## Building the Project
The hardware design is written in Chisel and compiled using SBT.

Start the build environment:
sbt

To generate RTL:
project core
run

Generated SystemVerilog will appear in the generated directories of each module.

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

## License

See LICENSE.md for license information.
