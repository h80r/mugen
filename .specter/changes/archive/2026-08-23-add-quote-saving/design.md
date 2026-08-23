# Design: Novel Quote Saving

## Data model

New SQLDelight table `novel_quotes`, mirroring the existing `novel_history` table's pattern (`data/src/main/sqldelightnovel/datanovel/novel_history.sq`) — FK'd to `novel_chapters`, one `.sq` file with named queries, following the same `datanovel` sourceSet as every other novel-specific table:

```sql
CREATE TABLE novel_quotes(
    _id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    chapter_id INTEGER NOT NULL,
    quote_text TEXT NOT NULL,
    saved_at INTEGER AS Date NOT NULL,
    FOREIGN KEY(chapter_id) REFERENCES novel_chapters (_id)
    ON DELETE CASCADE
);
```

Unlike `novel_history` (one row per chapter, upserted), quotes are append-only — a chapter can have many saved quotes, and a saved quote's text is a point-in-time excerpt, not something that gets updated. No `UNIQUE` constraint on `chapter_id`, no upsert query — only insert/select/delete.

**Source resolution for display** (novel title, media type, chapter label) is not denormalized into the quote row — it's joined at read time through `chapter_id → novel_chapters.novel_id → novels`, exactly like `novel_history`'s `getHistoryByNovelId` query joins through the same path. This keeps the quote row minimal and keeps titles/chapter numbers in sync if a novel's metadata is later edited, at the cost of the quote becoming orphaned (and thus invisible, via the FK cascade) if the source chapter is ever deleted — accepted as correct behavior: a quote without its source chapter has lost its context anyway.

Media type is not stored on the row either — every current quote is implicitly Novel, since that's the only scope. The list screen's "source (obra, tipo de obra, capítulo)" display can hardcode "Novel" as the type label for now. If manga quotes are added later, either a `media_type` column is added at that point or a small `novel_quotes`/future `manga_quotes` split is queried and merged at the domain layer, matching how `library`/`history` already keep separate per-media-type tables today rather than one polymorphic table.

## Domain layer

Follow the existing `domain/src/main/java/tachiyomi/domain/history/novel/` layering:
- `tachiyomi.domain.quote.novel.model.NovelQuote` (id, chapterId, text, savedAt)
- `tachiyomi.domain.quote.novel.repository.NovelQuoteRepository` (interface)
- Interactors: `GetNovelQuotes`, `InsertNovelQuote`, `DeleteNovelQuote` — mirroring the Get/Upsert/Remove split already established for anime/manga history (the `history` spec notes novel history itself lacks this full interactor set; quotes should not repeat that gap since it's a brand-new capability, not legacy debt)
- Repository impl in `data/src/main/java/tachiyomi/data/quote/novel/` backed by the new `.sq` queries

## Save action: replacing Share in the Aurora selection action console

The novel reader has two selection-related surfaces: the native `ActionMode` menu (`NovelPageReaderPageContent.kt`, driving `SelectedTextAction` / dictionary & translation lookups) and a separate Compose-owned bottom console, `NovelSelectedTextActionConsole` (`app/src/main/java/eu/kanade/presentation/reader/novel/NovelSelectedTextActionConsole.kt`), rendered by `NovelReaderContentHost.kt` whenever a selection is active. The console currently offers `COPY`, `SHARE`, `EXPAND`, `DICTIONARY`, `TRANSLATE` (`NovelSelectedTextConsoleAction`, `NovelSelectedTextTranslationModels.kt:25-31`), each wired to a handler in `NovelReaderContentHost.kt`'s `onAction` callback (~line 4167).

**Decision:** replace `NovelSelectedTextConsoleAction.SHARE` with a new `SAVE_QUOTE` action — the console keeps five slots, but "Share" becomes "Save quote" (icon, label, and `onAction` handler all swapped). This is a deliberate product choice (confirmed with the user, overriding the original plan to add a save action to the native `ActionMode` menu instead): the Aurora console is the primary, always-visible selection surface, and the Share action was judged less valuable there than quote-saving. The `onAction` handler for `SAVE_QUOTE` calls `InsertNovelQuote` with the console selection's text and the resolved `chapterId` (via the same reader-state plumbing the `DICTIONARY`/`TRANSLATE` branches already use to know the current chapter), then shows a snackbar/toast confirmation and clears the selection — mirroring `COPY`'s clear-after-action behavior.

The native `ActionMode` menu (`SelectedTextAction`, `MENU_ID_DICTIONARY`/`MENU_ID_TRANSLATION`) is left untouched — quote-saving is not added there.

**Alternative rejected:** adding `SAVE_QUOTE` as a third, always-visible item in the native `ActionMode` menu, additive alongside dictionary/translation. This was the original plan but was superseded once the user clarified they specifically want quote-saving to replace Share in the Aurora console — the console is the surface users reach for immediately after selecting text, and Share was judged the weakest of its five actions.

## Quotes list screen and More-tab entry

New top-level Mais-tab entry "Citações" (confirmed placement: alongside Categorias/Estatísticas/etc., per user's decision), following the existing `AuroraSettingItem` + `onXClick` wiring pattern in `MoreScreenAurora.kt` (same shape as `onCategoriesClick`/`onStatsClick`/`onHelpClick`).

The list screen queries `GetNovelQuotes` (all quotes, newest-first by `saved_at`) and renders each row with: quote text (truncated/expandable), source novel title + "Novel" type label + chapter label (resolved via the join described above), and a relative/formatted saved timestamp. No search, filter, or grouping in v1 — scope is explicitly a flat chronological list per the proposal. A swipe-to-delete or long-press-to-delete affordance is needed at minimum (no create-without-delete features in this codebase's conventions), backed by `DeleteNovelQuote`.

## Tap a quote to open the reader at that position

User-requested extension, added after initial manual testing (group 5 of `tasks.md`; full design discussion recorded in `/home/h80r/.claude/plans/cheerful-giggling-starfish.md`). Tapping a quote in the list opens `NovelReaderScreen` at `quote.chapterId` and, wherever possible, at the exact position the quote was originally selected from.

**Decision: search-by-text-content on open, not a persisted char offset.** The app has two structurally different reading systems — book mode (continuous document, `BookLocator(chapterId, blockIndex, charOffset)`) and classic per-chapter mode (native scroll / page reader / WebView, whose saved `lastPageRead` is a pure UI-scroll-position value with no relationship to text content). Persisting an exact offset at save time would require new `novel_quotes` columns (a schema migration) and new offset-capture plumbing in three separate renderers. Instead, the reader is given the quote's saved text as a one-shot "seek target" and searches the now-loaded chapter content for it on open, falling back to the normal resume behavior if the text isn't found (e.g. the chapter was re-fetched and its text changed):

- **Book mode**: `NovelBookArtifactSource.locatorOfQuoteInChapter(chapterId, quoteText)` reads the chapter's own artifact HTML slice, searches its normalized block children (each already carrying a whole-book character offset baked in at compile time) for the quote text, and converts a match to a `BookLocator`. Seeks with `BookSeekReason.Search` (previously declared, unused) instead of `BookSeekReason.Resume`.
- **Native scroll (plain) / page reader (plain + rich)**: `findQuoteTextBlockMatch` does a whitespace-normalized substring search over the chapter's already-rendered text blocks and overrides the initial scroll index / page.
- **WebView**: reuses `buildWebReaderTtsSyncJavascript` (built for TTS sync) unchanged — a `TreeWalker` text search plus `scrollIntoView`, with its own JS-side percent-scroll fallback if the text isn't found.
- **Out of scope for v1**: the "rich" native-scroll renderer (used by some sources) has no plain text block list to search against and always falls back to normal open — a deliberate scope line, not an oversight.

## Sequencing dependency

This change assumes text selection actually works, which `improve-novel-reader-behavior-settings` fixes (currently broken in both page-turn and scroll modes). The save-action menu item can be implemented and merged independently, but end-to-end verification (actually selecting text and saving a quote) is blocked until that other change lands, or until this change's own manual testing works around the selection bug locally. Track this as a soft dependency, not a hard branch dependency — `tasks.md` notes it explicitly.
