# ADR 0004: Chinese nine-key input and an offline reference engine

Status: accepted

## Context

The approved scope adds Chinese nine-key input while retaining the English full
keyboard, and a non-default offline engine with a limited bundled dictionary for
input-pipeline comparisons. Rime remains the production Chinese implementation.

## Decision

Expose keyboard layout in ChineseInputOptions and advertise nine-key support as
an engine capability. Expose reading choices in immutable snapshots and use a
small optional ReadingSelectionEngine port. Rime compiles numeric spellings with
its existing algebra; it continues to own parsing, segmentation and ranking.
Build the reading table from the existing pinned public dictionary. JNI permits
only bounded spelling input, and reading changes cannot overwrite fixed segments.

Add engine-dictionary as an independent JVM implementation of InputEngine with a
bounded public dictionary and prefix index. Its limited capabilities are explicit
in settings. Default selection stays Rime. No additional native dependency is
introduced. Both engines use the existing
privacy policy, preparation ownership and encrypted personalization boundaries.

Capture selected engine and layout in warm-up identity. Normal changes take effect
when composition becomes idle; privacy tightening keeps its immediate cancellation
behavior. Persist only ordinary preference values, with no encrypted format change.

## Consequences

The second engine can help distinguish UI/editor latency from Rime work, but its
small vocabulary and simple prefix ranking cannot substitute for sentence input.
It intentionally does not advertise nine-key, fuzzy, abbreviated or traditional
input. A future full engine can implement those capabilities behind the same ports.

Nine-key deployment costs occur on the bounded worker and retain the existing
preparation status. Per-session reading history is bounded and cleared on reset,
commit and close. Input correctness is covered by native, contract and device
tests; simulator timing does not establish performance on physical ARM devices.

## Alternatives

A separate pinyin parser would duplicate Rime's domain logic. Another native engine
would expand build and license maintenance beyond the confirmed limited-dictionary
test scope. Neither is needed for this iteration.
