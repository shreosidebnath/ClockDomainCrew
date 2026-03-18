# FPGA Operations and Supporting Processes

This document keeps the operational board/process notes that are related to running tests, but are not part of the Scapy framework architecture itself.

## Connecting to the FPGA Linux shell (Ubuntu image)

Once the FPGA is powered on, the power source is connected, the Ethernet cable is connected, and JTAG is connected to USB, run:

```bash
sudo putty /dev/ttyUSB1 -serial -sercfg 115200,8,n,1,N
```

Log in as:
- username: `ubuntu`
- password: `ubuntu`

## Configuring the IP address on FPGA board

Set FPGA `eth0`/`eth1` to the same subnet as the host NIC connected to FPGA.

Example:
- host NIC (`eno2np1`) = `192.168.10.1`
- FPGA `eth0` can be `192.168.10.10`

Command on FPGA:
```bash
sudo ifconfig eth0 192.168.10.10 up
```

## Enabling internet on FPGA board and installing dependencies

This section is only needed when you want internet access from FPGA Ubuntu (for package installs, etc.).

### On the host PC (bridge/forward internet)

`wls5` below is an example Wi-Fi interface; use your actual interface name.

```bash
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A POSTROUTING -o wls5 -j MASQUERADE
sudo iptables -A FORWARD -i eno2np1 -o wls5 -j ACCEPT
sudo iptables -A FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
```

### On the FPGA Ubuntu shell

Assuming host NIC is `192.168.10.1` and FPGA interface is `eth0`:

```bash
sudo ip route add default via 192.168.10.1 dev eth0
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
```

Validate:
```bash
ping -c 3 google.com
```

Install Scapy:
```bash
sudo apt install python3-scapy
```

## Scapy packet reflection on FPGA Linux (application-level, optional/legacy)

If your FPGA software stack does not already return packets, an application-level reflector can be used.

Create `reflect.py` on the FPGA:

```python
from scapy.all import *

def reflect_packet(pkt):
    if b"CDCREW" in raw(pkt):
        print(f"Captured test packet! Reflecting to {pkt[Ether].src}")
        reply = Ether(src=pkt[Ether].dst, dst=pkt[Ether].src) / pkt.payload
        sendp(reply, iface="eth0")

print("Starting FPGA Reflector on eth0... Press Ctrl+C to stop.")
sniff(iface="eth0", prn=reflect_packet, filter="ether dst 00:0a:35:24:7f:a3")
```

Run:
```bash
sudo python3 reflect.py
```

## File transfer to FPGA via SCP

If FPGA is reachable at `192.168.10.10`:

```bash
scp "file directory" ubuntu@192.168.10.10:/home/ubuntu/
```

## Loopback validation using file transfer payloads (custom workflow)

For setups where only PL logic is available and Linux-based packet handling is not used, you can validate loopback by sending known payload chunks and comparing RX/TX captures.

### Terminal A (capture TX)

```bash
sudo tcpdump -i ens6 -e -n -Q out ether proto 0x88b5 -w tx.pcap
```

### Terminal B (send packets)

Run:
```bash
sudo python3 send_file.py
```

Example `send_file.py`:

```python
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
```

Stop capture, then extract payload bytes:

```bash
tcpdump -r tx.pcap -XX | grep -oP '(?<=0x0030: ).*' | tr -d ' ' | xxd -r -p > tx.bin
```

### Repeat for RX (looped-back packets)

Capture RX:
```bash
sudo tcpdump -i ens6 -e -n -Q in ether proto 0x88b5 -w rx.pcap
```

Send again from Terminal B:
```bash
sudo python3 send_file.py
```

Stop capture, then extract:
```bash
tcpdump -r rx.pcap -XX | grep -oP '(?<=0x0030: ).*' | tr -d ' ' | xxd -r -p > rx.bin
```

Compare hashes:
```bash
sha256sum rx.bin tx.bin
```

## FWUEN mode (QSPI image upload) networking notes

Configure host NIC connected to FPGA:

```bash
sudo ip addr flush dev eno2np1
sudo ip addr add 192.168.0.10/24 dev eno2np1
sudo ip link set dev eno2np1 up
ip addr show dev eno2np1
ip route get 192.168.0.111
```

If route resolution still fails:

```bash
sudo ip route add 192.168.0.0/24 dev eno2np1
```
