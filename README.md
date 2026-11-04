# Exact RPN for Android

An RPN calculator using exact rational and constructive-real arithmetic.
[Download the latest development APK](https://github.com/sh1bot/AndroidRPNCalculator/releases/latest/download/ExactRPN.apk).

## Use

Enter a number, press **Enter**, enter another number, then choose an operation:
`12 Enter 3 ÷` gives `4`. The newest stack value is X (level 1); the value above it
is Y. Binary operations calculate **Y op X**.

In stack mode, **Enter** duplicates X and **Drop** removes it. Typing starts entry
mode: the stack moves above the input and Drop becomes backspace. Deleting the
last digit leaves entry mode active; **Enter** on an empty entry returns to the
stack. Results return to the bottom after operations; exact integers display in
full there, while values above an entry use compact notation.

**Swap** exchanges X and Y; **Undo** restores the previous state. **Functions**
expands the scientific keypad. Tap **RAD/DEG/GRAD** to change angle units and
**SCI/ENG** for display notation. **eˣ** calculates e to the power of X.

Keyboard: digits, decimal point, and `+ - * /` enter numbers and operations.
Enter/Space commits or duplicates; Backspace/Delete acts as backspace or Drop.
Escape cancels a calculation or entry. Ctrl+Z undoes, Ctrl+S swaps, Ctrl+L clears,
and F9 changes sign. Scientific shortcuts: `r` square root, `i` reciprocal,
`s/c/t` sin/cos/tan, `l` ln, `p` π, `e` Euler's number, `E` eˣ, and `^` power.

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
