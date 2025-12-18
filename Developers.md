# Developer Guide

## Getting Started

### Prerequisites

- SBT 1.9.7+
- Java 11+
- Scala 2.13.10

### Building
```bash
sbt compile
```

### Testing
```bash
sbt test
```

## Project Structure

This project follows chiselware standards with modular organization.

## Adding a New Module

1. Create directory: `modules/<module-name>/`
2. Add to `build.sbt`
3. Follow package structure: `org.chiselware.cores.o##.t###.<module-name>`

## Coding Standards

- Follow Scala style guide
- Use scalafmt for formatting: `sbt scalafmt`
- Write ChiselTest tests for all modules