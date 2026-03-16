# PCS

A 10GBASE-R Physical Coding Sublayer (PCS) implemented in Chisel.

## Description

The PCS core implements the Physical Coding Sublayer used in high-speed Ethernet systems. It performs encoding and decoding between the MAC XGMII interface and the SERDES interface.

The PCS provides both transmit (TX) and receive (RX) datapaths and implements functionality required by the 10GBASE-R specification, including:

- XGMII encoding and decoding
- 64b/66b block formatting
- scrambler / descrambler
- block lock detection
- gearbox synchronization
- PRBS31 test support
- error detection and reporting

The core is written in Chisel and generates synthesizable SystemVerilog RTL.


## Getting Started 
It is recommended that the user reads the PCS User Guide which can be found in:

modules/pcs/docs/user-guide

The user guide explains the architecture, datapaths, and integration of the PCS.

## Dependencies

The following tools are required to build and test the PCS:

- Chisel / Scala
- CIRCT / FIRRTL
- SBT

For synthesis regression testing the following open-source tools are used:

- Yosys (v0.9) – synthesis tool
- OpenSTA (v2.4.0) – static timing analysis

## Installation 

There are no special installation requirements.

Clone the repository and build using sbt:

git clone <https://github.com/shreosidebnath/ClockDomainCrew>

cd ClockDomainCrew

sbt

The PCS core can be used standalone or integrated into larger Ethernet designs.

## Generating Verilog RTL

Example SystemVerilog RTL can be generated using the provided Chisel main application.

Start sbt:

sbt

Then run the PCS project:
project core
run
Generated RTL will be placed in:
modules/pcs/generated/

Each synthesis configuration will generate:

- SystemVerilog RTL
- synthesis scripts
- timing constraint files

## Running Simulation

Simulation can be run using several supported Verilog simulators:

- Icarus Verilog
- Verilator
- VCS

Run tests with:
sbt
project core
test

This will execute constrained-random tests for PCS functionality.

## Synthesis

The PCS core is fully synthesizable.

Each configuration generates synthesis collateral in:

modules/pcs/generated/synTestCases/

Generated files include:

- RTL
- .sdc timing constraints
- Yosys synthesis scripts
- OpenSTA timing scripts

The included setup uses the Nangate 45nm library, but the scripts can be adapted for other technology libraries.

## Authors

ClockDomainCrew

## Version History
0.1.0

Initial release including TX and RX PCS implementation.

## License

This project is licensed under the CERN-OHL-S v2 license. 

See LICENSE.md for license information.




