#!/usr/bin/env python
# import argparse, sys
# from scapy.all import (
#     conf, get_if_hwaddr, getmacbyip,
#     get_windows_if_list,  # Windows helper
#     Ether, IP, ICMP, UDP, Raw,
#     sendp, sr, sniff
# )
import argparse
from scapy.all import (
    conf, IFACES, get_if_hwaddr, getmacbyip,
    Ether, IP, ICMP, UDP, Raw,
    sendp, sr, sniff, show_interfaces
)
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

if __name__ == "__main__":
    main()
