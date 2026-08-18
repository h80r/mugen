# Proposal: Unlock Easter Egg Cosmetics By Default

## Intent
`remove-easter-eggs` (next change) deletes the trigger mechanisms for Aurora Heart, Lattice Resonance, and Void Broadcast, and a later change, `remove-achievements-system`, deletes the entire achievement engine outright — while explicitly keeping `UnlockableManager`'s `isXAvailable()`/`isDefaultUnlockable()` methods alive, because the Cosméticos selector screen (and Treasury, until it too is removed) still read through them for "is this cosmetic available" checks. That later proposal's own text says those checks become "permanently true for every ID via the default-unlock allowlist from `unlock-easter-egg-cosmetics-by-default`" — i.e. it already assumes this change makes *every* achievement-granted cosmetic default-unlocked, not just the three easter eggs. Once the achievement engine that used to grant these rewards is gone, there is no other path left to earn them, so gating them behind unlock-state prefs that can never be set again would silently strand every cosmetic in the game behind an unreachable quest. This change makes that switch for the full reward-ID set *before* the quests and the achievement engine are deleted, so there is never a window where a cosmetic becomes unreachable.

`UnlockableManager` (`data/src/main/java/tachiyomi/data/achievement/UnlockableManager.kt`) already has the exact mechanism needed: `isDefaultUnlockable(unlockableId)` (line 243) is checked first by every availability method (`isThemeAvailable`, `isBadgeAvailable`, `isDisplayPreferenceAvailable`, `isUnlockableAvailable`) and, if true, short-circuits to "always available" without touching the unlock-state prefs at all. Currently it only recognizes `default_`/`theme_default_`/`badge_default_`/`display_default_` prefixes. This change extends it to also recognize every reward ID granted by any achievement (easter eggs included), with no new infrastructure.

## Scope
- Identify the full reward-ID list granted by every achievement in `achievements.json` (via `unlockableId` and `rewards[].id` across all 114 entries — titles, themes, auras, avatar frames, home badges, profile nickname effects, special backgrounds/navbar/tab rewards — not just the three easter eggs)
- Extend `isDefaultUnlockable()` (or add an adjacent, equally-checked allowlist) to treat every identified reward ID as always-unlocked, without changing its signature or call sites
- For any user who already unlocked any of these rewards through play, no behavior change (already unlocked, now also default-unlocked — same visible result)
- For any user who never completed a quest/achievement, every reward becomes visible/selectable for the first time — this is the intended outcome, not a bug
- Update `REMOVED_UNLOCKABLE_IDS` handling: confirm the two already-removed IDs (`theme_achievement_gold`, `theme_achievement_sapphire`) are not part of the current reward-ID set and remain excluded/removed as before
- No change to `debugBypassTreasuryLocks` — it becomes irrelevant once every reward is default-unlocked, but removing it is out of scope here (it's deleted along with Treasury in `remove-treasury-screen`)

## Out of scope
- Deleting the easter egg trigger mechanisms themselves — `remove-easter-eggs`
- Deleting the achievement entries/engine themselves — `remove-easter-eggs`, `remove-achievements-system`
- Changing the Cosméticos selector screen built in `migrate-stats-and-cosmetic-selectors` — it already shows all options unconditionally, so no follow-up work is needed there

## Approach
Enumerate every reward ID across all of `achievements.json` (`unlockableId` and `rewards[].id` fields), cross-checked against `UnlockableManager.getUnlockableNameRes()`'s catalogued IDs and each easter egg's domain code for any ID referenced directly outside the achievement flow (e.g. `App.kt`'s bootstrap hooks), extend `isDefaultUnlockable()` with that concrete ID set, and verify via a fresh-install manual test that every such cosmetic appears unlocked without ever completing any achievement.
