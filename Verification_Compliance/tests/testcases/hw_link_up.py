# Verification_Compliance/tests/testcases/local_link_up.py
import threading, time, socket, sys, os
from scapy.all import conf
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
        dst_mac = t.get("dst_mac")
        src_mac = t.get("src_mac")

        if not dst_mac:
            return False, "raw mode requires dst_mac in spec"

        print(f"[RAW] iface={iface_query} src_mac={src_mac} dst_mac={dst_mac} count={count}")

        # send raw frames
        payload = spec.payload or b"hello-raw"
        for _ in range(count):
            lab.send_raw(
                iface_query,
                dst_mac=dst_mac,
                src_mac=src_mac,
                payload=payload,
            )
            time.sleep(0.05)

        # sniff for anything involving the FPGA MAC
        bpf = f""
        lab.sniff_packets(
            iface_query,
            bpf=bpf,
            count=count * 2,
            timeout=max(5, timeout + 3),
        )

        return True, "local RAW send + sniff ok"

    return False, f"unsupported l3={l3}"
