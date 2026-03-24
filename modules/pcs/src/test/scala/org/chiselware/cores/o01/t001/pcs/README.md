# PCS Verification Environment

This verification environment is used to validate the Chisel PCS implementation against a trusted Verilog golden reference model from the Taxi repository. The overall strategy is to drive both implementations with identical stimuli, collect their outputs, and compare their observable behavior across encoding, decoding, synchronization, error handling, edge cases, randomized tests, and parameter variations.

The goal is to ensure that the Chisel PCS behaves equivalently to the reference model in terms of:

- encoded and decoded data behavior
- protocol compliance
- control/data block handling
- synchronization and lock behavior
- observable status outputs
- robustness under invalid or disturbed inputs
- supported parameter configurations

---

## High-Level Overview

The PCS verification environment is built around side-by-side comparison between:

- the **Chisel PCS implementation**
- the **Taxi Verilog PCS golden model**

The environment includes:

- a **BlackBox wrapper** for the Verilog PCS
- a **dual-wrapper harness** that instantiates both versions together
- helper utilities for XGMII and SERDES-side stimulus generation
- comparison logic for TX and RX behavior
- lock and status checking
- directed, edge-case, randomized, and parameter-focused tests

The purpose of the PCS tests is to verify that the Chisel PCS behaves like the Verilog golden model across:

- **encoding**: XGMII -> 64b/66b
- **decoding**: 64b/66b -> XGMII
- control/data block handling
- synchronization and block lock behavior
- header interpretation
- error handling and robustness

---

## Main Components

### `PcsBb`

`PcsBb` is a BlackBox wrapper around the Taxi Verilog PCS module. It allows the Verilog implementation to be instantiated directly inside the Chisel-based verification environment and used as the golden reference.

### `DualWrapperPcs`

`DualWrapperPcs` is the main comparison harness. It instantiates both:

- the **Verilog PCS golden model**
- the **Chisel PCS implementation**

Both versions receive equivalent stimuli, and their outputs are exposed side by side for direct comparison.

### `ComparePcsTester`

`ComparePcsTester` is the primary PCS test suite. It includes:

- helper routines for XGMII stimulus generation
- SERDES/header driving support
- TX path comparison logic
- RX path comparison logic
- lock/status checking
- coverage tracking for control-block and frame-boundary behavior
- directed tests
- edge-case tests
- randomized tests

### `otherParameterTesting/`

In addition to the main equivalence testbench, the PCS verification environment also includes parameter-focused test suites under the `otherParameterTesting` folder.

These tests are used to increase coverage and verify that supported parameter combinations behave correctly, even if those exact configurations are not used in the final project instantiation of the PCS.

This helps ensure that parameterized components remain robust and correct across the range of supported configurations, not just in the single project-specific setup.

---

## Test Flow

A typical PCS test operates in one of two directions.

### TX Path Verification

1. Generate an XGMII transmit stream.
2. Feed the same XGMII stream into both PCS implementations.
3. Observe the encoded outputs from both implementations.
4. Compare:
   - 64b/66b encoded data
   - sync headers
   - valid signaling
   - control/data block behavior

### RX Path Verification

1. Generate or inject a SERDES-side 64b/66b stream.
2. Feed the same encoded stream into both PCS implementations.
3. Observe the recovered XGMII outputs from both implementations.
4. Compare:
   - recovered XGMII data
   - control bytes
   - valid behavior
   - lock and status outputs
   - error-related indicators

In some cases, the RX-side encoded input is injected through a tap so that both implementations receive the same SERDES stream under identical conditions.

---

## What the PCS Tests Verify

### Functional Correctness

The PCS tests verify that the Chisel implementation performs the same protocol transformations as the Verilog golden model.

This includes:

- correct XGMII-to-64b/66b encoding
- correct 64b/66b-to-XGMII decoding
- correct handling of data blocks versus control blocks
- correct reconstruction of frames on the RX side

### Protocol Compliance

The PCS verification checks behavior expected from 10GBASE-R processing, including:

- correct sync header use
  - `01` for data blocks
  - `10` for control blocks
- correct `/S/` and `/T/` handling
- proper frame boundary encoding and decoding
- proper lane alignment behavior
- correct idle and control block treatment

### Synchronization and Status Behavior

The environment also verifies PCS-specific status functionality, such as:

- block lock acquisition
- stable lock under valid streams
- status behavior under clean and disturbed conditions
- error-related output consistency between Chisel and Verilog

### Robustness and Negative Cases

The PCS tests are not limited to clean traffic. They also exercise how the design behaves under abnormal conditions, such as:

- invalid headers
- glitches or disturbances in the incoming encoded stream
- improper IPG / block conditions
- malformed or unexpected encoded patterns

### Coverage-Oriented Scenarios

The PCS environment includes targeted cases to ensure important encoding/decoding patterns are exercised, such as:

- all possible terminate lane positions
- start/control placement behavior
- idle spacing behavior
- block pattern variety across different payload lengths

### Randomized Testing

Randomized streams and payload patterns are used to broaden scenario coverage and increase confidence that the Chisel PCS matches the golden model beyond only directed tests.

### Parameter Variation Testing

Additional tests under `otherParameterTesting` are used to verify that supported PCS parameter combinations still behave correctly outside the default project configuration.

These tests improve confidence that parameterized blocks remain correct, reusable, and stable across a broader legal design space.

---

## Important Comparison Note

The PCS environment is intended to provide **cycle-accurate comparison during meaningful packet-processing behavior**.

During active frame processing, encoded and decoded traffic as well as relevant status outputs are compared directly between the Chisel and Verilog implementations.

In idle or unused situations, some outputs may differ in ways that are not protocol-meaningful or are effectively don't-care behavior. These differences do not indicate a functional mismatch as long as all meaningful observable behavior matches during valid operation.

A concise way to state this is:

> The PCS implementations are cycle-by-cycle equivalent during active packet processing. Any idle-cycle differences are limited to unused or don't-care outputs, while all meaningful protocol-visible behavior matches.

---

## Verification Philosophy

The PCS testbench is fundamentally a **reference-model equivalence environment**. Rather than relying only on manually written expected outputs, it continuously uses the Taxi Verilog PCS as the known-good implementation and checks that the Chisel PCS reproduces the same observable behavior.

This provides strong confidence that the Chisel PCS is functionally aligned with the reference model across:

- encoding and decoding behavior
- protocol corner cases
- malformed or disturbed input cases
- synchronization behavior
- randomized traffic scenarios
- supported parameter variations

---

## Summary

The PCS verification environment performs side-by-side comparison between the Chisel PCS and a trusted Verilog golden model using protocol-aware helpers, TX/RX comparison logic, lock and status checking, coverage-oriented tests, and parameter-focused test suites.

It is designed to validate:

- correct 64b/66b encoding and decoding
- protocol compliance
- synchronization and status behavior
- robustness under abnormal conditions
- edge-case behavior
- randomized scenario handling
- correctness across supported parameter configurations