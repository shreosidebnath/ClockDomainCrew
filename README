# ClockDomainCrew

Chisel-based hardware designs following chiselware standards.

## Modules

### nfmac10g - 10 Gigabit Ethernet MAC

Located in `modules/nfmac10g/`

**Components:**
- XGMII interface
- AXI Stream conversion
- Transmit/Receive logic
- Flow control (pause frames)

## Building
```bash
# Compile all modules
sbt compile

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
├── build.sbt                 # Root sbt build (aggregator)
├── Makefile                  # Primary build + verification entry point
├── docs/                     # Project-level reports and documentation
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

## Documentation

- Module documentation: `modules/nfmac10g/docs/user-guide/`
- Verification: `docs/verification-compliance/`