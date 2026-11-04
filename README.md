# Exact RPN for Android

An RPN calculator using exact rational and constructive-real arithmetic.
[Download the latest development APK](https://github.com/sh1bot/AndroidRPNCalculator/releases/latest/download/ExactRPN.apk).

## Use

Enter a number, press **Enter**, enter another number, then choose an operation:
`12 Enter 3 ÷` gives `4`. The newest stack value is X (level 1); the value above it
is Y. Binary operations calculate **Y op X**.

**Enter** with no pending input duplicates X. **Swap** exchanges X and Y;
**Drop** removes X; **Undo** restores the previous state. **Functions** expands
the scientific keypad. Trigonometry uses radians; **eˣ** calculates e to the power
of X. **SCI/ENG** selects display notation without changing the exact values.
Tap **?** for keyboard shortcuts.

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.11.1. Set `ANDROID_HOME` to your SDK,
then run:

```sh
gradle :engine:regression :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio using a local Gradle distribution;
a Gradle wrapper is not bundled. The app requires Android 8.0 or newer.

## Sources and license

New code is licensed under [Apache 2.0](LICENSE). The arithmetic engine includes:

- `UnifiedReal`, `BoundedRational`, and `StringUtils` from the
  [AOSP ExactCalculator](https://android.googlesource.com/platform/packages/apps/ExactCalculator/),
  imported through [LineageOS](https://github.com/LineageOS/android_packages_apps_ExactCalculator/tree/lineage-18.1)
  under Apache 2.0.
- Hans-J. Boehm's [constructive-real library](https://android.googlesource.com/platform/external/crcalc/),
  imported through [lineage-next](https://github.com/lineage-next/crcalc),
  with AOSP Apache 2.0 additions and the original SGI/HP permissive notices.

Upstream notices are retained in the source, [NOTICE](NOTICE), and [licenses](licenses).
