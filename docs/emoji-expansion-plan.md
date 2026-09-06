# Emoji and kaomoji expansion

Date: 2026-09-06

Status: Implemented and validated. See [validation results](expression-validation.md)
for the final artifact, completed checks and remaining platform coverage.

## Checkpoint and assessment

The starting checkout contained uncommitted private text import and optional
system clipboard guard work. Local commit `ed389af` preserves all 45 changed or
new files before expression work. The checkpoint has not been pushed upstream.

The project has ten Gradle modules, replaceable Chinese and English engines,
full and nine-key Chinese input, encrypted personalization, private clipboard
storage and data-only language packs. The current expression surface is much
smaller than the rest of the input experience:

- `ime-ui/EmojiCatalog` contains 77 entries in seven public categories, plus a
  recent category. Search matches keyword substrings; there are no catalog tests.
- `EmojiPanelView` owns category and query state. Its fixed 48dp cells are suitable
  for emoji but need a separate width policy for longer kaomoji. Category labels
  and search controls contain Chinese strings outside localized resources.
- Search keystrokes are intercepted by `ZeroInputView` before the language engine.
  Pinyin aliases are therefore necessary for convenient Chinese keyboard search.
- `EmojiHistoryRepository` stores at most 128 entries and displays 32 recent
  values. Its 32-UTF-16-unit text limit excludes longer expressions. The current
  writer mutates its cached map before encrypted persistence succeeds.
- `ZeroInputService` already gates history through editor privacy and schedules
  encrypted work on a bounded worker. New personal expression operations must
  retain session and deletion-generation checks through completion.

## Requested scope

The user requested expanded emoji, kaomoji with nested tags, favorites and custom
kaomoji. The user confirmed the following implementation plan and additionally
requested a fix for the keyboard covering the screen in fullscreen applications:

1. Expand the bundled emoji catalog and add a Kaomoji parent category with
   emotion and situation subtags, including happiness, affection, sadness, anger,
   surprise, resignation and greetings. Pin and attribute any distributed data.
2. Support Chinese, English and pinyin search; selected-tab and empty states;
   complete expression display; and stable portrait/landscape layouts.
3. Add favorite/unfavorite actions and a custom expression management page with
   create, edit and delete operations for text, name, keywords and category.
4. Keep existing recent entries, extend validated expression lengths, and store
   favorites and custom entries in a separate encrypted file with a dedicated
   Keystore alias under noBackupFilesDir. Personal expression data is hidden and
   history writes are disabled whenever personalization is prohibited. Clearing
   personal data also clears the new store and its key.
5. Add behavior, failure, race and device-layout coverage, update architecture,
   threat and user documentation, and generate a verified Debug test APK.

Expression import/export and Rime candidate integration are outside this plan.
No runtime networking, permissions, third-party runtime or new module is needed.
Implementation remains sequential; no subagent work has been authorized.

## Ownership and data boundaries

- `ime-ui`: immutable presentation models, catalog search/filtering, expression
  panel, subtags, selection and favorite intents. No persistence or decryption.
- `app`: composition, bounded background operations, session/privacy identity,
  management Activity, localized forms and visible operation failures.
- `user-data`: validated encrypted personal expression storage, serialization,
  write publication, deletion generations and bounded history.
- `security`: a dedicated key alias using existing encrypted storage primitives.
- Documentation and license files: catalog provenance, behavior and acceptance.

Management must not put personal expression text in saved state, logs, content
capture, screenshots or exported components. UI snapshots and device fixtures
must use constructed public data. The existing user dictionary and private
clipboard data formats are outside the change.

## Reference review

| Repository | Inspected commit | Declared license | Assessment |
| --- | --- | --- | --- |
| rimeinn/rime-kaomoji | `e66d8f26ee47b4a2a840983b7d71ed69b5ecf756` | LGPL-3.0 | Six emotion groups; distribution must retain source data and license notices. |
| aoguai/rime_kaomoji_dict | `2f10ddca83f4cd87f41672442d27943f733b488f` | MIT | Multiple upstream collections; verify provenance for the actual selected data. |
| overmind1980/- | `0229f75621e725df85bd1e04e34e58ace8041184` | No explicit license found | Reference only until redistribution permission is established. |

Repository-level license labels do not resolve the provenance of every separately
collected dataset. The final distribution must identify actual included files,
their pinned sources and SHA-256 hashes; do not describe reference-only sources
as bundled dependencies.

## Baseline validation

The following command succeeded against the checkpoint, using real Rime sources
and existing up-to-date Gradle outputs:

```powershell
./gradlew.bat :app:assembleDebug testDebugUnitTest privacyCheck :app:lintDebug `
  "-Pandroid.injected.build.abi=x86_64" -PrequireRime=true --no-parallel --console=plain
```

This includes Debug/Release merged-manifest permission checks; it does not build
a Release APK. Baseline device tests on the connected x86_64 `emulator-5554`
passed all 73 tests in 58.418 seconds through:

```powershell
./tools/test-input-experience.ps1 -Serial emulator-5554
```

The script restored the original ZeroInput Debug input method after completion.
These are emulator results; no physical ARM-device behavior was verified during
this assessment.

## Planned acceptance

- Catalog uniqueness, category membership, length and Unicode preservation;
  Chinese/English/pinyin queries, empty results and favorite/custom filtering.
- Failed writes leave prior state intact; corrupt data and missing keys fail
  closed; clear/delete and queued updates cannot resurrect personal entries.
- Sensitive editors, disabled personalization, session replacement, stale UI
  selection and delayed worker callbacks cannot expose or insert personal data.
- Custom create/edit/delete, duplicate/capacity validation, process restart and
  secure management lifecycle.
- Light/dark themes, Chinese/English resources, 320/411/800dp widths and rotation;
  long expressions, search keyboard, subtags and controls fit without overlap.
- Unit tests, privacyCheck, Lint, real-Rime Debug build, device regressions and
  test APK signature/ABI/permission/hash checks. Report any unavailable physical
  ARM-device or OEM-specific acceptance separately.

## Completion

- [x] Preserve the starting code in local backup commit `ed389af`.
- [x] Expand to 371 emoji and 161 kaomoji, with nested groups and keyword search.
- [x] Add encrypted favorites and custom create/edit/delete management.
- [x] Enforce editor privacy, stale binding invalidation and clear/write generations.
- [x] Fix the opaque IME window and compact landscape geometry.
- [x] Pass the 99-test device suite and the final 6-test touch/layout regression.
- [x] Run the packaging script with tests, privacy checks, app Lint and real Rime.
- [x] Verify the universal Debug artifact, bundled expression sources and licenses.

Physical ARM/OEM execution and Release packaging remain outside the completed
Debug validation. Optional standalone ime-ui Lint still reports three preexisting
keyboard-view issues; no lint rules or baselines were weakened.
