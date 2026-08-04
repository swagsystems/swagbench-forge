#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${SWAGBENCH_REPEAT_OUT:-"$ROOT/build/swagbench-repeatability"}"
SCENARIO="${SWAGBENCH_REPEAT_SCENARIO:-mixed-v1}"
WARMUP="${SWAGBENCH_REPEAT_WARMUP:-8000}"
MEASURE="${SWAGBENCH_REPEAT_MEASURE:-1000}"
RUNS="${SWAGBENCH_REPEAT_RUNS:-3}"
PORT="${SWAGBENCH_REPEAT_PORT:-25566}"
THRESHOLD="${SWAGBENCH_REPEAT_THRESHOLD:-0.01}"
XMS="${SWAGBENCH_REPEAT_XMS:-2G}"
XMX="${SWAGBENCH_REPEAT_XMX:-2G}"
SKIP_PREFLIGHT="${SWAGBENCH_REPEAT_SKIP_PREFLIGHT:-0}"
RESOURCE="${SWAGBENCH_REPEAT_RESOURCE:-0}"
RESOURCE_INTERVAL_MILLIS="${SWAGBENCH_REPEAT_RESOURCE_INTERVAL_MILLIS:-250}"
RESOURCE_FULL="${SWAGBENCH_REPEAT_RESOURCE_FULL:-0}"

environment_preflight() {
  if [[ "$SKIP_PREFLIGHT" == "1" ]]; then
    echo "SwagBench repeatability environment preflight skipped by SWAGBENCH_REPEAT_SKIP_PREFLIGHT=1" >&2
    return 0
  fi

  local failures=()
  local swap_current_file="/sys/fs/cgroup/memory.swap.current"
  if [[ -r "$swap_current_file" ]]; then
    local swap_current
    swap_current="$(<"$swap_current_file")"
    if [[ "$swap_current" =~ ^[0-9]+$ && "$swap_current" -gt 0 ]]; then
      failures+=("cgroup swap is already in use: ${swap_current} bytes")
    fi
  fi

  if [[ -r /proc/loadavg ]]; then
    local load1
    local cores
    load1="$(awk '{print $1}' /proc/loadavg)"
    cores="$(nproc)"
    if awk -v load_avg="$load1" -v cores="$cores" 'BEGIN { exit !(load_avg > cores) }'; then
      failures+=("load average ${load1} exceeds visible cores ${cores}")
    fi
  fi

  if [[ "${#failures[@]}" -gt 0 ]]; then
    echo "SwagBench repeatability environment preflight failed:" >&2
    printf '  - %s\n' "${failures[@]}" >&2
    echo "Use SWAGBENCH_REPEAT_SKIP_PREFLIGHT=1 only for diagnostic, non-acceptance runs." >&2
    return 4
  fi
}

cd "$ROOT"
environment_preflight
rm -rf "$OUT" run
mkdir -p "$OUT" run
printf 'eula=true\n' > run/eula.txt
{
  printf 'server-ip=127.0.0.1\n'
  printf 'server-port=%s\n' "$PORT"
  printf 'online-mode=false\n'
  printf 'enable-query=false\n'
  printf 'enable-rcon=false\n'
  printf 'max-tick-time=-1\n'
} > run/server.properties

RUN_CONFIG="scenario=${SCENARIO},seed=0,warmup=${WARMUP},measure=${MEASURE},runs=${RUNS}"
if [[ "$RESOURCE" == "1" ]]; then
  RUN_CONFIG+=",resource=true,resourceIntervalMillis=${RESOURCE_INTERVAL_MILLIS}"
  if [[ "$RESOURCE_FULL" == "1" ]]; then
    RUN_CONFIG+=",resourceFull=true"
  fi
fi

set +e
./gradlew runServer --no-daemon \
  "-PswagbenchRunXms=${XMS}" \
  "-PswagbenchRunXmx=${XMX}" \
  "-Dswagbench.run=${RUN_CONFIG}" \
  "-Dswagbench.output=${OUT}"
code=$?
set -e

mapfile -t reports < <(find "$OUT" -maxdepth 1 -name 'swagbench-*.json' | sort)
if [[ "${#reports[@]}" -eq 0 ]]; then
  echo "SwagBench repeatability run failed before writing a report; Gradle exit code ${code}" >&2
  exit "$code"
fi

python3 - "$SCENARIO" "$MEASURE" "$RUNS" "$THRESHOLD" "${reports[@]}" <<'PY'
import json
import statistics
import sys

scenario = sys.argv[1]
expected_measure = int(sys.argv[2])
expected_reports = int(sys.argv[3])
threshold = float(sys.argv[4])
reports = sys.argv[5:]
if len(reports) != expected_reports:
    raise SystemExit(f"expected {expected_reports} reports, found {len(reports)}")

medians = []
mad_ratios = []
degraded = []
for path in reports:
    with open(path, encoding="utf-8") as handle:
        report = json.load(handle)

    if report["config"]["scenario"] != scenario:
        raise SystemExit(f"{path}: expected scenario {scenario}, got {report['config']['scenario']}")
    if report["config"]["measureTicks"] != expected_measure:
        raise SystemExit(f"{path}: expected measureTicks {expected_measure}, got {report['config']['measureTicks']}")
    tick = report["tickWindows"]["clean"]
    median = tick["medianNanos"]
    mad = tick["madNanos"]
    medians.append(median)
    mad_ratios.append(0.0 if median == 0 else mad / median)
    degraded.append(report["degraded"])

median_center = statistics.median(medians)
median_spread_ratio = 0.0 if median_center == 0 else (max(medians) - min(medians)) / median_center
median_abs_deviations = [abs(value - median_center) for value in medians]
cross_run_mad_ratio = 0.0 if median_center == 0 else statistics.median(median_abs_deviations) / median_center
max_mad_ratio = max(mad_ratios) if mad_ratios else 0.0

print(f"reports={len(reports)}")
print("median_nanos=" + ",".join(str(value) for value in medians))
print("mad_ratio=" + ",".join(f"{value:.6f}" for value in mad_ratios))
print(f"median_spread_ratio={median_spread_ratio:.6f}")
print(f"cross_run_mad_ratio={cross_run_mad_ratio:.6f}")
print(f"max_mad_ratio={max_mad_ratio:.6f}")
print("degraded=" + ",".join(str(value).lower() for value in degraded))

if any(degraded) or cross_run_mad_ratio > threshold or max_mad_ratio > threshold:
    sys.exit(1)
PY
