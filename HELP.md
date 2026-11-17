# Using Exact RPN

## Your first calculation

Type `12`, press `Enter`, type `3`, then press `÷`. The result is `4`. Enter separates operands; choose the operation after entering them.

The bottom value is X; the value above it is Y. Subtraction, division and powers calculate Y − X, Y ÷ X and Yˣ.

## Entering and editing numbers

Typing starts entry mode. Enter commits the entry; an operator or function commits it before calculating. If the operation fails, the entered value stays on the stack. Enter on an empty entry, or Back/Escape, returns to stack mode.

The trash key becomes backspace while editing. `CE` clears the entry without leaving edit mode. Long-press the decimal key to enter an exponent: `1.23e4` means `1.23 × 10⁴`.

## Working with the stack

Enter in stack mode duplicates X. The trash key drops X. `⇅` swaps X and Y. Long-press trash to move X to the top displayed row; long-press `⇅` to bring the top displayed row down to X. Backspace never rolls the stack while editing.

Long-press a digit in stack mode to copy that level to X: `1` picks X, `2` picks Y, and `0` picks level 10. The original values remain in place.

`AC` clears the stack. `⎌` undoes; long-press it to redo. During a long operation it becomes Cancel. Your calculation is restored when you reopen the app.

## Functions and constants

`ƒ()` opens functions; `ƒ⁻¹()` opens inverse and complementary functions. Tap the active panel button again to close it. Inverse trigonometric results use the selected angle unit. Long-press `±` to apply `|x|`, the absolute value.

`rem` gives Y remainder X with the sign of Y; `mod` uses the sign of X. `⌊x⌋` rounds toward negative infinity; `⌈x⌉` rounds toward positive infinity. `x!` requires a nonnegative integer. `Factorise` replaces an integer greater than 1 with its prime factors, smallest first: `84 Factorise` gives 2, 2, 3, 7, with 7 at X. Large factorisations can take time; Cancel stops them.

The constants are π, e and φ (the exact golden ratio). Long-press the `π`/`τ` key to switch it between pi and tau; the choice is remembered. Long-press `ƒ⁻¹()` in landscape to mirror the layout for left-handed use; long-press it again to switch back. A short press inserts the displayed constant. Tau is represented as exactly 2π.

## Display modes

`RAD/DEG/GRAD` selects radians, degrees or gradians. `SCI/ENG` selects scientific or engineering notation. The base button cycles through `DEC/HEX/DOZ` for decimal, hexadecimal and dozenal entry and display; dozenal uses ↊ and ↋ for ten and eleven. The fraction button cycles through `DEC/FRAC/IMPR` for decimals, mixed fractions and improper fractions. Irrational values remain decimal. Display modes never replace the stored exact value: a value too wide for its row is shown in compact notation, but keeps its exact value for arithmetic and copying.

`LOC/DOT/NONE` selects phone-locale formatting, decimal dot with thin-space grouping, or the locale decimal without grouping. Scientific and engineering notation use the selected base, drawn as ×10 with a raised exponent, where 10 is read in that base.

## Copying, pasting and selection

Long-press any stack row to copy just that value, including during entry or selection. Copy uses the configured decimal separator without grouping. Fraction modes copy plain text such as `7/3` or `2 1/3`. Decimal mode preserves terminating decimals and copies recurring or irrational values to 40 significant digits.

Long-press `Enter` or press Ctrl+V to paste. Multiple non-empty lines are added to the stack in order, with the last line at X, as one undoable action. Every line must be a valid, unambiguous number; otherwise an error identifies the line and nothing is added. Blank lines are ignored. Any current entry is committed first and must also be valid. Pasted text stays in entry mode. Ambiguous or invalid input is shown red and blocks Enter, operators and functions until corrected: edit the separator to match the configured decimal character, or remove ambiguous grouping.

Tap a stack row to select it; checkboxes appear, and tapping a row toggles it. Selection is available only in stack mode. Back/Escape exits selection.

In selection mode `Enter` becomes `Pick`, `AC` selects all rows, and the undo key clears the selection. Trash drops the selected values, `+` becomes `Σ` (sum), `×` becomes `Π` (product), `÷` becomes `x̄` (mean), and `−` becomes `σ` (standard deviation). Pick, sum, product, mean and standard deviation append results without removing the originals. Unary functions replace each selected value in place as one undoable action; if any result fails, none are changed. Binary operations are unavailable while selecting.

Root mean square is composed, not built in: select the values, apply `x²` from the `ƒ⁻¹()` panel to square each selected value in place, press `x̄` to append the mean of the squares, clear the selection with the undo key, then press `√`.

Large calculations or copied text can exceed the app’s 256 KiB data limit. The action then reports an error and preserves the previous calculation. Reducing the stack or copying fewer rows may help.

## Keyboard

Digits and the decimal separator enter numbers; A–F enter hexadecimal digits when hexadecimal mode is active. `+ - * /` operate. Enter/Space commits or duplicates; Backspace/Delete edits or drops. Left/Right and Home/End move the cursor; Shift selects text. Escape cancels a calculation or entry.

Ctrl+Z undoes, Ctrl+Y redoes, Ctrl+S swaps, Ctrl+L clears, Ctrl+V pastes, and F9 changes sign. Scientific shortcuts: r (square root), i (reciprocal), s/c/t (sin/cos/tan), l (ln), p (π), e (Euler’s number), E (eˣ), ^ (power), % (remainder), ! (factorial), f (factorisation).

## Quick Settings and the lock screen

Add Exact RPN using the phone’s Quick Settings editor. When locked, the tile opens a separate `TEMP` calculation that never loads or saves your normal stack, even after unlocking. Close it to discard it. When unlocked, the tile opens the normal calculator. Lock-screen Quick Settings availability depends on the phone; it must have been unlocked once after reboot.
