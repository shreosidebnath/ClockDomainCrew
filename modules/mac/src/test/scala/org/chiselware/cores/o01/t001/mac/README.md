# MAC Verification Environment

This verification environment is used to validate the Chisel MAC implementation against a trusted Verilog golden reference model from the Taxi repository. The overall strategy is to drive both implementations with identical stimuli, collect their outputs, and compare their observable behavior across valid traffic, error cases, corner cases, randomized tests, and parameter variations.

The goal is to ensure that the Chisel MAC behaves equivalently to the reference model in terms of:

- frame contents
- protocol behavior
- control signaling
- error handling
- observable status outputs
- edge-case behavior under varied traffic conditions
- supported parameter configurations

---

## High-Level Overview

The MAC verification environment is built around side-by-side comparison between:

- the **Chisel MAC implementation**
- the **Taxi Verilog MAC golden model**

The environment includes:

- a **BlackBox wrapper** for the Verilog MAC
- a **dual-wrapper harness** that instantiates both versions together
- **Bus Functional Models (BFMs)** for AXI-Stream and XGMII interfaces
- helper utilities for frame construction, encoding, and checking
- status and comparison logic
- directed, edge-case, randomized, and parameter-focused tests

---

## Main Components

### `MacBb`

`MacBb` is a BlackBox wrapper around the Taxi repository's Verilog MAC. This allows the Verilog implementation to be instantiated directly inside the Chisel-based verification environment and used as the golden reference.

### `DualWrapperMac`

`DualWrapperMac` is the main comparison harness. It instantiates both:

- the **Verilog MAC golden model**
- the **Chisel MAC implementation**

It aligns equivalent interfaces between the two designs, drives them with the same stimulus, and exposes both outputs so they can be compared directly.

### `CompareMacTester`

`CompareMacTester` is the primary MAC test suite. It includes:

- helper functions for Ethernet frame construction
- CRC/FCS generation and corruption helpers
- expected-data comparison helpers
- AXI-Stream drivers and sinks
- XGMII drivers and monitors
- status collection and checking logic
- directed tests
- error and edge-case tests
- randomized tests
- coverage-oriented scenarios

### `otherParameterTesting/`

In addition to the main equivalence testbench, the MAC verification environment also includes parameter-focused test suites under the `otherParameterTesting` folder.

These tests are used to increase coverage and verify that supported parameter combinations behave correctly, even if those exact configurations are not used in the final project instantiation of the MAC.

This is important because the MAC contains configurable and reusable logic. Correctness should not only hold for the single top-level configuration used in the final design, but also for other legal parameter settings supported by the implementation.

---

## Test Flow

A typical MAC test follows this sequence:

1. Construct an Ethernet frame, usually including payload and CRC/FCS.
2. Convert the frame into the appropriate interface format:
   - **XGMII** for RX-side testing
   - **AXI-Stream** for TX-side testing
3. Drive the same stimulus into both:
   - the Chisel MAC
   - the Verilog MAC golden model
4. Monitor and collect outputs from both implementations.
5. Compare:
   - frame data
   - control signals
   - error indicators
   - good/bad packet behavior
6. Confirm that both implementations behave equivalently for the scenario under test.

---

## What the MAC Tests Verify

### Functional Correctness

The MAC tests verify that valid packets are handled correctly across both the receive and transmit paths. This includes:

- correct frame contents
- proper payload transfer
- correct CRC/FCS behavior
- correct AXI/XGMII formatting behavior

### Error and Negative Cases

The environment also checks how the MAC responds to malformed or invalid traffic. These tests include scenarios such as:

- bad FCS / CRC corruption
- bad framing
- runt frames
- oversize frames
- invalid or unusual termination behavior where applicable

### Boundary and Coverage Scenarios

Targeted tests are included to exercise important protocol corner cases, such as:

- partial `tkeep` patterns
- different packet lengths
- termination placement cases
- minimum and maximum legal frame-related behaviors
- inter-frame spacing related situations where applicable

### Randomized Stress Testing

Randomized payloads and packet lengths are used to broaden test coverage and increase confidence that the Chisel MAC matches the Verilog model beyond only hand-picked directed examples.

### Parameter Variation Testing

Separate parameter-focused test suites under `otherParameterTesting` are used to validate supported behavior beyond the exact top-level MAC configuration used in the project.

These tests help ensure that alternate legal parameter values and configurable internal modules still operate correctly, improving coverage and confidence in the implementation as a reusable design rather than only as a single fixed instantiation.

---

## Verification Philosophy

The MAC testbench is fundamentally a **reference-model equivalence environment**. Rather than relying only on manually written expected outputs, it continuously uses the Taxi Verilog MAC as the known-good implementation and checks that the Chisel MAC reproduces the same observable behavior.

This provides strong confidence that the Chisel MAC is functionally aligned with the reference model across:

- normal packet traffic
- malformed frames
- protocol edge cases
- randomized traffic sequences
- supported parameter variations

---

## Summary

The MAC verification environment performs side-by-side comparison between the Chisel MAC and a trusted Verilog golden model using BFMs, protocol-aware helpers, status checking, coverage-oriented tests, and parameter-focused test suites.

It is designed to validate:

- functional correctness
- protocol compliance
- error handling
- edge-case behavior
- robustness under randomized traffic
- correctness across supported parameter configurations