# Project assessment and hardening

Date: 2026-09-06

## Initial public prerelease

The public name is now `zero finish input`; internal identifiers and encrypted
data formats retain their existing identity. The v0.1.0 prerelease adds the supplied
logo and launcher artwork, installation/contribution documentation, private GitHub
security reporting and packaged license/source notices. Validation and outstanding
device/signing work are recorded in [release-validation.md](release-validation.md).

## Starting point

The repository is a functional Android IME with ten registered modules, real
librime/JNI, full and nine-key Chinese input, an offline English engine, a small
reference dictionary engine, encrypted personalization, a private authenticated
clipboard and data-only language packs. Dependencies already follow stable
engine ports and bounded background queues. This checkout has no Git metadata.

The initial Gradle test/privacy/Lint run passed. There were 84 distinct unit
tests (160 executions including Android build variants), and the existing 32
device tests passed on the connected Android 17 x86_64 emulator. The missing
coverage was concentrated in encrypted storage and user dictionary boundaries.

## Completed scope

1. Unknown editor classes/variations previously reached the permissive default.
   They now use the sensitive path, clear preceding composition/candidates, and
   never instantiate a suggestion engine or access personal data.
2. Manual phrases could exceed the reader's 20,000-entry limit. Updates now enforce
   that limit and a 5 MiB compact JSON limit before writing; overflow cannot create
   an unreadable file or silently evict manually managed phrases.
3. User dictionary import now uses fixed-schema streaming validation. Malformed
   UTF-8, excessive nesting, duplicate fields/IDs/identities, unsupported languages,
   invalid lengths/scalars and trailing documents are rejected before mutation.
   Merges are indexed by phrase identity and cannot alias another local candidate.
4. Persistence now publishes a new memory snapshot only after a successful
   encrypted write. Clear invalidates older waiting updates and waits for active
   writes before deleting the file/key. A failed deletion blocks reads and writes
   until the user retries successfully. Tests inject storage failures through the
   new EncryptedStore port and use separate real Keystore aliases for crypto tests.
5. The phrase page confirms import merge rules and plaintext export scope before
   file selection. It uses localized safe failures, bounded inputs, lifecycle-aware
   callbacks, standard toolbar actions and a stable delete icon. Recycled rows and
   temporary byte buffers release their contents. Successful management changes
   invalidate the IME's personal-suggestion cache.

Format 1, the encrypted envelope, file name and production key alias are unchanged.
No migration, networking, permissions, external components or native algorithm
changes were introduced. See [ADR 0005](adr/0005-user-lexicon-boundary.md).

## Verification

- New failing regression tests reproduced the permissive editor default,
  over-capacity addition and acceptance of nested unknown JSON before the fixes.
- Core tests cover unknown editors following a composing session and password,
  visible password, web password, PIN, email, URI and privacy preference policies.
- The expanded device suite passed all 50 tests: 11 dictionary boundary tests,
  four real Keystore/storage tests, three phrase UI tests and the original 32.
- Phrase UI tests also passed separately in the actual system light and dark
  modes (three tests each), with 320/411/800 dp geometry, portrait/landscape,
  Chinese/English labels, confirmation gating and text contrast assertions.
  Android 17 synthetic night contexts did not consistently select application
  color resources, so actual system theme changes were used and restored.
- Generated public-fixture images were visually inspected for English/light
  export, Chinese/dark export and long phrase rows. No user data was captured.
- `./tools/package-test-apk.ps1` passed: 88 distinct unit tests (168 executions
  across JVM/Debug/Release variants), Debug/Release merged-manifest privacyCheck,
  Lint, real Rime builds for all three ABIs, and signature/ABI/permission checks.
- Artifact: `app/build/outputs/test-apk/20260906-154348/ZeroInput-0.1.0-debug-universal.apk`
  (28,903,783 bytes), with `SHA256SUMS.txt` beside it. SHA-256:
  `f963cf6d519884f6bd5b82ffdd542218a0382681b55b88f31933210d812ce925`.

## Next priorities

- Apply equivalent bounded streaming validation to language-pack manifests and
  verify actual expanded manifest bytes rather than trusting ZIP size metadata.
- Expand authentication cancellation and delayed/double-callback tests for the
  private clipboard, including settings changes and editor switches. These remain
  predominantly manual acceptance scenarios; this iteration did not change them.
- Measure Chinese typing and touch latency on physical ARM devices and target
  applications. Emulator timings are not evidence of OEM/device performance.
- Continue localizing the remaining management pages and handling their worker
  failures consistently. Avoid adding more engine features before these checks.

No Release APK was built or signed in this iteration. Physical ARM execution,
real biometric/credential UX and external document-provider overwrite behavior
still require maintainer/device acceptance. The delivered APK is a Debug test build.
