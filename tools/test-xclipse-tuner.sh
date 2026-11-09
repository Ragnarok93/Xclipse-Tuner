#!/system/bin/sh
# Host-side fixture test for the read-only telemetry protocol.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d "${TMPDIR:-/tmp}/xclipse-tuner-test.XXXXXX")
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

GPU="$TMP/gpu"
DEVFREQ="$TMP/devfreq"
THERMAL="$TMP/thermal"
mkdir -p "$GPU" "$DEVFREQ/22200000.sgpu" "$THERMAL/thermal_zone4" "$THERMAL/thermal_zone5" "$THERMAL/cooling_device7" "$THERMAL/cooling_device8"

printf '%s\n' 'Samsung Xclipse 940' > "$GPU/gpu_model"
printf '%s\n' '650000' > "$GPU/gpu_clock"
printf '%s\n' '73' > "$GPU/gpu_busy"
printf '%s\n' '  76.0  ' > "$GPU/gpu_tmu"
printf '%s\n' '252000' > "$GPU/gpu_min_clock"
printf '%s\n' '900000' > "$GPU/gpu_max_clock"
printf '%s\n' 'conservative' > "$GPU/gpu_governor"

printf '%s\n' '22200000.sgpu' > "$DEVFREQ/22200000.sgpu/name"
printf '%s\n' '650000' > "$DEVFREQ/22200000.sgpu/cur_freq"
printf '%s\n' '700000' > "$DEVFREQ/22200000.sgpu/target_freq"
printf '%s\n' '252000' > "$DEVFREQ/22200000.sgpu/min_freq"
printf '%s\n' '1095000' > "$DEVFREQ/22200000.sgpu/max_freq"

printf '%s\n' 'G3D' > "$THERMAL/thermal_zone4/type"
printf '%s\n' '  76000  ' > "$THERMAL/thermal_zone4/temp"
printf '%s\n' 'thermal-gpufreq-0' > "$THERMAL/cooling_device7/type"
printf '%s\n' '0' > "$THERMAL/cooling_device7/cur_state"
printf '%s\n' '13' > "$THERMAL/cooling_device7/max_state"

OUTPUT=$(XCLIPSE_GPU_ROOT="$GPU" XCLIPSE_DEVFREQ_ROOT="$DEVFREQ" XCLIPSE_THERMAL_ROOT="$THERMAL" sh "$ROOT/tools/xclipse-tuner.sh" status)
printf '%s' "$OUTPUT" | python3 -c '
import json, sys
data = json.load(sys.stdin)
assert data["schema_version"] == 1
assert data["detection"] == "supported"
assert data["gpu"]["model"] == "Samsung Xclipse 940"
assert data["gpu"]["clock_khz"] == 650000
assert data["gpu"]["busy_raw"] == 73
assert data["gpu"]["temperature_c"] == 76.0
assert data["devfreq"]["max_khz"] == 1095000
assert data["thermal"]["g3d_temperature_millic"] == 76000
assert data["thermal"]["g3d_temperature_c"] == 76.0
assert data["thermal"]["gpu_cooling_state"] == 0
zones = {zone["type"]: zone["temperature_millic"] for zone in data["thermal_zones"]}
assert zones["G3D"] == 76000
assert zones["CPU"] == 68000
cooling = {device["type"]: device["current_state"] for device in data["cooling_devices"]}
assert cooling["thermal-cpufreq-1"] == 9
assert cooling["thermal-gpufreq-0"] == 0
assert data["controls"]["writes_enabled"] is False
assert data["controls"]["validated_write_nodes"] == []
'

PROFILES=$(sh "$ROOT/tools/xclipse-tuner.sh" profiles)
printf '%s' "$PROFILES" | python3 -c '
import json, sys
data = json.load(sys.stdin)
assert data["schema_version"] == 1
assert data["profiles"][0]["id"] == "balanced"
assert data["profiles"][0]["available"] is True
assert all(not p["available"] for p in data["profiles"][1:])
'

if sh "$ROOT/tools/xclipse-tuner.sh" unsupported >/dev/null 2>&1; then
    echo "unknown command unexpectedly succeeded" >&2
    exit 1
fi

echo "Xclipse telemetry protocol tests passed."
