# Xclipse Tuner

Xclipse Tuner is a device-aware GPU tuning project for Samsung Xclipse GPUs, beginning with the Exynos 2400 / Xclipse 940 in the Galaxy S25 FE.

It consists of:

- A KernelSU module that detects supported Samsung/Xclipse controls, applies validated profiles, monitors state, and restores the user's original values.
- A native Android companion app for device detection, telemetry, profiles, and tuning controls.

## Current status

**Early development — diagnostic-only foundation.** The initial module does not change GPU settings. Hardware writes will remain disabled until the relevant sysfs nodes, accepted values, permissions, thermal interactions, and restoration behavior have been verified on target devices.

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

The implementation should remain capability-driven so other Xclipse generations can be added without assuming identical sysfs layouts.

## Repository layout

- `tools/xclipse-diagnose.sh`: read-only on-device discovery script.
- `module.prop`: KernelSU module metadata.
- `service.sh`: deliberately non-tuning service placeholder for the diagnostic-only stage.
- `companion/`: Android companion app project (to be scaffolded after the control/API contract is established).

## On-device investigation

Clone the repository on the phone or copy `tools/xclipse-diagnose.sh` to Termux, then run:

```sh
su -c 'sh /path/to/Xclipse-Tuner/tools/xclipse-diagnose.sh'
```

The script only reads sysfs/proc/property data. It does not write GPU settings. Review the report before attempting any tuning.

## Development

The first milestone is to establish the exact Xclipse 940 control surface and safe state transitions. The companion app and tuning profiles will be built against that verified contract, not guessed sysfs paths.
