package com.virgoagario.swagbench.forge;

import com.virgoagario.swagbench.core.GcEvent;
import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

public final class GcMonitor implements AutoCloseable {
    private final Queue<GcEvent> events = new ConcurrentLinkedQueue<>();
    private final List<NotificationEmitter> emitters = new ArrayList<>();
    private final AtomicLong postGcLiveSetBytes = new AtomicLong(-1L);
    private final NotificationListener listener;
    private final long startNanoTime;
    private final long startUptimeMillis;

    public GcMonitor() {
        this(System.nanoTime(), ManagementFactory.getRuntimeMXBean().getUptime(), true);
    }

    GcMonitor(long startNanoTime, long startUptimeMillis, boolean attachMxBeans) {
        this.startNanoTime = startNanoTime;
        this.startUptimeMillis = startUptimeMillis;
        this.listener = this::accept;
        if (!attachMxBeans) {
            return;
        }
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener(listener, null, null);
                emitters.add(emitter);
            }
        }
    }

    public List<GcEvent> drain() {
        List<GcEvent> drained = new ArrayList<>();
        GcEvent event;
        while ((event = events.poll()) != null) {
            drained.add(event);
        }
        return drained;
    }

    public long postGcLiveSetBytes() {
        return postGcLiveSetBytes.get();
    }

    @Override
    public void close() {
        for (NotificationEmitter emitter : emitters) {
            try {
                emitter.removeNotificationListener(listener);
            } catch (Exception ignored) {
                // Listener removal is best-effort during server shutdown.
            }
        }
    }

    void accept(Notification notification, Object handback) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            return;
        }
        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(
                (CompositeData) notification.getUserData());
        long start = toNanoTime(info.getGcInfo().getStartTime());
        long end = toNanoTime(info.getGcInfo().getEndTime());
        events.add(new GcEvent(info.getGcName(), start, end));
        recordPostGcLiveSet(info);
    }

    private long toNanoTime(long uptimeMillis) {
        return startNanoTime + (uptimeMillis - startUptimeMillis) * 1_000_000L;
    }

    private void recordPostGcLiveSet(GarbageCollectionNotificationInfo info) {
        long usedBytes = heapLikeUsedBytes(info.getGcInfo().getMemoryUsageAfterGc());
        if (usedBytes < 0L) {
            return;
        }
        postGcLiveSetBytes.set(usedBytes);
    }

    private static long heapLikeUsedBytes(java.util.Map<String, MemoryUsage> usageByPool) {
        long heapLike = 0L;
        long fallback = 0L;
        boolean sawHeapLike = false;
        for (java.util.Map.Entry<String, MemoryUsage> entry : usageByPool.entrySet()) {
            MemoryUsage usage = entry.getValue();
            if (usage == null || usage.getUsed() < 0L) {
                continue;
            }
            fallback += usage.getUsed();
            String pool = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            if (pool.contains("heap")
                    || pool.contains("eden")
                    || pool.contains("survivor")
                    || pool.contains("old")
                    || pool.contains("tenured")) {
                heapLike += usage.getUsed();
                sawHeapLike = true;
            }
        }
        if (sawHeapLike) {
            return heapLike;
        }
        return fallback > 0L ? fallback : -1L;
    }
}
