# Tasks

## 1. New ActivityDatabase sqldelight sourceSet
**Branch:** activity-database-sourceset
- [x] 1.1 Declare `ActivityDatabase` sourceSet in `data/build.gradle.kts`, mirroring the existing `AchievementsDatabase` declaration
- [x] 1.2 Create `data/src/main/sqldelightactivity/tachiyomi/data/activity/activity_log.sq`, copying the table definition and all queries from `data/src/main/sqldelightachievements/tachiyomi/data/achievement/activity_log.sq` verbatim
- [x] 1.3 Wire the new database's driver into DI (Koin module), following the existing `AchievementsDatabase` driver injection pattern
- [x] 1.4 Create `ActivityLogRepository` exposing read methods matching the existing achievement-side activity access shape (`getActivityForDateRange`, `getMonthActivity`, `getActivityStats`, `getActivityForDate`)

## 2. One-time copy migration
- [x] 2.1 Create a `Migration` (following `RecomputeGenreAchievementsMigration.kt`'s guarded-by-boolean-preference pattern) that copies all rows from `AchievementsDatabase.activity_log` into `ActivityDatabase.activity_log` via upsert
- [x] 2.2 Verify the migration is idempotent (safe to run twice) and guarded so it runs exactly once per install
- [x] 2.3 Manually verify: install with existing achievement/activity data, run the migration, confirm `ActivityDatabase.activity_log` matches the source table row-for-row

## 3. Stats screen: streak, comparison, yearly activity
- [x] 3.1 Create `ActivityStatsModel`/domain data classes (media-agnostic `MonthStats`-equivalent without achievements-unlocked) and a `SharedActivityStatsScreenModel` in the Stats layer, exposing current streak, current/previous month comparison, and 12-month activity, all backed by `ActivityLogRepository`
- [x] 3.2 Port `calculateCurrentStreak` (`AchievementScreenModel.kt:209-224`) logic into the new screen model
- [x] 3.3 Build `SharedActivityStreakCard`/`SharedActivityComparisonCard` composables (adapted from `ActivityStreakIndicator.kt`/`AchievementStatsComparison.kt`, dropping the achievements-unlocked stat)
- [x] 3.4 Build `SharedYearlyActivityGraph` composable (adapted from `AchievementActivityGraph.kt`), reusing its bar-chart approach
- [x] 3.5 Add a shared, media-agnostic `SharedActivityStatsSection` composable containing all three, and include it identically in `AnimeStatsAuroraContent.kt`, `MangaStatsAuroraContent.kt`, `NovelStatsAuroraContent.kt`
- [x] 3.6 Manually verify the new Stats section shows data matching what the (still-present) Achievements screen currently shows for the same account

## 4. Cosmetic selectors in Settings > Appearance
**Branch:** appearance-cosmetic-selectors
- [x] 4.1 Create the "Cosméticos" sub-screen, reachable via a nav-card `PreferenceItem` from `SettingsAppearanceScreen.kt`
- [x] 4.2 Theme selector group, bound to `UiPreferences.appTheme()`
- [x] 4.3 Aura selector group, bound to `UiPreferences.enabledAuras()`
- [x] 4.4 Special background selector group, bound to `UiPreferences.specialBackgroundStyle()`
- [x] 4.5 Tab customization selector group, bound to `UiPreferences.showTabGlow()`/`showCelestialNavbar()`/`showCircuitNavbar()`
- [x] 4.6 Profile title selector group, bound to `UserProfilePreferences.profileTitle()`
- [x] 4.7 Profile effect selector group, bound to `UserProfilePreferences.nicknameEffect()`
- [x] 4.8 Avatar frame selector group, bound to `UserProfilePreferences.avatarFrameStyle()`
- [x] 4.9 Home badge selector group, bound to `UserProfilePreferences.homeBadgeStyle()`
- [x] 4.10 Decide (based on resulting screen length) whether the 8 groups need internal tabs or work as a single scrollable screen; split into tabs only if needed
- [x] 4.11 Manually verify each selector's current value matches what Treasury shows as "currently applied" for the same account, and that changing a selection in the new screen is reflected identically to changing it via Treasury

## 5. Verification
- [x] 5.1 Full project build and existing test suite pass
- [x] 5.2 Confirm Achievements and Treasury screens still work unmodified (this change is additive only)
- [x] 5.3 Confirm the new Stats section and Cosméticos sub-screen are reachable and functional end-to-end on a device/emulator

## 6. Post-verification revision: Geral tab + Treasury-visual Cosméticos
Groups 1–5 above are functionally complete but were reshaped by user feedback after seeing the app running: the Stats block was duplicated across three tabs instead of living in one place, and Cosméticos looked like a plain settings list instead of Treasury's card-based selectors. This group relocates/restyles that work without redoing the underlying data layer.

- [x] 6.1 Add a `GENERAL` entry to `StatsContentTab` (`StatsTab.kt`), placed first in `statsContentTabs()`; add a new `generalStatsTab()` (mirroring `animeStatsTab()`'s shape) rendering `SharedActivityStatsSection` as its sole content; fix `isMangaTab` index math (Manga shifts from index 1 to 2)
- [x] 6.2 Remove the `SharedActivityStatsSection` call (and its per-tab `SharedActivityStatsScreenModel` instantiation/threading) from `AnimeStatsAuroraContent.kt`/`AnimeStatsTab.kt`, `MangaStatsAuroraContent.kt`/`MangaStatsTab.kt`, `NovelStatsAuroraContent.kt`/`NovelStatsTab.kt` — the block now lives only in the Geral tab
- [x] 6.3 Remove the Theme selector group from `SettingsCosmeticsScreen.kt` (Appearance's own theme selector already covers this)
- [x] 6.4 Flip `TreasuryPreset`, `TreasuryToggleSelector`, `TreasuryArtifactShard`, `TreasuryAuraSelector`, `TreasuryAuraChannel`, `TreasurySectionStage`, `getRewardIconResourceId`, and the `TreasuryGold`/`TreasuryViolet`/`TreasuryCyan` constants from `private` to `internal` in `SettingsTreasuryScreen.kt`, with no logic changes
- [x] 6.5 Rebuild the remaining 7 Cosméticos groups (aura, special background, tab customization, profile title, profile effect, avatar frame, home badge) to render via the now-internal Treasury components instead of `ListPreference`, passing a synthetic all-unlocked `unlockedUnlockables` set and an empty `rewardToAchievementMap` so every option always renders as selectable
- [x] 6.6 Manually verify on device: Geral tab shows streak/comparison/yearly-activity and Anime/Manga/Novel tabs no longer do; Cosméticos has no Theme group; the remaining 7 groups render as Treasury-style card grids with no lock indicators; selecting an option in 2–3 groups is reflected identically in Treasury
- [x] 6.7 Add a fixed identity preview card (avatar frame, styled nickname with home-hub badge, profile title) pinned above the scrollable Cosméticos selectors, reusing Treasury's own preview card — extracted as `TreasuryIdentityPreviewCard` so both screens share one implementation — and verify on device that it updates live as selections change and stays pinned through scroll
