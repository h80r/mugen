# Easter Eggs Specification

## Requirements

### Requirement: Achievement reward cosmetics are unconditionally unlocked
The system SHALL treat every cosmetic reward ID granted by any achievement in `achievements.json` — including but not limited to Aurora Heart, Lattice Resonance, and Void Broadcast — as unconditionally unlocked, via `UnlockableManager.isDefaultUnlockable()`, regardless of whether the associated achievement/quest was ever completed.
Source: `UnlockableManager.isDefaultUnlockable()` (`data/src/main/java/tachiyomi/data/achievement/UnlockableManager.kt:243`).

#### Scenario: Reward available without completing the quest
- GIVEN a user has never triggered or progressed a given achievement (easter egg or regular)
- WHEN `UnlockableManager.isUnlockableAvailable()` (or `isThemeAvailable()`/`isBadgeAvailable()`/`isDisplayPreferenceAvailable()`) is checked for one of that achievement's reward IDs
- THEN it returns true, identical to how default-prefixed unlockables already behave

#### Scenario: Previously-completed users see no change
- GIVEN a user already completed an achievement and its rewards are marked unlocked in preferences
- WHEN the reward availability is checked after this change ships
- THEN the reward is still available — `isDefaultUnlockable()` short-circuits to true before the stored unlock-state prefs are even consulted, so no state migration is needed
