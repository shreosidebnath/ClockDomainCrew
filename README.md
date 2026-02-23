# ClockDomainCrew

Chisel-based 10 Gigabit Ethernet implementation following chiselware standards.

## Modules

### MAC - Media Access Control
**Location:** `modules/mac/`

10G Ethernet MAC layer implementing IEEE 802.3 framing, CRC, and flow control.
- Standalone module usable independently
- AXI-Stream interface for packet data
- XGMII interface to PCS layer

**Status:** In development

### PCS - Physical Coding Sublayer
**Location:** `modules/pcs/`

Physical coding sublayer implementing 64b/66b encoding and lane management.
- Standalone module usable independently
- XGMII interface from MAC layer
- Serialized output to transceiver

**Status:** In development

### nfmac10g (Reference)
**Location:** `modules/nfmac10g/`

Original Verilog-to-Chisel translation. Kept for reference only.
Not following chiselware standards - do not use as template.

## Running the Project
The primary way to interact with the project is through the provided Makefiles. The build system is split to support the MAC and PCS cores independently.

(Note: You can substitute `Makefile.mac` with `Makefile.pcs` in any of the Make commands below to target the other module.)

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
make -f Makefile.mac verilog
```

Which is equivalent to running the following under the hood:
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

Under the hood, this executes:
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

## Project Structure
```
ClockDomainCrew/
├── build.sbt
├── Makefile.base         # Primary build + verification engine
├── Makefile.pcs          # Injects PCS variables into Makefile.base
├── Makefile.mac          # Injects MAC variables into Makefile.base
├── docs/                 # Project-level reports and documentation
├── modules/
│   ├── mac/              # MAC layer (standalone)
│   ├── pcs/              # PCS layer (standalone)
│   └── nfmac10g/         # Reference only (soon to be removed)
├── Scapy-Tests/          # Loopback tests using Scapy
└── project/              # sbt plugins and build config
```

The following is the structure inside each mac and pcs project directories.

```
...
│
├── modules/
│   └── <core_name>/        # Core hardware module (mac or pcs)
│       ├── docs/
│       │   └── user-guide/    # LaTeX-based user guide
│       ├── generated/         # Verilog, reports, coverage, synthesis output
│       ├── src/
│       │   ├── main/scala/    # Chisel sources
│       │   └── test/scala/    # ChiselTest tests
│       └── target/            # sbt build output
```

## Development Approach

Building incrementally following chiselware standards:
1. Clean interface definitions
2. Well-documented code with descriptive names
3. Modular design for reusability
4. Comprehensive testing at each phase