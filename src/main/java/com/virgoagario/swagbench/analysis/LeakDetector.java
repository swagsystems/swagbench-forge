package com.virgoagario.swagbench.analysis;

import com.virgoagario.swagbench.resource.ResourceSample;
import com.virgoagario.swagbench.resource.ResourceTimeline;
import java.util.ArrayList;
import java.util.List;

public final class LeakDetector {
    public static final double DEFAULT_SLOPE_THRESHOLD_BYTES_PER_MINUTE = 8.0 * 1024.0 * 1024.0;
    public static final double DEFAULT_R_SQUARED_THRESHOLD = 0.80;

    private final double slopeThresholdBytesPerMinute;
    private final double rSquaredThreshold;

    public LeakDetector() {
        this(DEFAULT_SLOPE_THRESHOLD_BYTES_PER_MINUTE, DEFAULT_R_SQUARED_THRESHOLD);
    }

    public LeakDetector(double slopeThresholdBytesPerMinute, double rSquaredThreshold) {
        this.slopeThresholdBytesPerMinute = slopeThresholdBytesPerMinute;
        this.rSquaredThreshold = rSquaredThreshold;
    }

    public LeakReport analyze(ResourceTimeline timeline) {
        return analyze(timeline, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public LeakReport analyze(
            ResourceTimeline timeline,
            long startEntityCount,
            long endEntityCount,
            long startBlockEntityCount,
            long endBlockEntityCount,
            long startLoadedChunkCount,
            long endLoadedChunkCount) {
        List<Point> points = points(timeline);
        long entityGrowth = growth(startEntityCount, endEntityCount);
        long blockEntityGrowth = growth(startBlockEntityCount, endBlockEntityCount);
        long chunkGrowth = growth(startLoadedChunkCount, endLoadedChunkCount);
        if (points.size() < 2) {
            return new LeakReport(false, 0.0, 0.0, points.size(), false, entityGrowth, blockEntityGrowth, chunkGrowth);
        }
        Regression regression = regress(points);
        boolean suspected = regression.slopeBytesPerMinute() >= slopeThresholdBytesPerMinute
                && regression.rSquared() >= rSquaredThreshold;
        return new LeakReport(
                suspected,
                regression.slopeBytesPerMinute(),
                regression.rSquared(),
                points.size(),
                false,
                entityGrowth,
                blockEntityGrowth,
                chunkGrowth);
    }

    private static List<Point> points(ResourceTimeline timeline) {
        if (timeline == null) {
            return List.of();
        }
        List<Point> points = new ArrayList<>();
        long firstNanoTime = Long.MIN_VALUE;
        for (ResourceSample sample : timeline.samples()) {
            if (sample.postGcLiveSetBytes() < 0L) {
                continue;
            }
            if (!points.isEmpty() && points.get(points.size() - 1).bytes() == sample.postGcLiveSetBytes()) {
                continue;
            }
            if (firstNanoTime == Long.MIN_VALUE) {
                firstNanoTime = sample.nanoTime();
            }
            double minutes = (sample.nanoTime() - firstNanoTime) / 60_000_000_000.0;
            points.add(new Point(minutes, sample.postGcLiveSetBytes()));
        }
        return points;
    }

    private static Regression regress(List<Point> points) {
        double meanX = points.stream().mapToDouble(Point::minutes).average().orElse(0.0);
        double meanY = points.stream().mapToDouble(Point::bytes).average().orElse(0.0);
        double numerator = 0.0;
        double denominator = 0.0;
        for (Point point : points) {
            double x = point.minutes() - meanX;
            double y = point.bytes() - meanY;
            numerator += x * y;
            denominator += x * x;
        }
        double slope = denominator == 0.0 ? 0.0 : numerator / denominator;
        double intercept = meanY - slope * meanX;
        double ssTotal = 0.0;
        double ssResidual = 0.0;
        for (Point point : points) {
            double y = point.bytes();
            double predicted = intercept + slope * point.minutes();
            ssTotal += Math.pow(y - meanY, 2.0);
            ssResidual += Math.pow(y - predicted, 2.0);
        }
        double rSquared = ssTotal == 0.0 ? 1.0 : 1.0 - ssResidual / ssTotal;
        return new Regression(slope, Math.max(0.0, Math.min(1.0, rSquared)));
    }

    private record Point(double minutes, double bytes) {
    }

    private record Regression(double slopeBytesPerMinute, double rSquared) {
    }

    private static long growth(long start, long end) {
        return start < 0L || end < 0L ? -1L : end - start;
    }
}
