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

## Save action: extending the existing selection ActionMode menu

The novel reader's text-selection context menu (`NovelPageReaderPageContent.kt:474-544`, a native `ActionMode.Callback2`) already has a clean extension point: `MENU_ID_DICTIONARY`/`MENU_ID_TRANSLATION` menu items that call `onSelectedTextSelectionChanged(selection.copy(triggerAction = ...))`, where `SelectedTextAction` (`NovelSelectedTextTranslationModels.kt:19-22`) is currently `{ DICTIONARY, TRANSLATION }`.

**Decision:** add `SAVE_QUOTE` as a third `SelectedTextAction` value and `MENU_ID_SAVE_QUOTE` as a third always-visible menu item (not gated behind a preference toggle, unlike dictionary/translation which are gated behind `isDictionaryEnabled`/`isTranslationEnabled` — quote-saving has no reason to be optional). The existing `onSelectedTextSelectionChanged` callback chain already carries the selection's text and (via the surrounding reader state) the current chapter — the same plumbing that lets dictionary/translation actions know what chapter they're operating in is reused to resolve `chapterId` for the new `InsertNovelQuote` call, avoiding a second parallel selection-context-passing mechanism.

**Alternative rejected:** a custom floating action button or toolbar shown only after selection, separate from the native `ActionMode` menu. Rejected because it would duplicate positioning/lifecycle logic the `ActionMode` already handles correctly (dismissal on tap-outside, on back, on scroll) — reusing the existing menu is strictly simpler and keeps quote-saving consistent with how dictionary/translation already work from the user's perspective (same menu, one more option).

## Quotes list screen and More-tab entry

New top-level Mais-tab entry "Citações" (confirmed placement: alongside Categorias/Estatísticas/etc., per user's decision), following the existing `AuroraSettingItem` + `onXClick` wiring pattern in `MoreScreenAurora.kt` (same shape as `onCategoriesClick`/`onStatsClick`/`onHelpClick`).

The list screen queries `GetNovelQuotes` (all quotes, newest-first by `saved_at`) and renders each row with: quote text (truncated/expandable), source novel title + "Novel" type label + chapter label (resolved via the join described above), and a relative/formatted saved timestamp. No search, filter, or grouping in v1 — scope is explicitly a flat chronological list per the proposal. A swipe-to-delete or long-press-to-delete affordance is needed at minimum (no create-without-delete features in this codebase's conventions), backed by `DeleteNovelQuote`.

## Sequencing dependency

This change assumes text selection actually works, which `improve-novel-reader-behavior-settings` fixes (currently broken in both page-turn and scroll modes). The save-action menu item can be implemented and merged independently, but end-to-end verification (actually selecting text and saving a quote) is blocked until that other change lands, or until this change's own manual testing works around the selection bug locally. Track this as a soft dependency, not a hard branch dependency — `tasks.md` notes it explicitly.
