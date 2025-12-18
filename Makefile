.PHONY: all clean compile test verilog help

all: compile

help:
	@echo "Available targets:"
	@echo "  compile  - Compile all modules"
	@echo "  test     - Run all tests"
	@echo "  verilog  - Generate Verilog for nfmac10g"
	@echo "  clean    - Clean build artifacts"

compile:
	sbt compile

test:
	sbt test

verilog:
	sbt "project nfmac10g" "runMain org.chiselware.cores.o01.t001.nfmac10g.NfMac10gVerilog"

clean:
	sbt clean
	rm -rf target
	rm -rf modules/*/target
	rm -rf project/target
	rm -f *.v *.fir *.anno.json