# Tasks

## 1. Fix "Código aberto" mistranslation
- [ ] 1.1 Update `aurora_open_source` pt-BR value in `i18n-aniyomi/src/commonMain/moko-resources/pt-rBR/strings.xml:1535` from "Código aberto" to "Abrir fontes"
- [ ] 1.2 Confirm the base (English) string at `i18n-aniyomi/.../base/strings.xml:1691` ("Open Source") doesn't also need rewording for clarity (e.g. "Browse Sources") while touching this key — align both if it improves clarity, otherwise leave base untouched

## 2. Media-aware Home hub empty-state wording
**Branch:** fix-home-empty-state-wording
- [ ] 2.1 Add new string key(s) for a reading-appropriate welcome title/subtitle (e.g. `aurora_welcome_title_reading`/`_subtitle`, or repurpose with a format arg) in base + pt-rBR strings.xml, keeping the existing `aurora_welcome_title`/`_subtitle` for anime
- [ ] 2.2 Give `WelcomeSection` (`app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeHubScreenSections.kt:405-426`) a parameter to select between the anime wording and the new reading wording
- [ ] 2.3 Thread the parameter from `AnimeHomeHub` (watch wording), `MangaHomeHub`, and `NovelHomeHub` (both read wording) call sites (same file, ~lines 90-261)
- [ ] 2.4 Manually verify: Home > Manga and Home > Novel empty states say "ler"/read-appropriate wording; Home > Anime is unchanged
