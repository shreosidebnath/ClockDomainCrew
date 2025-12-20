MAKEFLAGS += --silent
	
SBT = sbt
SHELL := /bin/bash
CORE_DIR := "modules/nfmac10g"
GEN_DIR := "${CORE_DIR}/generated"
ERROR_REP := "${GEN_DIR}/error.rpt"
TC_DIR := "${GEN_DIR}/synTestCases"
CORE := "rst_mod" # Change this to nfmac10g later

# Run everything and scan for errors
.PHONY: list
list:
	@grep '^[^#[:space:]].*:' Makefile

.PHONY: all
all: clean cov yosys docs check

.PHONY: check
check: 
	@echo 
	@echo Checking for errors
	rm -rf ${CORE_DIR}/generated/error.rpt
# root
	grep -Hn -E "Error|error" docs/doc.rpt | tee -a ${ERROR_REP}
# core docs
	grep -Hn -E "Error|error" ${CORE_DIR}/docs/doc.rpt | tee -a ${ERROR_REP} 
# test and verilog reports
	grep -Hn -E "Error|error" ${GEN_DIR}/verilog.rpt | tee -a ${ERROR_REP} 
	grep -Hn -E "Error|error" ${GEN_DIR}/test.rpt | tee -a ${ERROR_REP} 
	grep -Hn -E "fail" ${GEN_DIR}/test.rpt | grep -v "failed 0" | tee -a ${ERROR_REP} 
# summary reports
	grep -Hn -E "Error|error" ${TC_DIR}/area_summary.rpt | tee -a ${ERROR_REP} 
	grep -Hn -E "Error|error" ${TC_DIR}/timing_summary.rpt | tee -a ${ERROR_REP} 
# synTestCases
	grep -Hn -E "Error|error" ${TC_DIR}/*/timing.rpt | tee -a ${ERROR_REP} 
	grep -Hn -E "Error|error" ${TC_DIR}/*/yosys.log | tee -a ${ERROR_REP} 
# check for errors
	@if [ ! -s ${CORE_DIR}/generated/error.rpt ]; then \
	  printf "\033[1;32mALL TESTS PASSED WITH NO ERRORS\033[0m\n" ;\
	else \
	  printf "\033[1;31mTESTS COMPLETED WITH ERRORS\033[0m\n" ;\
	fi

# Start with a fresh directory
.PHONY: clean
clean: 
	@echo Cleaning
	rm -rf docs/*.rpt
	rm -rf target
	rm -rf project/target
	rm -rf project/project 
	rm -rf ${CORE_DIR}/docs/*.rpt
	rm -rf ${CORE_DIR}/generated 
	rm -rf ${CORE_DIR}/target 
	rm -rf ${CORE_DIR}/project/project 
	rm -rf ${CORE_DIR}/project/target

.PHONY: publish
publish: 
	@echo Publishing libraries locally
	rm -rf /home/tws/.ivy2/local/org.chiselware/chiselware-syn_2.13
	$(SBT) "project core" publishLocal | tee docs/publish.rpt

# Generate the documentation
.PHONY: docs
docs:
	@echo Building API docs
	$(SBT) "project core" doc | tee docs/doc.rpt
	firefox --new-window ${CORE_DIR}/target/scala-2.13/api/org/chiselware/cores/o01/t001/dff/index.html 2>/dev/null &
	@echo Building User Guide
	cd ${CORE_DIR}/docs/user-guide && pdflatex ${CORE}.tex 
# Rerun to generate TOC
	cd ${CORE_DIR}/docs/user-guide && pdflatex ${CORE}.tex | tee -a ../doc.rpt 
# Clean up temp files
	cd ${CORE_DIR}/docs/user-guide && rm *.aux *.toc *.out *.log 
	firefox ${CORE_DIR}/docs/user-guide/${CORE}.pdf 2>/dev/null & 

# Generate Verilog and synthesize
.PHONY: verilog
verilog:
	@echo Generate Verilog for synthesis
	mkdir -p ${CORE_DIR}/generated
	$(SBT) "project core" run | tee ${CORE_DIR}/generated/verilog.rpt
	rm -rf *anno.json

# Run the tests
.PHONY: test
test:
	@echo Running tests
	mkdir -p ${CORE_DIR}/generated
	$(SBT) "project core" test | tee ${CORE_DIR}/generated/test.rpt
	rm -rf *anno.json

# Run the tests with Scala code coverage enables
.PHONY: cov
cov:
	@echo Running tests with coverage enabled
	mkdir -p ${CORE_DIR}/generated
	$(SBT) clean \
	coverageOn \
	"project core" \
	test \
	run  \
	coverageReport | tee ${CORE_DIR}/generated/test.rpt
	rm -rf *.anno.json
	firefox --new-window ${CORE_DIR}/generated/scalaCoverage/scoverage-report/index.html 2>/dev/null &

# Run synthesis on generated Verilog; generate timing and area reports
.PHONY: yosys
yosys: 
	make verilog
	cd ${CORE_DIR}/generated/synTestCases && source run.sh
	echo "---------------------------------------------------------"
	echo "                      SUMMARY                            "
	echo "---------------------------------------------------------"
	cat ${CORE_DIR}/generated/synTestCases/{area,timing}_summary.rpt

