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

In stack mode, long-press a digit to **Pick** that level: 1 copies X, 2 copies Y,
and 0 copies level 10. The original stays in place and the copy becomes X.
During entry, holding a digit enters that digit.

Your stack and entry are restored when you reopen the app.

Add **Exact RPN** using your phone’s Quick Settings editor. From the lock screen,
the tile opens a separate temporary calculation; it never loads or saves the
normal stack, even after unlocking. Close it to discard it. Clipboard access
follows Android’s rules. When unlocked, the tile opens the normal calculator.
Quick Settings must be accessible while locked; availability depends on the phone
and its settings. The phone must have been unlocked once after a reboot.

**Swap** exchanges X and Y. **Clear** clears the stack. During entry it becomes
**CE**, which clears only the line and stays in edit mode. Enter on an empty line
or Back/Escape returns to stack mode. **Undo** reverses the last change; long-press
Undo to redo. During a long calculation, Undo becomes
**Cancel**. **f()** opens the scientific functions; **f⁻¹()** opens their inverse
and complementary functions. Tap the active button again to close, or the other
to switch sets. Wide, short windows place the keypad beside the stack; functions
open to the left of the digits when there is room. Closing them returns that
space to the stack. Tap **RAD/DEG/GRAD** to change angle units and
**SCI/ENG** for display notation. **DEC/FRAC/IMPR** cycles between decimals,
mixed fractions (`2 1/3`) and improper fractions (`7/3`). Fraction digits are smaller, raised and lowered
around the slash; the whole-number part stays full size. Irrational results remain decimal.

**f⁻¹()** switches sin/cos/tan to asin/acos/atan, ln to eˣ, log₁₀ to 10ˣ, square root to square,
power to the Xth root of Y, and remainder to modulo. Inverse trig results use the
selected angle unit. **rem** calculates Y remainder X, with the sign of Y;
**mod** uses the sign of X. **rem** is the default; f⁻¹() reveals mod.
**⌊x⌋** rounds down toward negative infinity; its complement **⌈x⌉** rounds up toward positive infinity.
Long-press **π/τ** to switch that key between pi and tau; the choice is saved
between app launches. A short press inserts the displayed constant, with tau
represented as exactly `2 × π`. Switching the key does not insert a value.
**φ** pushes the exact golden ratio `(1 + √5) / 2`. Both accept exact rational operands. **x!** computes
factorial for nonnegative integers. **Factorise** replaces a positive integer greater
than 1 with its prime factors, pushed smallest first: `84 Factorise` gives `2, 2, 3, 7`
(with 7 at X). Large factorisations may take time; Cancel stops them.
In stack mode, long-press **Drop** to move X to the top displayed row.
During entry, holding backspace only deletes from the entry.
Long-press **Swap** to move the top displayed value down to X, committing any
current entry first. Short presses still drop/backspace and swap respectively.

Keyboard: digits, decimal point, and `+ - * /` enter numbers and operations.
Enter/Space commits or duplicates; Backspace/Delete edits or drops X.
Left/Right and Home/End move the cursor; hold Shift to select.
Escape cancels a calculation or entry. Ctrl+Z undoes, Ctrl+Y redoes, Ctrl+S swaps, Ctrl+L clears,
and F9 changes sign. Scientific shortcuts: `r` square root, `i` reciprocal,
`s/c/t` sin/cos/tan, `l` ln, `p` π, `e` Euler's number, `E` eˣ, `^` power, `%` remainder, `!` factorial, and `f` factorisation.

**LOC/DOT/NONE** cycles between phone-locale numbers, decimal dot with thin-space
display grouping, and the locale decimal with no grouping. Copied numbers follow this choice but never
include grouping. Copy follows **DEC/FRAC/IMPR**, using plain text such as `7/3` or `2 1/3`;
DEC preserves exact terminating decimals and copies recurring/irrational values
to 40 significant digits. Tap a stack value
to copy it. Tap a stack label to select its row and show checkboxes; while selecting,
tap anywhere on a row to toggle it. Selection is available only in stack mode.
**Undo** becomes a stacked checkbox icon: checked to select every row, empty to
clear the selection.
Back/Escape also exits selection.

During selection, **Clear** becomes a copy icon and **Swap** becomes **Pick**.
Copy writes the selected values one per line, and Pick appends copies in display
order. **Drop** removes the selected values. **+** becomes **Σ** (sum) and **×**
becomes **Π** (product); both append an exact result, preserving the original
values and selection.

Long-press **Enter** or Ctrl+V to paste into entry. Safe input can proceed with **Enter**, an operator, or a function.
Ambiguous or invalid input stays red and blocks all three until corrected. Tap to position the cursor or select text; the decimal key replaces
a selected separator explicitly. Backspace/Delete edit at the cursor. In entry mode, long-press the decimal key
to insert `e` for a power of ten, for example `1.23e4`. Scientific and engineering
stack displays use ×10 with a raised exponent; entry and clipboard text use `e`.

## Build

Requires JDK 17, Android SDK 35, and Gradle 8.11.1. Set `ANDROID_HOME` to your SDK,
then run:

```sh
gradle :engine:regression :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio using a local Gradle distribution;
a Gradle wrapper is not bundled. The app requires Android 8.0 or newer.

UI text and error templates live in `app/src/main/res/values/strings.xml`.
Translations go in the corresponding `values-<language>` directory; Android chooses
the matching catalogue and falls back to English. Engine error codes and numeric
arguments stay independent of translated text.

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



