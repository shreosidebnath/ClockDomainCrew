import argparse
import socket, threading, time, yaml, os

from scapy.all import (
    conf,
    sr,
    sniff,
    get_if_list,
    get_if_addr,
    get_if_hwaddr,
    sendp,
)

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
    for name in get_if_list():
        mac = None
        ip = None
        try:
            mac = get_if_hwaddr(name)
        except Exception:
            pass
        try:
            ip = get_if_addr(name)
        except Exception:
            pass
        guid = None
        print(f"- {name} | guid={guid} | mac={mac} | ip={ip}")

    if get_windows_if_list:
        print("\nWindows raw list (from scapy.arch.windows):")
        for i in get_windows_if_list():
            print(f"- {i.get('name')} | guid={i.get('guid')} | desc={i.get('description')}")


def set_iface(query: str) -> str:
    """Pick interface by substring match on name (case-insensitive)."""
    q = query.lower()
    for name in get_if_list():
        if q in name.lower():
            conf.iface = name
            return conf.iface
    raise SystemExit(
        f"[ERR] No interface match for: {query}\n"
        f"Tip: run `list-ifaces` to see exact names."
    )


def send_ping(iface: str, dst_ip: str, count: int = 1, timeout: int = 2):
    conf.iface = set_iface(iface)
    pkt = IP(dst=dst_ip) / ICMP()
    ans, unans = sr(pkt, retry=0, timeout=timeout, verbose=False)
    if ans:
        for snd, rcv in ans:
            print(f"[ICMP] reply from {rcv[IP].src}, ttl={rcv[IP].ttl}")
    else:
        print("[ICMP] sent (no replies observed via sr); use sniff to confirm")


def send_udp(
    iface: str,
    dst_ip: str,
    dport: int = 9999,
    payload: bytes = b"hello",
    timeout: int = 2,
):
    conf.iface = set_iface(iface)
    pkt = IP(dst=dst_ip) / UDP(dport=dport, sport=54321) / Raw(load=payload)
    sr(pkt, timeout=timeout, verbose=False)
    print(f"[UDP] sent to {dst_ip}:{dport} (check sniff output)")

def sniff_packets(iface: str, bpf: str = "", count: int = 5, timeout: int = 5):
    conf.iface = set_iface(iface)
    conf.sniff_promisc = True
    print(f"[SNIFF] iface='{conf.iface}' filter='{bpf}' count={count} timeout={timeout}s promisc=True")

    try:
        pkts = sniff(
            iface=conf.iface,
            filter=bpf if bpf else None,
            count=count,
            timeout=timeout,
            store=True,
            promisc=True,
        )
    except Exception as e:
        print(f"[SNIFF] ERROR: {type(e).__name__}: {e}")
        return []

    if not pkts:
        print("[SNIFF] no packets captured")
    else:
        for i, p in enumerate(pkts, 1):
            print(f"[{i}] {p.summary()}")
    return pkts


def selftest():
    """No-network unit sanity: build/serialize/reparse common packets."""
    print("[SELFTEST] start")
    vectors = [
        Ether(dst="ff:ff:ff:ff:ff:ff") / IP(dst="192.0.2.1") / ICMP(),
        IP(dst="198.51.100.7")
        / UDP(dport=9999, sport=12345)
        / Raw(load=b"hello-udp"),
        Ether(dst="00:11:22:33:44:55") / Raw(load=b"raw-ether"),
    ]
    for idx, pkt in enumerate(vectors, 1):
        raw_bytes = bytes(pkt)  # serialize
        pkt2 = Ether(raw_bytes) if raw_bytes[:1] != b"E" else IP(raw_bytes)
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


def send_raw(
    iface: str,
    dst_mac: str,
    src_mac: str | None = None,
    payload: bytes = b"hello-raw",
):
    """
    Send a single raw Ethernet frame: Ether(dst, src)/Raw(payload).
    If src_mac is None, use the NIC's hardware MAC.
    """
    conf.iface = set_iface(iface)
    if src_mac is None:
        try:
            src_mac = get_if_hwaddr(conf.iface)
        except Exception:
            src_mac = "00:00:00:00:00:00"

    frame = Ether(dst=dst_mac, src=src_mac) / Raw(load=payload)
    sendp(frame, iface=conf.iface, verbose=False)
    print(
        f"[RAW] sent {len(payload)}B frame: {src_mac} -> {dst_mac} on {conf.iface}"
    )


def main():
    ap = argparse.ArgumentParser(description="Scapy lab (Windows/Linux-friendly)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list-ifaces", help="list interfaces")

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
        send_udp(
            args.iface,
            args.dst_ip,
            args.dport,
            args.payload.encode(),
            args.timeout,
        )
    elif args.cmd == "sniff":
        sniff_packets(args.iface, args.bpf, args.count, args.timeout)


if __name__ == "__main__":
    main()
