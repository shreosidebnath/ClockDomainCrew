## Prerequisites for Tests

### Verilator (Required)

This project requires **Verilator v5.044 or v5.045**, built using **Clang/clang++**.

Some environments may run into Verilator PCH / build issues when Verilator is compiled with `g++` (e.g., missing file paths during compilation). Building Verilator with **Clang** resolves this reliably. Newer Verilator versions also improve support for the SystemVerilog golden model, but this project is validated against **v5.044** and **v5.045**.

#### Build Verilator with Clang

Clone Verilator, check out the appropriate release, then build/install:

```bash
export CC=clang
export CXX=clang++

autoconf
./configure
make          # this may take a while
sudo make install
```

#### If you previously installed Verilator via Ubuntu packages, ensure the /usr/local/bin install takes precedence:

```bash
export PATH=/usr/local/bin:$PATH
```

### Java (Required)

#### Java JDK 17 is required to run and integrate with the blackbox implementation of the golden model.

#### Switch your system Java to JDK 17
```bash
sudo update-alternatives --config java
```

Select the java-17 option.
If java-17 is not listed, install JDK 17 first, then re-run the command above.