#!/system/bin/sh
# Xclipse Tuner companion protocol v1.
# READ-ONLY: this command never writes to sysfs or changes device state.
set -eu

PATH=/system/bin:/system/xbin:/vendor/bin:/sbin:$PATH
export PATH

GPU_ROOT=${XCLIPSE_GPU_ROOT:-/sys/kernel/gpu}
DEVFREQ_ROOT=${XCLIPSE_DEVFREQ_ROOT:-/sys/class/devfreq}
THERMAL_ROOT=${XCLIPSE_THERMAL_ROOT:-/sys/class/thermal}

read_value() {
    [ -r "$1" ] || return 0
    value=$(cat "$1" 2>/dev/null || true)
    printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

json_string() {
    escaped=$(printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')
    printf '"%s"' "$escaped"
}

json_number() {
    value=$1
    if printf '%s' "$value" | grep -Eq '^-?[0-9]+([.][0-9]+)?$'; then
        printf '%s' "$value"
    else
        printf 'null'
    fi
}

property() {
    if command -v getprop >/dev/null 2>&1; then
        getprop "$1" 2>/dev/null || true
    fi
}

find_devfreq() {
    for candidate in "$DEVFREQ_ROOT"/*; do
        [ -e "$candidate" ] || continue
        name=$(read_value "$candidate/name")
        case "${name:-${candidate##*/}}" in
            *sgpu*|*gpu*|*GPU*|*xclipse*) printf '%s' "$candidate"; return 0 ;;
        esac
    done
    return 0
}

find_thermal_zone() {
    for candidate in "$THERMAL_ROOT"/thermal_zone*; do
        [ -d "$candidate" ] || continue
        type=$(read_value "$candidate/type")
        [ "$type" = "G3D" ] && { printf '%s' "$candidate"; return 0; }
    done
    return 0
}

find_gpu_cooling() {
    for candidate in "$THERMAL_ROOT"/cooling_device*; do
        [ -d "$candidate" ] || continue
        type=$(read_value "$candidate/type")
        case "$type" in
            *gpu*|*GPU*|*sgpu*|*xclipse*) printf '%s' "$candidate"; return 0 ;;
        esac
    done
    return 0
}

command_name=${1:-status}
case "$command_name" in
    status|telemetry|capabilities) ;;
    profiles)
        printf '%s\n' '{"schema_version":1,"profiles":[{"id":"balanced","available":true,"mode":"stock"},{"id":"gaming","available":false,"reason":"No Xclipse write control has passed validation."},{"id":"battery_saver","available":false,"reason":"No Xclipse write control has passed validation."},{"id":"custom","available":false,"reason":"No Xclipse write control has passed validation."}]}'
        exit 0
        ;;
    *)
        printf '%s\n' '{"schema_version":1,"error":{"code":"unknown_command","message":"Supported commands: status, telemetry, capabilities, profiles."}}'
        exit 2
        ;;
esac

gpu_model=$(read_value "$GPU_ROOT/gpu_model")
gpu_clock=$(read_value "$GPU_ROOT/gpu_clock")
gpu_busy=$(read_value "$GPU_ROOT/gpu_busy")
gpu_temp=$(read_value "$GPU_ROOT/gpu_tmu")
gpu_min=$(read_value "$GPU_ROOT/gpu_min_clock")
gpu_max=$(read_value "$GPU_ROOT/gpu_max_clock")
gpu_governor=$(read_value "$GPU_ROOT/gpu_governor")

devfreq=$(find_devfreq)
devfreq_name=
devfreq_cur=
devfreq_target=
devfreq_min=
devfreq_max=
if [ -n "$devfreq" ]; then
    devfreq_name=$(read_value "$devfreq/name")
    devfreq_cur=$(read_value "$devfreq/cur_freq")
    devfreq_target=$(read_value "$devfreq/target_freq")
    devfreq_min=$(read_value "$devfreq/min_freq")
    devfreq_max=$(read_value "$devfreq/max_freq")
fi

g3d_zone=$(find_thermal_zone)
g3d_temp=
[ -n "$g3d_zone" ] && g3d_temp=$(read_value "$g3d_zone/temp")

gpu_cooling=$(find_gpu_cooling)
cooling_type=
cooling_state=
cooling_max=
if [ -n "$gpu_cooling" ]; then
    cooling_type=$(read_value "$gpu_cooling/type")
    cooling_state=$(read_value "$gpu_cooling/cur_state")
    cooling_max=$(read_value "$gpu_cooling/max_state")
fi

# Capture all thermal zones and cooling devices so CPU/SoC policy can be
# correlated with the GPU cap without assuming Samsung's zone numbering.
thermal_zones_json=
first_zone=1
for candidate in "$THERMAL_ROOT"/thermal_zone*; do
    [ -d "$candidate" ] || continue
    zone_type=$(read_value "$candidate/type")
    zone_temp=$(read_value "$candidate/temp")
    [ -n "$zone_type" ] || continue
    if [ "$first_zone" -eq 1 ]; then
        thermal_zones_json='['
        first_zone=0
    else
        thermal_zones_json="$thermal_zones_json,"
    fi
    zone_type_json=$(json_string "$zone_type")
    zone_temp_json=$(json_number "$zone_temp")
    thermal_zones_json="$thermal_zones_json{\"type\":$zone_type_json,\"temperature_millic\":$zone_temp_json}"
done
[ "$first_zone" -eq 1 ] && thermal_zones_json='[]' || thermal_zones_json="$thermal_zones_json]"

cooling_devices_json=
first_cooling=1
for candidate in "$THERMAL_ROOT"/cooling_device*; do
    [ -d "$candidate" ] || continue
    device_type=$(read_value "$candidate/type")
    device_state=$(read_value "$candidate/cur_state")
    device_max=$(read_value "$candidate/max_state")
    [ -n "$device_type" ] || continue
    if [ "$first_cooling" -eq 1 ]; then
        cooling_devices_json='['
        first_cooling=0
    else
        cooling_devices_json="$cooling_devices_json,"
    fi
    device_type_json=$(json_string "$device_type")
    device_state_json=$(json_number "$device_state")
    device_max_json=$(json_number "$device_max")
    cooling_devices_json="$cooling_devices_json{\"type\":$device_type_json,\"current_state\":$device_state_json,\"max_state\":$device_max_json}"
done
[ "$first_cooling" -eq 1 ] && cooling_devices_json='[]' || cooling_devices_json="$cooling_devices_json]"

device_model=$(property ro.product.model)
device_name=$(property ro.product.device)
soc_model=$(property ro.soc.model)
board_platform=$(property ro.board.platform)
kernel=$(uname -r 2>/dev/null || true)

if [ -n "$gpu_model" ]; then
    detection="supported"
else
    detection="unsupported"
fi

printf '{'
printf '"schema_version":1,'
printf '"command":'; json_string "$command_name"; printf ','
printf '"detection":'; json_string "$detection"; printf ','
printf '"device":{'
printf '"model":'; json_string "$device_model"; printf ','
printf '"name":'; json_string "$device_name"; printf ','
printf '"soc":'; json_string "$soc_model"; printf ','
printf '"platform":'; json_string "$board_platform"; printf ','
printf '"kernel":'; json_string "$kernel"
printf '},'
printf '"gpu":{'
printf '"model":'; json_string "$gpu_model"; printf ','
printf '"clock_khz":'; json_number "$gpu_clock"; printf ','
printf '"busy_raw":'; json_number "$gpu_busy"; printf ','
printf '"temperature_c":'; json_number "$gpu_temp"; printf ','
printf '"min_clock_khz":'; json_number "$gpu_min"; printf ','
printf '"max_clock_khz":'; json_number "$gpu_max"; printf ','
printf '"governor":'; json_string "$gpu_governor"
printf '},'
printf '"devfreq":{'
printf '"name":'; json_string "$devfreq_name"; printf ','
printf '"current_khz":'; json_number "$devfreq_cur"; printf ','
printf '"target_khz":'; json_number "$devfreq_target"; printf ','
printf '"min_khz":'; json_number "$devfreq_min"; printf ','
printf '"max_khz":'; json_number "$devfreq_max"
printf '},'
printf '"thermal":{'
printf '"g3d_temperature_millic":'; json_number "$g3d_temp"; printf ','
printf '"g3d_temperature_c":'; if [ -n "$g3d_temp" ]; then awk -v value="$g3d_temp" 'BEGIN { printf "%.1f", value / 1000 }'; else printf 'null'; fi; printf ','
printf '"gpu_cooling_type":'; json_string "$cooling_type"; printf ','
printf '"gpu_cooling_state":'; json_number "$cooling_state"; printf ','
printf '"gpu_cooling_max_state":'; json_number "$cooling_max"
printf '},'
printf '"thermal_zones":%s,' "$thermal_zones_json"
printf '"cooling_devices":%s,' "$cooling_devices_json"
printf '"controls":{"writes_enabled":false,"validated_write_nodes":[],"reason":"Read-only milestone. No GPU sysfs write path is enabled."}'
printf '}\n'
