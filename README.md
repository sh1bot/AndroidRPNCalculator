# Exact RPN for Android

An RPN calculator using exact rational and constructive-real arithmetic.
[Download the stable APK](https://github.com/sh1bot/AndroidRPNCalculator/releases/latest/download/ExactRPN.apk) · [Development builds](https://github.com/sh1bot/AndroidRPNCalculator/releases?q=0.10-dev).

The arithmetic pairs exact rational arithmetic with constructive reals —
numbers that can be evaluated at increasing precision. Approximate displays
refine near a rounding boundary, up to a precision limit. The approach is described by Hans-J. Boehm in
[Small-data computing: Correct calculator arithmetic](https://research.google/pubs/small-data-computing-correct-calculator-arithmetic/)
(Communications of the ACM, August 2017); the AOSP calculator's own
[arithmetic overview](https://android.googlesource.com/platform/packages/apps/ExactCalculator/+/7265aa3a922a7e7c0389dae4b26cca562aa777a6/docs/arithmetic-overview.html)
covers the same ground.

## Use

How to use the calculator is documented in [HELP.md](HELP.md).

## Build

Requires JDK 17, Android SDK 36, and Gradle 8.11.1. Set `ANDROID_HOME` to your SDK,
then run:

```sh
gradle :engine:regression :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio using a local Gradle distribution;
a Gradle wrapper is not bundled. The app requires Android 8.0 or newer.

## Sources and license

Exact RPN's own code — the app and the engine's `dev.exactrpn` packages — is new
and licensed under [Apache 2.0](LICENSE). The arithmetic engine also includes:

- `UnifiedReal`, `BoundedRational` and `StringUtils` (the engine's
  `com.android.calculator2` package), from the
  [AOSP ExactCalculator](https://android.googlesource.com/platform/packages/apps/ExactCalculator/),
  imported through [LineageOS](https://github.com/LineageOS/android_packages_apps_ExactCalculator/tree/lineage-18.1).
  Copyright (C) 2015, 2016 The Android Open Source Project, under
  [Apache 2.0](licenses/APACHE-2.0.txt); attribution in [NOTICE](NOTICE).
- The constructive-real library (the engine's `com.hp.creals` package) by
  Hans-J. Boehm, from [AOSP crcalc](https://android.googlesource.com/platform/external/crcalc/),
  imported through [lineage-next](https://github.com/lineage-next/crcalc).
  It carries the original Silicon Graphics and Hewlett-Packard permissive
  notices, reproduced in the [constructive-real library notices](licenses/CR-COPYRIGHT.txt),
  with AOSP additions under Apache 2.0.

The full licence texts are also bundled in the app itself: the three-dot menu's
Licenses section shows them offline. Upstream notices are retained in the
source files, [NOTICE](NOTICE) and [licenses](licenses).
