from scapy_framework import send_packet
from scapy.all import IP, ICMP
p = IP(dst="127.0.0.1", ihl=2, version=3)/ICMP()
# Inspect locally:
p.show(); p.hexdump()
# If you must send: do it only to a VM you control