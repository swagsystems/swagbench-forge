package com.virgoagario.swagbench.resource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ResourceTimeline(List<ResourceSample> samples, ResourceSummary summary) {
    public static final int DEFAULT_DOWNSAMPLE_LIMIT = 600;

    public ResourceTimeline {
        samples = List.copyOf(samples == null ? List.of() : samples);
        summary = summary == null ? ResourceSummary.from(samples) : summary;
    }

    public static ResourceTimeline of(List<ResourceSample> samples) {
        List<ResourceSample> safeSamples = List.copyOf(samples == null ? List.of() : samples);
        return new ResourceTimeline(safeSamples, ResourceSummary.from(safeSamples));
    }

    public ResourceTimeline downsampled(int limit) {
        if (limit < 2 || samples.size() <= limit) {
            return this;
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        for (int i = 0; i < limit; i++) {
            indexes.add((int) Math.round(i * (samples.size() - 1) / (double) (limit - 1)));
        }
        List<ResourceSample> downsampled = new ArrayList<>(indexes.size());
        for (int index : indexes) {
            downsampled.add(samples.get(index));
        }
        return new ResourceTimeline(downsampled, summary);
    }
}
