import os
import time
import subprocess
from typing import Tuple, Optional, Dict, Any

from scapy.all import conf, get_if_list, get_if_hwaddr, sendp
from scapy.layers.l2 import Ether, Dot1Q
from scapy.packet import Raw


def set_iface(query: str) -> str:
    """
    Pick interface by substring match on name (case-insensitive).
    Returns the actual interface name used by Scapy.
    """
    q = query.lower().strip()
    for name in get_if_list():
        if q == name.lower() or q in name.lower():
            conf.iface = name
            return conf.iface
    raise SystemExit(
        f"[ERR] No interface match for: {query}\n"
        f"Tip: print(get_if_list()) to see exact names."
    )


def get_iface_mac(iface: str) -> str:
    """Return MAC address for an interface (lowercase)."""
    return get_if_hwaddr(iface).lower()


def check_link(iface: str, timeout: float = 2.0) -> Tuple[bool, str]:
    """
    Linux link/carrier check.
    Passes if carrier=1. Useful for: "is DAC/cable plugged and link negotiated?"
    """
    carrier_path = f"/sys/class/net/{iface}/carrier"
    oper_path = f"/sys/class/net/{iface}/operstate"

    if not os.path.exists(carrier_path):
        return False, f"{iface}: /sys/class/net entry missing (not Linux or iface invalid)"

    t0 = time.time()
    last = ""
    while time.time() - t0 < timeout:
        try:
            with open(oper_path, "r", encoding="utf-8") as f:
                oper = f.read().strip()
            with open(carrier_path, "r", encoding="utf-8") as f:
                carrier = f.read().strip()
        except Exception as e:
            return False, f"{iface}: error reading link state: {type(e).__name__}: {e}"

        last = f"operstate={oper}, carrier={carrier}"
        if carrier == "1":
            speed = _try_get_speed(iface)
            return True, f"{last}, speed={speed}"

        time.sleep(0.1)

    speed = _try_get_speed(iface)
    return False, f"{last}, speed={speed} (cable/DAC likely unplugged or link down)"


def _try_get_speed(iface: str) -> str:
    """Best-effort speed read via ethtool (Linux)."""
    try:
        out = subprocess.check_output(["ethtool", iface], text=True, stderr=subprocess.DEVNULL)
        for line in out.splitlines():
            if "Speed:" in line:
                return line.split("Speed:", 1)[1].strip()
    except Exception:
        pass
    return "unknown"


def send_raw(
    iface: str,
    dst_mac: str,
    payload: bytes,
    src_mac: Optional[str] = None,
    ether_type: int = 0x9000,
    vlan: Optional[Dict[str, Any]] = None,
):
    """
    Send a raw Ethernet frame:
      - Ether(dst, src, type=ether_type)
      - Optional Dot1Q(vlan=vid, prio=pcp)
      - Raw(payload)

    NOTE: Caller should have already set iface via set_iface().
    """
    conf.iface = iface

    if src_mac is None:
        try:
            src_mac = get_if_hwaddr(conf.iface)
        except Exception:
            src_mac = "00:00:00:00:00:00"

    if isinstance(vlan, dict) and bool(vlan.get("enabled", False)):
        vid = int(vlan.get("vid", 1))
        pcp = int(vlan.get("pcp", 0))
        frame = (
            Ether(dst=dst_mac, src=src_mac, type=0x8100)
            / Dot1Q(vlan=vid, prio=pcp, type=ether_type)
            / Raw(load=payload)
        )
    else:
        frame = Ether(dst=dst_mac, src=src_mac, type=ether_type) / Raw(load=payload)

    sendp(frame, iface=conf.iface, verbose=False)

