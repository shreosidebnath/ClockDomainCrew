import argparse, sys
from .testcases.hw_connectivity import run as hw_run
from tests.base import load_spec

# DISPATCH = {
#     ("local", "link_up"): hw_link_run,
#     ("hw",    "link_up"): hw_link_run,  # for now, hw uses the same runner
    
# }

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec", required=True)
    args = ap.parse_args()

    # ts = load_spec(args.spec)
    # test_type = ts.test.get("type", "link_up")
    # key = (ts.backend, test_type)

    # if key not in DISPATCH:
    #     print(f"Unknown backend/test: {key}", file=sys.stderr)
    #     sys.exit(2)

    ts = load_spec(args.spec)
    l3 = (ts.test.get("l3") or "icmp").lower()
    backend = ts.backend

    ok, msg = hw_run(args.spec)
    print(("PASS" if ok else "FAIL") + " - " + msg)
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()