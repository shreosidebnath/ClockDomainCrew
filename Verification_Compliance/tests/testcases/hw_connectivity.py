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
import struct
from typing import Tuple

from scapy.all import conf, AsyncSniffer
from scapy.layers.l2 import Ether
from scapy.packet import Raw

from tests.base import load_spec, TestSpec

# Add repo root so we can import scapy_framework.py
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _REPO_ROOT not in sys.path:
    sys.path.append(_REPO_ROOT)

import scapy_framework as lab  # type: ignore[import]


def run(spec_path: str) -> Tuple[bool, str]:
    spec: TestSpec = load_spec(spec_path)
    t = spec.test

    iface_query: str = t.get("iface", "ens1f0")
    l3: str = (t.get("l3") or "raw").lower().strip()

    # Bind interface in Scapy (and normalize iface name)
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
    # RAW LOOPBACK (NIC↔FPGA)
    # -------------------------
    if l3 == "raw":
        src_mac_cfg = t.get("src_mac")
        dst_mac_cfg = t.get("dst_mac")  # optional; if omitted we broadcast + learn peer
        verify_loopback = bool(t.get("verify_loopback", False))
        marker = (t.get("marker", "CDCREW")).encode()

        count = int(t.get("count", 5))
        timeout = float(t.get("timeout", 3))
        arm_delay = float(t.get("arm_delay", 0.2))
        inter_gap = float(t.get("inter_gap", 0.05))

        host_mac = lab.get_iface_mac(conf.iface).lower()
        tx_src = host_mac if not src_mac_cfg else str(src_mac_cfg).lower()

        # Destination behavior:
        # - if dst_mac provided: send to that MAC (strict peer matching)
        # - else: send broadcast and learn the peer MAC from first returned marked frame
        dst_mac_norm = (str(dst_mac_cfg).strip().lower() if dst_mac_cfg else "")
        if dst_mac_norm:
            tx_dst = dst_mac_norm
            expected_src_strict = dst_mac_norm
        else:
            tx_dst = "ff:ff:ff:ff:ff:ff"
            expected_src_strict = ""

        expected_dst = host_mac
        marker_prefix = marker + b":"
        payload = spec.payload or b"hello-raw"

        def make_payload(seq: int) -> bytes:
            return marker_prefix + struct.pack("!I", seq) + payload

        # BPF: only our custom EtherType, and destined either to us or broadcast
        bpf = f"ether proto 0x9000 and (ether dst {expected_dst} or ether dst ff:ff:ff:ff:ff:ff)"

        learned_peer_mac = {"mac": ""}

        def is_returned(p) -> bool:
            if not p.haslayer(Ether) or not p.haslayer(Raw):
                return False

            eth = p[Ether]
            raw_bytes = bytes(p[Raw].load)

            if not raw_bytes.startswith(marker_prefix):
                return False

            # IMPORTANT: if verifying loopback, ignore our own TX frames
            if verify_loopback and eth.src.lower() == host_mac:
                return False

            # Returned frame must be for us (or broadcast in some setups)
            if eth.dst.lower() not in (expected_dst, "ff:ff:ff:ff:ff:ff"):
                return False

            src_l = eth.src.lower()

            # If strict peer MAC is known, enforce it
            if expected_src_strict:
                return src_l == expected_src_strict

            # Otherwise learn first non-host source MAC, then enforce it
            if not learned_peer_mac["mac"]:
                if src_l != host_mac:
                    learned_peer_mac["mac"] = src_l
                    return True
                return False

            return src_l == learned_peer_mac["mac"]

        print(
            f"[RAW] iface={conf.iface} host_mac={host_mac} tx={tx_src}->{tx_dst} "
            f"verify_loopback={verify_loopback} count={count}"
        )

        sniffer = AsyncSniffer(
            iface=conf.iface,
            filter=bpf,
            lfilter=is_returned,
            store=True,
            promisc=True,
        )
        sniffer.start()
        time.sleep(arm_delay)

        for seq in range(count):
            lab.send_raw(
                iface=conf.iface,
                dst_mac=tx_dst,
                src_mac=tx_src,
                payload=make_payload(seq),
            )
            time.sleep(inter_gap)

        # Stop and collect
        pkts = sniffer.stop() or []  # fixes Optional Iterable / None typing

        if not verify_loopback:
            return True, f"RAW send completed (sniffed {len(pkts)} matching frames)"

        # Count UNIQUE sequences returned (dedupe duplicates cleanly)
        seen = set()
        for p in pkts:
            raw_bytes = bytes(p[Raw].load)
            if len(raw_bytes) >= len(marker_prefix) + 4:
                seq = struct.unpack("!I", raw_bytes[len(marker_prefix) : len(marker_prefix) + 4])[0]
                seen.add(seq)

        captured = len(seen)
        peer_mac_final = learned_peer_mac["mac"] or expected_src_strict or "unknown"

        if captured >= count:
            return True, f"RAW loopback OK (peer_mac={peer_mac_final}, {captured}/{count} unique frames)"
        return False, f"RAW loopback FAIL (peer_mac={peer_mac_final}, {captured}/{count} unique frames)"

    return False, f"unsupported l3={l3} (expected 'link' or 'raw')"
