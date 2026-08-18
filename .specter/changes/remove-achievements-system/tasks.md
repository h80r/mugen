# Tasks

## 1. Audit non-easter-egg achievement rewards before deleting content
- [x] 1.1 List every reward ID in `achievements.json` NOT already covered by the default-unlock allowlist from `unlock-easter-egg-cosmetics-by-default` (i.e. regular QUANTITY/STREAK/DIVERSITY/etc. achievement cosmetics — themes, badges, display prefs earned through normal play rather than easter eggs)
  - Result: only 3 ids are uncovered — `badge_achievement_master`, `badge_week_warrior`, `display_grid_large`. Every other non-easter-egg reward id (themes, auras, titles, avatar frames, home badges, profile nickname effects, special backgrounds) is already in `ACHIEVEMENT_UNLOCKABLE_IDS`.
- [x] 1.2 Decide, with the user if ambiguous, whether these rewards should also become default-unlocked (consistent with "keep cosmetics already unlocked by default") or are acceptable to lose entirely once achievements are removed — this determines whether `isDefaultUnlockable()` needs a second, broader allowlist addition before achievements.json is deleted
  - Decision (user, confirmed after investigation): let all 3 die, do NOT add to the allowlist. Investigation showed none of the 3 ever had player-facing effect: `badge_achievement_master`/`badge_week_warrior` only appended to `UserProfile.badges`, a list read by zero UI (only touched by `BackupUserProfile`/`AchievementRestorer`) — distinct from the visual, already-covered `home_badge_*` ids. `display_grid_large` was meant to gate a library grid density but the library already uses `LibraryDisplayMode` ungated; the unlockable was never wired to it. Nothing is lost by letting them go.
  - Follow-up (user): also delete dead code tied specifically to these three/the `badge_*`/`display_*` unlockable types once the relevant later steps are reached — see notes added to tasks 6.5 and 7.1 below.
- [x] 1.3 If broadening default-unlock is needed, extend `UnlockableManager.isDefaultUnlockable()` accordingly (same mechanism as the easter-egg change) before proceeding to deletion tasks below
  - Not applicable — decision in 1.2 was not to broaden default-unlock.

## 2. Remove Achievements UI (Step 1)
**Branch:** remove-achievements-ui
- [ ] 2.1 Delete `AchievementsTab.kt` and its nav entry from wherever it currently lives (More tab / Settings root)
- [ ] 2.2 Delete `AchievementScreenVoyager.kt`, `AchievementScreen.kt`, `AchievementScreenModel.kt`
- [ ] 2.3 Delete `AchievementCard.kt`, `AchievementCategoryTabs.kt`, `AchievementTabsAndGrid.kt`
- [ ] 2.4 Delete `AchievementScreenTest.kt` and other achievement-specific tests
- [ ] 2.5 Compile and fix any remaining references

## 3. Remove event emission call sites (Step 2)
**Branch:** remove-achievement-event-emitters
- [ ] 3.1 Grep every remaining `AchievementEventBus` emission call site and remove each, leaving surrounding feature code untouched
- [ ] 3.2 Compile and fix any remaining references

## 4. Remove AchievementHandler, migrations, and the event bus (Step 3)
- [ ] 4.1 Delete `AchievementHandler` (`processEvent`, tiered-progress logic, `sanitizeCrossCategoryFirstAchievements`, `recomputeGenreAchievements`)
- [ ] 4.2 Delete `RecomputeGenreAchievementsMigration.kt`
- [ ] 4.3 Delete `AchievementEventBus`
- [ ] 4.4 Compile and fix any remaining references

## 5. Remove the achievements database (Step 4)
**Branch:** remove-achievements-database
- [ ] 5.1 Confirm the `ActivityDatabase` copy migration has run and `ActivityDatabase.activity_log` is current
- [ ] 5.2 Remove the `AchievementsDatabase` sourceSet block from `data/build.gradle.kts`
- [ ] 5.3 Delete `data/src/main/sqldelightachievements/` entirely
- [ ] 5.4 Delete `data/src/main/java/tachiyomi/data/achievement/database/AchievementsDatabase.kt` and its DI wiring
- [ ] 5.5 Full clean build to verify no stale sqldelight-generated code remains

## 6. Remove domain models and JSON content (Step 5)
- [ ] 6.1 Grep every read site of `UserProfile`/`UserProfileManager` to determine whether `UserProfile` survives in slimmed form or is deleted entirely (per `design.md`'s Step 5 guidance)
- [ ] 6.2 Delete `Achievement`, `AchievementType`, `AchievementCategory` domain models
- [ ] 6.3 Delete `app/src/main/assets/achievements/achievements.json`
- [ ] 6.4 Delete `PointsManager` and XP/level formula code
- [ ] 6.5 Apply the `UserProfile` decision from 6.1: delete entirely, or drop only achievement-specific fields (`titles`, `badges`, `achievements_unlocked`, `total_achievements`, XP/level fields)
  - Per task 1.2's decision: `badges` is confirmed dead (no UI reads it) and must go regardless of the broader `UserProfile` decision — also remove `UserProfileManager.addBadge`/`removeBadge`, `UserProfileRepositoryImpl.addBadge`/`removeBadge`, and their sqldelight query if achievements DB is already gone by this point (else covered in step 4/5).
- [ ] 6.6 Compile and fix any remaining references

## 7. Clean up UnlockableManager (Step 6, last)
- [ ] 7.1 Remove `unlockAchievementRewards`, `recomputeUnlockablesFromUnlockedAchievements`, `lockUnlockablesForAchievement`
  - Per task 1.2's decision: also remove `isBadgeAvailable`, `isDisplayPreferenceAvailable` (both are unused outside their own definitions — the `badge_*`/`display_*` unlockables they gate, `badge_achievement_master`/`badge_week_warrior`/`display_grid_large`, were never wired to any selector UI and were deliberately let die rather than default-unlocked), the `badge_`/`display_` branches in `applyUnlockable`/`getUnlockableType`, and the badge/display entries in `getUnlockableNameRes`.
- [ ] 7.2 Confirm `isUnlockableUnlocked`, `isThemeAvailable`, `isBadgeAvailable`, `isDisplayPreferenceAvailable`, `isUnlockableAvailable`, `isDefaultUnlockable`, `getUnlockableNameRes`, `getUnlockableName`, `getUnlockableType` all remain intact and unbroken
- [ ] 7.3 Compile and fix any remaining references

## 8. Retire the achievements spec
- [ ] 8.1 Confirm the REMOVED spec delta in this change's `specs/achievements/spec.md` covers every requirement in `.specter/specs/achievements/spec.md`

## 9. Verification
- [ ] 9.1 Full project build and existing test suite pass
- [ ] 9.2 Grep the codebase for any remaining reference to `Achievement`, `achievement_` (preference/pref keys), `PointsManager` and confirm none remain outside historical spec archives
- [ ] 9.3 Confirm the Stats screen's streak/comparison/yearly-activity sections still work correctly (unaffected by this change, per `migrate-stats-and-cosmetic-selectors`)
- [ ] 9.4 Confirm the Cosméticos selector screen still shows every cosmetic (easter-egg and, per task 1, any broadened default-unlock set) as selectable
- [ ] 9.5 Confirm Treasury (still present) still renders without crashing, now showing everything unlocked
