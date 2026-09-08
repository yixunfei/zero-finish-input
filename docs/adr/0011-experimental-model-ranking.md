# ADR 0011: Bounded offline short-word model scoring

Status: accepted, 2026-09-08; maintainer explicitly confirmed the experimental
quality exception and UER Apache-2.0 licensing basis.

## Context

Rime supplies useful pinyin candidates but lacks contextual homophone selection.
The preferred RoBERTa-Mini INT8 model meets the 20 MB weight/vocabulary budget and
the 10 ms Android 16 emulator inference p95 budget for short words. It improves
the constructed held-out set but exceeds the original regression limit. Long
phrases also exceed the latency budget. Context introduces a new transient
protected asset and ONNX Runtime adds a substantial native dependency.

## Decision

Ship a separate default-off experiment with the frozen 1.25 confidence margin.
`engine-api/CandidateScorer` is a worker-confined, synchronous scoring port.
`model-scoring` implements tokenization, pinned asset validation and ONNX resource
ownership without knowing editors, stores, settings, Rime or UI. This isolates a
replaceable native inference dependency rather than placing it in the Rime adapter.

`ime-core` owns the eligibility and ranking policy, wipeable context primitive,
bounded worker and original candidate routes. `app/ModelRankingCoordinator`
connects successful platform commits to context and applies session/privacy/UI
gates. Core accepts results only for the current revision and original candidate
identity. Rime publishes canonical readings through `Candidate.input`; generic
core code never parses native comments or calls JNI.

One lazy serial executor owns a runtime session and the latest pending request.
All initialization/I/O/inference/destruction stays on that worker. Cancellation
invalidates in-flight work immediately; bounded native computation can finish
before buffers are cleared. Main-thread delivery is coalesced and contains only
opaque revision/winner values. No per-input timing, text, tokens or scores are
logged or saved. Disabled input creates no worker or model.

## Consequences and alternatives

The feature may still promote an incorrect first candidate. It adds runtime and
APK overhead beyond the 15.01 MB model, and emulator evidence does not establish
ARM phone performance. The native allocator cannot guarantee erasure of every
internal tensor copy; application-owned buffers are wiped promptly and sessions
released at lifecycle boundaries. No context survives sessions or enters storage.

Keeping the strict quality gate would leave integration unimplemented; the user
explicitly chose the experiment. Tiny was measured but is not substituted for the
preferred Mini. Full phrase scoring, arbitrary editor context, cloud ranking and
model-based generation are excluded. Reduced runtimes, additional vocabularies or
policy changes require fresh measurements and a new independent test split.
