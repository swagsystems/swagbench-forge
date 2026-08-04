#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${SWAGBENCH_SMOKE_OUT:-"$ROOT/build/swagbench-smoke"}"
WARMUP="${SWAGBENCH_SMOKE_WARMUP:-20}"
MEASURE="${SWAGBENCH_SMOKE_MEASURE:-60}"
RUNS="${SWAGBENCH_SMOKE_RUNS:-1}"
PORT="${SWAGBENCH_SMOKE_PORT:-25566}"
XMS="${SWAGBENCH_SMOKE_XMS:-2G}"
XMX="${SWAGBENCH_SMOKE_XMX:-2G}"

cd "$ROOT"
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

set +e
./gradlew runServer --no-daemon \
  "-PswagbenchRunXms=${XMS}" \
  "-PswagbenchRunXmx=${XMX}" \
  "-Dswagbench.run=scenario=mixed-v1,seed=0,warmup=${WARMUP},measure=${MEASURE},runs=${RUNS}" \
  "-Dswagbench.output=${OUT}"
code=$?
set -e

mapfile -t reports < <(find "$OUT" -maxdepth 1 -name 'swagbench-*.json' | sort)
if [[ "${#reports[@]}" -eq 0 ]]; then
  echo "SwagBench smoke failed before writing a report; Gradle exit code ${code}" >&2
  exit "$code"
fi

# Headless contract: exit code 0 or 3 is acceptable for this tiny smoke window.
# Gradle wraps the child JVM's degraded exit 3 as a task failure, so the report is
# the authoritative contract check here.
python3 - "$MEASURE" "$RUNS" "${reports[@]}" <<'PY'
import json
import sys

expected_measure = int(sys.argv[1])
expected_reports = int(sys.argv[2])
reports = sys.argv[3:]
assert len(reports) == expected_reports, (len(reports), expected_reports)
for path in reports:
    with open(path, encoding="utf-8") as handle:
        report = json.load(handle)

    assert report["schemaVersion"] == 1
    assert report["config"]["scenario"] == "mixed-v1"
    assert report["config"]["measureTicks"] == expected_measure
    assert "clean" in report["tick"]
    assert "raw" in report["tick"]
    assert "gc" in report
print("validated " + ", ".join(reports))
PY
