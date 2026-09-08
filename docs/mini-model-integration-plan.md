# RoBERTa-Mini evaluation and conditional integration

Date: 2026-09-08. Status: the user explicitly accepted Mini INT8 integration as
a default-off experiment despite the original quality gate failure. The user selected
the local Android 16 emulator as the current acceptance target. Emulator timing
does not establish physical ARM performance.

## Preferred artifact

`uer/roberta-mini-wwm-chinese-cluecorpussmall`, revision
`5e567169018f5cf84cf1d85a2b391c8d51211eda`: four BERT layers, hidden size 256,
four heads, vocabulary size 21,128. The source checkpoint is 35,184,327 bytes.
Its SHA-256 is
`47727c489bfa5c57b53149c83b85a35655b5e49f7355202e272ec50c9c4467d6`.

Mini INT8 is the preferred evaluated variant: 14,898,764 graph bytes plus a
109,540-byte vocabulary. Its graph SHA-256 is
`5fb4dbe2c618e8757258253e10481ea9181e8a7b9a8efea03ee70c3a5ca19446`.
FP16 also fits the existing 20,000,000-byte budget, but is slower.
Preserve tied embedding/decoder tensors where the quantization layout permits.
FP16 storage, inference
arithmetic, runtime memory and compressed APK size are separate measurements.
Further INT8 quantization is justified only by measured size/latency needs and
a new quality comparison. Tiny remains a comparison, not an automatic substitute.

## Evaluation work

1. Verify pinned artifacts and measure FP32 versus FP16 storage and ranking on
   the existing public screening fixtures. Generalize the existing exact BERT
   evaluator only for the two pinned architectures. Project vocabulary logits
   only at masked positions and verify parity with full projection.
2. Export a portable ONNX scoring graph and compare its numeric outputs with
   the host reference before evaluating Android. Use a separate developer-only
   benchmark application with no permissions, editor access or production module
   dependency. Keep model files and generated results in ignored `build/` paths.
3. Run bounded, single-thread CPU inference on an Android 16 / API 36 x86_64
   emulator. Measure model load, eight-candidate p50/p95, process CPU and PSS.
   Synthetic fixture timing excludes real IME dispatch and rendering.
4. Resolve explicit model redistribution terms and evaluate held-out candidate
   lists from the current Rime baseline. The existing 24 constructed pairs and
   character-frequency prior cannot satisfy the quality gate.

## Conditional product integration

Under the confirmed experimental exception, introduce a separately owned model scorer implementation
behind an engine-api scoring port. Keep lifecycle scheduling and bounded context
ownership outside the Rime/JNI adapter. The controller owns visible candidate
identity and selection routing; reranking must preserve original page and ID.

- Separate experimental setting, default off. Disabled means no model loading,
  worker initialization or inference. All inference is offline.
- Initially use at most 16 preceding Chinese characters committed by this IME
  in the same editor session, at most eight eligible whole-reading candidates,
  and exactly two Chinese characters per scored candidate. Longer candidates keep
  their original ordering: scoring eight eight-character phrases exceeds the
  latency budget. Do not read arbitrary editor text,
  the clipboard or stored personal history to obtain context.
- Preserve explicit user choices, fixed composition segments, related-reading
  tiers and personal-candidate priorities. Skip unsupported tokens, absent context,
  incomplete candidates and active candidate browsing/touch gestures.
- The model scores existing candidates; it neither decodes pinyin nor generates
  arbitrary completions. Confidence and original ordering must be calibrated
  independently of the held-out evaluation set before any first-choice changes.
- One worker with a bounded latest-request slot. Stale completion, settings or
  privacy change, cursor movement, session replacement, view closure and service
  destruction invalidate results and clear transient context. A timeout keeps
  deterministic candidates usable and does not block a key or editor operation.
- Sensitive, unknown, email/URI, incognito, no-learning and learning-disabled
  editors cannot supply model context. No logs, files, saved state or cross-session
  plaintext context. No user-data format change or permission is required.

## Acceptance and stopping conditions

Keep the existing quality thresholds: held-out top-one improvement at least two
percentage points and clean-spelling regression at most 0.5 percentage point.
Weights and vocabulary must total at most 20 MB. Eight-candidate p95 should be
at most 10 ms on the user-selected Android 16 emulator, with physical ARM still
unverified. Report cold load, runtime footprint and APK overhead separately.

The user explicitly confirmed the UER project's Apache-2.0 license as the model's
licensing basis, linking https://github.com/dbiir/UER-py. Record that project
decision and the source revision in any eventual model distribution notice; the
Hugging Face model card itself still has no license metadata.

The frozen evaluation uses 24 calibration and 94 held-out contexts, with disjoint
pinyin groups and actual candidates from the current Rime engine. A confidence
margin of 1.25 is selected only on calibration data, then frozen. On held-out data,
top-one correctness changes from 36/94 to 47/94 (+11.70 points), but one originally
correct first choice changes to an incorrect one (1.06 points). This exceeds the
original 0.5-point regression limit. Do not adjust the margin using this test split
and continue describing it as held out. See `small-model-evaluation.md`.

The quality gate failure remains recorded and is not reclassified as a pass.
The user's explicit confirmation authorizes the default-off experiment with the
frozen margin; it does not establish broad input accuracy or physical ARM speed.

Any eventual product change requires core/engine regression tests, selection and
stale-result tests, privacy negative tests, Debug/Release manifest review, Lint,
all supported ABI build checks, notices, architecture/threat-model updates and an
ADR for the new runtime/context boundary. This plan does not weaken those checks.
