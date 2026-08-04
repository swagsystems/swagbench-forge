# Measurement methodology

## Goal

Measure dedicated-server tick behavior while preserving enough environmental and subsystem context to reject misleading comparisons.

SwagBench is not a universal Minecraft performance score. It is a controlled harness for comparing builds, JVM settings, or mod changes under the same declared workload and environment.

## Run phases

1. **Environment capture**
   - Record Minecraft, Forge, Java, JVM, OS, CPU, heap, mod-set, cgroup, memory, and swap context.
2. **Scenario setup**
   - Enter a dedicated flat void dimension.
   - Pin time, weather, mob spawning, mob griefing, and random tick speed.
   - Force-load the benchmark region.
   - Place the bundled workload structure and deterministic entity census.
3. **Warmup**
   - Execute the configured warmup ticks so class loading, JIT compilation, caches, and scenario transients do not dominate the measured interval.
4. **GC boundary**
   - Request one collection and briefly settle before measurement.
   - Start GC monitoring after the boundary so warmup allocation does not contaminate measurement accounting.
5. **Measurement**
   - Record whole-tick duration and mixin-backed subsystem buckets.
   - Track GC events and mark contaminated ticks.
   - Optionally sample CPU, disk, memory, and allocation metrics.
6. **Analysis and gating**
   - Produce raw and clean statistics.
   - Compute fixed-window timing distributions.
   - Mark reports degraded when configured contamination or dispersion thresholds are exceeded.
7. **Report write and process exit**
   - Write one schema-versioned JSON report per run.
   - Exit with a stable code that distinguishes success, execution failure, and degraded evidence.

## Statistics

Each statistics block can include:

- count
- minimum and maximum
- median / p50
- p90, p95, and p99
- mean
- standard deviation
- median absolute deviation (MAD)

### Why median and MAD lead

Mean and standard deviation are useful diagnostics, but server-tick samples often have long tails from GC, scheduling, I/O, or workload phase changes. Median and MAD provide a more robust center and dispersion estimate for repeatability decisions.

For marginal-change work, compare:

1. matching environments and configs;
2. clean window medians;
3. within-run window MAD ratios;
4. cross-run median dispersion;
5. raw tails and contamination as diagnostic context.

Do not average away a failed quality gate.

## Clean versus raw timing

- **Raw:** every measured tick; useful for understanding observed user-facing behavior.
- **Clean:** ticks not marked as GC-contaminated; useful for isolating steady-state execution.

Both are retained. Removing contaminated ticks without preserving raw behavior would hide important operational cost.

## Degraded evidence

A written report may be marked degraded for conditions including:

- excessive GC-contaminated ticks;
- active cgroup swap at capture time;
- clean 100-tick window MAD above the marginal-sensitivity threshold;
- setup or instrumentation conditions that invalidate the intended comparison.

A degraded report remains useful for diagnosis, but it is not acceptance evidence for small performance claims.

## Determinism boundary

The harness controls the benchmark seed, dimension, coordinates, structure placement, gamerules, force-loaded chunks, and entity census. It does not claim complete determinism across every vanilla, Forge, JVM, operating-system, or third-party mod scheduling path. The report exposes this boundary through RNG metadata rather than silently asserting full pinning.

## Comparing two builds

A defensible A/B run should keep constant:

- physical host and power policy;
- CPU affinity/allocation;
- Java vendor and version;
- JVM flags, collector, and heap;
- Minecraft and Forge versions;
- mod list and configuration except the tested change;
- SwagBench scenario, seed, warmup, and measurement counts;
- background workload and memory pressure.

Run multiple fresh JVMs when startup/JIT state matters. Treat any mismatch in environment metadata or degradation gates as a reason to investigate before claiming causality.
