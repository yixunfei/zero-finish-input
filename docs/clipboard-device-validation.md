# Clipboard guard device validation

## Scope

The target physical device is an iQOO Neo9 running Android 16. It was not attached
during implementation. Local device checks use the API 37 emulator; they do not
establish OriginOS menu support, background lifetime or overlay permission behavior.

The implemented guard targets the current Android primary clip. The requested
iQOO history deletion is an unresolved product requirement, not covered by passing
current-item tests. Vendor histories, pinned items, cloud copies and previous
readers cannot be erased by the current implementation. An overlay is
a reminder, not an access-control or arbitrary text-selection capability.

## Automated checks

The completed 2026-09-06 run passed all 35 clipboard device cases with no skipped
cases against the universal Debug APK. Unit tests, privacyCheck, Android Lint,
Debug/Release merged-manifest checks, and packaging signature/permission/ABI/hash
checks also passed. The package includes the required native Rime libraries for
arm64-v8a, armeabi-v7a and x86_64; it is a Debug test artifact, not a signed release.

The 2026-09-09 Android 16 follow-up passed all 131 application instrumentation
cases with no skips, including the repaired confirmation and cross-application
selection cases and three new clipboard regressions. See the
[integration validation](model-integration-validation.md#clipboard-validation-follow-up)
for the separate historical and current evidence.

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

The platform fixture waits for its editor to gain window focus and for the actual
ZeroInput keyboard to become visible before enabling monitoring or writing a
public clip. Instrumentation restarts the target process; Android 16 can retain
an incomplete IME binding until a later input request reconnects it. The test
driver bounds input restart/show requests to six seconds, at most one request
per 250 ms. Failure to show the keyboard fails preparation; it does not skip the
case, attach a fake service, or relax the subsequent guard-state assertions.
See the [integration follow-up](model-integration-validation.md#clipboard-validation-follow-up)
for the Android 16 reproduction and current results.

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
The real confirmation page also has regressions for cancellation and disabling
monitoring while it is open; both must preserve the current public fixture.

The instrumentation APK contains a separate-UID source with fixed public text.
It uses native TextView selection/copy and the system text sharesheet. The text
view has an ID, which Android requires before adding PROCESS_TEXT actions. A
passive action-mode callback reports whether the native menu actually contains
ZeroInput's action; it does not add menu items or dispatch the import itself.
Closing the fixture resets its own separate process between cases. The fixture is
excluded from both main Debug and Release manifests.
Its content applies system-bar and display-cutout insets so the selectable text
stays outside system gesture areas. The driver waits for stable on-screen bounds
before injecting a synchronous physical long press; focus alone does not establish
that the window has finished moving. Native menu assertions remain unchanged.

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
6. Copy before enabling monitoring. Confirmation mode must preserve the item until
   "Check and clear current item" is confirmed. Explicit automatic mode must also
   process the existing item. Test the focused settings page with another keyboard
   selected. Enable identity verification separately and check denial and success.
7. Lock the phone, revoke permission, disable monitoring, change the selected IME
   and enter ZeroInput settings. Check that old reminders and requests cannot
   trigger cleanup. Check long-idle background behavior separately on OriginOS.
8. Inspect native selection menus and their overflow in the target apps. When an
   action is absent, try plain-text sharing. Record each source app and menu result;
   no receiver setting can force a source app to include a text-processing action.
9. Check vendor clipboard history separately using public fixtures. Old entries
   may remain after current-item cleanup succeeds; record this as an unmet history
   requirement. Manual deletion does not establish ZeroInput cleanup capability.

## History report follow-up (2026-09-09)

The reported surviving items were in the Android device's bundled keyboard and
Baidu keyboard history lists. The bundled keyboard vendor/version was not specified.
Android's public `clearPrimaryClip` operation targets the current item only;
default-IME status does not grant access to another application's saved history.
The existing platform tests cover the current item and never established history
erasure. The earlier passing results must not be interpreted as history validation.

Chinese and English confirmation, automatic-mode warnings and result text now
state the current-item scope and that history is not deleted. The result reports
that cleanup was requested because the platform API has no success receipt and
missing metadata can also mean access was withheld. To validate on a vendor
device, use public fixtures and check ordinary current-item paste separately from
the owner application's history list, pinned entries and sync settings. Earlier
advice to delete records in the owning application did not meet the requested
ZeroInput behavior and is superseded by the product repair follow-up below.
Turning off history or sync does not establish that existing copies were deleted.

Validation of the updated text on `ZeroInputModelApi36` (API 36, x86_64):

- All 38 clipboard instrumentation cases passed in 47.755 seconds, with no
  failures or skips in raw instrumentation status. This covers current-item
  confirmation and automatic cleanup, cancellation, disabled monitoring, stale
  requests, private imports and settings layouts.
- `testDebugUnitTest` succeeded with 163 Debug cases and no failures, errors or
  skips; unchanged unit-test tasks reused up-to-date results.
- `privacyCheck`, Debug/Release merged-manifest checks, `:app:lintDebug`, Debug
  and instrumentation APK builds passed with actual Rime and `-PrequireRime=true`.
- Layout regressions covered Chinese/English, light/dark themes, narrow and
  landscape dimensions at font scale 1.3. Constructed Chinese and English settings
  images were also inspected; text wraps within the scrollable page.

Build command from the repository root:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest testDebugUnitTest `
  privacyCheck :app:lintDebug -PrequireRime=true `
  "-Pandroid.injected.build.abi=x86_64" --no-parallel
```

Evidence is under ignored `build/clipboard-clear-validation/`: `checks.log`,
`device-tests.txt`, `guard-zh-top.png` and `guard-en-top.png`. No physical phone was
connected, and no Baidu/vendor history deletion was performed or verified. This
text-only follow-up did not rebuild ARM/Release APKs or rerun the full device suite.
Passing current-item tests does not establish deletion of any saved history.

## Product repair and authenticated return (2026-09-09)

The user identified Android 16 on an iQOO Neo9 and approved ordinary-app repairs,
supported vendor APIs if available, and authentication followed by an explicit
confirmation after returning to the input field. Root, Shizuku and privileged
user workarounds are excluded. The implementation follows
[ADR 0012](adr/0012-keyboard-private-copy-and-foreground-cleanup.md).

- The private clipboard panel has a visible Copy selection to ZeroInput action.
  It reads only the explicit bounded editor selection, then requires system
  authentication and Save in the secure import page. PROCESS_TEXT and sharing
  remain alternatives for noneditable source text; third-party menu ordering
  cannot be forced by this receiver.
- Choosing a saved snippet starts system authentication. Returning shows Confirm
  paste and Cancel paste, without decrypting or inserting text automatically.
  Confirmation binds the actual returned editor connection. Cancellation, edits,
  leaving, settings/deletion and expiry revoke the pending operation. Unexpected
  selection changes or keyboard hiding also cancel an already queued body read;
  the vault checks cancellation again after acquiring its serialization lock.
- Explicit automatic mode now handles a preexisting current item when protection
  starts or resumes. The focused settings page can clear the current item with
  another IME selected. Background monitoring still needs platform eligibility.

Current implementation acceptance steps, using public fixtures only:

1. Select editable text, open ZeroInput's private panel and use Copy selection.
   Authenticate and explicitly Save. Check that ordinary system paste still
   contains its original fixture, while the new private snippet exists.
2. Select the snippet, authenticate with device credentials, return to the input
   field and verify no text was inserted. Confirm once and verify one insertion.
   Repeat with Cancel, typing, a different input field, settings changes and
   deletion; a stale confirmation must not insert anything. Check biometrics
   separately on hardware.
3. Enable automatic current-item cleanup explicitly with a preexisting fixture.
   Check actual current-item paste, including after returning to the focused
   guard settings with another keyboard selected. Do not infer history deletion
   from current-item metadata or a successful clear request.
4. On the reported iQOO build, identify the long-press history component and its
   version without collecting real records. Test ordinary and pinned public
   fixtures independently and reopen the owner. Manual deletion is not acceptance.

There is no physical iQOO connected and no verified supported history-deletion
interface. The [vendor investigation](clipboard-product-repair-plan.md#vendor-interface-investigation)
found only a read-permission policy in the public vivo catalogue. This does not
rule out private/partner interfaces. **The reported history deletion requirement
is still unmet.** No user-facing state claims otherwise.

Final validation on `ZeroInputModelApi36`, Android 16 / API 36, x86_64:

| Check | Result |
| --- | --- |
| Real device-credential copy/save/paste, seven returned/queued revocation scenarios, consent and vault negatives, copy/confirmation layouts | 17 tests passed in 22.544 seconds; no failures or skips |
| Current-item platform cleanup, guard runtime, overlay, external imports, input panels/editor integration, encrypted-store failures | 51 tests passed in 57.685 seconds; no failures or skips |
| JVM tests across Debug/Release and JVM modules | 352 tests; no failures, errors or skips |
| Debug and instrumentation builds, privacyCheck, Lint | Passed with actual Rime and `-PrequireRime=true` |
| Debug/Release merged manifests | Private selection entry is nonexported, external source fixture excluded, backups disabled; Release is not debuggable |
| ARM64 test packaging | Signature, ABI, permission and license-notice checks passed |

Before the final run, a blocked-vault regression reproduced late insertion after
an editor change. It passes with read cancellation and lock-boundary revalidation.
Repeated credential navigation also exposed early return binding: the prompt
callback could precede the authentication Activity's exit and a late IME hide
would cancel the just-created confirmation. Authentication result delivery now
waits for that Activity's destruction. Three consecutive combined-flow runs
passed after this change, followed by the final 17-test run with all temporary
diagnostic code removed. No authentication/session checks were bypassed.

Layout tests cover Chinese/English, both themes, 320/411 dp portrait and 800 dp
landscape panels at font scale 1.3; constructed copy and confirmation images
were inspected. Live secret-window screenshot protection remained enabled.
The temporary emulator PIN was removed and the emulator rebooted before overlay
and screen-off tests. This verifies OS device credentials on the emulator;
physical biometrics, older API levels, OriginOS background behavior and the
reported iQOO history remain unverified. Full Release and 32-bit APKs were not
rebuilt for this clipboard-only follow-up; no native dependency was changed.

Final Debug-signed test package (not a production-signed release):

- Path: `app/build/outputs/test-apk/20260909-081642/zero-finish-input-0.2.0-debug-arm64-v8a.apk`
- Size: 58,698,153 bytes.
- SHA-256: `a2f031825acc78689f2aa84c4dae0f72c591bc2795f77e9ed63fa0583a1ad69c`.
- The same directory contains `SHA256SUMS.txt` and the license-notice archive.

Evidence is under ignored `build/clipboard-product-audit/`: `auth-final.txt`,
`regression-final.txt`, `checks-final-accepted.log`, `package-final-arm64.log`,
`unit-summary.json`, `paste-queued-before-fix.txt`, `navigation-complete-1.txt`
through `navigation-complete-3.txt`, and constructed panel PNGs. Earlier failures
remain recorded as reproduction evidence, not final passing results. Build and
packaging commands are the repository-root commands above and
`./tools/package-test-apk.ps1 -Abi arm64-v8a`. Real-credential fixtures require a
disposable emulator explicitly provisioned with their public test PIN and the
instrumentation argument `-e clipboardTestPin 2468`; the final passing run used
that argument, without authentication mocks.
