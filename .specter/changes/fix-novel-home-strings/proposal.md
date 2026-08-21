# Proposal: Fix Home Hub Wording Issues (Sources Label, Media-Aware Empty State)

## Intent
Two independent Home-hub strings read wrong in Portuguese. The "quick source" fallback button says "Código aberto" ("Open Source" as in source code), when it should read "Abrir fontes" ("Open sources", i.e. browse extension sources) — a mistranslation that misleads users about what the button does. Separately, the empty-state welcome card on Home > Manga and Home > Novel says "Comece a assistir" ("Start watching"), which only makes sense for anime — manga and novel are read, not watched.

## Scope
- Fix the `aurora_open_source` string's pt-BR translation.
- Make the Home hub welcome/empty-state title and subtitle media-aware, so manga/novel show "read"-appropriate wording while anime keeps "watch"-appropriate wording.
- Out of scope: any other Home hub copy, the `aurora_browse_sources` CTA button label (unless it also needs a verb check as part of the same fix), and non-Home-hub occurrences of "assistir"/"watch" phrasing.

## Approach
Correct `aurora_open_source`'s pt-BR value in `i18n-aniyomi/src/commonMain/moko-resources/pt-rBR/strings.xml`. For the empty-state strings, give `WelcomeSection` (`HomeHubScreenSections.kt`) a media-type parameter and either two new string keys (anime-specific vs. manga/novel-shared) or a single parameterized string, then thread the media type through from `AnimeHomeHub`/`MangaHomeHub`/`NovelHomeHub`.
