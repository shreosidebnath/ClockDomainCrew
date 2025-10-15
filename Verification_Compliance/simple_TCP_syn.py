from scapy_framework import build_tcp_syn, send_and_receive
pkt = build_tcp_syn("192.168.1.10", dport=22)
resp = send_and_receive(pkt, timeout=2)

if resp:
    resp.show()   # might be SYN/ACK or RST