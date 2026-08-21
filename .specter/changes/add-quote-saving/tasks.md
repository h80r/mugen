# Tasks

## 1. Data layer
- [ ] 1.1 Add `novel_quotes` table + queries to a new `.sq` file (`data/src/main/sqldelightnovel/datanovel/novel_quotes.sq`), following `novel_history.sq`'s pattern: `insert`, `getAll` (joined through `novel_chapters`/`novels` for title/chapter display, ordered by `saved_at DESC`), `delete`
- [ ] 1.2 Run/regenerate SQLDelight codegen and confirm the new table builds

## 2. Domain layer
- [ ] 2.1 Add `tachiyomi.domain.quote.novel.model.NovelQuote` domain model
- [ ] 2.2 Add `NovelQuoteRepository` interface (`tachiyomi.domain.quote.novel.repository`)
- [ ] 2.3 Add `GetNovelQuotes`, `InsertNovelQuote`, `DeleteNovelQuote` interactors
- [ ] 2.4 Implement `NovelQuoteRepositoryImpl` in `data/src/main/java/tachiyomi/data/quote/novel/`, backed by the new `.sq` queries
- [ ] 2.5 Register the repository/interactors in the relevant DI module (mirroring how `history/novel`'s equivalents are registered)

## 3. Save-quote action in the reader
**Branch:** add-quote-save-action
- [ ] 3.1 Add `SAVE_QUOTE` to `SelectedTextAction` (`NovelSelectedTextTranslationModels.kt:19-22`)
- [ ] 3.2 Add `MENU_ID_SAVE_QUOTE` menu item to the selection `ActionMode` menu (`NovelPageReaderPageContent.kt:474-544`), always visible (not gated behind a preference), following the existing `MENU_ID_DICTIONARY`/`MENU_ID_TRANSLATION` pattern
- [ ] 3.3 Wire the action through `onSelectedTextSelectionChanged` up to a screen-model-level handler that resolves the current `chapterId` and calls `InsertNovelQuote`
- [ ] 3.4 Add a small confirmation (snackbar/toast) when a quote is saved
- [ ] 3.5 Note: end-to-end manual verification of this task depends on text selection actually working, which is fixed by `improve-novel-reader-behavior-settings` — if that change hasn't landed yet, verify locally via a temporary workaround or wait for it before final manual testing

## 4. Quotes list screen and More-tab entry
**Branch:** add-quotes-list-screen
- [ ] 4.1 Add a `QuotesScreen` (or equivalent Voyager `Screen`) that queries `GetNovelQuotes` and renders a chronological list: quote text, source novel title, "Novel" type label, chapter label, saved timestamp
- [ ] 4.2 Add delete affordance per row (swipe or long-press), backed by `DeleteNovelQuote`
- [ ] 4.3 Add "Citações" as a new `AuroraSettingItem` entry in `MoreScreenAurora.kt`, following the existing `onCategoriesClick`/`onStatsClick`/`onHelpClick` wiring pattern, and wire its navigation in `MoreTab.kt`
- [ ] 4.4 Manually verify: save a quote while reading a novel, open Mais > Citações, confirm it appears with correct source/timestamp, then delete it and confirm it's gone
