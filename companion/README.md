# Xclipse Tuner Companion

The companion will be a native Android app (Kotlin + Jetpack Compose).

It will communicate with the installed KernelSU module through a narrow, versioned command/response interface. The app must not write GPU sysfs nodes directly. The privileged module-side controller will own capability detection, validation, apply/restore operations, and audit logging.

## Planned screens

- **Dashboard:** detected GPU/SoC, driver/control availability, current profile, live telemetry where readable.
- **Profiles:** Balanced, Gaming, Battery Saver, and Custom.
- **Controls:** only controls proven writable and safe on the detected device; unsupported controls are visibly unavailable.
- **Diagnostics:** export a read-only hardware report and recent module log.
- **Safety:** restore saved values, disable tuning, and show the state of thermal/vendor protections.

## Integration contract

The app will consume structured JSON from a module-side CLI. The command names, schema version, authorization behavior, and error codes will be specified and tested before the UI is connected. The app will never construct arbitrary sysfs paths or pass unchecked raw values to the privileged layer.
