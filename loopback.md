CDC

## How to run the yml file for Scapy
Run the following command in the project's main directory.
```bash
sudo python3 -m tests.runner --spec tests/specs/02_raw_fpga.yml
```


## Configuring the IP address on FPGA board
It is important to set the IP address of eth0 or eth1 so that it matches the first three digits of the IP address of eno2np1 
For example, if the IP address of eno2np1 is 192.168.10.1, the IP of eth0 should be 192.168.10.x (where x can be any number between 0 and 255)

Run the following command
```bash
sudo ifconfig eth0 192.168.10.10 up
```

## Enabling internet on FPGA board and Installing Scapy dependencies (Ubuntu environment)
This is only for accessing internet. We used it to install scapy and pip

Internet on the FPGA board can be configured by "bridging" the PC's Wi-Fi to the Ethernet port (eno2np1) so the FPGA can use the PC's internet.

Run this on the PC terminal
- `wls5` is the internet interface on the PC
```bash
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A POSTROUTING -o wls5 -j MASQUERADE
sudo iptables -A FORWARD -i eno2np1 -o wls5 -j ACCEPT
sudo iptables -A FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
```

Run this on the FPGA terminal
- `192.168.10.1` is the IP address of eno2np1 and `eth0` is the ethernet interface on the FPGA side
```bash
sudo ip route add default via 192.168.10.1 dev eth0
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
```

Test the connection with the following command
```bash
ping -c 3 google.com
```
Successfully receiving packets from google.com verifies that the internet is working.

Run the following command to install Scapy on the FPGA
```bash
sudo apt install python3-scapy
```

## Scapy Logic for echoing packets back (FPGA side)
By default, the FPGA with Ubuntu running on it does not send packets back. It will simply receive the packets and drop it, making the fpga loopback fail.

To ensure that packets are echoed back, create a python file on Ubuntu on FPGA called `reflect.py`
```python
from scapy.all import *

def reflect_packet(pkt):
    if b"CDCREW" in raw(pkt):
        print(f"Captured test packet! Reflecting to {pkt[Ether].src}")
        # Swap MAC addresses to send it back
        reply = Ether(src=pkt[Ether].dst, dst=pkt[Ether].src) / pkt.payload
        sendp(reply, iface="eth0")

print("Starting FPGA Reflector on eth0... Press Ctrl+C to stop.")
sniff(iface="eth0", prn=reflect_packet, filter="ether dst 00:0a:35:24:7f:a3")
```

Save the file and run `sudo python3 reflect.py`. Note that Scapy must be installed on Ubuntu on the FPGA side. See previous section for help

## Connecting to the FPGA Linux shell (Ubuntu image)

Once the FPGA is powered on, the power source is connected, the ethernet cable is connected, and the JTAG is connected to the USB port, run the following:

```bash
sudo putty /dev/ttyUSB1 -serial -sercfg 115200,8,n,1,N
```

The terminal should prompt for a login.
Log in as `Ubuntu` and use the password `Ubuntu`.