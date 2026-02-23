Please note PCH verilator problems can be solved by building it with Clang and forcing it to use Clang as there is defaulting for the system verilog golden model and newer versions of verilator support that, however, when trying to compile if using g++ it looks for files that do not exist whereas Clang works. To do this please when making verilator use these steps clone the repo and checkout to latest release:

export CC=clang
export CXX=clang++
autoconf
CXX=clang++ ./configure
make -> this will take a while
make install

If you have installed packaged ubuntu verilator add this new installation to path so it takes precedence
export PATH=/usr/local/bin:$PATH

Please note that java JDK 17 is required to run and work with the blackbox implementation of the golden model please switch to this version by:
sudo update-alternatives --config java
then selecting java-17
if not listed then install java JDK 17 and then rerun above command to switch