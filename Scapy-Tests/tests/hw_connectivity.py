import os
import sys
import time
import json
import struct
import socket
from typing import Tuple, Dict, Any, List

from scapy.all import conf, AsyncSniffer
from scapy.layers.l2 import Ether, Dot1Q
from scapy.packet import Raw

from tests.base import load_spec, TestSpec

# Add repo root so we can import scapy_framework.py
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _REPO_ROOT not in sys.path:
    sys.path.append(_REPO_ROOT)

import scapy_framework as lab  # type: ignore[import]


def _pattern_bytes(n: int, pattern: str) -> bytes:
    if n <= 0:
        return b""
    p = pattern.lower().strip()
    if p == "zeros":
        return b"\x00" * n
    if p == "prbs":
        # deterministic PRBS-ish bytes (simple LFSR)
        out = bytearray()
        x = 0xACE1
        for _ in range(n):
            x = ((x >> 1) ^ (0xB400 if (x & 1) else 0)) & 0xFFFF
            out.append(x & 0xFF)
        return bytes(out)
    # default "inc"
    return bytes((i & 0xFF) for i in range(n))


def run(spec_path: str) -> Tuple[bool, str]:
    spec: TestSpec = load_spec(spec_path)
    t: Dict[str, Any] = spec.test

    iface_query: str = t.get("iface", "ens1f0")
    l3: str = (t.get("l3") or "raw").lower().strip()

    # Bind interface in Scapy (normalize actual name)
    conf.iface = lab.set_iface(iface_query)

    # -------------------------
    # LINK CHECK (NO TRAFFIC)
    # -------------------------
    if l3 == "link":
        ok, info = lab.check_link(conf.iface, timeout=float(t.get("timeout", 2)))
        if ok:
            return True, f"LINK OK - {info}"
        return False, f"LINK FAIL - {info}"
    
        # -------------------------
    # PAUSE FRAME TEST (NIC -> DUT)
    # - Send a stream of RAW frames that the DUT loops back (creates DUT TX)
    # - Inject PAUSE frames mid-stream
    # - Verify we observe a "quiet gap" in returned frames after PAUSE
    # -------------------------
    if l3 == "pause_frame":
        # --- parameters ---
        marker = (t.get("marker", "CDCREW")).encode()
        marker_prefix = marker + b":"

        data_count = int(t.get("data_count", 200))
        data_payload_len = int(t.get("data_payload_len", 256))
        data_pattern = str(t.get("data_pattern", "inc"))
        data_inter_gap = float(t.get("data_inter_gap", 0.0005))
        inject_after = int(t.get("inject_after", max(1, data_count // 4)))

        pause_quanta = int(t.get("pause_quanta", 0xFFFF))
        pause_count = int(t.get("pause_count", 5))
        pause_inter_gap = float(t.get("pause_inter_gap", 0.01))

        observe_window_s = float(t.get("observe_window_s", 0.25))
        require_gap_s = float(t.get("require_gap_s", 0.05))

        arm_delay = float(t.get("arm_delay", 0.05))
        timeout = float(t.get("timeout", 2))

        vlan = t.get("vlan", None) if isinstance(t.get("vlan", None), dict) else None

        host_mac = lab.get_iface_mac(conf.iface).lower()

        # Destination defaults to broadcast (consistent with your other tests)
        dst_kind = str(t.get("dst_kind", "")).lower().strip()
        dst_mac_cfg = t.get("dst_mac")
        
        tx_dst = "ff:ff:ff:ff:ff:ff"
        tx_src = host_mac if not t.get("src_mac") else str(t.get("src_mac")).lower()

        # Payload generator: marker + seq + padding bytes
        def make_data_payload(seq: int) -> bytes:
            header = marker_prefix + struct.pack("!I", seq)
            if data_payload_len <= len(header):
                return header[:data_payload_len]
            body = _pattern_bytes(data_payload_len - len(header), data_pattern)
            return header + body

        # Sniff filter: capture our test ether_type for both untagged and VLAN-tagged frames
        # (If VLAN is used, inner ethertype is 0x9000)
        bpf = "ether proto 0x9000 or (vlan and ether proto 0x9000)"

        def is_marked(p) -> bool:
            # Only count frames that contain our marker prefix in the Raw payload.
            # (We keep it strict to your existing scheme.)
            if not p.haslayer(Raw):
                return False
            raw_bytes = bytes(p[Raw].load)
            return raw_bytes.startswith(marker_prefix)

        print(
            f"[PAUSE_FRAME] iface={conf.iface} host_mac={host_mac} tx={tx_src}->{tx_dst} "
            f"rx={tx_dst}->{host_mac} data_count={data_count} inject_after={inject_after} "
            f"pause_quanta={pause_quanta} pause_count={pause_count} "
            f"require_gap_s={require_gap_s} observe_window_s={observe_window_s}"
        )

        # Start sniffer first
        listen_sock = conf.L2listen(iface=conf.iface, filter=bpf)
        try:
            pkt_ignore = getattr(socket, "PACKET_IGNORE_OUTGOING", 23)
            sol_packet = getattr(socket, "SOL_PACKET", 263)
            listen_sock.ins.setsockopt(sol_packet, pkt_ignore, 1)
        except Exception as e:
            print(f"[WARN] PACKET_IGNORE_OUTGOING not set (may see TX copies): {e}")

        sniffer = AsyncSniffer(
            opened_socket=listen_sock,
            lfilter=is_marked,
            store=True,
            promisc=True,
        )
        sniffer.start()
        time.sleep(arm_delay)

        # --- transmit stream + inject pause ---
        inject_t = None

        for seq in range(data_count):
            # Inject PAUSE at the configured point
            if inject_t is None and seq == inject_after:
                # Build and send PAUSE frames (802.3x MAC Control)
                dst = "01:80:c2:00:00:01"
                opcode = 0x0001
                pause_payload = struct.pack("!HH", opcode, pause_quanta) + (b"\x00" * 42)

                inject_t = time.time()
                for _ in range(pause_count):
                    lab.send_raw(
                        iface=conf.iface,
                        dst_mac=dst,
                        src_mac=host_mac,
                        payload=pause_payload,
                        ether_type=0x8808,
                        vlan=None,
                    )
                    time.sleep(pause_inter_gap)

            # Send a data frame that should be looped back by DUT
            lab.send_raw(
                iface=conf.iface,
                dst_mac=tx_dst,
                src_mac=tx_src,
                payload=make_data_payload(seq),
                ether_type=0x9000,
                vlan=vlan,
            )
            if data_inter_gap > 0:
                time.sleep(data_inter_gap)

        # Give time for returns to arrive after injection
        if inject_t is None:
            inject_t = time.time()
        time.sleep(min(timeout, observe_window_s + 0.05))

        pkts = sniffer.stop() or []

        # --- analyze returned marked frames for a quiet gap after pause injection ---
        # Use packet timestamps from scapy (p.time) when available; fallback to "now"
        times = []
        for p in pkts:
            try:
                times.append(float(getattr(p, "time")))
            except Exception:
                pass

        times.sort()
        window_start = float(inject_t)
        window_end = window_start + float(observe_window_s)

        # Keep only times in the observation window
        win = [x for x in times if window_start <= x <= window_end]

        if not win:
            return True, f"PAUSE FRAME OK (no returned frames in {observe_window_s:.3f}s window after injection)"

        # Compute the maximum quiet gap within the window, including from window_start to first packet
        max_gap = win[0] - window_start
        for a, b in zip(win, win[1:]):
            max_gap = max(max_gap, b - a)
        # Also consider last packet to window_end
        max_gap = max(max_gap, window_end - win[-1])

        if max_gap >= require_gap_s:
            return True, f"PAUSE FRAME OK (max quiet gap {max_gap:.3f}s >= {require_gap_s:.3f}s)"
        return False, f"PAUSE FRAME FAIL (max quiet gap {max_gap:.3f}s < {require_gap_s:.3f}s)"

    # -------------------------
    # RAW LOOPBACK (NIC↔FPGA)
    # -------------------------
    if l3 != "raw":
        return False, f"unsupported l3={l3} (expected 'link', 'raw', or 'pause')"

    # ---- RAW config fields ----
    test_type = str(t.get("type", "loopback")).lower().strip()
    verify_loopback = bool(t.get("verify_loopback", False))
    expect = str(t.get("expect", "pass")).lower().strip()  # "pass" or "drop"

    marker = (t.get("marker", "CDCREW")).encode()
    marker_prefix = marker + b":"

    count = int(t.get("count", 5))
    arm_delay = float(t.get("arm_delay", 0.2))
    inter_gap = float(t.get("inter_gap", 0.05))

    payload_len = t.get("payload_len")  # int or None
    pattern = str(t.get("pattern", "inc"))
    include_ts = bool(t.get("include_ts", False))

    vlan = t.get("vlan", None) if isinstance(t.get("vlan", None), dict) else None

    metrics = t.get("metrics", {}) if isinstance(t.get("metrics", {}), dict) else {}
    json_out = metrics.get("json_out")

    # ---- MAC selection (NO FPGA MAC needed) ----
    host_mac = lab.get_iface_mac(conf.iface).lower()

    src_mac_cfg = t.get("src_mac")
    tx_src = host_mac if not src_mac_cfg else str(src_mac_cfg).lower()

    dst_kind = str(t.get("dst_kind", "")).lower().strip()
    dst_mac_cfg = t.get("dst_mac")

    if dst_kind == "broadcast":
        tx_dst = "ff:ff:ff:ff:ff:ff"
    elif dst_kind == "multicast":
        tx_dst = str(t.get("multicast_mac", "01:00:5e:00:00:01")).lower()
    else:
        tx_dst = str(dst_mac_cfg).lower().strip() if dst_mac_cfg else "ff:ff:ff:ff:ff:ff"

    send_times_us: Dict[int, int] = {}

    def make_payload(seq: int) -> bytes:
        header = marker_prefix + struct.pack("!I", seq)
        if include_ts:
            ts_us = int(time.time() * 1e6)
            send_times_us[seq] = ts_us
            header += struct.pack("!Q", ts_us)

        if payload_len is None:
            body = spec.payload if spec.payload else b"hello-raw"
            return header + body

        target = int(payload_len)
        if target <= len(header):
            return header[:target]

        body = _pattern_bytes(target - len(header), pattern)
        return header + body

    # Return filter:
    # - must carry our marker (so we only count our test frames)
    # - we do NOT enforce src != host_mac (your loopback may not rewrite MACs)
    def is_returned(p) -> bool:
        if not p.haslayer(Ether) or not p.haslayer(Raw):
            return False

        raw_bytes = bytes(p[Raw].load)
        if not raw_bytes.startswith(marker_prefix):
            return False

        if vlan and vlan.get("enabled"):
            if not p.haslayer(Dot1Q):
                return False
            dot1q = p[Dot1Q]

            vid = int(vlan.get("vid", 0))
            if vid and int(dot1q.vlan) != vid:
                return False

            if "pcp" in vlan:
                if int(dot1q.prio) != int(vlan.get("pcp", 0)):
                    return False

        return True

    print(
        f"[RAW] iface={conf.iface} "
        f"TX {tx_src}->{tx_dst}  "
        f"RX {tx_dst}->{host_mac}  "
        f"verify={verify_loopback} count={count} payload_len={payload_len} "
        f"dst_kind={dst_kind or 'mac/broadcast'} "
        f"vlan={'on' if (vlan and vlan.get('enabled')) else 'off'} "
        f"expect={expect}"
    )

    # Capture setup:
    # Prefer ignoring outgoing copies at socket level so we don't need MAC-based heuristics.
    # If the kernel doesn't support it / call fails, we'll still likely see outgoing frames,
    # BUT the marker+seq uniqueness logic will still work in many cases.
    bpf = "ether proto 0x9000 or (vlan and ether proto 0x9000)"
    listen_sock = conf.L2listen(iface=conf.iface, filter=bpf)

    try:
        pkt_ignore = getattr(socket, "PACKET_IGNORE_OUTGOING", 23)
        sol_packet = getattr(socket, "SOL_PACKET", 263)  # fallback for builds missing SOL_PACKET
        listen_sock.ins.setsockopt(sol_packet, pkt_ignore, 1)
    except Exception as e:
        print(f"[WARN] PACKET_IGNORE_OUTGOING not set (may see TX copies): {e}")

    sniffer = AsyncSniffer(
        opened_socket=listen_sock,
        lfilter=is_returned,
        store=True,
        promisc=True,
    )
    sniffer.start()
    time.sleep(arm_delay)

    t_start = time.time()
    for seq in range(count):
        lab.send_raw(
            iface=conf.iface,
            dst_mac=tx_dst,
            src_mac=tx_src,
            payload=make_payload(seq),
            ether_type=0x9000,
            vlan=vlan,
        )
        if inter_gap > 0:
            time.sleep(inter_gap)

    pkts = sniffer.stop() or []

    if not verify_loopback:
        return True, f"RAW send completed (sniffed {len(pkts)} frames with marker)"

    # Unique seq count + optional RTT
    seen = set()
    rtts_us: List[int] = []

    for p in pkts:
        if not p.haslayer(Raw):
            continue
        raw_bytes = bytes(p[Raw].load)
        if len(raw_bytes) < len(marker_prefix) + 4:
            continue

        seq = struct.unpack("!I", raw_bytes[len(marker_prefix) : len(marker_prefix) + 4])[0]
        seen.add(seq)

        if include_ts and len(raw_bytes) >= len(marker_prefix) + 4 + 8:
            ts_sent = struct.unpack(
                "!Q",
                raw_bytes[len(marker_prefix) + 4 : len(marker_prefix) + 12])[0]
            # Use packet capture timestamp (when packet was sniffed), NOT analysis time
            rx_us = int(float(getattr(p, "time", time.time())) * 1e6)
            rtts_us.append(max(0, rx_us - ts_sent))

    captured = len(seen)
    duration_s = max(1e-9, time.time() - t_start)
    loss = max(0, count - captured)

    # Metrics JSON (optional)
    if json_out:
        os.makedirs(os.path.dirname(json_out), exist_ok=True)
        out: Dict[str, Any] = {
            "name": spec.name,
            "iface": conf.iface,
            "test_type": test_type,
            "host_mac": host_mac,
            "tx_dst": tx_dst,
            "dst_kind": dst_kind,
            "sent": count,
            "received_unique": captured,
            "loss": loss,
            "duration_s": duration_s,
            "pps": (count / duration_s),
            "payload_len": payload_len,
            "pattern": pattern,
            "include_ts": include_ts,
            "vlan": vlan,
            "expect": expect,
        }
        if rtts_us:
            rtts_sorted = sorted(rtts_us)
            out["rtt_us"] = {
                "min": rtts_sorted[0],
                "p50": rtts_sorted[len(rtts_sorted) // 2],
                "p95": rtts_sorted[int(len(rtts_sorted) * 0.95) - 1],
                "p99": rtts_sorted[int(len(rtts_sorted) * 0.99) - 1],
                "max": rtts_sorted[-1],
                "n": len(rtts_sorted),
            }
        with open(json_out, "w", encoding="utf-8") as f:
            json.dump(out, f, indent=2)

    # Expectation handling
    if expect == "drop":
        if captured <= 1:
            return True, f"RAW expected-drop OK (returned {captured}/{count})"
        return False, f"RAW expected-drop FAIL (returned {captured}/{count})"

    if captured >= count:
        return True, f"RAW loopback OK ({captured}/{count} unique frames)"
    return False, f"RAW loopback FAIL ({captured}/{count} unique frames)"

