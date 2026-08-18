# Design: Achievement System Teardown Sequencing

## Why this needs explicit sequencing
Unlike the other four changes, this one touches ~20+ call sites spread across unrelated feature areas (reader, player, library, downloads, search) plus a full sqldelight sourceSet, a domain model, and a UI stack — all interconnected through the event bus. Deleting in the wrong order produces a build that won't compile at intermediate steps, which defeats the purpose of doing this as a sequence of verifiable, revertable commits within the change.

## Ordering (outermost consumers first, foundational plumbing last)

### Step 1: UI layer
Delete `AchievementsTab.kt`, `AchievementScreenVoyager.kt`, `AchievementScreen.kt`, `AchievementScreenModel.kt`, `AchievementCard.kt`, `AchievementCategoryTabs.kt`, `AchievementTabsAndGrid.kt`, and their nav entries first. Nothing else in the codebase depends on these — they're leaf consumers of the achievement domain/data layer. Removing them first shrinks the surface the next steps need to worry about breaking.

### Step 2: Event emission call sites
Grep every remaining `AchievementEventBus.emit(...)`-shaped call site (originally ~27 across 21 files, minus whatever `remove-easter-eggs` already removed) and delete each call, leaving the surrounding feature code (`SetReadStatus`, `Browse*SourceScreenModel`, `Downloader` classes, etc.) otherwise untouched — these are single-line/small-block removals in files whose primary purpose is unrelated to achievements. Compile after this step; the event bus itself can still exist as dead code temporarily, which keeps this step low-risk and independently revertable.

### Step 3: AchievementHandler and migrations
Delete `AchievementHandler` (the event consumer) and `RecomputeGenreAchievementsMigration.kt`. By this point nothing emits events and nothing consumes them, so this is safe. Also remove the now-unreachable `AchievementEventBus` itself here (no more emitters, no more consumers).

### Step 4: Database sourceSet
Before deleting: confirm (or trigger once more) the `ActivityDatabase` copy migration from `migrate-stats-and-cosmetic-selectors` has run for the build under test, so no activity data is lost. Then:
- Remove the `AchievementsDatabase` sourceSet block from `data/build.gradle.kts`
- Delete `data/src/main/sqldelightachievements/` entirely
- Delete `data/src/main/java/tachiyomi/data/achievement/database/AchievementsDatabase.kt` and its DI wiring
- Run a full clean build (sqldelight code generation must not leave stale generated sources behind)

### Step 5: Domain models and JSON content
Delete `Achievement`, `AchievementType`, `AchievementCategory` domain models, `app/src/main/assets/achievements/achievements.json`, `PointsManager`, and achievement-only `UserProfile` fields. This step requires deciding `UserProfile`'s fate:
- **If** `UserProfile` is used only for achievement-adjacent data (level, XP, titles, badges counts) → delete it entirely, and check what (if anything) the profile screen falls back to for username/avatar display (likely `UserProfilePreferences`, which is untouched and already holds `name()`/`avatarUrl()` directly as preferences, independent of the `UserProfile` DB row)
- **If** the profile screen genuinely reads `UserProfile` DB fields beyond achievement data → keep a slimmed `UserProfile` with only the non-achievement fields, dropping `titles`/`badges`/`achievements_unlocked`/`total_achievements`/XP fields
- This determination should be made during implementation by grepping every read site of `UserProfile`/`UserProfileManager` before deciding which path to take — do not assume upfront

### Step 6: UnlockableManager cleanup (last)
Remove `unlockAchievementRewards`, `recomputeUnlockablesFromUnlockedAchievements`, `lockUnlockablesForAchievement` — the only methods with `Achievement`-typed parameters, now dangling since the `Achievement` model was deleted in Step 5. Keep every other method (`isUnlockableUnlocked`, `isThemeAvailable`, `isBadgeAvailable`, `isDisplayPreferenceAvailable`, `isUnlockableAvailable`, `isDefaultUnlockable`, `getUnlockableNameRes`, etc.) — these remain load-bearing for the Cosméticos selector screen and Treasury (until the next change removes Treasury) since default-unlock coverage from `unlock-easter-egg-cosmetics-by-default` depends entirely on `isDefaultUnlockable()` staying intact and correct.

## Compile checkpoints
Compile after each of the 6 steps, not just at the end — this change is large enough that a single "delete everything, then fix" pass risks losing track of which deletion caused which break. Steps 2 and 4 are the highest-risk (most files touched / most generated-code churn respectively) and warrant the most careful incremental verification.
