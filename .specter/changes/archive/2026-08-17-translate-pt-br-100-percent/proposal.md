# Proposal: Complete pt-BR Translation Coverage

## Intent
An audit of the app's two moko-resources i18n modules found that Brazilian Portuguese (`pt-rBR`) is missing translations for roughly 3,025 of ~4,379 total base (English) strings — the `i18n` module has ~810 untranslated strings and `i18n-aniyomi` has ~2,215. The generic `pt` locale, which Android would otherwise fall back to, is itself barely populated and doesn't meaningfully close the gap. There is no CI check for translation completeness, so the gap grew silently as newer features (novel reader/AI translator, achievements, aurora greetings, onboarding, player settings, unlockables, import flows) shipped English-only strings. Additionally, a source scan found ~40 genuine hardcoded user-facing strings in Compose UI that bypass the resource system entirely (an entire dialog in `DubbingSelectionDialog.kt`, content descriptions, snackbar messages, settings/debug screen labels), which are untranslatable regardless of locale file state. This change closes both gaps so pt-BR users see no English text anywhere in the app.

## Scope
- Translate all ~810 missing strings in `i18n/src/commonMain/moko-resources/pt-rBR/strings.xml` to match `base/strings.xml`
- Translate all ~2,215 missing strings in `i18n-aniyomi/src/commonMain/moko-resources/pt-rBR/strings.xml` to match `base/strings.xml`
- Match the existing pt-rBR style: informal register ("você", not "tu"), natural Brazilian Portuguese (not literal/European Portuguese), preserving all `%s`/`%1$s`-style format placeholders exactly
- Exclude from translation: 6 keys in `i18n-aniyomi` that are provider API-key URLs (`novel_reader_ai_translator_api_url_*`), which are intentionally not human text
- Fix ~40 hardcoded Compose strings found in: `AchievementCard.kt`, `AchievementDetailDialog.kt`, `AchievementActivityGraph.kt`, `SelectedTextTranslationOverlay.kt`, `GoogleTranslationDialog.kt`, `NovelScreenModel.kt`, `NovelExtensionsTab.kt`, `DubbingSelectionDialog.kt` (near-entirely hardcoded), `TrackerWebViewLoginActivity.kt`, `SettingsAdvancedScreen.kt`, `HelpScreen.kt`, `AboutScreen.kt`, `DebugInfoScreen.kt`, `WorkerInfoScreen.kt`, `NovelLibraryContent.kt`, `ExpandableCard.kt` — by moving each literal into new `base` + `pt-rBR` string resource pairs
- Remove two apparent debug leftovers found during the scan: `Text("Hello World")` and `Text("SPOjao;sjd")` in `ExpandableCard.kt`, which are not real UI copy in any language
- **Out of scope**: the deliberately bilingual EN/RU `AuroraLocalization.translate()` system used by the Aurora/Lattice easter-egg modules (`AuroraConstellationPad.kt`, `AuroraRiddleScreen.kt`, `MeltdownRitual.kt`, `TerminalGlitchDialog.kt`, `CouncilCodeLock.kt`, `RealityBreach.kt`) — separate system by design, not part of moko-resources
- **Out of scope**: any other locale besides pt-rBR; the generic `pt` locale is not being backfilled
- **Out of scope**: adding new features or changing any non-text behavior

## Approach
Pure content-translation work for the bulk of the scope (no code changes needed — Android/moko-resources already resolve `pt-rBR` correctly once keys exist), executed in batches grouped by feature-prefix (novel_reader, aurora_greeting, achievement, pref_player, import flows, ai_translator, downloads, remainder) with a scripted key-set diff after each batch to catch omissions. The hardcoded-string fixes are a smaller, separate final task group requiring actual Kotlin edits (extract literal → new string resource key in both `base` and `pt-rBR` → reference via `stringResource`/`MR.strings`).
