package com.virgoagario.swagbench.probe;

import com.virgoagario.swagbench.core.SubsystemBucket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class SubsystemProbes {
    private static final AtomicReference<BucketRecorder> ACTIVE = new AtomicReference<>();

    private SubsystemProbes() {
    }

    public static void beginMeasurement(BucketRecorder recorder) {
        ACTIVE.set(recorder);
    }

    public static void endMeasurement() {
        ACTIVE.set(null);
    }

    public static void enter(SubsystemBucket bucket) {
        BucketRecorder recorder = ACTIVE.get();
        if (recorder != null) {
            recorder.enter(bucket);
        }
    }

    public static void exit(SubsystemBucket bucket) {
        BucketRecorder recorder = ACTIVE.get();
        if (recorder != null) {
            recorder.exit(bucket);
        }
    }

    public static Map<SubsystemBucket, Long> drainActiveBuckets() {
        BucketRecorder recorder = ACTIVE.get();
        return recorder == null ? Map.of() : recorder.drain();
    }
}
