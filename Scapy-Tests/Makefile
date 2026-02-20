# Makefile (place at repo root)

PY      := python3
RUNNER  := tests.runner
SPECDIR := tests/specs

.DEFAULT_GOAL := help

.PHONY: help list run
help:
	@echo "Usage:"
	@echo "  make NN            # run spec tests/specs/NN_*.yml (e.g., make 02)"
	@echo "  make run SPEC=...  # run exact spec path (e.g., make run SPEC=$(SPECDIR)/02_nic_loopback.yml)"
	@echo "  make list          # list available spec files"

list:
	@ls -1 $(SPECDIR)/*.yml 2>/dev/null || true

# Run an explicit spec file:
run:
	@test -n "$(SPEC)" || (echo "ERROR: SPEC is required, e.g. make run SPEC=$(SPECDIR)/02_nic_loopback.yml" && exit 2)
	@echo "[RUN] $(SPEC)"
	@sudo $(PY) -m $(RUNNER) --spec "$(SPEC)"

# Pattern rule: make 02 -> finds tests/specs/02_*.yml and runs it
%:
	@case "$@" in \
	  [0-9][0-9]) ;; \
	  *) echo "Unknown target: $@"; $(MAKE) help; exit 2 ;; \
	esac
	@spec="$$(ls -1 $(SPECDIR)/$@_*.yml 2>/dev/null | head -n 1)"; \
	if [ -z "$$spec" ]; then \
	  echo "ERROR: No spec found matching $(SPECDIR)/$@_*.yml"; \
	  echo "Available:"; \
	  ls -1 $(SPECDIR)/*.yml 2>/dev/null || true; \
	  exit 2; \
	fi; \
	echo "[RUN] $$spec"; \
	sudo $(PY) -m $(RUNNER) --spec "$$spec"