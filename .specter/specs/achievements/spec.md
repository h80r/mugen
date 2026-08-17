# Achievements Specification

## Requirements

### Requirement: Event-driven evaluation
The system SHALL evaluate achievement progress by consuming events from a shared `AchievementEventBus`, with META achievements evaluated on every event unconditionally to avoid staleness, and standard achievements evaluated only when not already unlocked.
Source: `AchievementHandler.processEvent`, `data/.../achievement/handler/`.

#### Scenario: Already-unlocked standard achievements are skipped
- GIVEN an achievement is already unlocked
- WHEN a relevant event occurs
- THEN its rule is not re-evaluated

#### Scenario: META achievements evaluate on every event
- GIVEN a META-type achievement tracks unlock counts of other achievements
- WHEN any event occurs, including ones unrelated to a fresh unlock
- THEN the META rule is still evaluated, specifically to stay correct after a restore or migration that could otherwise leave it stale

#### Scenario: Event bus drops oldest events under sustained overflow
- GIVEN the event bus buffer (capacity 100, replay 1) is full
- WHEN a new event is emitted faster than consumers can drain the buffer
- THEN the oldest buffered event is dropped (`DROP_OLDEST`), not the newest, and the emitter is never back-pressured

### Requirement: Achievement taxonomy
The system SHALL classify achievements into 10 types (QUANTITY, EVENT, DIVERSITY, STREAK, LIBRARY, META, BALANCED, SECRET, TIME_BASED, FEATURE_BASED) and 5 categories (ANIME, MANGA, NOVEL, BOTH, SECRET).
Source: `domain/.../achievement/model/{AchievementType,AchievementCategory}.kt`, `app/src/main/assets/achievements/achievements.json` (schema v25, 114 entries).

#### Scenario: BOTH category spans all media types
- GIVEN an achievement is categorized `BOTH`
- WHEN its rule evaluates cross-media data
- THEN it queries anime, manga, and novel database handlers separately and merges the results in application code, since there is no shared cross-media query layer

### Requirement: Tiered mechanics exist but are unused by current content
The system SHALL support multi-level tiered achievements (`applyTieredProgressUpdate`, tier-up rewards, tier XP = `points * 10`) as a fully implemented and tested mechanism, even though no achievement in the current `achievements.json` content actually uses the `tiers` field.

#### Scenario: Tier mechanics are exercised only by tests today
- GIVEN a developer inspects all 114 live achievement definitions
- WHEN they check the `tiers` field
- THEN none are populated — the tiered-progress code path is dead in production content but covered by unit tests, so a content update introducing tiers requires no code changes

### Requirement: Points, XP, and level formula
The system SHALL compute level from total accumulated XP using `getXPForLevel(level) = floor(100 * level^1.5)`, recomputing the level deterministically from total XP rather than storing incremental level state independently.
Source: `UserProfile`, `PointsManager`.

#### Scenario: Level is re-derivable from total XP alone
- GIVEN a backup is restored containing only a `total_xp` value
- WHEN the level is computed
- THEN `getLevelFromXP` deterministically re-derives the correct level by accumulating `getXPForLevel` thresholds, without needing a separately-stored level field to be correct

#### Scenario: Point additions are atomic and mutex-guarded
- GIVEN two achievement unlocks happen in quick succession
- WHEN both call `PointsManager.addPoints`
- THEN additions are serialized via a mutex and applied atomically at the DB layer, avoiding a lost-update race

### Requirement: Unlock and reward flow
The system SHALL, on achievement unlock, record an activity-log entry, add points, unlock associated reward IDs via `UnlockableManager`, and grant any profile rewards, before invoking a UI callback.

#### Scenario: Removed unlockables are never re-granted
- GIVEN an unlockable ID is present in `REMOVED_UNLOCKABLE_IDS` (e.g. `theme_achievement_gold`)
- WHEN a restored backup or legacy DB row references that ID
- THEN it is not re-granted, treating it as a permanently deprecated reward

### Requirement: Self-healing consistency checks
The system SHALL detect and repair inconsistent achievement state caused by bad restores or historical bugs, without ever lowering already-correct progress.
Source: `sanitizeCrossCategoryFirstAchievements`, `recomputeGenreAchievements` / `RecomputeGenreAchievementsMigration`.

#### Scenario: "First chapter" achievement re-locks if history is actually empty
- GIVEN a "first chapter read" achievement is unlocked but the user has zero manga history records
- WHEN `sanitizeCrossCategoryFirstAchievements` runs at startup
- THEN the achievement is forcibly re-locked, its progress reset to 0, and its associated unlockables locked, followed by a META recompute

#### Scenario: Genre-diversity repair only ever raises progress
- GIVEN a Cyrillic-genre under-counting bug caused diversity progress to be recorded too low
- WHEN `recomputeGenreAchievements` runs as a one-time migration repair
- THEN progress is only ever increased, never decreased, and already-unlocked achievements are never touched

### Requirement: Streak calculation
The system SHALL count consecutive days with reading or watching activity, walking backward from today, where an inactive "today" does not break an existing streak (only a fully missed prior day does), capped at 365 days.
Source: `StreakAchievementChecker`.

#### Scenario: No activity yet today does not reset the streak
- GIVEN the user has a 5-day streak and has not yet read or watched anything today
- WHEN the streak is checked mid-day
- THEN the streak remains 5 (today is skipped, not counted as a break) — only a fully elapsed day with zero activity would break it

### Requirement: Diversity caching
The system SHALL cache genre/source diversity counts for 5 minutes, invalidating immediately on library add/remove events.
Source: `DiversityAchievementChecker`.

#### Scenario: Library add invalidates the cache immediately
- GIVEN diversity counts were cached 1 minute ago
- WHEN the user adds a new-genre title to the library
- THEN the cache is invalidated immediately rather than waiting out the remaining 4 minutes of TTL
