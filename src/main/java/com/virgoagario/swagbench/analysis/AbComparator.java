package com.virgoagario.swagbench.analysis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class AbComparator {
    private static final int BOOTSTRAP_ITERATIONS = 500;

    public AbComparisonResult compare(Path baselineDir, Path candidateDir) throws IOException {
        List<JsonObject> baseline = reports(baselineDir);
        List<JsonObject> candidate = reports(candidateDir);
        if (baseline.isEmpty() || candidate.isEmpty()) {
            throw new IllegalArgumentException("baseline and candidate must each contain at least one report");
        }
        List<AbComparisonResult.MetricComparison> metrics = new ArrayList<>();
        compareLowerIsBetter(metrics, "tick.medianNanos", baseline, candidate, "tick", "clean", "medianNanos");
        compareLowerIsBetter(metrics, "tick.p95Nanos", baseline, candidate, "tick", "clean", "p95Nanos");
        compareLowerIsBetter(metrics, "subsystems.entities.p95Nanos", baseline, candidate,
                "subsystems", "entities", "clean", "p95Nanos");
        compareLowerIsBetter(metrics, "subsystems.chunk.p95Nanos", baseline, candidate,
                "subsystems", "chunk", "clean", "p95Nanos");
        compareLowerIsBetter(metrics, "network.bytesIn", baseline, candidate, "network", "bytesIn");
        compareLowerIsBetter(metrics, "network.bytesOut", baseline, candidate, "network", "bytesOut");
        compareLowerIsBetter(metrics, "resource.maxCoreUtilization.median", baseline, candidate,
                "resourceSummary", "maxCoreUtilization", "median");
        return new AbComparisonResult(metrics);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: AbComparator <baseline-report-dir> <candidate-report-dir>");
            System.exit(2);
        }
        System.out.print(new AbComparator().compare(Path.of(args[0]), Path.of(args[1])).toText());
    }

    private static void compareLowerIsBetter(
            List<AbComparisonResult.MetricComparison> out,
            String name,
            List<JsonObject> baseline,
            List<JsonObject> candidate,
            String... path) {
        double baselineMedian = median(values(baseline, path));
        List<Double> candidateValues = values(candidate, path);
        List<Double> baselineValues = values(baseline, path);
        double candidateMedian = median(candidateValues);
        double delta = candidateMedian - baselineMedian;
        double deltaPercent = baselineMedian == 0.0 ? 0.0 : delta / baselineMedian;
        ConfidenceInterval ci = bootstrapDeltaCi(baselineValues, candidateValues);
        String verdict = "inconclusive";
        if (ci.high() < 0.0) {
            verdict = "better";
        } else if (ci.low() > 0.0) {
            verdict = "worse";
        }
        out.add(new AbComparisonResult.MetricComparison(
                name,
                baselineMedian,
                candidateMedian,
                delta,
                deltaPercent,
                ci.low(),
                ci.high(),
                verdict));
    }

    private static List<JsonObject> reports(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            List<Path> paths = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            List<JsonObject> reports = new ArrayList<>();
            for (Path path : paths) {
                JsonObject report = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (report.has("degraded") && report.get("degraded").getAsBoolean()) {
                    throw new IllegalArgumentException("degraded report is not valid for A/B comparison: " + path);
                }
                String mode = report.has("mode") ? report.get("mode").getAsString() : "marginal";
                if (!"realistic".equals(mode)) {
                    throw new IllegalArgumentException("A/B comparison requires realistic reports: " + path);
                }
                reports.add(report);
            }
            return reports;
        }
    }

    private static List<Double> values(List<JsonObject> reports, String... path) {
        List<Double> values = new ArrayList<>();
        for (JsonObject report : reports) {
            JsonElement current = report;
            for (String segment : path) {
                if (!current.isJsonObject() || !current.getAsJsonObject().has(segment)) {
                    current = null;
                    break;
                }
                current = current.getAsJsonObject().get(segment);
            }
            if (current != null && current.isJsonPrimitive() && current.getAsJsonPrimitive().isNumber()) {
                values.add(current.getAsDouble());
            } else {
                throw new IllegalArgumentException("missing numeric metric: " + String.join(".", path));
            }
        }
        return values;
    }

    private static double median(List<Double> values) {
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }
        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    private static ConfidenceInterval bootstrapDeltaCi(List<Double> baseline, List<Double> candidate) {
        Random random = new Random(0L);
        double[] deltas = new double[BOOTSTRAP_ITERATIONS];
        for (int i = 0; i < BOOTSTRAP_ITERATIONS; i++) {
            deltas[i] = median(sample(candidate, random)) - median(sample(baseline, random));
        }
        java.util.Arrays.sort(deltas);
        return new ConfidenceInterval(
                deltas[(int) Math.floor(0.025 * (deltas.length - 1))],
                deltas[(int) Math.ceil(0.975 * (deltas.length - 1))]);
    }

    private static List<Double> sample(List<Double> values, Random random) {
        List<Double> sample = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            sample.add(values.get(random.nextInt(values.size())));
        }
        return sample;
    }

    private record ConfidenceInterval(double low, double high) {
    }
}
