#!/system/bin/sh
# Xclipse Tuner hardware discovery.
# READ-ONLY: this script never writes to sysfs or changes device state.

PATH=/system/bin:/system/xbin:/vendor/bin:/sbin:/data/data/com.termux/files/usr/bin
export PATH

section() {
    printf '\n===== %s =====\n' "$1"
}

show_file() {
    file="$1"
    [ -e "$file" ] || return 0
    printf '\n--- %s ---\n' "$file"
    ls -lZ "$file" 2>/dev/null || ls -l "$file" 2>/dev/null
    if [ -r "$file" ]; then
        printf 'value:\n'
        cat "$file" 2>&1
        printf '\n'
    else
        echo 'value: [not readable by this process]'
    fi
    echo 'note: permission bits are informational; no write test is performed.'
}

show_dir_files() {
    dir="$1"
    [ -d "$dir" ] || return 0
    echo
    echo "### $dir"
    for file in "$dir"/*; do
        [ -f "$file" ] || continue
        show_file "$file"
    done
}

section "DEVICE"
for prop in ro.product.model ro.product.device ro.soc.model ro.board.platform ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do
    printf '%s=' "$prop"
    getprop "$prop" 2>/dev/null
done
printf 'kernel='
uname -a 2>/dev/null
printf 'identity='
id 2>/dev/null
printf 'SELinux='
getenforce 2>/dev/null

section "SAMSUNG XCLIPSE CONTROL INTERFACE"
GPU=/sys/kernel/gpu
if [ -d "$GPU" ]; then
    ls -laZ "$GPU" 2>/dev/null
    for name in gpu_model gpu_info gpu_freq_table gpu_clock gpu_governor gpu_available_governor gpu_busy gpu_tmu gpu_max_clock gpu_min_clock gpu_siop_max_clock gpu_mm_min_clock gpu_cl_boost_disable gpu_disable_llc_way gpu_ifpo_control gpu_ifpo_runtime_control sgpu_pm_monitor sgpu_swap_ctrl mem_info ctx_mem_info; do
        show_file "$GPU/$name"
    done
    show_dir_files "$GPU/proc"
else
    echo "$GPU not present"
fi

section "GPU DEVFREQ"
FOUND=0
for dev in /sys/class/devfreq/*; do
    [ -e "$dev" ] || continue
    name=${dev##*/}
    case "$name" in
        *gpu*|*GPU*|*sgpu*|*xclipse*)
            FOUND=1
            show_dir_files "$dev"
            ;;
    esac
done
[ "$FOUND" -eq 1 ] || echo "No GPU-named devfreq device found under /sys/class/devfreq"

section "GPU PLATFORM AND PROFILER"
for dir in /sys/devices/platform/22200000.sgpu /sys/devices/platform/exynos-gpu-profiler /sys/devices/platform/gpu_buffer_dma_heap; do
    show_dir_files "$dir"
done

section "THERMAL ZONES (ALL)"
for zone in /sys/class/thermal/thermal_zone*; do
    [ -d "$zone" ] || continue
    echo
    printf '%s ' "$zone"
    printf 'type='
    cat "$zone/type" 2>/dev/null
    printf 'temp='
    cat "$zone/temp" 2>/dev/null
    for attr in mode policy available_policies; do
        [ -r "$zone/$attr" ] || continue
        printf '%s=' "$attr"
        cat "$zone/$attr" 2>/dev/null
    done
    for trip in "$zone"/trip_point_*_type "$zone"/trip_point_*_temp; do
        [ -r "$trip" ] || continue
        printf '%s=' "${trip##*/}"
        cat "$trip" 2>/dev/null
    done
done

section "GPU COOLING DEVICES"
for cool in /sys/class/thermal/cooling_device*; do
    [ -d "$cool" ] || continue
    type=$(cat "$cool/type" 2>/dev/null)
    case "$type" in
        *gpu*|*GPU*|*sgpu*|*xclipse*)
            echo "$cool type=$type"
            for attr in cur_state max_state; do
                [ -r "$cool/$attr" ] || continue
                printf '%s=' "$attr"
                cat "$cool/$attr" 2>/dev/null
            done
            ;;
    esac
done

section "KERNEL MODULES"
grep -iE 'gpu|xclipse|sgpu|exynos.*profiler' /proc/modules 2>/dev/null || true

section "END"
echo "Read-only discovery complete. No settings were changed."
