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
    ls -l "$file" 2>/dev/null
    if [ -r "$file" ]; then
        printf 'value: '
        cat "$file" 2>/dev/null
        printf '\n'
    else
        echo 'value: [not readable]'
    fi
    if [ -w "$file" ]; then
        echo 'access: shell reports writable (not tested by writing)'
    else
        echo 'access: shell does not report writable'
    fi
    if command -v ls >/dev/null 2>&1; then
        ls -lZ "$file" 2>/dev/null | sed 's/^/selinux: /'
    fi
}

section "DEVICE"
for prop in ro.product.model ro.product.device ro.soc.model ro.board.platform ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do
    printf '%s=' "$prop"
    getprop "$prop" 2>/dev/null
done
printf 'kernel='
 uname -a 2>/dev/null
printf 'uid='
 id 2>/dev/null

section "KERNEL GPU INTERFACE"
GPU=/sys/kernel/gpu
if [ -d "$GPU" ]; then
    ls -la "$GPU" 2>/dev/null
    for name in gpu_model gpu_freq_table gpu_clock gpu_governor gpu_available_governor gpu_busy gpu_tmu gpu_max_clock gpu_min_clock gpu_cl_boost_disable gpu_ifpo_control gpu_ifpo_runtime_control; do
        show_file "$GPU/$name"
    done
else
    echo "$GPU not present"
fi

section "GPU DEVFREQ DEVICES"
FOUND=0
for dev in /sys/class/devfreq/*; do
    [ -e "$dev" ] || continue
    name=${dev##*/}
    case "$name" in
        *gpu*|*GPU*|*sgpu*|*xclipse*)
            FOUND=1
            echo
            echo "### $dev"
            for attr in name governor available_governors cur_freq min_freq max_freq min_freq_limit max_freq_limit available_frequencies trans_stat polling_interval; do
                show_file "$dev/$attr"
            done
            ;;
    esac
done
[ "$FOUND" -eq 1 ] || echo "No GPU-named devfreq device found under /sys/class/devfreq"

section "GPU-RELATED PLATFORM PATHS"
for pattern in /sys/devices/platform/*gpu* /sys/devices/platform/*sgpu* /sys/devices/platform/*xclipse*; do
    [ -e "$pattern" ] || continue
    ls -ld "$pattern" 2>/dev/null
    [ -d "$pattern/devfreq" ] && ls -la "$pattern/devfreq" 2>/dev/null
done

section "THERMAL SNAPSHOT"
for zone in /sys/class/thermal/thermal_zone*; do
    [ -d "$zone" ] || continue
    type=$(cat "$zone/type" 2>/dev/null)
    case "$type" in
        *gpu*|*GPU*|*sgpu*|*xclipse*|*soc*|*SOC*)
            printf '%s type=%s temp=' "$zone" "$type"
            cat "$zone/temp" 2>/dev/null
            ;;
    esac
done

section "KERNELSU MODULE STATE"
printf 'KernelSU: '
ksud -V 2>/dev/null || echo 'ksud version unavailable'
printf 'SELinux: '
getenforce 2>/dev/null
printf 'Kernel modules containing gpu/xclipse names (if exposed):\n'
grep -iE 'gpu|xclipse|sgpu|amdgpu' /proc/modules 2>/dev/null || true

section "END"
echo "Read-only discovery complete. No settings were changed."
