import time
from scapy_framework import AsyncCapture, save_packets
ac = AsyncCapture(iface="eth0", filter="icmp") # TODO: fix iface=""
ac.start()
# ... do some sends or wait
time.sleep(5)
pkts = ac.stop()
save_packets(pkts, "capture.pcap")