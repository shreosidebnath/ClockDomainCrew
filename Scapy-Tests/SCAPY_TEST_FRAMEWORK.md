# Scapy Tests Documentation

This directory contains hardware-in-the-loop Ethernet validation for the FPGA MAC+PCS path using Scapy.

Related documentation:
- `SCAPY_TEST_FRAMEWORK.md` (this file): Scapy test framework architecture, specs, and execution.
- `FPGA_OPERATIONS.md`: board bring-up and operational workflows (serial login, IP setup, internet bridging, SCP, file-transfer loopback, FWUEN notes).

## 1. Purpose and Scope

The `Scapy-Tests/` suite validates that:
- link negotiation is up (`l3: link` test),
- the FPGA loopback datapath returns frames correctly (`l3: raw` tests),
- frame behavior is correct across edge cases (min/MTU/jumbo, VLAN VID/PCP, broadcast/multicast, under/oversize drop expectations),
- PAUSE frame behavior shows transmit throttling (`l3: pause_frame` test),
- performance indicators are measurable (throughput and RTT metrics in JSON artifacts).

These tests exercise the integrated MAC+PCS behavior on real hardware, not just simulation.

## 2. Directory Structure

```text
Scapy-Tests/
  Makefile
  scapy_framework.py
  SCAPY_TEST_FRAMEWORK.md
  artifacts/
  tests/
    __init__.py
    base.py
    hw_connectivity.py
    runner.py
    specs/
      01_connectivity.yml ... 15_latency.yml
```

## 3. How the Framework Works

Execution flow:
1. `tests/runner.py` parses `--spec` and validates allowed fields (`backend`, `test.type`, `test.l3`).
2. `tests/base.py` loads YAML into a typed `TestSpec` dataclass.
3. `tests/hw_connectivity.py` dispatches by `test.l3`:
   - `link` -> `check_link()`
   - `raw` -> send/sniff/validate loopback frames
   - `pause_frame` -> inject PAUSE frames and verify quiet gap
4. `scapy_framework.py` provides low-level helpers used by `hw_connectivity.py`:
   - interface selection,
   - MAC lookup,
   - link state checks,
   - raw frame construction/transmit (tagged or untagged).

## 4. File-by-File Explanation

### `tests/specs/*.yml`
- Each YAML file is a full test configuration.
- Top-level fields:
  - `name`
  - `backend` (must be `hw`)
  - `test` (all runtime controls)
  - optional `payload` (used when `payload_len` is not set)
- `test` controls include interface (`iface`), mode (`l3`), counts, timing, payload pattern, VLAN settings, expected result, and metrics output.

### `tests/base.py`
- Defines `TestSpec` dataclass:
  - `name`, `backend`, `test`, `payload`
- `load_spec(path)`:
  - reads YAML safely,
  - normalizes `payload` to bytes,
  - returns structured `TestSpec`.
- `now_us()` helper provides microsecond timestamps.

### `tests/runner.py`
- Entry-point for all tests: `python3 -m tests.runner --spec ...`
- Validation gates:
  - `backend` must be `hw`
  - `test.type` must be one of `connectivity` / `loopback`
  - `test.l3` must be one of `link` / `raw` / `pause_frame`
- Calls `tests.hw_connectivity.run(spec)` and exits with:
  - `0` on PASS
  - `1` on FAIL
  - `2` on invalid configuration

### `scapy_framework.py`
- Hardware/network utility layer:
  - `set_iface(query)`: resolves interface by substring or exact match and binds Scapy.
  - `get_iface_mac(iface)`: reads NIC MAC address.
  - `check_link(iface, timeout)`: Linux `/sys/class/net/*` carrier + operstate poll.
  - `_try_get_speed(iface)`: best-effort speed read from `ethtool`.
  - `send_raw(...)`: builds and sends Ethernet frame:
    - untagged: `Ether(type=ether_type)/Raw`
    - tagged: `Ether(type=0x8100)/Dot1Q(..., type=ether_type)/Raw`

### `tests/hw_connectivity.py`
Main orchestrator for test logic.

- `l3: link`
  - Runs `check_link()`.
  - No traffic injection; verifies physical/electrical link status.

- `l3: raw`
  - Builds payload with marker (`CDCREW:<seq>` format).
  - Sends frames with ethertype `0x9000`.
  - Sniffs return traffic with BPF:
    - `ether proto 0x9000` or VLAN equivalent.
  - Uses `PACKET_IGNORE_OUTGOING` (when supported) to avoid counting locally transmitted copies.
  - Validates:
    - loopback pass (`expect: pass`) or
    - intentional drop behavior (`expect: drop`).
  - Optional metrics JSON (`metrics.json_out`) includes sent/received/loss/pps and RTT stats when `include_ts: true`.

- `l3: pause_frame`
  - Sends loopback data stream (`0x9000`) with marker.
  - Injects IEEE 802.3x PAUSE control frames (ethertype `0x8808`, opcode `0x0001`).
  - Observes returned marked traffic for a post-injection quiet window.
  - Pass condition: max silent gap >= `require_gap_s`.

## 5. Why This Verifies MAC+PCS on FPGA

The tests validate the real datapath end-to-end because they:
- transmit on the host NIC into the FPGA physical link,
- traverse PCS + MAC receive path,
- go through the FPGA loopback behavior under test,
- traverse MAC + PCS transmit path back to host,
- and are verified by marker/sequence-aware sniffing.

Coverage relevance:
- Frame sizing: min / standard MTU / jumbo.
- L2 tags and classes: untagged, VLAN VID, VLAN PCP.
- Destination classes: unicast default, broadcast, multicast.
- Robustness: undersize/oversize expected-drop behavior.
- Flow control: PAUSE-induced quiet period.
- Performance characterization: sustained burst + RTT metrics.

## 6. Spec Catalog (`tests/specs`)

- `01_connectivity.yml`: link/carrier smoke test.
- `02_nic_loopback.yml`: basic raw loopback sanity.
- `03_min_frame_payload.yml`: minimum payload stress.
- `04_standard_mtu_payload.yml`: 1500-byte payload loopback.
- `05_jumbo_payload.yml`: jumbo payload loopback.
- `06_untagged_baseline.yml`: untagged baseline.
- `07_vlan_tagged.yml`: VLAN VID path.
- `08_vlan_pcp.yml`: VLAN VID + PCP.
- `09_broadcast_dest.yml`: broadcast destination handling.
- `10_multicast_dest.yml`: multicast destination handling.
- `11_pause_frame_behaviour.yml`: PAUSE throttle behavior.
- `12_undersize.yml`: runt robustness, expected drop.
- `13_oversize.yml`: oversize robustness, expected drop.
- `14_funct_through.yml`: sustained burst + throughput artifact.
- `15_latency.yml`: RTT measurement artifact.

## 7. Makefile Shortcuts

Run commands from inside `Scapy-Tests/`.

Useful targets:
- `make help`
- `make list`
- `make all` (runs every `tests/specs/*.yml`)
- `make run SPEC=tests/specs/02_nic_loopback.yml`
- `make 02` (pattern target -> first `tests/specs/02_*.yml`)

All run targets call:
```bash
sudo python3 -m tests.runner --spec <spec-file>
```

## 8. Manual Runner Examples

```bash
sudo python3 -m tests.runner --spec tests/specs/01_connectivity.yml
sudo python3 -m tests.runner --spec tests/specs/02_nic_loopback.yml
sudo python3 -m tests.runner --spec tests/specs/11_pause_frame_behaviour.yml
```

## 9. Host/Environment Prerequisites

- Linux host with Scapy installed.
- Root permissions (`sudo`) for raw packet send/sniff.
- Correct NIC selected in spec (`iface`, currently examples use `ens6`).
- FPGA image loaded with MAC+PCS path configured for expected loopback behavior.
- Link physically connected and up before running traffic tests.

## 10. Notes and Troubleshooting

- If interface matching fails, verify NIC name using Scapy `get_if_list()` or system tools (`ip link`).
- If link test fails, check cable/DAC, SFP, and negotiated mode.
- If false positives appear, ensure outgoing packet ignore is supported and host is not mirrored/bridged in a way that re-injects TX.
- For VLAN tests, verify FPGA path preserves/expects the same VID/PCP configured in the spec.
- For drop tests (`12`, `13`), pass criteria is intentionally low return count (`<=1` unique frame).
