# Input experience validation

Date: 2026-09-06

## Environment and method

- Android emulator `sdk_gphone16k_x86_64`, Android 17, AVD `FlutterTest`.
- Debug build with real librime and `-PrequireRime=true`.
- InputPipelineTest uses an attached ZeroInput IME window and a dedicated editor
  with personalized learning disabled. It dispatches MotionEvents to the actual
  key views. This is not a measurement of OS touch injection or physical sensors.
- The same 57 public-fixture keystrokes were measured before and after the touch
  change. Editor TextWatcher updates and the next Choreographer frame are measured
  independently. No user input, package identities or private data are reported.

## Measurements

| Measure | Before p50 / p95 | Final run p50 / p95 |
| --- | --- | --- |
| Editor update | 19.84 / 30.16 ms | 5.44 / 13.65 ms |
| Next frame | 14.86 / 16.42 ms | 14.80 / 17.46 ms |

The former posted click returned before the engine and editor work started.
The new immediate click includes that work in dispatch time, so dispatch timings
cannot be compared directly. Final dispatch p95 was 17.19 ms; final editor maximum
was 14.23 ms. Earlier verification runs after the change had editor p95 values of
7.49 to 11.13 ms, illustrating emulator scheduling variance. Frame times did not
show a corresponding improvement.

The reported 300-500 ms pause per character was not reproduced. These results
establish reduced deferred-click latency in this fixture, not a diagnosis or
guarantee for a particular phone, OEM keyboard host or target application.

## Verification

- `./tools/package-test-apk.ps1`: all JVM/Android unit-test variants, privacyCheck,
  Lint, real Rime builds for arm64-v8a/armeabi-v7a/x86_64, universal Debug package,
  signature, permissions and ABI validation passed.
- `./tools/test-input-experience.ps1 -Serial emulator-5554`: 32 device tests passed.
  This includes native nine-key decoding, reading selection/undo, partial candidate
  selection, paging, full keyboard regressions, settings, continuous touch-target
  coverage, overlapping pointers, cancelled gestures and literal symbol digits.
- The actual IME/editor test commits ten fixture phrases in rapid succession for
  each of Rime full keyboard, Rime nine-key and the offline dictionary engine.
- 320/411/800 dp layouts in light/dark modes passed geometry checks. Constructed
  fixture images were rendered and small portrait/light plus wide landscape/dark
  images were inspected. The images contain public fixture text only.
- Existing script conversion, abbreviations, fuzzy flags, initialization/fallback,
  privacy policy and stale preparation regressions remain passing.

## Remaining device acceptance

Physical ARM execution, OEM input hosts, target-app latency, physical touch timing
and hardware haptics still require a target phone. No physical device was attached.
This iteration produces a Debug test APK; a new signed Release artifact was not
requested or generated. Authentication scenarios remain subject to the existing
manual acceptance checklist in README.md.

The offline dictionary engine is deliberately limited to bundled reference words;
it does not implement sentence generation, traditional output, abbreviation,
fuzzy spelling or nine-key input. Rime remains the default.
