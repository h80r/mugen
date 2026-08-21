# Tasks

## 1. Fix search state trimming
- [ ] 1.1 Remove `.trim()` from `NovelLibraryScreenModel.search()` (`app/src/main/java/eu/kanade/tachiyomi/ui/library/novel/NovelLibraryScreenModel.kt:358-362`), matching `MangaLibraryScreenModel.search()`'s untouched assignment
- [ ] 1.2 Check every other reader of `state.searchQuery` in the novel library screen model/filtering code to confirm none relied on the value already being trimmed (e.g. blank-check via `.ifBlank`); adjust only if a real bug would otherwise surface (e.g. a query of only spaces should still be treated as "no search")
- [ ] 1.3 Manually verify: type a multi-word query with spaces into Títulos > Novel > Busca and confirm it behaves like the manga search
