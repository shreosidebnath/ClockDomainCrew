# Verification_Compliance/tests/testcases/local_link_up.py
import threading, time, socket, sys, os
from scapy.all import conf
from scapy.layers.l2 import Ether
from scapy.packet import Raw
from tests.base import load_spec, TestSpec


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
        from scapy.all import AsyncSniffer  # local import to avoid affecting other modes

        dst_mac = t.get("dst_mac")
        src_mac = t.get("src_mac")
        verify_loopback = bool(t.get("verify_loopback", False))

        if not src_mac:
            # default to NIC MAC if not provided
            try:
                src_mac = lab.get_if_hwaddr(conf.iface)
            except Exception:
                src_mac = None

        if not dst_mac:
            # for NIC loopback tests, dst_mac is usually your own MAC
            if verify_loopback and src_mac:
                dst_mac = src_mac
            else:
                return False, "raw mode requires dst_mac (or set verify_loopback: true with src_mac available)"

        count = int(t.get("count", 5))
        timeout = int(t.get("timeout", 3))

        print(
            f"[RAW] iface={iface_query} src_mac={src_mac} dst_mac={dst_mac} "
            f"count={count} verify_loopback={verify_loopback}"
        )

        payload = spec.payload or b"hello-raw"
        marker = t.get("marker", "CDCREW").encode()
        marker_prefix = marker + b":"

        # Put a marker in the payload so we can identify our own frames
        tx_payload = marker_prefix + payload

        # Tight BPF: only our test EtherType
        bpf = "ether proto 0x9000"

        # Start sniffer first to avoid race conditions
        sniffer = AsyncSniffer(
            iface=conf.iface,        # conf.iface already set earlier via lab.set_iface()
            filter=bpf,
            store=True,
            promisc=True,
        )
        sniffer.start()

        # Small arm delay helps on busy systems
        time.sleep(0.2)

        # Send frames
        for _ in range(count):
            lab.send_raw(
                iface_query,
                dst_mac=dst_mac,
                src_mac=src_mac,
                payload=tx_payload,
            )
            time.sleep(0.05)

        # Stop sniffer and collect packets
        pkts = sniffer.stop()

        # Count marked frames
        captured = 0
        for p in pkts:
            if not p.haslayer(Raw) or not p.haslayer(Ether):
                continue

            data = bytes(p[Raw].load)

            # Must contain our marker
            if not data.startswith(marker_prefix):
                continue
            # FPGA loopback check:
            # frame must come BACK to the host NIC
            if verify_loopback:
                if p[Ether].dst.lower() != src_mac.lower():
                    continue

            captured += 1

        if verify_loopback:
            if captured >= count:
                return True, f"NIC RAW loopback OK (captured {captured}/{count} marked frames)"
            return False, f"NIC RAW loopback FAIL (captured {captured}/{count} marked frames)"
        else:
            return True, f"RAW send + sniff completed (captured {captured} marked frames)"

    return False, f"unsupported l3={l3}"
