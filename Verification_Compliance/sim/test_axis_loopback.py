import pyverilator
import time

# Build the simulation
sim = pyverilator.PyVerilator.build('../rtl/axis_loopback.v')

# If your module name is not axis_loopback, replace accordingly:
top = sim.io

# Initialize clock and reset
top.resetn = 0
top.clk156 = 0
for _ in range(5):
    sim.eval()
top.resetn = 1

# Toggle the clock for a few cycles
for cycle in range(20):
    top.clk156 = not top.clk156
    sim.eval()
    print(f"Cycle {cycle}, clk156={top.clk156}")

print("Simulation finished successfully.")
