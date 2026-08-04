package com.virgoagario.swagbench.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virgoagario.swagbench.core.SubsystemBucket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BucketRecorderTest {
    @Test
    void nestedSameBucketProbeKeepsOuterInclusiveTiming() {
        long[] nanos = {10L, 50L};
        AtomicInteger index = new AtomicInteger();
        BucketRecorder recorder = new BucketRecorder(() -> nanos[index.getAndIncrement()]);

        recorder.enter(SubsystemBucket.ENTITIES);
        recorder.enter(SubsystemBucket.ENTITIES);
        recorder.exit(SubsystemBucket.ENTITIES);
        recorder.exit(SubsystemBucket.ENTITIES);

        assertEquals(Map.of(SubsystemBucket.ENTITIES, 40L), recorder.drain());
    }

    @Test
    void nestedDifferentBucketsRemainIndependent() {
        long[] nanos = {100L, 110L, 150L, 170L};
        AtomicInteger index = new AtomicInteger();
        BucketRecorder recorder = new BucketRecorder(() -> nanos[index.getAndIncrement()]);

        recorder.enter(SubsystemBucket.ENTITIES);
        recorder.enter(SubsystemBucket.CHUNK);
        recorder.exit(SubsystemBucket.CHUNK);
        recorder.exit(SubsystemBucket.ENTITIES);

        assertEquals(Map.of(
                SubsystemBucket.ENTITIES, 70L,
                SubsystemBucket.CHUNK, 40L), recorder.drain());
    }
}
