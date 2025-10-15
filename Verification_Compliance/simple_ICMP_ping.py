from scapy_framework import build_icmp_ping, send_and_receive

resp = send_and_receive(build_icmp_ping("127.0.0.1"), timeout=1)

if resp:
    resp.show()