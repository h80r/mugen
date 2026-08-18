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
**Migration**: None — no other system depends on level/XP state. If `UserProfile` survives in slimmed form for non-achievement fields (see `remove-achievements-system/design.md`), XP/level/points fields are dropped from it.

### Requirement: Unlock and reward flow
**Reason**: The achievement-to-reward unlock flow is deleted. Reward availability itself is preserved separately — via `UnlockableManager.isDefaultUnlockable()`, made permanent for easter-egg rewards in `unlock-easter-egg-cosmetics-by-default` — but the *mechanism that unlocks things via achievement completion* is gone.
**Migration**: None needed for easter-egg rewards (already default-unlocked). Any other achievement-gated reward not covered by a default-unlock allowlist becomes permanently locked with no unlock path — if any such reward is user-visible and valued, it should be added to a default-unlock allowlist before this change ships (see verification task).

### Requirement: Self-healing consistency checks
**Reason**: `sanitizeCrossCategoryFirstAchievements` and `recomputeGenreAchievements` only existed to repair achievement progress state, which no longer exists.
**Migration**: None.

### Requirement: Streak calculation
**Reason**: Streak calculation is relocated to the Stats screen (see `migrate-stats-and-cosmetic-selectors`'s `stats` spec, "Reading/watching streak on Stats screen" requirement) rather than removed — this entry is superseded, not dropped.
**Migration**: None — already ported and verified in the prerequisite change before this one ships.
