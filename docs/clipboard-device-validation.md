# Clipboard guard device validation

## Scope

The target physical device is an iQOO Neo9 running Android 16. It was not attached
during implementation. Local device checks use the API 37 emulator; they do not
establish OriginOS menu support, background lifetime or overlay permission behavior.

Only the current Android primary clip is in scope. Vendor histories, pinned items,
cloud copies and previous readers cannot be erased by this feature. An overlay is
a reminder, not an access-control or arbitrary text-selection capability.

## Automated checks

The completed 2026-09-06 run passed all 35 clipboard device cases with no skipped
cases against the universal Debug APK. Unit tests, privacyCheck, Android Lint,
Debug/Release merged-manifest checks, and packaging signature/permission/ABI/hash
checks also passed. The package includes the required native Rime libraries for
arm64-v8a, armeabi-v7a and x86_64; it is a Debug test artifact, not a signed release.

From the repository root, with a configured JDK and Android SDK:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest testDebugUnitTest privacyCheck :app:lintDebug `
  --no-parallel -PrequireRime=true "-Pandroid.injected.build.abi=x86_64"
```

Install the main Debug APK and instrumentation APK on a dedicated test device.
Select ZeroInput as the current IME, disable the real guard and leave the current
clipboard empty before starting platform mutation tests. Existing content causes
these tests to skip; inspect raw instrumentation status for code `-4` instead of
interpreting an `OK` footer as proof that every case ran.

```powershell
adb shell am instrument -w -r -e package dev.zeroinput.ime.clipboardguard,dev.zeroinput.ime.clipboard `
  dev.zeroinput.ime.debug.test/androidx.test.runner.AndroidJUnitRunner
./tools/package-test-apk.ps1
```

Coverage includes default-off settings, unavailable service, explicit inspection
of an existing item, cancellation during blocked work, failed initialization and
retry, stale tickets, default-IME changes, authentication requirements, repeated
automatic clears and cleanup after foreground confirmation. Overlay checks cover
permission education, cancellation and retry, denial, revocation, one-window
replacement, dismissal, timeout, screen-off, foreground suppression and preview.
Delayed controls from a finishing settings page cannot restore old options.

The instrumentation APK contains a separate-UID source with fixed public text.
It uses native TextView selection/copy and the system text sharesheet. The text
view has an ID, which Android requires before adding PROCESS_TEXT actions. A
passive action-mode callback reports whether the native menu actually contains
ZeroInput's action; it does not add menu items or dispatch the import itself.
Closing the fixture resets its own separate process between cases. The fixture is
excluded from both main Debug and Release manifests.

The API 37 remote floating toolbar does not expose its buttons in the available
accessibility tree. Its overflow menu was separately inspected on screen and
"Copy to ZeroInput" was clicked manually. It opened the protected import review,
which showed only a character count and the authentication action. The sharesheet
entry is exercised end to end by instrumentation. Credential/biometric success on
the physical device remains a manual check.

Constructed settings and overlay layouts are checked in Chinese and English,
light and dark themes, narrow and landscape dimensions, at font scale 1.3. Live
secret-page and overlay screenshot protection stays enabled throughout testing.

## Physical-device acceptance

Use only public sample text for these checks:

1. Open the guard settings. Monitoring, each reminder and authentication should be
   off initially, and the cleanup mode should be no triggered cleanup.
2. Enable the overlay option. Cancel the explanation first; no permission page or
   opt-in should occur. Try again and continue, then deny permission. The settings
   should show that the overlay is unavailable. A retry must explain the purpose
   again before opening system settings.
3. Grant overlay permission and run the preview. Check top/bottom placement,
   3/5/10-second duration, dismissal, portrait/landscape and large text. The preview
   should not inspect the clipboard or show text from another page.
4. Select ZeroInput and open its keyboard once. Enable monitoring and confirmation
   mode, then copy in messaging and a browser. Check the reminder over the source
   app and open its confirmation page. Cancel once, then confirm a fresh request.
5. Try automatic mode with public text. Repeated copies should clear the current
   item and produce separate reminders. Stop if normal copy/paste is needed;
   automatic mode intentionally makes those contents unavailable.
6. Copy before enabling monitoring, then use "Check and clear current item".
   Inspection must require confirmation even in automatic mode. Enable identity
   verification separately and check both denial and successful authentication.
7. Lock the phone, revoke permission, disable monitoring, change the selected IME
   and enter ZeroInput settings. Check that old reminders and requests cannot
   trigger cleanup. Check long-idle background behavior separately on OriginOS.
8. Inspect native selection menus and their overflow in the target apps. When an
   action is absent, try plain-text sharing. Record each source app and menu result;
   no receiver setting can force a source app to include a text-processing action.
9. Check vendor clipboard history separately. Old history entries may remain even
   after current-item cleanup succeeds. Disable and delete them in their owner app.
