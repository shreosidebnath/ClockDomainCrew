# import threading, time, socket, sys, os
# from scapy.all import conf
# from scapy.layers.l2 import Ether
# from scapy.packet import Raw
# from tests.base import load_spec, TestSpec
# from scapy.all import AsyncSniffer 
# import struct


# # Add repo root (Verification_Compliance) to sys.path so we can import scapy_lab_win.py
# _REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# if _REPO_ROOT not in sys.path:
#     sys.path.append(_REPO_ROOT)

# import scapy_framework as lab  # type: ignore[import]


# def _udp_echo_server(bind_ip="127.0.0.1", port=12345, stop_after=5.0):
#     def _run():
#         s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
#         s.bind((bind_ip, port))
#         s.settimeout(0.25)
#         t0 = time.time()
#         try:
#             while time.time() - t0 < stop_after:
#                 try:
#                     data, addr = s.recvfrom(65535)
#                     if data:
#                         s.sendto(data, addr)
#                 except socket.timeout:
#                     pass
#         finally:
#             s.close()

#     th = threading.Thread(target=_run, daemon=True)
#     th.start()
#     return th


# def run(spec_path: str):
#     spec: TestSpec = load_spec(spec_path)
#     t = spec.test

#     iface_query: str = t.get("iface", "ens1f0")
#     l3: str = t.get("l3", "icmp").lower()
#     dst_ip: str = t.get("dst_ip", "127.0.0.1")
#     count: int = int(t.get("count", 3))
#     timeout: int = int(t.get("timeout", 10))

#     # Bind interface in Scapy
#     conf.iface = lab.set_iface(iface_query)

#     # ---- ICMP ----
#     if l3 == "icmp":
#         lab.send_ping(iface_query, dst_ip, count=count, timeout=timeout)
#         lab.sniff_packets(
#             iface_query,
#             bpf="icmp",
#             count=count,
#             timeout=max(5, timeout + 2),
#         )
#         return True, "local ICMP ping + sniff ok"

#     # ---- UDP (local echo on localhost) ----
#     if l3 == "udp":
#         udp_port = int(t.get("udp_port", 12345))
#         echo = _udp_echo_server(dst_ip, udp_port, stop_after=5.0)

#         sniffer = threading.Thread(
#             target=lambda: lab.sniff_packets(
#                 iface_query,
#                 bpf="udp",
#                 count=count * 2,
#                 timeout=5,
#             ),
#             daemon=True,
#         )
#         sniffer.start()
#         time.sleep(0.2)

#         for _ in range(count):
#             lab.send_udp(
#                 iface_query,
#                 dst_ip,
#                 dport=udp_port,
#                 payload=spec.payload or b"hello",
#                 timeout=timeout,
#             )
#             time.sleep(0.05)

#         sniffer.join(timeout=6)
#         echo.join(timeout=6)
#         return True, "local UDP echo + sniff ok"

#     # ---- RAW ETHERNET (FPGA/NIC test) ----
#     if l3 == "raw":
#         # --- read spec fields ---
#         src_mac_cfg = t.get("src_mac")
#         dst_mac_cfg = t.get("dst_mac")
#         verify_loopback = bool(t.get("verify_loopback", False))
#         mode = (t.get("loopback_mode") or "").lower().strip()  # "nic" or "peer"
#         marker = (t.get("marker", "CDCREW")).encode()

#         count = int(t.get("count", 5))
#         timeout = int(t.get("timeout", 3))
#         arm_delay = float(t.get("arm_delay", 0.2))
#         inter_gap = float(t.get("inter_gap", 0.05))

#         # --- ground truth: host interface MAC ---
#         host_mac = lab.get_if_hwaddr(conf.iface).lower()

#         # --- decide loopback mode (never infer if explicitly provided) ---
#         dst_mac_l = (dst_mac_cfg.lower() if dst_mac_cfg else "")
#         if not mode:
#             # infer: if user is sending to self, treat as NIC loopback, otherwise peer
#             mode = "nic" if (dst_mac_l == host_mac or not dst_mac_l) else "peer"

#         # --- compute TX src/dst and expected RX src/dst ---
#         if mode == "nic":
#             # NIC self-loopback: send to self, expect src=self dst=self
#             tx_src = host_mac
#             tx_dst = host_mac
#             expected_src = host_mac
#             expected_dst = host_mac
#             label = "NIC RAW loopback"
#         else:
#             # Peer/FPGA loopback: send to peer, expect return src=peer dst=host
#             if not dst_mac_cfg:
#                 return False, "peer loopback requires dst_mac (peer/FPGA MAC) in spec"

#             peer_mac = dst_mac_cfg.lower()
#             tx_src = host_mac if not src_mac_cfg else src_mac_cfg.lower()
#             tx_dst = peer_mac
#             expected_src = peer_mac
#             expected_dst = host_mac
#             label = "FPGA RAW loopback"

#         # --- payload format: marker + seq + user_payload ---
#         payload = spec.payload or b"hello-raw"
#         marker_prefix = marker + b":"
#         # we’ll include a 32-bit sequence to dedupe reliably (NIC loopback often double-captures)
#         # frame payload: b"CDCREW:" + seq(4B) + payload
#         def make_payload(seq: int) -> bytes:
#             return marker_prefix + struct.pack("!I", seq) + payload

#         # --- sniffer filters ---
#         # BPF narrows to our EtherType and frames destined to expected_dst (host_mac in both modes)
#         bpf = f"ether proto 0x9000 and ether dst {expected_dst}"

#         # lfilter ensures marker and expected src/dst match
#         def is_returned(p):
#             if not p.haslayer(Ether) or not p.haslayer(Raw):
#                 return False
#             eth = p[Ether]
#             raw = bytes(p[Raw].load)

#             if not raw.startswith(marker_prefix):
#                 return False

#             # strict src/dst match for the RETURNED frame
#             if eth.src.lower() != expected_src:
#                 return False
#             if eth.dst.lower() != expected_dst:
#                 return False

#             return True

#         print(
#             f"[RAW] iface={iface_query} mode={mode} host_mac={host_mac} "
#             f"tx={tx_src}->{tx_dst} expect={expected_src}->{expected_dst} "
#             f"count={count} verify_loopback={verify_loopback}"
#         )

#         # --- start sniffer first (race-safe) ---
#         sniffer = AsyncSniffer(
#             iface=conf.iface,
#             filter=bpf,
#             lfilter=is_returned,
#             store=True,
#             promisc=True,
#         )
#         sniffer.start()
#         time.sleep(arm_delay)

#         # --- send frames ---
#         for seq in range(count):
#             lab.send_raw(
#                 iface_query,
#                 dst_mac=tx_dst,
#                 src_mac=tx_src,
#                 payload=make_payload(seq),
#             )
#             time.sleep(inter_gap)

#         # --- stop and collect ---
#         pkts = sniffer.stop()

#         if not verify_loopback:
#             # if you ever want a "send-only" mode, this at least confirms we saw something relevant
#             return True, f"RAW send completed (sniffed {len(pkts)} matching frames)"

#         # --- count UNIQUE sequences returned (fixes 20/10 NIC duplicates cleanly) ---
#         seen = set()
#         for p in pkts:
#             raw = bytes(p[Raw].load)
#             # raw is b"CDCREW:" + 4B seq + payload
#             if len(raw) >= len(marker_prefix) + 4:
#                 seq = struct.unpack("!I", raw[len(marker_prefix):len(marker_prefix) + 4])[0]
#                 seen.add(seq)

#         captured = len(seen)

#         if captured >= count:
#             return True, f"{label} OK (captured {captured}/{count} unique marked frames)"
#         else:
#             return False, f"{label} FAIL (captured {captured}/{count} unique marked frames)"

#     return False, f"unsupported l3={l3}"
import os
import sys
import time
import json
import struct
import socket
from typing import Tuple, Dict, Any, List

from scapy.all import conf, AsyncSniffer
from scapy.layers.l2 import Ether
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
    # PAUSE FRAME INJECTION
    # -------------------------
    if l3 == "pause":
        pause_quanta = int(t.get("pause_quanta", 0xFFFF))
        count = int(t.get("count", 1))
        inter_gap = float(t.get("inter_gap", 0.05))

        host_mac = lab.get_iface_mac(conf.iface).lower()

        # 802.3x MAC Control Pause frame:
        # DA = 01:80:C2:00:00:01 (slow protocols multicast)
        # EtherType = 0x8808
        # Opcode = 0x0001, Quanta = 16-bit
        dst = "01:80:c2:00:00:01"
        opcode = 0x0001
        payload = struct.pack("!HH", opcode, pause_quanta) + (b"\x00" * 42)  # pad to >=46 bytes

        for _ in range(count):
            lab.send_raw(
                iface=conf.iface,
                dst_mac=dst,
                src_mac=host_mac,
                payload=payload,
                ether_type=0x8808,
                vlan=None,
            )
            time.sleep(inter_gap)

        return True, f"PAUSE frames sent (count={count}, quanta={pause_quanta}, iface={conf.iface})"

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
    timeout = float(t.get("timeout", 3))  # currently unused but kept for compatibility
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
        return raw_bytes.startswith(marker_prefix)

    print(
        f"[RAW] iface={conf.iface} host_mac={host_mac} tx={tx_src}->{tx_dst} "
        f"verify={verify_loopback} count={count} payload_len={payload_len} "
        f"dst_kind={dst_kind or 'mac/broadcast'} vlan={'on' if (vlan and vlan.get('enabled')) else 'off'} "
        f"expect={expect}"
    )

    # Capture setup:
    # Prefer ignoring outgoing copies at socket level so we don't need MAC-based heuristics.
    # If the kernel doesn't support it / call fails, we'll still likely see outgoing frames,
    # BUT the marker+seq uniqueness logic will still work in many cases.
    bpf = "ether proto 0x9000"
    listen_sock = conf.L2listen(iface=conf.iface, filter=bpf)

    try:
        pkt_ignore = getattr(socket, "PACKET_IGNORE_OUTGOING", 23)
        listen_sock.ins.setsockopt(socket.SOL_PACKET, pkt_ignore, 1)
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
            ts_sent = struct.unpack("!Q", raw_bytes[len(marker_prefix) + 4 : len(marker_prefix) + 12])[0]
            ts_now = int(time.time() * 1e6)
            rtts_us.append(max(0, ts_now - ts_sent))

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

