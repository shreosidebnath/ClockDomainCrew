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

## Building
```bash
# Compile all modules
sbt compile

# Compile specific module
sbt "project mac" compile
sbt "project pcs" compile

# Run tests
sbt "project mac" test
sbt "project pcs" test
# Compile only the core project
sbt "project core" compile
```

## Testing

Recommended: 
```bash
make test
```

This runs:
```bash
sbt "project core" test
```

and writes output to:
```bash
modules/nfmac10g/generated/test.rpt
```

## Generating Verilog

Generate SystemVerilog and synthesis collateral:
```bash
make verilog
```

This runs:
```bash
sbt "project core" run
```

Entrypoint:
```bash
org.chiselware.cores.o01.t001.nfmac10g.Main
```

If you want to generate using sbt instead of makefile:
```bash
sbt "project core" "runMain org.chiselware.cores.o01.t001.nfmac10g.NfMac10gVerilog"
```

## Full regression Flow
Run everything end-to-end:

```bash
make all
```

## Project Structure
```
ClockDomainCrew/
├── build.sbt
├── Makefile              # Primary build + verification entry point
├── docs/                     # Project-level reports and documentation
├── modules/
│   ├── mac/              # MAC layer (standalone)
│   ├── pcs/              # PCS layer (standalone)
│   └── nfmac10g/         # Reference only
└── project/


The following is project structure of the 00-000-dff template. This is kept here as a reference for now
├── modules/
│   └── nfmac10g/              # Core hardware module
│       ├── docs/
│       │   └── user-guide/    # LaTeX-based user guide
│       ├── generated/         # Verilog, reports, coverage, synthesis output
│       ├── src/
│       │   ├── main/scala/    # Chisel sources
│       │   └── test/scala/    # ChiselTest tests
│       └── target/            # sbt build output
└── project/                   # sbt plugins and build config
```

## Development Approach

Building incrementally following chiselware standards:
1. Clean interface definitions
2. Well-documented code with descriptive names
3. Modular design for reusability
4. Comprehensive testing at each phase