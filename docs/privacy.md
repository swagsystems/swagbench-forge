# Privacy and operational safety

SwagBench reports intentionally capture enough environment metadata to explain noisy or invalid performance results. That same metadata can identify a machine or reveal operational details.

## Review before publishing reports

Inspect and sanitize fields such as:

- host name and user-supplied paths;
- CPU and operating-system details;
- JVM command-line arguments;
- cgroup/container identifiers;
- mounted filesystem and disk-device metadata;
- mod names and versions from private packs;
- output paths and process context;
- IP addresses or network-interface details added by future probes.

The public repository excludes private acceptance logs, generated worlds, host snapshots, benchmark jars, and machine-specific reports.

## Temporary benchmark server

The included shell scripts:

- bind the generated offline-mode server to `127.0.0.1`;
- disable RCON and query interfaces;
- use a non-default temporary port;
- remove and recreate the generated `run/` directory.

Offline mode is used only for an isolated local benchmark process. Do not expose that temporary server to an untrusted network.

## Resource impact

ForgeGradle and the benchmark JVM are intentionally memory-intensive. Default smoke/repeatability heaps are 2 GiB and the Gradle daemon is disabled. Run benchmarks on an isolated test host or container, inspect current memory/swap pressure first, and avoid running them against a production game server.

## Generated artifacts

The following paths are ignored and should stay untracked:

```text
.gradle/
build/
run/
swagbench-reports/
```

Do not commit `eula.txt`, server properties from real deployments, world data, logs, crash reports, or third-party modpacks.
