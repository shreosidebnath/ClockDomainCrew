import argparse
import sys

from tests.base import load_spec
from tests.testcases.hw_connectivity import run as hw_run


ALLOWED_L3 = {"link", "raw"}
ALLOWED_TYPES = {"connectivity", "loopback"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True)
    args = ap.parse_args()

    ts = load_spec(args.spec)

    if ts.backend != "hw":
        print(f"FAIL - unsupported backend '{ts.backend}' (this repo is HW-only)", file=sys.stderr)
        sys.exit(2)

    test_type = str(ts.test.get("type", "connectivity")).lower().strip()
    l3 = str(ts.test.get("l3", "")).lower().strip()

    if test_type not in ALLOWED_TYPES:
        print(f"FAIL - unsupported test.type='{test_type}' (allowed: {sorted(ALLOWED_TYPES)})", file=sys.stderr)
        sys.exit(2)

    if l3 not in ALLOWED_L3:
        print(f"FAIL - unsupported test.l3='{l3}' (allowed: {sorted(ALLOWED_L3)})", file=sys.stderr)
        sys.exit(2)

    ok, msg = hw_run(args.spec)
    print(("PASS" if ok else "FAIL") + " - " + msg)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
