# Exact RPN for Android

An initial native Android RPN calculator using the original AOSP `UnifiedReal`,
`BoundedRational`, and Hans Boehm constructive-real arithmetic code. No floating
point conversion occurs in input parsing, stack arithmetic, or display formatting.

## Automatic builds

[![Android build](https://github.com/sh1bot/AndroidRPNCalculator/actions/workflows/android.yml/badge.svg)](https://github.com/sh1bot/AndroidRPNCalculator/actions/workflows/android.yml)

GitHub Actions runs the engine regression checks and builds a debug APK on every
push and pull request. You can also start it manually from **Actions → Android
build → Run workflow**. Open a successful run and download **ExactRPN-debug-…**
under **Artifacts**, unzip it, and install `app-debug.apk` on your Android device.
Artifacts are retained for 30 days. These are debug builds, not signed release
builds for the Play Store. Each runner generates its own debug signing key, so
installing a later build may require uninstalling the previous one (clearing its
data).

## Build

Open this directory in Android Studio with JDK 17 (or 21), Android SDK 35, and
Gradle 8.11.1. The Android Gradle plugin is pinned to 8.9.2. If using the command
line, install Gradle 8.11.1 and set `ANDROID_HOME` to your SDK:

```sh
gradle :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

A Gradle wrapper is not bundled. Android Studio can use a local Gradle distribution.
The app requires Android 8.0 or newer and requests no network permissions.

Run arithmetic/stack regression checks with a JDK, without Android or Gradle:

```sh
./scripts/test-engine.sh
```

Or use `gradle :engine:regression` with the Android SDK configured.

## Use

Enter `0.1`, ENTER, `0.2`, + to obtain exactly 3/10. The bottom row of the stack is
X (marked 1); the row above it is Y. Binary operations calculate Y op X.
ENTER pushes the pending entry; when no entry is pending it duplicates X.
Operations automatically push a pending entry before consuming operands. SWAP
exchanges X and Y; DROP removes X. UNDO restores the entire previous state,
including pending input, for up to 100 actions. ± changes the entry sign or negates X.
Trig functions use radians. π and e push exact constants.

Known exact values appear as integers, fractions, or symbolic constants. Other
values show an approximation marked ≈ with 24 fractional decimal places. This
finite display is never used in later arithmetic. The original library provides
bounded approximation error; this app does not promise correctly rounded final
digits for every transcendental expression.

Calculations run on a single background thread. Press Android Back while a
calculation is running to interrupt it. Failed operations restore their previous
stack and entry. Rotation retains the session's exact objects. Closing the app or
process death clears the session; disk persistence is not implemented.

## Structure

- `engine`: vendored Java arithmetic plus the independent RPN controller.
- `app`: native Android activity and keypad, with no third-party UI dependencies.
- `licenses`: upstream license text. Source headers retain all upstream notices.

## Verification and limitations

The engine is compiled with Java 17 compatibility and its 16 regression checks
pass, including exact decimals, rational division, symbolic simplification,
operand order, undo, stack operations, and error rollback.

The Android APK has not been compiled or run here: the environment has no Android
SDK or Gradle, and its shell download proxy is unavailable. Device layout,
accessibility, lifecycle handling, and installation still need Android validation.
This is an initial implementation, not a Play Store release.

## Source provenance

AOSP-origin files imported without modifications:

- `UnifiedReal.java`, `BoundedRational.java`, `StringUtils.java` from
  https://github.com/LineageOS/android_packages_apps_ExactCalculator/tree/lineage-18.1/src/com/android/calculator2
  (Apache 2.0).
- `CR.java`, `UnaryCRFunction.java`, `StringFloatRep.java` and `COPYRIGHT.txt` from
  https://github.com/lineage-next/crcalc/tree/main
  (SGI/HP permissive notices retained in source and `licenses/CR-COPYRIGHT.txt`).

`licenses/SHA256SUMS` records the imported source contents. The new controller
and Android interface are separate from the unmodified upstream classes.

## License

New project code is licensed under Apache 2.0; see `LICENSE`. Vendored code
retains its upstream license and copyright notices; see `NOTICE` and `licenses/`.
