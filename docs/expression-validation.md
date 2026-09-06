# Expression and fullscreen validation

Date: 2026-09-06

## Scope and result

The implementation expands the original 77 emoji to 371 emoji and 161 kaomoji.
It adds kaomoji subtags, Chinese/English/pinyin search, encrypted favorites,
custom expression management and privacy-aware recent usage. The IME window is
transparent above its content; landscape rows and candidate geometry leave the
fullscreen host editor visible.

The original checkout was preserved in local commit `ed389af`. Expression work
and subsequent user clipboard-overlay changes remain in the working tree. There
was no remote push. The existing clipboard changes were included in regression
and packaging checks.

## Automated validation

Commands run from the repository root using JDK 17 and the configured Android SDK:

```powershell
./tools/test-input-experience.ps1 -Serial emulator-5554
./tools/package-test-apk.ps1
```

- Full device suite: **99 tests passed**, 96.558 seconds, on x86_64 emulator
  `emulator-5554` reporting API 37. The original input method was restored.
- Final expression touch/layout regression: **6 tests passed**, 7.026 seconds,
  after adjusting gesture observation and rejecting stale long-press gestures.
  This reran `ExpressionPanelTest` and `FullscreenInputTest` against rebuilt APKs.
- Final packaging script: passed `test`, `privacyCheck`, `:app:lintDebug` and
  `:app:assembleDebug`, with `-PrequireRime=true` and no skipped checks.
- All module test reports contain zero failures/errors. The new pure tests cover
  catalog/search/privacy (8 tests) and custom persistence/failure/races (11 tests),
  in both Debug and Release unit-test variants.
- App Lint: `No issues found.` Privacy validation checked Debug and Release merged
  manifests. Expression management is nonexported and adds no permission.
- Real Rime native code was built/packaged for arm64-v8a, armeabi-v7a and x86_64.
  The package script verified package identity, signature, ABIs and permissions.

Device expression tests cover actual Keystore round trips, clear, tamper/key loss,
bounded history, management CRUD, discarded drafts, secure windows, revoked
personal cells, recycled touch/long-press gestures and complete long-text layout.
Fixtures use constructed public text and isolated stores, without reading the
user's custom expression library.

## Visual and interaction checks

Layout assertions cover 320/411/800dp widths and both light/dark configurations.
Actual attached IME screenshots confirmed readable category icons and English
subtag labels, kaomoji search results, and host content above the keyboard.
Chinese labels were checked in the configured panel fixtures. Public fullscreen
fixture screenshots and pixel checks passed in portrait and landscape.

An attached-window test sends a touch down/up pair to a kaomoji result and checks
that the editor receives the complete selected value. The search query remains
inside the IME and does not enter the editor. The keyboard and content fit the
available window after rotation.

Offscreen Canvas snapshots omit MaterialButton labels on this emulator; attached
window screenshots render those labels correctly. Visual artifacts under
`app/build/expression-fixtures-latest/` contain only public fixtures and are not
committed.

## Artifact

Final directory:

`app/build/outputs/test-apk/20260906-230429/`

| File | SHA-256 |
| --- | --- |
| `zero-finish-input-0.1.0-debug-universal.apk` | `00d4ea4244cdd763231c3dccc66867ca84daf8a2a602c95184e9474a6ebb41bd` |
| `zero-finish-input-0.1.0-debug-notices.zip` | `4d4b9881a185146c1bb4f4e105800e129afccb6e0d6d07b14a0786bded0d5a13` |

`SHA256SUMS.txt` accompanies these files. The APK is a local Debug test artifact,
not a signed Release build.

The APK contains original `rime-kaomoji-source.txt`, adapted `KaomojiCatalog.kt`,
LGPL/GPL license texts, NOTICE and source/rebuild instructions. The original data
matches pinned SHA-256
`0772e42f7410b4ed452b1103bcf2d9360ec952318383631d0702bc0418931d62`.
The adapted asset matches the repository source SHA-256
`48ed47744cdce8e43f4347b18b14480bd1ddf99fdc169d6e31978147747c1b27`.

## Remaining coverage

- Physical ARM devices, OEM fullscreen editors, Android 8.x window behavior and
  actual TalkBack navigation were not exercised. ABI packaging does not establish
  device behavior on those platforms.
- Release unit tests and merged-manifest checks ran, but no Release APK was built
  or signed for this feature task.
- Extra `:ime-ui:lintDebug` validation still fails on three issues already present
  in the backup: `KeyboardKeyView` and `KeyboardRow` lack layout-tool constructors;
  `KeyboardKeyView` lacks a declared `performClick` override. The new expression
  code has no remaining findings in that report. These existing controls were
  left in scope for a separate cleanup; no lint rule or baseline was relaxed.

Manual device acceptance should include favorite/unfavorite, custom editing and
deletion, process restart, sensitive editor transitions, large font/TalkBack use,
and switching between a target fullscreen application and an ordinary editor.
