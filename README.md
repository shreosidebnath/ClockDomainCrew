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
```

## Project Structure
```
ClockDomainCrew/
├── build.sbt
├── modules/
│   ├── mac/              # MAC layer (standalone)
│   ├── pcs/              # PCS layer (standalone)
│   └── nfmac10g/         # Reference only
└── project/
```

## Development Approach

Building incrementally following chiselware standards:
1. Clean interface definitions
2. Well-documented code with descriptive names
3. Modular design for reusability
4. Comprehensive testing at each phase
