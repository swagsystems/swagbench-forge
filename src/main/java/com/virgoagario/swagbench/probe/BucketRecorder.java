package com.virgoagario.swagbench.probe;

import com.virgoagario.swagbench.core.SubsystemBucket;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class BucketRecorder {
    private final LongSupplier nanoTime;
    private final ThreadLocal<EnumMap<SubsystemBucket, Long>> buckets =
            ThreadLocal.withInitial(() -> new EnumMap<>(SubsystemBucket.class));
    private final ThreadLocal<EnumMap<SubsystemBucket, Long>> starts =
            ThreadLocal.withInitial(() -> new EnumMap<>(SubsystemBucket.class));
    private final ThreadLocal<EnumMap<SubsystemBucket, Integer>> depths =
            ThreadLocal.withInitial(() -> new EnumMap<>(SubsystemBucket.class));

    public BucketRecorder() {
        this(System::nanoTime);
    }

    BucketRecorder(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public void enter(SubsystemBucket bucket) {
        EnumMap<SubsystemBucket, Integer> depthMap = depths.get();
        int depth = depthMap.getOrDefault(bucket, 0);
        if (depth == 0) {
            starts.get().put(bucket, nanoTime.getAsLong());
        }
        depthMap.put(bucket, depth + 1);
    }

    public void exit(SubsystemBucket bucket) {
        EnumMap<SubsystemBucket, Integer> depthMap = depths.get();
        Integer depth = depthMap.get(bucket);
        if (depth == null || depth == 0) {
            starts.get().remove(bucket);
            return;
        }
        if (depth > 1) {
            depthMap.put(bucket, depth - 1);
            return;
        }
        depthMap.remove(bucket);
        Long start = starts.get().remove(bucket);
        if (start == null) {
            return;
        }
        long elapsed = Math.max(0L, nanoTime.getAsLong() - start);
        buckets.get().merge(bucket, elapsed, Long::sum);
    }

    public Map<SubsystemBucket, Long> drain() {
        EnumMap<SubsystemBucket, Long> copy = new EnumMap<>(buckets.get());
        buckets.get().clear();
        starts.get().clear();
        depths.get().clear();
        return copy;
    }
}
