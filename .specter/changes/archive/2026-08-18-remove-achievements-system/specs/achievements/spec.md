# Achievements Specification Delta

## REMOVED Requirements

### Requirement: Event-driven evaluation
**Reason**: The achievement system, its event bus, and its evaluation engine are deleted entirely.
**Migration**: None — no user-facing state depends on the event bus surviving.

### Requirement: Achievement taxonomy
**Reason**: `Achievement`, `AchievementType`, `AchievementCategory` domain models and `achievements.json` content are deleted.
**Migration**: None.

### Requirement: Tiered mechanics exist but are unused by current content
**Reason**: Dead-in-production tiered-progress code is deleted alongside the rest of the achievement engine.
**Migration**: None — no content ever used this mechanism.

### Requirement: Points, XP, and level formula
**Reason**: `PointsManager` and the XP/level formula are deleted; they had no purpose outside achievement progression.
**Migration**: None — no other system depends on level/XP state. `UserProfile` (which held these fields) was deleted entirely rather than slimmed — see task 5.1a's investigation: its only two still-read fields (`unlockedThemes`, `achievementsUnlocked`) were themselves confirmed dead weight, so the whole class went, XP/level fields included.

### Requirement: Unlock and reward flow
**Reason**: The achievement-to-reward unlock flow is deleted. Reward availability itself is preserved separately — via `UnlockableManager.isDefaultUnlockable()`, made permanent for easter-egg rewards in `unlock-easter-egg-cosmetics-by-default` — but the *mechanism that unlocks things via achievement completion* is gone.
**Migration**: None needed for easter-egg rewards or any other reward already covered by the default-unlock allowlist (every non-easter-egg reward except 3, confirmed in task 1.1). The remaining 3 — `badge_achievement_master`, `badge_week_warrior`, `display_grid_large` — were confirmed to have zero player-facing effect even while the achievement engine was live (no UI ever surfaced them) and were deliberately left unlockable via neither path, per the user's decision in task 1.2.

### Requirement: Self-healing consistency checks
**Reason**: `sanitizeCrossCategoryFirstAchievements` and `recomputeGenreAchievements` only existed to repair achievement progress state, which no longer exists.
**Migration**: None.

### Requirement: Streak calculation
**Reason**: Streak calculation is relocated to the Stats screen (see `migrate-stats-and-cosmetic-selectors`'s `stats` spec, "Reading/watching streak on Stats screen" requirement) rather than removed — this entry is superseded, not dropped.
**Migration**: None — already ported and verified in the prerequisite change before this one ships.

### Requirement: Diversity caching
**Reason**: `DiversityAchievementChecker`, the only thing this 5-minute genre/source diversity cache served, is deleted along with the rest of the achievement engine — nothing else read from it.
**Migration**: None — the cache had no consumer outside the achievement rule it fed.
