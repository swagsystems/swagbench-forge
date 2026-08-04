package com.virgoagario.swagbench.resource;

import java.lang.management.ManagementFactory;

final class AllocationProbe {
    private final com.sun.management.ThreadMXBean threadBean;
    private long previousTotal = -1L;
    private long previousNanoTime = -1L;

    AllocationProbe() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        this.threadBean = bean instanceof com.sun.management.ThreadMXBean sunBean ? sunBean : null;
        if (threadBean != null && threadBean.isThreadAllocatedMemorySupported()
                && !threadBean.isThreadAllocatedMemoryEnabled()) {
            try {
                threadBean.setThreadAllocatedMemoryEnabled(true);
            } catch (UnsupportedOperationException ignored) {
                // Unsupported JVMs stay disabled and report unknown allocation metrics.
            }
        }
    }

    AllocationSnapshot sample(long nanoTime) {
        long total = totalAllocatedBytes();
        if (total < 0L || previousTotal < 0L || nanoTime <= previousNanoTime) {
            previousTotal = total;
            previousNanoTime = nanoTime;
            return new AllocationSnapshot(-1L, total);
        }
        long deltaBytes = Math.max(0L, total - previousTotal);
        long deltaNanos = nanoTime - previousNanoTime;
        previousTotal = total;
        previousNanoTime = nanoTime;
        return new AllocationSnapshot(Math.round(deltaBytes * 1_000_000_000.0 / deltaNanos), total);
    }

    private long totalAllocatedBytes() {
        if (threadBean == null
                || !threadBean.isThreadAllocatedMemorySupported()
                || !threadBean.isThreadAllocatedMemoryEnabled()) {
            return -1L;
        }
        long total = 0L;
        for (long threadId : threadBean.getAllThreadIds()) {
            long bytes = threadBean.getThreadAllocatedBytes(threadId);
            if (bytes > 0L) {
                total += bytes;
            }
        }
        return total;
    }

    record AllocationSnapshot(long allocationRateBytesPerSecond, long allocatedBytesTotal) {
    }
}
