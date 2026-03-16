**MAC**

A parameterized Ethernet Media Access Control (MAC) core.

**Description**

The MAC core implements the Ethernet Media Access Control layer responsible for transmitting and receiving Ethernet frames between application logic and the physical interface.
The MAC connects a packet-based AXI-Stream interface to a 64-bit XGMII interface, enabling integration with high-speed Ethernet Physical Coding Sublayer (PCS) modules.


The design includes independent transmit and receive datapaths that perform the following functions:
- Ethernet frame generation
- Ethernet frame reception
- Frame Check Sequence (FCS) generation and verification
- Frame padding for minimum frame size enforcement
- Inter-frame gap (IFG) insertion
- Packet classification
- Packet statistics reporting
- Optional PTP timestamp support

The MAC is implemented in Chisel and generates synthesizable SystemVerilog RTL.


**Getting Started**

It is recommended that the user reads the MAC User Guide which can be found in:
modules/mac/docs/user-guide

The user guide contains detailed documentation on:
-MAC architecture
-AXI Stream interfaces
-XGMII interface
-Transmit datapath
-Receive datapath
-configuration parameters
-statistics and monitoring signals

**Dependencies**

The MAC core relies on the following tools for simulation, testing, and synthesis flows:

**Required Tools**

- sbt – Scala build tool used to compile Chisel code
- Chisel3 – Hardware construction language used to generate RT

**Optional Tools (for testing and synthesis)**

- Yosys (version 0.9 or later)
Open-source synthesis framework used to generate gate-level netlists.
- OpenSTA (version 2.4.0 or later)
Static timing analysis tool used to verify timing constraints.
- Verilator
Open-source compiled simulator for Verilog/SystemVerilog.
- iVerilog
Open-source event-driven Verilog simulator.
- VCS
Commercial simulation tool from Synopsys.

These tools are only required for running regression tests and synthesis flows.


**Installation**

There are no special installation requirements 
The MAC core can be cloned and used directly as part of the project repository.

Example

git clone<https://github.com/shreosidebnath/ClockDomainCrew>

cd ClockDomainCrew

The MAC core resides in:
module/mac

**Generating Verilog RTL**

The MAC is written in Chisel and generates synthesizable SystemVerilog RTL

To generate RTL

$ sbt

sbt:ClockDomainCrew>

sbt:ClockDomainCrew> project core

sbt:ClockDomainCrew-core-mac> run

Generated RTL files will appear in:

modules/mac/generated

The generated output inculdes:
- SystemVerilog RTL
- configuration-specific build artifacts
- synthesis test configurations

**Running a Simulation**

Simulation can be performed using several supported simulators.

Supported simulators include:
- Verilator
- iVerilog
- VCS

Simulation tests verify the correct operation of:
- frame transmission
- frame reception
- CRC generation
- padding logic
- inter-frame gap enforcement

Tests can be executed with:
$ sbt

sbt:ClockDomainCrew>

sbt:ClockDomainCrew> project core

sbt:ClockDomainCrew-core-mac> test

**Synthesis**

The MAC core is a fully synthesizable hardware design.
The design supports synthesis flows using both open-source and commercial tools.

Generated synthesis artifacts include:
- SystemVerilog RTL
- constraint files (.sdc)
- synthesis scripts
Example synthesis directories:
modules/mac/generated/synTestCases/

These directories include:
- synthesis scripts for Yosys
- timing scripts for OpenSTA
- example technology libraries

The included flows allow users to run synthesis regressions out-of-the-box and can be adapted to commercial EDA tools.

**Authors**

ClockDomainCrew Capstone Team University of Calgary 2026

**Version History**

1.0.0
Initial release of the MAC core.

Features include:
- AXI Stream transmit interface
- AXI Stream receive interface
- XGMII interface support
- CRC generation and verification
- frame padding
- inter-frame gap insertion
- packet statistics
- optional timestamp support

**License**
See the LICENSE.md file for license rights and limitations.

