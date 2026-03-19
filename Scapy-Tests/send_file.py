from scapy.all import *
import struct

iface = "ens6"
ethertype = 0x88B5
chunk_size = 1400

with open("random.txt", "rb") as f:
    data = f.read()

for i in range(0, len(data), chunk_size):
    payload = struct.pack("!I", i) + data[i:i+chunk_size]
    pkt = Ether(dst="ff:ff:ff:ff:ff:ff", type=ethertype)/Raw(load=payload)
    sendp(pkt, iface=iface, verbose=False)

print("Sent", len(data), "bytes")
