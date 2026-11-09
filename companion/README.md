# Xclipse Tuner Companion

Native Android companion app (Kotlin + Jetpack Compose), styled with the project's OneUI Compose library.

## UI design source

The app uses `Ragnarok93/oneui-compose` through the JitPack artifact `com.github.TrainerSnow:oneui-compose:0.7.0`. The root `OneUITheme` and `OneUITheme.colors` palette provide adaptive One UI theming, SESL surfaces/backgrounds, primary accents, text hierarchy, and functional status colors.

## Build

Open this `companion/` directory in Android Studio, or build with a compatible Gradle installation:

```sh
gradle -p . :app:assembleDebug
```

A local Android SDK and JDK 17 are required.

## Privileged interface

The app invokes only the fixed module command:

```sh
su -c '/data/adb/modules/xclipse_tuner/bin/xclipse-tuner status'
```

The command returns JSON schema version 1. The app never accepts arbitrary sysfs paths or passes user-controlled values to a root shell. The module owns hardware discovery and any future validated controls.

## Current behavior

- Displays device, SoC, GPU, kernel, GPU clock, raw busy counter, temperature, governor, devfreq constraints, G3D thermal-zone temperature, and GPU cooling state.
- Shows Balanced as the no-override profile.
- Shows Gaming, Battery Saver, and Custom as unavailable until their controls have been validated.
- Does not write GPU sysfs nodes or alter thermal/vendor power management.
