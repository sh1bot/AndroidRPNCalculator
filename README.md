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
Trig functions use radians. π and e push exact constants. The `eˣ` key computes
e raised to X; it is a unary function, not decimal-exponent entry.

Stack rows sit at the bottom of the display, with newest values last. Small,
muted stack indices occupy a separate left column. New input and results scroll
to the bottom; you can scroll back through older values between actions. Entry
uses a larger font; entry and values use monospace, tabular digits.

Values display up to 12 significant digits. Large and tiny values use scientific
notation; tap SCI/ENG to switch to engineering notation (exponents divisible by
three). The newest stack value is displayed in full when it is a known exact
integer; older integers are compacted too. Rounded values are marked ≈. Values
too close to zero to resolve at the display's 1e-1000 detection limit are marked
explicitly. Display approximations never replace the exact arithmetic values.

Calculations run on a single background thread. Press Android Back or the visible
Cancel button while a calculation is running to interrupt it. Cancellation is
cooperative, so an indivisible library operation may take time to return. Both
legacy and modern Android Back callbacks are handled. Work is evaluated on a
private stack copy and committed only after display evaluation completes, so
errors and cancellation preserve the previous stack, entry, and undo history.
Rotation retains the session's exact objects. Closing the app or process death
clears the session; disk persistence is not implemented.

## Keyboard

Click the emulator/device window so it receives keys. Digits, decimal point,
`+ - * /`, Enter (or Space), Backspace, and Delete work directly. Escape cancels
a calculation or clears the unfinished entry. Ctrl+Z undoes, Ctrl+D duplicates,
Ctrl+S swaps X/Y, Ctrl+L clears the stack, and F9 changes sign. `^` raises Y to X.
Use `r` for square root, `i` for reciprocal, `s/c/t` for sin/cos/tan, `l` for ln,
`p` for π, `e` for Euler's number, and uppercase `E` for eˣ. Numpad keys are
supported. The `?` button shows this reference in the app. Input is queued in order
while calculations finish; cancelling or an error discards subsequent queued keys.
Some browser-based emulators may intercept shortcuts before Android receives them;
use the on-screen controls in that case.

## Structure

- `engine`: vendored Java arithmetic plus the independent RPN controller.
- `app`: native Android activity and keypad, with no third-party UI dependencies.
- `licenses`: upstream license text. Source headers retain all upstream notices.

## Verification and limitations

The engine is compiled with Java 17 compatibility and its 48 regression checks
pass, including exact decimals, rational division, symbolic simplification,
operand order, undo, stack operations, and error rollback.

GitHub Actions builds the Android APK and runs the engine regressions. Device
layout, accessibility, lifecycle handling, and installation still need hands-on
Android validation.
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

