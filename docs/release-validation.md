# v0.1.0 release validation

Validated on 2026-09-06 for the initial zero finish input prerelease. The public
download is a debug-signed testing APK, not a production-signed Release build.

## Environment

- Windows / PowerShell, JDK 17, Gradle Wrapper 8.14, Android SDK 36.
- NDK 28.2.13676358; pinned librime 1.13.1 and dependency sources.
- Android 17 x86_64 emulator; no physical ARM device used.

## Checks completed

| Check | Result |
| --- | --- |
| `./tools/bootstrap-rime.ps1` | Exact librime commit and bundled dictionary hashes verified |
| `./tools/package-test-apk.ps1` | Passed with actual Rime; no checks skipped |
| JVM tests | 168 executions, 0 failures/errors/skips (88 distinct tests across JVM/Android variants) |
| `privacyCheck` | Passed; Debug and Release merged manifests checked |
| `:app:lintDebug` | No issues found; warnings remain errors |
| `./tools/test-input-experience.ps1 -Serial emulator-5554` | 50 tests passed; original selected keyboard restored |
| `:app:assembleRelease -PrequireRime=true --no-parallel` | Passed; three unsigned ABI APKs, R8/resource shrinking and vital Lint |
| Packaged APK checks | Expected package, version, minimum SDK, three ABIs, allowed permissions and valid Debug signature |
| License packaging | 14 notice/source files present inside Debug and all Release APKs; separate notices archive generated |
| Branding | English/default labels match; logo-derived adaptive icon and settings title inspected on emulator |
| Source hygiene | Local SDK configuration, credentials, fetched native trees, caches and binaries excluded |

The device suite covers actual Rime, Chinese options, nine-key input, keyboard
touch handling, input pipeline, panel layout, user dictionary imports and storage
failure/clear races. Public test fixtures are used throughout. Layout coverage
includes small/wide widths, light/dark themes and rotation. The launcher artwork
was additionally inspected with a circular adaptive mask and in Android app info.

## Published artifact

- APK: `zero-finish-input-0.1.0-debug-universal.apk`
- Size: 30,084,568 bytes.
- Application ID: `dev.zeroinput.ime.debug`; version: `0.1.0-debug` (code 1).
- Minimum Android: API 26; target API 36.
- ABIs: arm64-v8a, armeabi-v7a, x86_64.
- SHA-256: `3989d66735678061ac89de39303ec42d5ac5c7fc7737cf6e0fa3d25a6fd1d907`.
- Notices archive SHA-256: `5c59eb900ebe3be66760559c0a5777d2372356c1033beb155ea1c2c76753aedf`.

The checksum file distributed with the release covers both assets. License texts
and corresponding source locations also remain accessible inside the APK.

## Remaining validation

- Physical arm64-v8a / armeabi-v7a devices and vendor-specific input fields.
- Real biometric/device-credential dialogs, cancellation and delayed callbacks
  across real applications. Unit/device state tests do not replace that manual UX.
- Production signing and secure key ownership. No release key was generated.
- Broad Android 8+ device coverage and external document-provider behavior.
- Fresh-machine toolchain installation; this build used the existing SDK and
  caches. Pinned sources were checked, but a full cold build was not repeated.

The prerelease does not claim production readiness or universal device support.
