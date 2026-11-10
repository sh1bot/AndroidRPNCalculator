# Numerical boundary

The private Android service evaluates exact stack arithmetic and constructive reals.
The caller owns entry parsing, display modes, locale, error translation and clipboard text.
This is an internal protocol, not an exported Android API or a scripting language.

## Numerical requests

A request supplies an integer radix (2–36), an arbitrary-precision exclusive bound
`limit > 1`, and a byte budget for optional exact rational data. It receives:

- A signed integer `num` and signed integer `scale`, representing
  `num × base^(-scale)`, with `abs(num) < limit`.
- An exactness flag for that scaled result.
- Optionally, a normalized exact numerator and positive denominator. These describe
  the original value, independently of the scaled approximation.
- For an unresolved near-zero constructive real, a bound describing
  `abs(value) < base^(-zeroBound)`, without claiming exact zero.

The evaluator chooses the finest radix scale whose rounded coefficient fits the
exclusive bound, rounding halfway cases to an even coefficient. Overflow after
rounding is handled by rounding the original value at a coarser scale, rather than
rounding twice. Redundant trailing radix factors are removed. Constructive-real
evaluation retains guard digits; unresolved zeros use a bounded search.

For `base=10, limit=100000`, exact examples include `(123, 0)` for 123,
`(123, 2)` for 1.23 and `(123, -3)` for 123000. Binary, hexadecimal, dozenal,
and non-power limits such as 2000 use the same contract. A bound smaller than the
radix can produce a rounded zero at a coarse scale.

Exact rational data is omitted when it exceeds the optional budget. A strict exact
request instead fails if a known rational cannot fit; an irrational still receives
a scaled approximation. No calculation is silently replaced with an approximation.
Whole Binder messages remain limited to 256 KiB.

## Caller and state

The caller normally requests 12 significant decimal digits and bounded exact data;
copy requests 40 digits and exact rational data on demand. SCI/ENG, mixed/improper
fractions, grouping, glyphs and translated messages are caller decisions. Display
mode changes reuse cached numeric results without arithmetic requests.

Canonical exact batches cross the service boundary after the caller validates all
clipboard lines. Entry snapshots retain their original restoration metadata for
undo/redo; arithmetic consumes canonical input and does not interpret their locale.
Individual entry edits remain local. The saved calculation format and exact
expression graph remain compatible with version 0.9.
