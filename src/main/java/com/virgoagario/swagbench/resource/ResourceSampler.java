package com.virgoagario.swagbench.resource;

import com.virgoagario.swagbench.core.AllocationSummary;
import com.virgoagario.swagbench.forge.GcMonitor;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ResourceSampler implements AutoCloseable {
    private final ResourceSamplerConfig config;
    private final GcMonitor gcMonitor;
    private final CpuProbe cpuProbe;
    private final DiskProbe diskProbe;
    private final AllocationProbe allocationProbe;
    private final MemoryMXBean memoryBean;
    private final Queue<ResourceSample> samples = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private ResourceSampler(ResourceSamplerConfig config, GcMonitor gcMonitor) {
        this.config = config;
        this.gcMonitor = gcMonitor;
        if (config.enabled()) {
            cpuProbe = new CpuProbe();
            diskProbe = new DiskProbe();
            allocationProbe = new AllocationProbe();
            memoryBean = ManagementFactory.getMemoryMXBean();
        } else {
            cpuProbe = null;
            diskProbe = null;
            allocationProbe = null;
            memoryBean = null;
        }
    }

    public static ResourceSampler start(ResourceSamplerConfig config, GcMonitor gcMonitor) {
        ResourceSampler sampler = new ResourceSampler(config, gcMonitor);
        if (config.enabled()) {
            sampler.start();
        }
        return sampler;
    }

    private void start() {
        running.set(true);
        thread = new Thread(this::runLoop, "swagbench-resource-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop() {
        while (running.get()) {
            sampleOnce();
            try {
                TimeUnit.MILLISECONDS.sleep(config.intervalMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sampleOnce() {
        try {
            long nanoTime = System.nanoTime();
            if (cpuProbe == null || diskProbe == null || allocationProbe == null || memoryBean == null) {
                return;
            }
            CpuProbe.CpuSnapshot cpu = cpuProbe.sample();
            DiskProbe.IoDelta disk = diskProbe.sample();
            AllocationProbe.AllocationSnapshot alloc = allocationProbe.sample(nanoTime);
            samples.add(new ResourceSample(
                    nanoTime,
                    finiteOrUnknown(cpu.processCpuLoad()),
                    finiteOrUnknown(cpu.systemLoadAverage()),
                    cpu.perCoreUtilization(),
                    cpu.processCpuTimeNanos(),
                    cpu.serverThreadCpuTimeNanos(),
                    cpu.workerThreadCpuTimeNanos(),
                    memoryBean.getHeapMemoryUsage().getUsed(),
                    memoryBean.getNonHeapMemoryUsage().getUsed(),
                    gcMonitor == null ? -1L : gcMonitor.postGcLiveSetBytes(),
                    disk.readBytes(),
                    disk.writeBytes(),
                    alloc.allocationRateBytesPerSecond(),
                    alloc.allocatedBytesTotal()));
        } catch (RuntimeException ignored) {
            // Resource sampling is diagnostic-only and must never perturb the benchmark path.
        }
    }

    public ResourceTimeline timeline() {
        ResourceTimeline timeline = ResourceTimeline.of(new ArrayList<>(samples));
        return config.fullTimeline() ? timeline : timeline.downsampled(ResourceTimeline.DEFAULT_DOWNSAMPLE_LIMIT);
    }

    public AllocationSummary allocationSummary(int measureTicks) {
        if (!config.enabled() || samples.isEmpty()) {
            return AllocationSummary.disabled();
        }
        List<ResourceSample> knownTotals = samples.stream()
                .filter(sample -> sample.allocatedBytesTotal() >= 0L)
                .toList();
        if (knownTotals.size() < 2) {
            return AllocationSummary.disabled();
        }
        long totalBytes = Math.max(0L,
                knownTotals.get(knownTotals.size() - 1).allocatedBytesTotal() - knownTotals.get(0).allocatedBytesTotal());
        long bytesPerTick = measureTicks <= 0 ? 0L : Math.round(totalBytes / (double) measureTicks);
        return new AllocationSummary(true, totalBytes, bytesPerTick);
    }

    @Override
    public void close() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static double finiteOrUnknown(double value) {
        return Double.isFinite(value) ? value : -1.0;
    }
}
