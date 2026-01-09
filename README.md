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

## Enabling internet on FPGA board (Ubuntu environment)
This is only for accessing internet. We used it to install scapy and pip
Run this on the PC terminal
```bash
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A POSTROUTING -o wls5 -j MASQUERADE
sudo iptables -A FORWARD -i eno2np1 -o wls5 -j ACCEPT
sudo iptables -A FORWARD -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
```

Run this on the FPGA terminal
```bash
sudo ip route add default via 192.168.10.1 dev eth0
echo "nameserver 8.8.8.8" | sudo tee /etc/resolv.conf
```

Test the connection with the following command
```bash
ping -c 3 google.com
```