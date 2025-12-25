# Verification_Compliance/tests/testcases/local_link_up.py
import threading, time, socket, sys, os
from scapy.all import conf
from scapy.layers.l2 import Ether
from scapy.packet import Raw
from tests.base import load_spec, TestSpec
from scapy.all import AsyncSniffer  
import struct


# Add repo root (Verification_Compliance) to sys.path so we can import scapy_lab_win.py
_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _REPO_ROOT not in sys.path:
    sys.path.append(_REPO_ROOT)

import scapy_framework as lab  # type: ignore[import]


def _udp_echo_server(bind_ip="127.0.0.1", port=12345, stop_after=5.0):
    def _run():
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.bind((bind_ip, port))
        s.settimeout(0.25)
        t0 = time.time()
        try:
            while time.time() - t0 < stop_after:
                try:
                    data, addr = s.recvfrom(65535)
                    if data:
                        s.sendto(data, addr)
                except socket.timeout:
                    pass
        finally:
            s.close()

    th = threading.Thread(target=_run, daemon=True)
    th.start()
    return th


def run(spec_path: str):
    spec: TestSpec = load_spec(spec_path)
    t = spec.test

    iface_query: str = t.get("iface", "ens1f0")
    l3: str = t.get("l3", "icmp").lower()
    dst_ip: str = t.get("dst_ip", "127.0.0.1")
    count: int = int(t.get("count", 3))
    timeout: int = int(t.get("timeout", 10))

    # Bind interface in Scapy
    conf.iface = lab.set_iface(iface_query)

    # ---- ICMP ----
    if l3 == "icmp":
        lab.send_ping(iface_query, dst_ip, count=count, timeout=timeout)
        lab.sniff_packets(
            iface_query,
            bpf="icmp",
            count=count,
            timeout=max(5, timeout + 2),
        )
        return True, "local ICMP ping + sniff ok"

    # ---- UDP (local echo on localhost) ----
    if l3 == "udp":
        udp_port = int(t.get("udp_port", 12345))
        echo = _udp_echo_server(dst_ip, udp_port, stop_after=5.0)

        sniffer = threading.Thread(
            target=lambda: lab.sniff_packets(
                iface_query,
                bpf="udp",
                count=count * 2,
                timeout=5,
            ),
            daemon=True,
        )
        sniffer.start()
        time.sleep(0.2)

        for _ in range(count):
            lab.send_udp(
                iface_query,
                dst_ip,
                dport=udp_port,
                payload=spec.payload or b"hello",
                timeout=timeout,
            )
            time.sleep(0.05)

        sniffer.join(timeout=6)
        echo.join(timeout=6)
        return True, "local UDP echo + sniff ok"

    # ---- RAW ETHERNET (FPGA/NIC test) ----
    if l3 == "raw":
        # --- read spec fields ---
        src_mac_cfg = t.get("src_mac")
        dst_mac_cfg = t.get("dst_mac")
        verify_loopback = bool(t.get("verify_loopback", False))
        mode = (t.get("loopback_mode") or "").lower().strip()  # "nic" or "peer"
        marker = (t.get("marker", "CDCREW")).encode()

        count = int(t.get("count", 5))
        timeout = int(t.get("timeout", 3))
        arm_delay = float(t.get("arm_delay", 0.2))
        inter_gap = float(t.get("inter_gap", 0.05))

        # --- ground truth: host interface MAC ---
        host_mac = lab.get_if_hwaddr(conf.iface).lower()

        # --- decide loopback mode (never infer if explicitly provided) ---
        dst_mac_l = (dst_mac_cfg.lower() if dst_mac_cfg else "")
        if not mode:
            # infer: if user is sending to self, treat as NIC loopback, otherwise peer
            mode = "nic" if (dst_mac_l == host_mac or not dst_mac_l) else "peer"

        # --- compute TX src/dst and expected RX src/dst ---
        if mode == "nic":
            # NIC self-loopback: send to self, expect src=self dst=self
            tx_src = host_mac
            tx_dst = host_mac
            expected_src = host_mac
            expected_dst = host_mac
            label = "NIC RAW loopback"
        else:
            # Peer/FPGA loopback: send to peer, expect return src=peer dst=host
            if not dst_mac_cfg:
                return False, "peer loopback requires dst_mac (peer/FPGA MAC) in spec"

            peer_mac = dst_mac_cfg.lower()
            tx_src = host_mac if not src_mac_cfg else src_mac_cfg.lower()
            tx_dst = peer_mac
            expected_src = peer_mac
            expected_dst = host_mac
            label = "FPGA RAW loopback"

        # --- payload format: marker + seq + user_payload ---
        payload = spec.payload or b"hello-raw"
        marker_prefix = marker + b":"
        # we’ll include a 32-bit sequence to dedupe reliably (NIC loopback often double-captures)
        # frame payload: b"CDCREW:" + seq(4B) + payload
        def make_payload(seq: int) -> bytes:
            return marker_prefix + struct.pack("!I", seq) + payload

        # --- sniffer filters ---
        # BPF narrows to our EtherType and frames destined to expected_dst (host_mac in both modes)
        bpf = f"ether proto 0x9000 and ether dst {expected_dst}"

        # lfilter ensures marker and expected src/dst match
        def is_returned(p):
            if not p.haslayer(Ether) or not p.haslayer(Raw):
                return False
            eth = p[Ether]
            raw = bytes(p[Raw].load)

            if not raw.startswith(marker_prefix):
                return False

            # strict src/dst match for the RETURNED frame
            if eth.src.lower() != expected_src:
                return False
            if eth.dst.lower() != expected_dst:
                return False

            return True

        print(
            f"[RAW] iface={iface_query} mode={mode} host_mac={host_mac} "
            f"tx={tx_src}->{tx_dst} expect={expected_src}->{expected_dst} "
            f"count={count} verify_loopback={verify_loopback}"
        )

        # --- start sniffer first (race-safe) ---
        sniffer = AsyncSniffer(
            iface=conf.iface,
            filter=bpf,
            lfilter=is_returned,
            store=True,
            promisc=True,
        )
        sniffer.start()
        time.sleep(arm_delay)

        # --- send frames ---
        for seq in range(count):
            lab.send_raw(
                iface_query,
                dst_mac=tx_dst,
                src_mac=tx_src,
                payload=make_payload(seq),
            )
            time.sleep(inter_gap)

        # --- stop and collect ---
        pkts = sniffer.stop()

        if not verify_loopback:
            # if you ever want a "send-only" mode, this at least confirms we saw something relevant
            return True, f"RAW send completed (sniffed {len(pkts)} matching frames)"

        # --- count UNIQUE sequences returned (fixes 20/10 NIC duplicates cleanly) ---
        seen = set()
        for p in pkts:
            raw = bytes(p[Raw].load)
            # raw is b"CDCREW:" + 4B seq + payload
            if len(raw) >= len(marker_prefix) + 4:
                seq = struct.unpack("!I", raw[len(marker_prefix):len(marker_prefix) + 4])[0]
                seen.add(seq)

        captured = len(seen)

        if captured >= count:
            return True, f"{label} OK (captured {captured}/{count} unique marked frames)"
        else:
            return False, f"{label} FAIL (captured {captured}/{count} unique marked frames)"

    return False, f"unsupported l3={l3}"
