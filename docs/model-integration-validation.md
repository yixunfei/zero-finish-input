# Mini integration validation

Date: 2026-09-08. The approved Mini INT8 experiment is integrated and off by
default. The original quality regression gate remains failed; the maintainer's
explicit acceptance permits this experiment without changing the frozen policy.

## Behavior and privacy

Production tests cover actual Rime candidates, the Mini scorer, Android editor
commits and controller routing. With the public context `老师正在传授`, the model
promotes `知识`; tap, Space, Enter and literal punctuation select that original
native candidate. A separate attached IME test verifies that Space commits the
displayed first candidate when personal priorities apply. Personal candidates
are deliberately excluded from model promotion.

Tests cover disabled initialization, private/unknown/password/PIN/email/URI and
no-learning editors, context invalidation, failed commits, cursor/deletion and
reconversion, held touches, bounded replacement, late/repeated results, load and
dispatcher failure, shutdown during initialization, expired delivery, confidence
ties, unsupported tokens, input bounds and corrupt public asset checks.

Worker requests carry no controller, InputConnection, UI snapshot or candidate
string. Requests own bounded character arrays; their buffers and token
arrays are wiped, tensors closed, and stale results rejected by generation and
controller revision. Public model data is the only model file stored on-device.

## Android 16 measurements

User-selected AVD: `ZeroInputModelApi36`, API 36, x86_64, two cores and 2,048 MB.
The isolated production-scorer run waited for the application's Rime warm-up
before measuring. It used 16 Chinese context characters, eight two-character
candidates, one intra/inter-op CPU thread, ten warmups and 100 timed samples.

| Metric | Result |
| --- | ---: |
| Model and vocabulary | 15,008,304 bytes |
| Initialize using verified cached graph | 84.22 ms |
| Scoring p50 / p95 | 6.17 / 6.53 ms |
| Whole-process CPU during 100 batches | 623 ms |
| Whole-process PSS before / after | 77,812 / 126,346 KiB |
| Observed process PSS increase | 47.4 MiB |

Initialization includes hash/vocabulary verification and runtime/session setup,
but the public model file was already deployed and storage caches were warm.
Scoring includes tensor construction, inference and score extraction. These are
scorer timings, not keyboard-to-editor or touch-to-photon latency. PSS is a
whole-process measurement including managed/native allocation, not weight memory.
Other production fixture runs recorded p95 between 6.35 and 9.31 ms. Physical ARM
latency, sustained battery use and long-duration memory pressure remain unmeasured.

The original standalone benchmark and frozen quality results are preserved in
[small-model-evaluation.md](small-model-evaluation.md). On 94 held-out constructed
contexts, correct first choices rose from 36 to 47, but one previously correct
choice became incorrect (1.06 points, exceeding the original 0.5-point limit).
These figures do not establish population accuracy or mainstream-IME parity.

## Original integration checks

- 171 Debug/JVM unit tests passed, zero failures or skips. The packaging script
  also ran the root `test` task, including Release unit tests.
- Six model instrumentation tests passed, including actual native/scorer/editor
  integration and the privacy matrix. The routing test exercises four commit
  commands independently with an empty personalization port.
- The full 128-test Android 16 run reported zero failures: 125 passed and three
  clipboard platform tests skipped because ZeroInput was not the default IME.
  After explicitly selecting it, two of those three passed.
- `SystemClipboardPlatformTest.confirmationPageClearsOnlyAfterTheForegroundButtonIsPressed`
  failed waiting for `ClipboardGuardStatus.WAITING`. An isolated retry failed at
  the same boundary. The pre-model APK from `20260908-074905`, SHA-256
  `3bf273b307c817bc7702a08f17e3d7d419eb6841fa645f887db11300e9fcfec3`, also failed
  identically on this emulator. That run was not counted as passing. The test
  startup failure is addressed in the follow-up below. The final model APK and
  original default IME were restored after the original comparison.
- An earlier cross-application selection-menu test failed in a combined run,
  then passed in isolation and in the final full run. No test or privacy rule was
  weakened to suppress either platform result.
- `privacyCheck`, app/model/UI Lint, actual Rime Debug and unsigned Release builds
  passed for arm64-v8a, armeabi-v7a and x86_64. Debug/Release merged manifests retain
  `allowBackup=false`, existing permissions and no INTERNET; Release is nondebuggable.
- All seven final Debug/Release APKs contain only the approved Mini graph and
  vocabulary, verified by size/SHA-256, and the appropriate Rime/ONNX native libraries.
  Third-party license texts are bundled. The universal package excludes ONNX's
  unsupported x86 ABI. Old incremental packaging output was moved to an ignored
  backup before repackaging to remove 33 MB of unreferenced archive space.
- Modified/new model source files are under 1,500 lines and methods under 100.
  Local document links and delivered SHA-256 manifests were checked.

## Clipboard validation follow-up

Date: 2026-09-09. The Android 16 confirmation failure reproduced with guard status
`UNAVAILABLE`: ZeroInput was selected, but its real IME service had not been bound.
The Debug editor requested its keyboard before Android served the focused view.
Moving that request after window focus also exposed an incomplete system binding
after instrumentation restarted the IME process. Waiting for `WAITING` alone
could not recover that binding.

The Debug fixture now requests input after window focus. Before enabling the
guard or writing public clipboard data, the platform test driver establishes a
visible ZeroInput keyboard with bounded input restart/show requests. Preparation
has a six-second deadline and spaces requests at least 250 ms apart.
The existing five-second guard deadline and real foreground-clear assertions
remain in force. Confirmation interactions wait for the dialog's focused window.
Runtime regressions preserve the requirement that an unattached service cannot
subscribe; platform regressions preserve the item when confirmation is cancelled
or monitoring is disabled. These changes are confined to Debug/test fixtures.

The full follow-up run also reproduced the selection-menu failure. The separate
public source placed text underneath Android 16's status-bar touch region using
fixed padding, so a long press could open the notification shade instead of the
native menu. Its layout now applies system-bar and display-cutout insets. The
driver waits for stable bounds in the focused source window and synchronously
delivers a real touch down/up pair. The existing passive assertion still checks
the native PROCESS_TEXT menu; it does not add an action or invoke the import itself.

Final validation on `ZeroInputModelApi36` (API 36, x86_64):

- All 131 instrumentation tests passed in 185.993 seconds, with zero failures or
  skips, including the six model tests and all clipboard platform cases.
- The confirmation case passed five consecutive process-start runs. The native
  selection-menu and sharesheet pair passed three consecutive runs after the
  source-layout fix. Neither test requires a passing run to prime the next one.
- Root `test` completed successfully: 334 JVM cases across Debug, Release and
  pure-JVM modules, with zero failures, errors or skips. This includes the 171
  Debug/JVM cases; unchanged Gradle tasks reused up-to-date results.
- `privacyCheck`, Debug/Release merged-manifest checks, `:app:lintDebug`, actual
  Rime x86_64 Debug and instrumentation builds passed with `-PrequireRime=true`.
  The source Activity is absent from both main APK manifests, and the editor
  fixture is absent from Release. Backup and permission restrictions are intact.

Evidence is under ignored `build/clipboard-validation/`: `diagnostic-confirmation.txt`
preserves the reproduced `UNAVAILABLE` failure; `full-device.txt` preserves the
intermediate selection-menu failure; `full-device-final.txt` and
`complete-checks.log` record final validation. `retry-input-cold-1.txt` through
`retry-input-cold-5.txt` and `insets-1.txt` through `insets-3.txt` record the repeated
checks. `artifacts/` contains the tested x86_64 Debug and instrumentation APKs plus
`SHA256SUMS.txt`; both APK signatures and installed-file hashes were verified.
These are test-only artifacts installed with `adb install -r -t`, not releases.

The model quality gate, model/runtime selection and physical-device measurement
gaps described above remain unchanged. Android 16 emulator checks do not establish
vendor background behavior or physical biometric/credential behavior. This
follow-up did not rebuild ARM/Release APKs or repeat model performance measurements.

The subsequent [v0.2.0 release validation](releases/v0.2.0-validation.md) records
the new-version three-ABI rebuild, public package checks and device rerun.

## Original integration deliverables (2026-09-08)

Debug-signed test artifacts are in
`app/build/outputs/test-apk/20260908-230414/`, with `SHA256SUMS.txt` and the license
notice archive. They are test APKs, not production-signed releases.

| Debug APK | Bytes | SHA-256 |
| --- | ---: | --- |
| `zero-finish-input-0.1.0-debug-x86_64.apk` | 64,178,613 | `6bce698ff6349d59933794c773bfe455ac90aad20e1fff5ef7f780b3d6ccbc4b` |
| `zero-finish-input-0.1.0-debug-arm64-v8a.apk` | 58,677,545 | `4f8c5a80fad5b9f5a30836d555cbbb7f0cbb564c83dd3dba02634e4e8c6b0adb` |
| `zero-finish-input-0.1.0-debug-universal.apk` | 122,620,981 | `ee9854020599aa5ae3a4357125df3eb7cab639137f5f2519051f531709e6e7a4` |

Unsigned Release APK sizes are 49,202,103 bytes for arm64-v8a, 40,113,075 for
armeabi-v7a and 54,996,251 for x86_64. Runtime/native/APK sizes are separate from
the 15.01 MB model budget. The full runtime is substantial; a reduced runtime has
not been substituted without another numerical and ABI evaluation.

Detailed generated evidence is under ignored `build/model-evaluation/`:
`integration-model-metrics.txt`, `integration-model-final.txt`,
`integration-full-device-final.txt`, `integration-platform-final.txt`,
`integration-platform-confirmation-recheck.txt`, `integration-platform-baseline.txt`,
`integration-package-final.log`, `integration-release-final.log` and
`integration-artifact-check.json`.
