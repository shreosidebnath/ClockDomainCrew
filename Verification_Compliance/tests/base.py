from dataclasses import dataclass
from typing import Any, Dict
import yaml, time

@dataclass
class TestSpec:
    name: str
    backend: str
    test: Dict[str, Any]
    payload: bytes

def load_spec(path: str) -> TestSpec:
    with open(path, "r", encoding="utf-8") as f:
        doc = yaml.safe_load(f) or {}
    payload = doc.get("payload", b"")
    if isinstance(payload, str):
        payload = payload.encode()
    elif payload is None:
        payload = b""
    return TestSpec(
        name=doc.get("name","unnamed"),
        backend=doc.get("backend","local"),
        test=doc.get("test",{}),
        payload=payload
    )

def now_us() -> int:
    return int(time.time()*1e6)
