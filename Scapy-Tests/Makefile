# Scapy-Tests Makefile
# Examples:
#   make 01
#   make 01 03 15
#   make run SPECS=01,02,03
#   make all
# Optional:
#   make 02 IFACE=ens6

PY     ?= python3
SUDO   ?= sudo
RUNNER := -m tests.runner
IFACE  ?= ens6

SPECDIR := tests/specs

# Map numbers -> spec files (keep these in sync with your filenames)
SPEC_01 := $(SPECDIR)/01_connectivity.yml
SPEC_02 := $(SPECDIR)/02_nic_loopback.yml
SPEC_03 := $(SPECDIR)/03_min_frame_payload.yml
SPEC_04 := $(SPECDIR)/04_standard_mtu_payload.yml
SPEC_05 := $(SPECDIR)/05_jumbo_payload.yml
SPEC_06 := $(SPECDIR)/06_untagged_baseline.yml
SPEC_07 := $(SPECDIR)/07_vlan_tagged.yml
SPEC_08 := $(SPECDIR)/08_vlan_pcp.yml
SPEC_09 := $(SPECDIR)/09_broadcast_dest.yml
SPEC_10 := $(SPECDIR)/10_multicast_dest.yml
SPEC_11 := $(SPECDIR)/11_pause_frames.yml
SPEC_12 := $(SPECDIR)/12_undersize.yml
SPEC_13 := $(SPECDIR)/13_oversize.yml
SPEC_14 := $(SPECDIR)/14_funct_through.yml
SPEC_15 := $(SPECDIR)/15_latency.yml

ALL_SPECS := 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15

# --- helpers ---

define run_spec_with_iface
	@spec="$(1)"; \
	if [ ! -f "$$spec" ]; then echo "ERROR: missing spec file: $$spec"; exit 2; fi; \
	tmp=$$(mktemp /tmp/scapy_spec_XXXX.yml); \
	sed 's/^[[:space:]]*iface:[[:space:]]*".*"/  iface: "$(IFACE)"/; s/^[[:space:]]*iface:[[:space:]]*[^"].*/  iface: "$(IFACE)"/' "$$spec" > "$$tmp"; \
	echo "==> Running $$spec (IFACE=$(IFACE))"; \
	$(SUDO) $(PY) $(RUNNER) --spec "$$tmp"; \
	rc=$$?; rm -f "$$tmp"; exit $$rc
endef

define run_num
	@num="$(1)"; \
	var="SPEC_$${num}"; \
	spec="$${!var}"; \
	if [ -z "$$spec" ]; then echo "ERROR: Unknown spec number $$num"; exit 2; fi; \
	$(call run_spec_with_iface,$$spec)
endef

.PHONY: help all run list clean $(ALL_SPECS)

help:
	@echo "Usage:"
	@echo "  make 01                Run spec 01"
	@echo "  make 01 03 15          Run multiple specs (space-separated)"
	@echo "  make run SPECS=01,02   Run multiple specs (comma-separated)"
	@echo "  make all               Run all specs (01..15)"
	@echo ""
	@echo "Options:"
	@echo "  IFACE=ens6             Override interface used inside specs"
	@echo ""
	@echo "Examples:"
	@echo "  make 02 IFACE=ens6"
	@echo "  make 01 02 03 IFACE=en2np1"
	@echo "  make run SPECS=03,04,05 IFACE=ens6"

# number targets: make 01, make 02, ...
$(ALL_SPECS):
	$(call run_num,$@)

# Run all in order
all:
	@set -e; \
	for n in $(ALL_SPECS); do \
		$(MAKE) $$n IFACE=$(IFACE); \
	done

# Comma-separated runner: make run SPECS=01,02,03
run:
	@if [ -z "$(SPECS)" ]; then echo "ERROR: provide SPECS=01,02,03"; exit 2; fi
	@set -e; \
	list=$$(echo "$(SPECS)" | tr ',' ' '); \
	for n in $$list; do \
		$(MAKE) $$n IFACE=$(IFACE); \
	done

list:
	@echo "Specs:"
	@for n in $(ALL_SPECS); do \
		var="SPEC_$$n"; echo "$$n -> $${!var}"; \
	done

clean:
	@rm -f artifacts/*.json 2>/dev/null || true
	@echo "Cleaned artifacts/*.json"
