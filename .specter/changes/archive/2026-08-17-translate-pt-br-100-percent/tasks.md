# Tasks

Verification command used throughout (run from repo root, substituting `$MOD` and comparing against a known exception list):
```bash
grep -oP '(?<=name=")[^"]+' "$MOD/src/commonMain/moko-resources/base/strings.xml" | LC_ALL=C sort -u > /tmp/base.txt
grep -oP '(?<=name=")[^"]+' "$MOD/src/commonMain/moko-resources/pt-rBR/strings.xml" | LC_ALL=C sort -u > /tmp/ptbr.txt
LC_ALL=C comm -23 /tmp/base.txt /tmp/ptbr.txt
```

## 1. Translate i18n-aniyomi: novel_reader_*
- [x] 1.1 Translate all missing `novel_reader_*` keys (~693) in `i18n-aniyomi/src/commonMain/moko-resources/pt-rBR/strings.xml`, matching `base/strings.xml` values, informal "você" register, preserving placeholders; skip the 6 `novel_reader_ai_translator_api_url_*` URL keys
- [x] 1.2 Run the verification diff for `i18n-aniyomi` scoped to `novel_reader_*` keys; confirm only the 6 URL keys remain missing

## 2. Translate i18n-aniyomi: aurora_greeting_* / aurora_* / aurora_nickname_*
- [x] 2.1 Translate all missing `aurora_greeting_*`, `aurora_*`, and `aurora_nickname_*` keys (~280) in `i18n-aniyomi` pt-rBR
- [x] 2.2 Run the verification diff scoped to these prefixes

## 3. Translate achievement_* (both modules)
- [x] 3.1 Translate all missing `achievement_*` keys in `i18n` pt-rBR
- [x] 3.2 Translate all missing `achievement_*` keys in `i18n-aniyomi` pt-rBR
- [x] 3.3 Run the verification diff scoped to `achievement_*` in both modules

## 4. Translate i18n-aniyomi: pref_player_* / pref_show_* / pref_aurora_*
- [x] 4.1 Translate all missing keys under these prefixes (~90) in `i18n-aniyomi` pt-rBR
- [x] 4.2 Run the verification diff scoped to these prefixes

## 5. Translate i18n-aniyomi: import flows
- [x] 5.1 Translate all missing `anixart_import_*` and `shikimori_import_*` keys (~63) in `i18n-aniyomi` pt-rBR
- [x] 5.2 Run the verification diff scoped to these prefixes

## 6. Translate i18n: ai_translator_*
- [x] 6.1 Translate all missing `ai_translator_*` keys (~65) in `i18n` pt-rBR
- [x] 6.2 Run the verification diff scoped to this prefix

## 7. Translate downloads/exports (both modules)
- [x] 7.1 Translate all missing `download_engine_*`, `novel_download_*`, `novel_export_*`, `novel_batch_*` keys (~90) across both modules' pt-rBR files
- [x] 7.2 Run the verification diff scoped to these prefixes

## 8. Translate remaining strings (both modules)
- [x] 8.1 Translate all remaining missing keys in `i18n` pt-rBR not covered by tasks 1-7 (onboarding_*, unlockable_*, donation_option_*, pref_*, status_*, tip_*, migration_*, etc.)
- [x] 8.2 Translate all remaining missing keys in `i18n-aniyomi` pt-rBR not covered by tasks 1-7 (unlockable_*, treasury_*, meltdown_code_*, home_header_*, achievement_rank_*, etc.)
- [x] 8.3 Run the full verification diff for both modules; confirm the only remaining gaps are the 6 excluded URL keys

## 9. Fix hardcoded strings — achievements, novel reader, tracking
- [x] 9.1 `AchievementCard.kt:384` (`"PROGRESS"`), `AchievementDetailDialog.kt:138` (`contentDescription = "Close"`), `AchievementActivityGraph.kt:354` (`contentDescription = "Activity bar for ${month.month.name}"`) — extract to new string keys in `base` + `pt-rBR`, reference via `stringResource`
- [x] 9.2 `SelectedTextTranslationOverlay.kt:298,439` (`"Speak"`, `"Source language: $lang"`), `GoogleTranslationDialog.kt:245,258` (`"auto"`, `"ru"` placeholders) — extract to string keys
- [x] 9.3 `NovelScreenModel.kt:2752,2761,2778` snackbar messages (`"File not found"`, `"Unable to find folder"`, `"Downloaded translation deleted"`) — extract to string keys
- [x] 9.4 `NovelExtensionsTab.kt:93` clipboard label (`"Novel extension diagnostic"`) — extract to string key
- [x] 9.5 `TrackerWebViewLoginActivity.kt:179,184,192,198,258` (dialog title, back/refresh/complete content descriptions, instruction text) — extract to string keys

## 10. Fix hardcoded strings — DubbingSelectionDialog
- [x] 10.1 `DubbingSelectionDialog.kt` — extract all hardcoded text to string keys: headline ("Playback Preferences"), section headers ("Player"/"Dubbing"/"Quality", appearing in both landscape/portrait layouts), player option labels ("Auto"/"CDN"/"Kodik"/"Parlorate"), and the "Best Available" quality label

## 11. Fix hardcoded strings — settings/debug screens and novel library
- [x] 11.1 `SettingsAdvancedScreen.kt:241,317` ("Glitch Rift", "Don't kill my app!"), `HelpScreen.kt:54` ("GitHub Issues" title), `AboutScreen.kt:641,651` ("Tadami", "mugen" footer link titles) — extract to string keys
- [x] 11.2 `DebugInfoScreen.kt` (App info/Version/Build time/WebView version/Profile compilation status/Model/OneUI version/MIUI version/Android version/Device info labels), `WorkerInfoScreen.kt:97,100,103` ("Enqueued"/"Finished"/"Running") — extract to string keys
- [x] 11.3 `NovelLibraryContent.kt:351,439` (`Badge(text = "DL")`) — extract to a string key; align with the numeric-only pattern used by `DownloadsBadge`/`UnviewedBadge` in `LibraryBadges.kt` if applicable, otherwise just localize the "DL" label text
- [x] 11.4 `ExpandableCard.kt:100-101` — remove the leftover `Text("Hello World")` and `Text("SPOjao;sjd")` placeholder composables (not real UI copy, not a translation task)

## 12. Final verification
- [x] 12.1 Run the full verification diff for `i18n` and `i18n-aniyomi` pt-rBR; confirm zero gaps beyond the 6 excluded URL keys
- [x] 12.2 Grep the newly-added `base` keys from tasks 9-11 to confirm each has a matching `pt-rBR` entry
- [x] 12.3 Build the app, set language to Portuguese (Brazil), and spot-check: an achievement detail dialog, the novel reader AI translator overlay, the Dubbing preferences dialog, the tracker login WebView, and the About screen — confirm no English text or placeholder debug text appears
