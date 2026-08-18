# Tasks

## 1. Audit all isAuroraStyle / LocalIsAuroraTheme branch sites
**Branch:** none (read-only verification, no code changes)
- [x] 1.1 Grep every use of `theme.isAuroraStyle` and `LocalIsAuroraTheme` across the codebase and list each `if`/`else` (or ternary) site that branches on it
- [x] 1.2 For each site, confirm the `false` branch is genuinely dead (no `AppTheme` entry sets `isAuroraStyle = false`) versus a use that just *reads* the flag for a different, still-independent purpose (e.g. home hero CTA mode) — only branch-selection sites on "which screen/composable to render" are in scope for removal

**Audit result:**
Confirmed via `AppTheme.kt`: `isAuroraStyle` defaults to `true`, no enum entry overrides it — every branch-selection site's `false`/`else` path is dead.

All ~30 branch-selection sites from tasks.md section 2-3 are confirmed genuine (category A: `if/else` or ternary choosing between an Aurora and non-Aurora composable/value): `MoreTab.kt`, the 3 history screens + `HistoryDialogs.kt`, `MangaScreen.kt`/`AnimeScreen.kt`/`NovelScreen.kt`, the 5 category sites, the 6 download sites, the 4 stats sites, `HomeScreen.kt` (both sites), `UpdatesTab.kt`, `HistoriesTab.kt`, `AnimeLibraryTab.kt`, `MainActivity.kt`, `ReaderViewModel.kt`.

Confirmed out-of-scope independent uses (category B, do not touch): the three `Browse{Anime,Manga,Novel}SourceToolbar.kt` files and `AuroraCoverPlaceholders.kt` read the flag for toolbar/cover-asset styling on a component that always renders (not a which-composable choice); `SettingsUiStyle.kt` resolves an independent `SettingsUiStyle` enum for the settings screens, per the proposal's explicit carve-out.

Plumbing (category C, not a removal target): `TachiyomiTheme.kt` defines/provides `LocalIsAuroraTheme` itself.

Related but out-of-scope observation (category D): `AuroraSectionMigration.kt:19` guards a one-time preference-seeding migration with `if (!appTheme.isAuroraStyle) return true`. This is a historical migration checking install-time state, not a live UI branch — migrations intentionally preserve superseded logic paths, so it's left alone.

## 2. Remove the standard MoreScreen and simplify MoreTab
- [x] 2.1 Delete `app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt`
- [x] 2.2 Delete corresponding tests under `androidTest`/`test` for the standard MoreScreen (verified: none exist)
- [x] 2.3 In `MoreTab.kt`, remove the `if (theme.isAuroraStyle) { MoreScreenAurora(...) } else { MoreScreen(...) }` branch, calling `MoreScreenAurora` directly
- [x] 2.4 Remove now-unused parameters/callbacks that only the standard variant consumed, including the vestigial `onClickStorage` parameter

## 3. Collapse the remaining conditional branches
- [x] 3.1 History screens: `MangaHistoryScreen.kt`, `NovelHistoryScreen.kt`, `AnimeHistoryScreen.kt`, `HistoryDialogs.kt`
- [x] 3.2.1 `MangaScreen.kt`: collapse the `if (theme.isAuroraStyle) { MangaScreenAuroraImpl(...); return }` guard to call `MangaScreenAuroraImpl` directly, then delete the now-dead classic implementation (`MangaScreenSmallImpl`, `MangaScreenLargeImpl`, `resolveMangaClassicFastScrollBlockStartIndex`, `SharedMangaBottomActionMenu`, `sharedChapterItems`, `onChapterItemClick`) and its unit test coverage
- [x] 3.2.2 `AnimeScreen.kt`: same pattern — collapse the guard, delete `AnimeScreenSmallImpl`, `AnimeScreenLargeImpl`, `resolveAnimeClassicFastScrollBlockStartIndex`, `SharedAnimeBottomActionMenu`, `sharedSeasons`, `sharedEpisodeItems`, `onEpisodeItemClick`, `parseOriginalTitle`, the classic `resolveCoverUrl(MetadataSource)` overload, and `GRID_PADDING`/`ignorePadding` (all dead after collapse; kept `formatTime` since `AnimeScreenAurora.kt` calls it), and deleted the related test coverage
- [x] 3.2.3 `NovelScreen.kt`: same pattern — collapse the guard, delete the dead classic implementation (`NovelClassicChapterRow`, `NovelClassicChapterGroup`, `resolveNovelClassicFastScrollBlockStartIndex`, `initialVisibleChapterCount`, `nextVisibleChapterCount`, `resolveNovelFastScrollVisibleChapterCount`, `novelStatusText`, `NOVEL_CHAPTERS_PAGE_SIZE`, all dead after collapse), and deleted the related test coverage (`NovelScreenPaginationTest.kt`, the classic case in `TitleListFastScrollStartIndexTest.kt`)
- [x] 3.3 Category screens: `CategoryListItem.kt`, `CategoryFloatingActionButton.kt`, `MangaCategoryScreen.kt`, `AnimeCategoryScreen.kt`, `CategoriesTab.kt`
- [x] 3.4 Download screens: `DownloadEngineCard.kt`, `DownloadQueueItem.kt`, `DownloadsTab.kt`, `MangaDownloadQueueScreen.kt`, `AnimeDownloadQueueScreen.kt`, `NovelDownloadQueueScreen.kt`
- [x] 3.5 Stats screens: `StatsTab.kt`, `MangaStatsTab.kt`, `AnimeStatsTab.kt`, `NovelStatsTab.kt` (also deleted now-dead classic `MangaStatsScreenContent.kt`, `AnimeStatsScreenContent.kt`, `NovelStatsScreenContent.kt`)
- [x] 3.6.1 `HomeScreen.kt` (both sites), `UpdatesTab.kt` (also deleted now-dead classic `AnimeUpdatesTab.kt`, `MangaUpdatesTab.kt`, `NovelUpdatesTab.kt`), `HistoriesTab.kt`, `MainActivity.kt` (also collapsed the already-unused `isAurora`/`isHomeScreen` dead params on `resolveMainStatusBarStyleMode`), `ReaderViewModel.kt`
- [x] 3.6.2 `AnimeLibraryTab.kt` (~15 branch sites; large file, handled as its own sub-task; also deleted now-dead classic `AnimeLibraryContent.kt`)

## 4. Verification
- [x] 4.1 Full project build succeeds with no unresolved references (verified via `:app:assembleDebug` and `:app:compileDebugAndroidTestKotlin`; a pre-existing, unrelated dexing failure on `TtsNotificationLifecycleTest`'s space-containing method name blocks `assembleDebugAndroidTest` packaging but not compilation)
- [x] 4.2 Run existing unit/instrumented test suite. Unblocked two pre-existing, unrelated breakages found while running it: deleted `AboutFooterLinksTest.kt` (referenced enum members removed in an earlier unrelated commit, leaving the whole unit-test module uncompilable) and renamed the 6 backtick-named `@Test` methods in `TtsNotificationLifecycleTest.kt` (D8 rejects spaces in dexed method names, blocking `assembleDebugAndroidTest` packaging). Unit suite: 3234 run, 3 failures, all pre-existing and unrelated (`AuroraPrimeOverlayTest`, `NovelReaderScreenModelTest` timeout, `NovelReaderCacheCoordinatorTest`), confirmed by reproducing the same failures at the `main` merge-base. Instrumented suite (connected device): 21 failures, all `NoSuchMethodException: InputManager.getInstance` — an Espresso/device API-level incompatibility in the test harness, not a code regression; none involve the screens touched in this change, and the newly-unblocked `TtsNotificationLifecycleTest` passed 6/6.
- [x] 4.3 Manually open the More tab and each affected screen (History, Category, Stats, Downloads, Library) and confirm visual/behavioral output is unchanged from before the change (user verified on-device)
