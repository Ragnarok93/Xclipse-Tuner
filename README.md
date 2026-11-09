# Xclipse Tuner

Xclipse Tuner is a device-aware GPU tuning project for Samsung Xclipse GPUs, beginning with the Exynos 2400 / Xclipse 940 in the Galaxy S25 FE.

It consists of:

- A KernelSU module for device discovery, telemetry, and eventually validated profiles with automatic restoration.
- A native Android companion app using the project's OneUI Compose design system.

## Current status

**Read-only telemetry milestone.** The module exposes a versioned JSON CLI and packages it for KernelSU. The companion app adds configurable live polling, bounded in-memory trend charts, timestamped session CSV logging/export, device and thermal telemetry, and a capability-gated tuning panel. No GPU sysfs writes are implemented or enabled. Gaming, Battery Saver, and Custom profiles remain unavailable pending kernel-driver and vendor-policy validation.

## Safety principles

1. Never assume that a Mali or Adreno sysfs interface applies to Xclipse.
2. Discover controls at runtime; do not hardcode a GPU address as the only detection mechanism.
3. Read and validate a node's current value and supported values before considering a write.
4. Preserve original values before changing anything, and restore them on disable/uninstall where possible.
5. Never disable thermal throttling, Samsung GPU protection, IFPO, or other vendor safety mechanisms.
6. Keep a safe, no-tuning mode and make unsupported controls unavailable rather than guessing.
7. Do not force maximum clocks as a default gaming profile.

## Initial target

- Device: Samsung Galaxy S25 FE (SM-S731U)
- SoC: Exynos 2400
- GPU: Samsung Xclipse 940
- Kernel family: Samsung Android 6.1
- Root/module manager: KernelSU

The implementation remains capability-driven so other Xclipse generations can be added without assuming identical sysfs layouts. The current companion's profile controls remain informational/locked: no tuning write interface is enabled.

## Repository layout

- `tools/xclipse-diagnose.sh`: expanded read-only hardware discovery report.
- `tools/xclipse-tuner.sh`: read-only companion protocol v1 (`status`, `telemetry`, `capabilities`, `profiles`).
- `tools/test-xclipse-tuner.sh`: host-side fixture tests for the JSON protocol.
- `module.prop` and `service.sh`: KernelSU metadata and non-tuning boot log.
- `build/build-module.sh`: packages the diagnostic tools into a KernelSU ZIP.
- `companion/`: Kotlin/Compose app using `Ragnarok93/oneui-compose`.

## Module commands

After installing the ZIP in KernelSU, the companion command is available at:

```sh
su -c '/data/adb/modules/xclipse_tuner/bin/xclipse-tuner status'
su -c '/data/adb/modules/xclipse_tuner/bin/xclipse-tuner profiles'
```

The command returns JSON schema version 1. It reads the Samsung GPU sysfs interface, matching GPU devfreq node, G3D thermal zone, and GPU cooling-device state when present. The GPU busy field is intentionally named `busy_raw`; its driver semantics have not yet been verified.

## On-device investigation

Run the full read-only diagnostic script:

```sh
su -c 'sh /path/to/Xclipse-Tuner/tools/xclipse-diagnose.sh'
```

## Build the KernelSU module

From the repository root:

```sh
sh build/build-module.sh
```

The ZIP is written to `dist/`. It includes both read-only diagnostic tools and does not change GPU settings.

## Build the companion app

Builds are produced exclusively by GitHub Actions. Download the `xclipse-tuner-companion-debug` artifact from the latest successful Validate workflow run; no local Android Studio, Gradle, or SDK build is required.

## Next milestone

Identify the exact Samsung driver and vendor policy responsible for dynamic Xclipse constraints. Only after the sysfs store handlers, accepted values, permissions, thermal interactions, and restoration behavior are understood should a write interface be designed.
