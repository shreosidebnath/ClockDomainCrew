"""
scapy_framework.py
Small helper wrapper around Scapy for basic send/receive/sniff workflows.

Usage examples at bottom (if __name__ == "__main__":).
Run in a safe lab environment only.
Requires scapy installed (e.g., pip install scapy).
"""

from scapy.all import (
    IP, IPv6, ICMP, TCP, UDP,
    send, sendp, sr1, sr, sniff, AsyncSniffer,
    conf, wrpcap
)
import logging
import threading
from typing import Optional, Callable, List
import time

# Configure logging
logger = logging.getLogger("scapy_framework")
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s: %(message)s"))
logger.addHandler(handler)
logger.setLevel(logging.INFO)

# Quiet Scapy verbosity unless explicitly requested
conf.verb = 0


def set_iface(iface: Optional[str]):
    """Set scapy default interface if provided."""
    if iface:
        conf.iface = iface
        logger.debug(f"Using interface: {iface}")


def send_packet(pkt, iface: Optional[str] = None, count: int = 1, interval: float = 0.0, layer2: bool = False):
    """
    Send a packet.
    - layer2=False uses send() (IP-layer). For raw Ethernet frames use sendp() (layer2=True).
    """
    set_iface(iface)
    logger.info(f"Sending packet: {pkt.summary()} (count={count}, iface={conf.iface})")
    try:
        if layer2:
            sendp(pkt, iface=conf.iface, count=count, inter=interval, verbose=False)
        else:
            send(pkt, iface=conf.iface, count=count, inter=interval, verbose=False)
    except PermissionError:
        logger.error("Permission error: you must run as root/admin to send raw packets.")


def send_and_receive(pkt, timeout: float = 2.0, iface: Optional[str] = None, retry: int = 0):
    """
    Send packet and wait for 1 response (sr1). Returns the received packet or None.
    Useful for ICMP pings, TCP SYN->SYN/ACK responses, etc.
    """
    set_iface(iface)
    for attempt in range(1 + retry):
        logger.info(f"sr1: sending {pkt.summary()} (attempt {attempt + 1}/{1 + retry})")
        try:
            resp = sr1(pkt, timeout=timeout, iface=conf.iface, verbose=False)
            if resp is not None:
                logger.info(f"Received response: {resp.summary()}")
                return resp
            elif attempt < retry:
                logger.debug("No response — retrying")
        except PermissionError:
            logger.error("Permission error: you must run as root/admin to send/receive raw packets.")
            return None
    logger.info("No response received.")
    return None


def send_and_receive_mult(pkt_list: List, timeout: float = 3.0, iface: Optional[str] = None):
    """
    Send multiple packets and collect responses (sr). Returns (answered, unanswered).
    """
    set_iface(iface)
    logger.info(f"sr: sending {len(pkt_list)} packets")
    try:
        ans, unans = sr(pkt_list, timeout=timeout, iface=conf.iface, verbose=False)
        logger.info(f"Answered: {len(ans)}, Unanswered: {len(unans)}")
        return ans, unans
    except PermissionError:
        logger.error("Permission error: you must run as root/admin to send/receive raw packets.")
        return None, None


def sniff_packets(
    count: int = 0,
    timeout: Optional[float] = None,
    iface: Optional[str] = None,
    lfilter: Optional[Callable] = None,
    prn: Optional[Callable] = None,
    store: bool = False
):
    """
    Blocking sniff. Returns list of captured packets (or a Scapy PacketList).
    - count=0 => sniff forever until timeout
    - lfilter is a Python function pkt -> bool applied before storing/calling prn
    - prn is a callback called for each packet
    """
    set_iface(iface)
    logger.info(f"Sniffing on iface={conf.iface} count={count} timeout={timeout}")
    try:
        pkts = sniff(count=count, timeout=timeout, iface=conf.iface, lfilter=lfilter, prn=prn, store=store)
        logger.info(f"Sniff finished, captured {len(pkts)} packets")
        return pkts
    except PermissionError:
        logger.error("Permission error: you must run as root/admin to sniff packets.")
        return None


class AsyncCapture:
    """Helper to run sniff in background using AsyncSniffer (start/stop)."""

    def __init__(self, iface: Optional[str] = None, filter: Optional[str] = None, prn: Optional[Callable] = None):
        set_iface(iface)
        self.sniffer = AsyncSniffer(iface=conf.iface, filter=filter, prn=prn, store=True)
        logger.debug("AsyncCapture created")

    def start(self):
        logger.info("Starting async sniffer")
        self.sniffer.start()

    def stop(self, timeout: Optional[float] = None):
        logger.info("Stopping async sniffer")
        self.sniffer.stop()
        # give scapy time to flush
        time.sleep(0.1)
        return self.sniffer.results


def save_packets(pkts, filename: str):
    """Save PacketList to pcap file."""
    if not pkts:
        logger.warning("No packets to save.")
        return
    logger.info(f"Writing {len(pkts)} packets to {filename}")
    wrpcap(filename, pkts)


# -------------------------
# Example helper builders
# -------------------------
def build_icmp_ping(dst_ip: str, payload: bytes = b"hello", ttl: int = 64):
    """Return a simple ICMP echo request."""
    return IP(dst=dst_ip, ttl=ttl) / ICMP() / payload


def build_tcp_syn(dst_ip: str, dport: int = 80, sport: int = 12345, seq: int = 1000):
    """Return a TCP SYN packet (IP/TCP)."""
    return IP(dst=dst_ip) / TCP(dport=dport, sport=sport, flags="S", seq=seq)


# -------------------------
# Example command-line style usage
# -------------------------
if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="scapy_framework quick demo (lab only)")
    parser.add_argument("--iface", help="Interface to use", default=None)
    parser.add_argument("--ping", help="Send ICMP ping to target", default=None)
    parser.add_argument("--sniff", help="Sniff for 10s", action="store_true")
    parser.add_argument("--pcap", help="Save sniff to PCAP filename", default=None)
    args = parser.parse_args()

    if args.iface:
        set_iface(args.iface)

    if args.ping:
        pkt = build_icmp_ping(args.ping)
        resp = send_and_receive(pkt, timeout=2, iface=args.iface)
        if resp:
            resp.show()
        else:
            logger.info("No reply (ICMP or blocked)")

    if args.sniff:
        # simple demo: sniff 10 seconds and optionally write to pcap
        captured = sniff_packets(timeout=10, iface=args.iface, prn=lambda p: logger.info(p.summary()), store=True)
        if args.pcap and captured:
            save_packets(captured, args.pcap)
