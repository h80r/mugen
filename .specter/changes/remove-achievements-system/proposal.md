# Proposal: Remove Achievements System

## Intent
With easter eggs already deleted (`remove-easter-eggs`) and `activity_log` already migrated to its own `ActivityDatabase` (`migrate-stats-and-cosmetic-selectors`), nothing depends on the achievement system anymore except the achievement UI itself. This change removes the event-driven achievement engine end to end: the event bus, all emission call sites, the achievement database, the JSON content, and the Achievements screen — deleting the largest, most spread-out part of the gamification layer.

## Scope

### Event bus and emission sites
- Delete `AchievementEventBus` (`MutableSharedFlow`, replay=1, buffer 100, `DROP_OLDEST`)
- Remove all remaining event emission call sites (originally 27 across 21 files before easter-egg-only sites were removed in the prior change) spread across: `SetReadStatus`, `SetSeenStatus`, the six `Browse*SourceScreenModel` classes, the three `Global*SearchScreenModel` classes, the three `Downloader` classes, and other feature-tracking sites in reader/player/library/download/search code
- Delete `AchievementHandler` (`processEvent`, tiered-progress logic, self-healing consistency checks: `sanitizeCrossCategoryFirstAchievements`, `recomputeGenreAchievements`)
- Delete `RecomputeGenreAchievementsMigration.kt` and any other achievement-only migration

### Database
- Delete the `AchievementsDatabase` sqldelight sourceSet declaration from `data/build.gradle.kts`
- Delete `data/src/main/sqldelightachievements/` entirely (`achievement_progress.sq`, `user_profile.sq`, `activity_log.sq` — already superseded by `ActivityDatabase`, `achievements.sq`, migrations)
- Delete `data/src/main/java/tachiyomi/data/achievement/database/AchievementsDatabase.kt` and related driver/DI wiring
- Before dropping the database file, run the `ActivityDatabase` copy migration from `migrate-stats-and-cosmetic-selectors` one final time (or confirm it already ran and is current) so no in-flight activity data is lost between the last app session and this update

### Domain/data layer
- Delete `UnlockableManager`'s achievement-specific coupling: `unlockAchievementRewards`, `recomputeUnlockablesFromUnlockedAchievements`, `lockUnlockablesForAchievement` and their `Achievement`-typed parameters — but **keep** the class itself and its `isXAvailable()`/`isDefaultUnlockable()` methods, since the Cosméticos selector screen and (until the next change) Treasury still read through it for "is this cosmetic available" checks, now permanently true for every ID via the default-unlock allowlist from `unlock-easter-egg-cosmetics-by-default`
- Delete `PointsManager`, XP/level formula code (`getXPForLevel`, `getLevelFromXP`), and `UserProfile` fields that only serve achievements (`achievements_unlocked`, `total_achievements`, `titles`, `points`/`xp` fields) — evaluate whether `UserProfile` survives in a slimmed form (it may still back the profile screen's username/avatar) or whether achievement-specific columns are dropped from it while the rest stays
- Delete `app/src/main/assets/achievements/achievements.json`
- Delete the `AchievementType`/`AchievementCategory` domain models and the `Achievement` domain model itself

### UI
- Delete `AchievementCard.kt`, `AchievementCategoryTabs.kt`, `AchievementTabsAndGrid.kt`
- Delete `AchievementScreenVoyager.kt`, `AchievementScreen.kt`, `AchievementScreenModel.kt`
- Delete `AchievementsTab.kt` and its entry from the More tab / Settings root (wherever it currently appears, independent of whether `reorganize-more-section-ia` has landed — see sequencing note in the parent plan)
- Delete `AchievementScreenTest.kt` and any other achievement-specific test

### Spec retirement
- Retire the `achievements` spec entirely (REMOVED delta covering every requirement currently in `.specter/specs/achievements/spec.md`)

## Out of scope
- Treasury — `remove-treasury-screen`, since Treasury's gating logic reads `UnlockableManager` (kept) rather than the achievement engine being deleted here
- The Stats screen's new streak/comparison/yearly-activity sections — already fully independent since `migrate-stats-and-cosmetic-selectors` (this change must not touch `ActivityDatabase` or Stats code, only confirm they're unaffected)
- The Cosméticos selector screen — unaffected, since it never depended on the achievement engine, only on `UnlockableManager`'s availability checks

## Approach
Delete in dependency order to keep the build compiling at each step: UI screens first (nothing else depends on them), then event bus + emission sites (grep-verify every emitter is gone before touching the handler), then `AchievementHandler` and migrations, then the database sourceSet, then remaining domain models and JSON content, then `UnlockableManager`'s achievement-coupled methods last (since other still-live code paths call into the class). Requires a `design.md` covering the event-bus teardown sequencing across ~20 emission sites and the `AchievementsDatabase` sourceSet removal, given the size and spread of this change relative to the others.
