# Report and exit-code contract

## Report lifecycle

Each configured run writes one JSON object. Repeated runs inside one JVM still produce separate files so an external driver can validate and aggregate them independently.

Default directory:

```text
swagbench-reports/
```

Override:

```text
-Dswagbench.output=/path/to/reports
```

## Top-level schema

`schemaVersion: 1` reports include these major sections:

| Field | Purpose |
|---|---|
| `schemaVersion` | Contract version for consumers |
| `runId` | Unique run identity |
| `env` | Minecraft, Forge, JVM, OS, CPU, heap, mod, memory, cgroup, and load context |
| `config` | Scenario, seed, warmup ticks, measurement ticks, and optional probes |
| `rng` | Declared pinning state and known limits |
| `tick` | Raw and clean per-tick statistics |
| `tickWindows` | Raw and clean fixed-window statistics used by repeatability gates |
| `subsystems` | Entity, block-entity, chunk, scheduled-tick, network, and residual timing |
| `gc` | Event count, pause duration, and contaminated tick count |
| `alloc` | Optional allocation summary |
| `resource` | Optional CPU, disk, and memory timeline/summary |
| `repeatability` | Within-run quality metrics |
| `degraded` | Whether the report is unsuitable for acceptance claims |
| `degradedReason` | Machine-readable/human-readable reason for refusal |

Consumers should reject unsupported future schema versions rather than guessing field semantics.

## Exit codes

| Code | Contract |
|---:|---|
| `0` | Run completed, report written, and evidence passed quality gates |
| `2` | Setup or report-write failure; a trustworthy report is not guaranteed |
| `3` | Run completed and report written, but quality gates marked it degraded |

ForgeGradle may wrap a child JVM's exit code in a failed Gradle task. Verification scripts therefore require both the expected report file and valid report contents instead of treating the Gradle wrapper status alone as the benchmark contract.

## Consumer rules

An external driver should:

1. require the expected number of reports;
2. require `schemaVersion == 1`;
3. compare the requested config against the report config;
4. verify environment compatibility before A/B analysis;
5. refuse marginal comparisons when `degraded` is true;
6. retain raw measurements and degradation reasons with any summarized result;
7. avoid publishing host inventory fields without review.

## Repeatability summary

The included repeatability script evaluates:

- clean window median for every run;
- within-run clean window MAD ratio;
- cross-run median absolute deviation ratio;
- degradation state for every report.

The default threshold is 1%. It is a quality gate for marginal-change work, not a claim that all differences above 1% are statistically or operationally meaningful.
