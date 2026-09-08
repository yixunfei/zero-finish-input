# Input improvement plan

Status: approved implementation delivered; model integration withheld after screening.
Date: 2026-09-08.

## Objective

Improve continuous Chinese candidate browsing, phrase composition, landscape
usability and measured input efficiency. Add optional experimental typo handling
and evaluate a small offline Chinese scoring model. Preserve offline operation,
editor isolation and the existing encrypted personalization boundary.

## Initial inspection

- Rime is the default engine. Its schema enables sentence generation and
  completion, and its adapter exposes native candidate paging. A page size is not
  the total native candidate count.
- The immediate fallback has a small fixed dictionary, takes eight results and
  declines paging. The reference dictionary engine caps lookup at 64 results.
- The expanded candidate panel replaces one page at a time. It does not load
  subsequent pages during scrolling. Personal suggestions are limited to three
  entries and are prepended again to every native page.
- Rime has a successful partial-selection test for `nihao`. Existing coverage
  does not establish arbitrary out-of-dictionary phrase creation, segment undo,
  corrected reading provenance or the full personalization round trip.
- The controller learns commits using the preceding snapshot's raw input. It
  has no explicit engine-provided canonical reading for a corrected composition.
- Fullscreen extraction is already disabled. Landscape rows use fixed heights.
  Current rotation coverage checks a particular fullscreen fixture's visibility;
  it does not establish usability at small available heights or across repeated
  live rotations and all input panels. The reported cause is not yet reproduced.
- Native processing remains synchronous with editor updates. JNI uses a shared
  mutex. Personal lookup sorts matches on a worker; it does not perform this
  scan on the IME thread. Both deserve measurement before optimization.
- Existing performance reports describe earlier emulator runs, not a current
  baseline or physical-device result. An x86_64 emulator is available locally;
  no physical phone was attached during inspection.

## Product decisions for review

1. Continuous browsing means retrieving further relevant results on demand, with
   finite work and bounded memory per request. Exhausted sources stop cleanly.
   Repeated or unrelated results must not manufacture an endless list.
2. Offer exact readings and words first, then alternative valid segmentation and
   clearly identified related readings. Existing fuzzy-pinyin preferences remain
   meaningful. Keyboard typo expansion belongs to its separate experimental
   switch, which defaults off.
3. Phrase editing includes the active composition: confirm a segment,
   keep unresolved syllables, undo a previous selection, edit and complete the
   phrase. The user also requires explicitly reopening the last committed word.
   This must verify the original connection, collapsed selection and exact text
   before modifying the editor; changed or unverifiable editors fail closed.
4. A completed, explicitly selected new phrase uses the existing learning setting
   and encrypted user lexicon. Partial, cancelled, stale or disallowed input must
   not become a learned phrase. Preserve the current persisted data format if its
   existing fields suffice; any required format change needs a reviewed strategy.
5. Recommended model scope is offline candidate scoring, with model weights and
   vocabulary together at most 20 MB. Report runtime and packaged size separately.
   Embeddings alone do not provide pinyin decoding or next-word prediction.
   Model integration depends on verified quality, latency, memory and licensing.
6. Work is serial by default. No agents are delegated without user agreement.
   Gradle, device tests and native builds use one coordinator to avoid contention.

## Implementation sequence

### 1. Establish reproducible failures and a quality baseline

- Use public constructed fixtures for exact pinyin, ambiguous segmentation,
  abbreviations, unknown multiword phrases, long compositions, near readings,
  repeated letters, missing letters and adjacent-key errors.
- Compare documented and reproducible offline behavior of Rime and mainstream
  Chinese keyboards, including Gboard, Microsoft Pinyin and Sogou. Record product
  version, source, feature availability and whether each result was actually
  tested. Do not infer proprietary algorithms or equate cloud features with
  offline capabilities.
- Measure first-choice/top-five accuracy, phrase completion rate, corrections
  per fixture and keystrokes per committed character using held-out examples.
- Reproduce landscape problems with an attached IME and an actual fixture editor,
  including portrait-to-landscape-to-portrait while typing and browsing panels.
- Capture latency distributions, process CPU time, Java/native/PSS memory,
  allocation/GC behavior and cold/warm startup using synthetic input only.

### 2. Repair landscape window and panel sizing

- Own available-window sizing and insets in the Android adapter, with keyboard
  geometry and panel presentation in ime-ui.
- Recompute from current window bounds after rotation, resize and inset changes;
  account for navigation, cutouts and optional reminder rows.
- Maintain usable key targets and visible editor/cursor space through an
  appropriate compact layout. Do not simply scale the entire view or fill the
  screen. Apply the same geometry to candidate, expression and clipboard panels.
- Validate real input, candidate selection, backspace and Enter after rotation;
  also cover font scaling, themes and the configured keyboard heights.

### 3. Build continuous, correctly routed candidate browsing

- Keep candidate retrieval and reading expansion inside engine adapters. Define
  only the required engine-owned continuation/selection contract in engine-api.
  Do not expose native sessions or Rime structures to the UI or controller.
- Browse exact candidates lazily, then valid segmentation and related-reading
  tiers, with deterministic order, deduplication and explicit exhaustion.
- Use recycled candidate views and a bounded page window. Retain or regenerate
  earlier pages through engine-owned cursors instead of retaining unlimited text.
- Bind selections to composition generation and stable candidate identity,
  including native page identity. Paging must neither rewrite composition nor
  select an entry that shifted after an asynchronous update.
- Include personal suggestions in the same routing and paging policy. Avoid
  repeatedly injecting the same three entries on each page.
- Extend the immediate fallback to support usable syllable-level composition
  without dictionary scans or initialization on the first input frame. Keep the
  reference engine's advertised capabilities accurate.

### 4. Complete phrase composition and local learning

- Reuse Rime segmentation and sentence generation. Add the missing user intents
  and engine state needed to choose smaller segments and undo selections.
- Preserve fixed segments and unresolved input; define backspace, apostrophe,
  punctuation, literal digits and final-commit behavior consistently.
- Carry the full canonical reading and completed phrase through the engine-owned
  commit result where needed. Do not learn a typo or only the final suffix as the
  complete phrase's reading.
- Learn through PersonalizationStore after valid completion and demonstrate that
  the phrase is offered again in a subsequent permitted session.
- Cancel composition-related state on editor/session changes and invalidate
  pending learning on privacy tightening, data clear and destruction.

### 5. Improve matching and add experimental typo handling

- Rank by match quality, segmentation, public word frequencies and permitted
  personal frequency/recency. Measure each strategy against the baseline.
- Add an independent settings switch, default off, with capability-aware behavior
  for full pinyin and nine-key input.
- Evaluate bounded adjacent-key alternatives, transposition, missing/extra letters
  and valid-syllable checks. Touch geometry may supply a few weighted alternatives
  for the active key event; it must not become recorded touch history.
- Keep original input recoverable and correction choices visible. Explicit user
  selections take priority. Incorrect automatic changes count as regressions.
- Clear ephemeral correction state when composition ends or becomes invalid.

### 6. Optimize measured resource costs

- Address demonstrated native/locking, candidate allocation, repeated conversion,
  personalization lookup or rendering costs in their owning modules.
- Put expensive expansion/scoring on bounded cancellable workers if measurement
  requires it. Keep editor mutations ordered and reject stale results.
- Record p50/p95/p99 and maximum key-to-editor/candidate latency, paging latency,
  startup time, CPU use and memory across repeated sessions.
- Use identical fixtures and controlled warm-up for before/after results. Report
  emulator and physical ARM results independently, with experiment switches both
  off and on. Do not claim improvement from code inspection alone.

### 7. Evaluate and conditionally integrate a small model

- Compare a compact statistical language model with a quantized neural candidate
  scorer. Choose based on measured benefit and maintenance cost, not the label.
- Verify model/data redistribution rights, exact source revisions and hashes,
  tokenizer/operator support, three-ABI feasibility and Android 8 compatibility.
- Evaluate on held-out public fixtures against the improved non-model baseline;
  include typo false positives and ranking regressions, not only good examples.
- Load only after opt-in, on a worker. Limit context, threads, candidates and work
  per request. Late or failed inference leaves deterministic candidates usable.
- Retain no private training corpus or cross-session plaintext model state.
  Respect sensitive-editor and personalization restrictions.
- Deliver measured size, quality and resource results. A placeholder interface or
  untrained model is not a working model deliverable. If no suitable model passes,
  document that result and the concrete missing prerequisites.

## Module ownership and documentation

- engine-api: minimal immutable candidate/commit/correction contracts.
- engine-rime: native continuation, segmentation, reading expansion and fallback.
- ime-core: selection commands, composition coordination and privacy gating.
- ime-ui: recycled browsing, stable geometry and transient touch evidence.
- app: platform sizing, settings, lifecycle and bounded execution integration.
- user-data: existing encrypted phrase persistence and indexed lookup if justified.
- Other engines: contract updates and regressions required by shared changes.
- Update architecture, threat model, SECURITY, relevant ADRs, README and validation
  reports as behavior changes. Update notices and pinned assets for any new data,
  library or model. Keep source files and functions within repository limits.

## Acceptance gates

- Exhaustively enumerate fixture candidates beyond former limits without skipped,
  duplicate, incorrectly selected or stale entries; demonstrate clean exhaustion.
- Complete an unknown fixture phrase by segment selection, undo and correction;
  verify final text and later encrypted-personalization retrieval.
- Negative tests cover privacy tightening, editor switches, old callbacks,
  cancelled corrections, rejected worker submissions and clear/write races.
- Attached UI tests verify editor visibility and usable input after repeated
  rotation across full keyboard, nine-key, numeric and auxiliary panels.
- Run affected module tests and all affected engine contracts, privacyCheck,
  Debug/Release merged manifest checks, app/UI Lint and Debug packaging.
- Native changes require real librime with requireRime=true, all three ABI builds
  and explicit native-failure fallback tests. Execute physical ARM tests when a
  phone is available; a build alone does not establish device behavior.
- Publish comparable synthetic benchmark results and identify unmeasured cases.
  Default-off experiments must incur no model initialization or inference cost.
- Supply a Debug test APK with checksums and precise validation limitations.

## Confirmed scope and remaining device information

- The user approved the sequence and requires post-commit word reopening.
- Primary phone model, Android version, engine/layout and example failing input.
- Model integration follows evaluation and only proceeds when the gates pass.
  Report weights/vocabulary, added package footprint and runtime memory separately.

## Delivery interpretation

See `input-improvement-validation.md` for implemented behavior and measured limits.
The model was evaluated as a research candidate and is not integrated. Physical
ARM, older/OEM editors, long-duration power/leak testing and broad held-out quality
remain unverified. Competitor comparison uses official documentation, not invented
binary measurements. Continuous browsing stops when finite relevant sources end;
deduplication covers the resident window, and no unlimited private text cache exists.
