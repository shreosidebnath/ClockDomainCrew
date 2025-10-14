import os
import cocotb
from cocotb.clock import Clock
from cocotb.triggers import RisingEdge
from scapy.all import Ether, IP, ICMP

CLK_NS = 6.4  # ~156.25 MHz

@cocotb.test()
async def link_up_sim(dut):
    # clock + reset
    cocotb.start_soon(Clock(dut.clk156, CLK_NS, units="ns").start())
    dut.resetn.value = 0
    for _ in range(10): await RisingEdge(dut.clk156)
    dut.resetn.value = 1
    for _ in range(10): await RisingEdge(dut.clk156)

    # tiny 2-beat payload test using the loopback stub
    pkt = Ether(dst="02:ca:fe:00:00:01", src="02:ca:fe:00:00:02")/IP(dst="192.168.100.2")/ICMP()/b"hello10g"
    data = bytes(pkt)

    # naive 64-bit AXIS send (pack 8 bytes per beat)
    async def axis_send(payload: bytes):
        i = 0
        while i < len(payload):
            chunk = payload[i:i+8]
            keep  = (1 << len(chunk)) - 1
            # pack as {b7..b0}; flip if your DUT needs different endianness
            word = int.from_bytes(chunk, "big")
            dut.tx_axis_tdata.value  = word
            dut.tx_axis_tkeep.value  = keep
            dut.tx_axis_tlast.value  = int(i+8 >= len(payload))
            dut.tx_axis_tvalid.value = 1
            # wait handshake
            while not int(dut.tx_axis_tready.value):
                await RisingEdge(dut.clk156)
            await RisingEdge(dut.clk156)
            i += 8
        dut.tx_axis_tvalid.value = 0
        dut.tx_axis_tlast.value  = 0

    # naive receive until tlast
    async def axis_recv():
        out = bytearray()
        dut.rx_axis_tready.value = 1
        for _ in range(5000):
            await RisingEdge(dut.clk156)
            if int(dut.rx_axis_tvalid.value):
                word = int(dut.rx_axis_tdata.value)
                keep = int(dut.rx_axis_tkeep.value)
                raw  = word.to_bytes(8, "big")
                used = keep.bit_count()
                out.extend(raw[:used])
                if int(dut.rx_axis_tlast.value):
                    break
        return bytes(out)

    await axis_send(data)
    rx = await axis_recv()
    assert rx, "No RX bytes from loopback"
    got = Ether(rx)
    assert got.haslayer(ICMP), f"Unexpected packet: {got.summary()}"
