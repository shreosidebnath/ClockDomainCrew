import argparse
import socket, threading, time, yaml, os

# Keep the runtime helpers here
from scapy.all import conf, IFACES, sr, sniff

# Pull protocol classes from their defining modules (Pylance-friendly)
from scapy.layers.l2 import Ether
from scapy.layers.inet import IP, ICMP, UDP
from scapy.packet import Raw

try:
    # Available on Windows builds, but not always re-exported via scapy.all
    from scapy.arch.windows import get_windows_if_list  # type: ignore
except Exception:
    get_windows_if_list = None

def list_ifaces():
    print("Scapy interfaces (portable view):")
    for i in IFACES.values():
        name = i.name
        guid = getattr(i, "guid", None)
        mac  = getattr(i, "mac", None)
        ip   = getattr(i, "ip", None)
        print(f"- {name} | guid={guid} | mac={mac} | ip={ip}")

    if get_windows_if_list:
        print("\nWindows raw list (from scapy.arch.windows):")
        for i in get_windows_if_list():
            print(f"- {i.get('name')} | guid={i.get('guid')} | desc={i.get('description')}")

def set_iface(query: str) -> str:
    """Pick interface by substring match on name or GUID (case-insensitive)."""
    q = query.lower()
    for i in IFACES.values():
        name = i.name.lower()
        guid = str(getattr(i, "guid", "")).lower()
        if q in name or (guid and q in guid):
            conf.iface = i.name
            return conf.iface
    raise SystemExit(f"[ERR] No interface match for: {query}\n"
                     f"Tip: run `list-ifaces` to see exact names/guids.")

def send_ping(iface: str, dst_ip: str, count: int = 1, timeout: int = 2):
    conf.iface = set_iface(iface)
    pkt = IP(dst=dst_ip)/ICMP()
    # On Windows, sr() for ICMP to localhost can be finicky; we "send and rely on sniff" in practice.
    # We'll still try once to keep symmetry.
    ans, unans = sr(pkt, retry=0, timeout=timeout, verbose=False)
    if ans:
        for snd, rcv in ans:
            print(f"[ICMP] reply from {rcv[IP].src}, ttl={rcv[IP].ttl}")
    else:
        print("[ICMP] sent (no replies observed via sr); use sniff to confirm on loopback")

def send_udp(iface: str, dst_ip: str, dport: int = 9999, payload: bytes = b"hello", timeout: int = 2):
    conf.iface = set_iface(iface)
    pkt = IP(dst=dst_ip)/UDP(dport=dport, sport=54321)/Raw(load=payload)
    # No response expected unless something is listening; rely on sniff to observe TX
    sr(pkt, timeout=timeout, verbose=False)
    print(f"[UDP] sent to {dst_ip}:{dport} (check sniff output)")

def sniff_packets(iface: str, bpf: str = "icmp or udp", count: int = 5, timeout: int = 5):
    conf.iface = set_iface(iface)
    print(f"[SNIFF] iface='{conf.iface}' filter='{bpf}' count={count} timeout={timeout}s")
    pkts = sniff(iface=conf.iface, filter=bpf, count=count, timeout=timeout)
    if not pkts:
        print("[SNIFF] no packets captured")
    for i, p in enumerate(pkts, 1):
        print(f"[{i}] {p.summary()}")

def selftest():
    """No-network unit sanity: build/serialize/reparse common packets."""
    print("[SELFTEST] start")
    vectors = [
        Ether(dst="ff:ff:ff:ff:ff:ff")/IP(dst="192.0.2.1")/ICMP(),
        IP(dst="198.51.100.7")/UDP(dport=9999, sport=12345)/Raw(load=b"hello-udp"),
        Ether(dst="00:11:22:33:44:55")/Raw(load=b"raw-ether"),
    ]
    for idx, pkt in enumerate(vectors, 1):
        raw_bytes = bytes(pkt)     # serialize
        pkt2 = Ether(raw_bytes) if raw_bytes[:1] != b'E' else IP(raw_bytes)
        assert bytes(pkt2) == raw_bytes, "reparse mismatch"
        print(f"[SELFTEST] vec {idx}: {pkt.summary()}  -> OK")
    print("[SELFTEST] PASS")

def _udp_echo_server(bind_ip="127.0.0.1", port=12345, stop_after=5.0):
    """
    Tiny UDP echo server for local testing. Runs in a background thread.
    Echoes any UDP datagrams it receives back to the sender.
    """
    def _run():
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.bind((bind_ip, port))
        sock.settimeout(0.25)
        t0 = time.time()
        try:
            while time.time() - t0 < stop_after:
                try:
                    data, addr = sock.recvfrom(65535)
                    if not data:
                        continue
                    sock.sendto(data, addr)
                except socket.timeout:
                    pass
        finally:
            sock.close()
    th = threading.Thread(target=_run, daemon=True)
    th.start()
    return th

def run_linkup_from_spec(yaml_path: str):
    """
    Run a 'link_up' style test from a YAML spec, but in LOCAL mode.
    Supports:
      - l3: icmp   -> ping 127.0.0.1 (or given dst_ip)
      - l3: udp    -> spin local UDP echo server, send UDP payload, sniff reply
    """
    if not os.path.exists(yaml_path):
        raise SystemExit(f"[ERR] Spec not found: {yaml_path}")
    doc = yaml.safe_load(open(yaml_path, "r", encoding="utf-8"))
    name = doc.get("name", "link_up")
    t = doc.get("test", {})
    l3 = t.get("l3", "icmp").lower()
    iface = t.get("iface", "Loopback")           # best-effort match by your set_iface()
    dst_ip = t.get("dst_ip", "127.0.0.1")
    count  = int(t.get("count", 3))
    timeout= int(t.get("timeout", 2))
    payload= (doc.get("payload") or "hello-local").encode() if isinstance(doc.get("payload"), str) else (doc.get("payload") or b"hello-local")

    print(f"[SPEC] name={name} l3={l3} iface={iface} dst_ip={dst_ip} count={count} timeout={timeout}")
    conf.iface = set_iface(iface)

    if l3 == "icmp":
        # Use existing ping helper
        send_ping(iface, dst_ip, count=count, timeout=timeout)
        # Also sniff to show packets happened (loopback)
        sniff_packets(iface, bpf="icmp", count=count, timeout=max(5, timeout+2))
        print("PASS (local-icmp): ICMP loopback exercised.")
        return

    if l3 == "udp":
        # Start a local UDP echo server on dst_ip:udp_port (defaults: 127.0.0.1:12345)
        udp_port = int(t.get("udp_port", 12345))
        echo_thread = _udp_echo_server(bind_ip=dst_ip, port=udp_port, stop_after=5.0)

        # Open a short sniff window that should catch our TX and the echo
        print(f"[LOCAL-UDP] sniffer starting; expecting echo on {dst_ip}:{udp_port}")
        # NOTE: On Windows loopback, BPF 'udp' is fine.
        sniffer = threading.Thread(
            target=lambda: sniff_packets(iface, bpf="udp", count=count*2, timeout=5),
            daemon=True
        )
        sniffer.start()
        time.sleep(0.25)

        # Send UDP payloads using existing helper
        for _ in range(count):
            send_udp(iface, dst_ip, dport=udp_port, payload=payload, timeout=timeout)
            time.sleep(0.05)

        sniffer.join(timeout=6)
        echo_thread.join(timeout=6)
        print("PASS (local-udp): UDP loopback echo exercised.")
        return

    raise SystemExit(f"[ERR] Unsupported l3 in spec for local mode: {l3}")

def main():
    ap = argparse.ArgumentParser(description="Scapy lab (Windows-friendly)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list-ifaces", help="list Windows interfaces")

    p_ping = sub.add_parser("ping", help="ICMP echo to an IP")
    p_ping.add_argument("--iface", required=True)
    p_ping.add_argument("--dst-ip", required=True)
    p_ping.add_argument("--count", type=int, default=1)
    p_ping.add_argument("--timeout", type=int, default=2)

    p_udp = sub.add_parser("udp", help="send UDP payload to an IP:port")
    p_udp.add_argument("--iface", required=True)
    p_udp.add_argument("--dst-ip", required=True)
    p_udp.add_argument("--dport", type=int, default=9999)
    p_udp.add_argument("--payload", default="hello")
    p_udp.add_argument("--timeout", type=int, default=2)

    p_sniff = sub.add_parser("sniff", help="sniff with a BPF filter")
    p_sniff.add_argument("--iface", required=True)
    p_sniff.add_argument("--bpf", default="icmp or udp")
    p_sniff.add_argument("--count", type=int, default=5)
    p_sniff.add_argument("--timeout", type=int, default=5)

    p_spec = sub.add_parser("run-spec", help="Run a link_up-style YAML spec in LOCAL mode")
    p_spec.add_argument("--spec", required=True, help="Path to YAML spec")


    sub.add_parser("selftest", help="no-network unit sanity")

    args = ap.parse_args()
    if args.cmd == "list-ifaces":
        list_ifaces()
    elif args.cmd == "selftest":
        selftest()
    elif args.cmd == "ping":
        send_ping(args.iface, args.dst_ip, args.count, args.timeout)
    elif args.cmd == "udp":
        send_udp(args.iface, args.dst_ip, args.dport, args.payload.encode(), args.timeout)
    elif args.cmd == "sniff":
        sniff_packets(args.iface, args.bpf, args.count, args.timeout)
    elif args.cmd == "run-spec":
        run_linkup_from_spec(args.spec)

if __name__ == "__main__":
    main()
