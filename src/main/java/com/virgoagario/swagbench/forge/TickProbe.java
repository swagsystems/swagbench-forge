package com.virgoagario.swagbench.forge;

import com.virgoagario.swagbench.core.SubsystemBucket;
import com.virgoagario.swagbench.core.TickSample;
import com.virgoagario.swagbench.probe.BucketRecorder;
import com.virgoagario.swagbench.probe.SubsystemProbes;
import java.util.Map;
import java.util.Optional;
import net.minecraftforge.event.TickEvent;

public final class TickProbe {
    private final BucketRecorder recorder = new BucketRecorder();
    private long tickStartNanos = -1L;
    private int tickIndex;
    private TickSample lastSample;

    public void beginMeasurement() {
        tickIndex = 0;
        tickStartNanos = -1L;
        lastSample = null;
        SubsystemProbes.beginMeasurement(recorder);
    }

    public void endMeasurement() {
        SubsystemProbes.endMeasurement();
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStartNanos = System.nanoTime();
            recorder.drain();
            return;
        }
        if (event.phase == TickEvent.Phase.END && tickStartNanos >= 0L) {
            Map<SubsystemBucket, Long> buckets = recorder.drain();
            long end = System.nanoTime();
            lastSample = TickSample.of(tickIndex++, tickStartNanos, end, buckets);
            tickStartNanos = -1L;
        }
    }

    public Optional<TickSample> drainLastSample() {
        TickSample sample = lastSample;
        lastSample = null;
        return Optional.ofNullable(sample);
    }
}
