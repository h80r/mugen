# Proposal: Fix Novel Library Search Stripping Spaces

## Intent
Users cannot type a space in Títulos > Novel > Busca — every keystroke calls `.trim()` on the live search state, so a trailing space (which is where new spaces are always typed) is stripped right back out before the next character can follow it. Manga's equivalent search has no such issue. This blocks any multi-word search in the novel library.

## Scope
- Fix `NovelLibraryScreenModel.search()` so spaces are preserved while typing, matching manga's behavior.
- Out of scope: any change to how the search query is actually used for filtering/matching (only the stored live-state value is affected).

## Approach
Remove `.trim()` from the state assignment in `NovelLibraryScreenModel.search()` (`app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt:358-362`), mirroring `MangaLibraryScreenModel.search()` (`MangaLibraryScreenModel.kt:1160-1162`), which stores the raw query untouched. If trimming is still needed for filtering/matching correctness, apply it only at the point the query is consumed for comparison, never on the value bound to the TextField.
