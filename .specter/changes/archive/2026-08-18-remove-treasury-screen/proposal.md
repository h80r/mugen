# Proposal: Remove Treasury Screen

## Intent
Treasury (`SettingsTreasuryScreen.kt`) was both a cosmetic-selection control panel and a visual "unlockables gallery" showing lock/progress state. `migrate-stats-and-cosmetic-selectors` already relocated every functional selector to a new Cosméticos sub-screen under Settings > Appearance, and `unlock-easter-egg-cosmetics-by-default` + `remove-achievements-system` (task 1) already made every cosmetic — easter-egg and regular achievement rewards alike — unconditionally available. Treasury's lock/progress gallery has nothing left to gate or show progress toward, and its selectors are redundant with the Cosméticos screen. This change deletes it.

## Scope
- Delete `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTreasuryScreen.kt` (all 3011 lines: vault hero, constellation backdrop, core orb, theme/aura/toggle selectors, reward paths, artifact shards, etc.)
- Delete `app/src/test/java/eu/kanade/presentation/more/settings/screen/TreasuryRewardProgressTest.kt`
- Delete `TreasuryRewardProgress`, `TreasuryPreset`, `TreasuryExclusiveThemeSpec` and other Treasury-only data classes/helpers
- Remove the Treasury entry from wherever it currently lives (More tab root and/or Settings root — independent of whether `reorganize-more-section-ia` has landed by this point, since that change also touches this entry; whichever change lands first, the other's corresponding lines become no-ops)
- Remove `shouldShowTreasury` (the "hidden until first unlock, always shown in DEBUG" gating logic) — no longer meaningful once there's no lock state to hide
- Remove `debugBypassTreasuryLocks` preference (`UiPreferences.debugBypassTreasuryLocks()`) and its debug-only `SwitchPreference` entry — dead now that all locks are already bypassed permanently
- Remove any remaining `debug_bypass_treasury_locks` checks inside `UnlockableManager` (`isThemeAvailable`, `isBadgeAvailable`, `isDisplayPreferenceAvailable`, `isUnlockableAvailable` each currently check this flag as a secondary bypass — safe to remove since `isDefaultUnlockable()` now covers every cosmetic unconditionally)
- Retire the Treasury requirement from the `theming-aurora-ui` spec (REMOVED delta)

## Out of scope
- The Cosméticos selector screen — already complete and independent, from `migrate-stats-and-cosmetic-selectors`
- Any further change to `UnlockableManager`'s core availability methods beyond removing the now-dead debug-bypass checks
- `reorganize-more-section-ia`/`remove-dead-standard-more-ui` — independent, unimplemented changes; this change does not wait for them

## Approach
Delete the screen and its test, then work outward: remove the nav entry, remove the `shouldShowTreasury` gating function, remove the debug preference and its remaining `UnlockableManager` checks. Compile after each step. Finish with a full build and a manual walk confirming: Treasury is unreachable from any entry point, the Cosméticos screen (from `migrate-stats-and-cosmetic-selectors`) is the sole place to select cosmetics, and every previously-Treasury-gated cosmetic still renders correctly wherever it's applied (auras, backgrounds, tab glow, nickname effects, avatar frames, home badges) — this is the final change in the gamification-removal sequence, so it's also the point to do an end-to-end smoke test of the whole removed system's absence.
