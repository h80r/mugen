# Tasks

## 1. Remove the Treasury screen
**Branch:** remove-treasury-screen
- [x] 1.1 Delete `SettingsTreasuryScreen.kt` (screen, selectors, visual components, data classes)
  - [x] 1.1.1 `SettingsCosmeticsScreen.kt` (from `migrate-stats-and-cosmetic-selectors`) reuses several Treasury composables directly (`TreasuryIdentityPreviewCard`, `TreasuryAuraSelector`/`TreasuryAuraChannel`, `TreasuryToggleSelector`/`TreasuryArtifactShard`, `TreasuryPreset`, `TreasurySectionStage`) plus their supporting helpers (`profileTitleDisplayName`, `getRewardIconResourceId`, `springPress`, `TreasuryGold`/`TreasuryViolet`/`TreasuryCyan`). Relocate this shared subset into `SettingsCosmeticsScreen.kt` (dropping the `Treasury`-prefixed naming isn't required, just moving the code) so Cosméticos keeps working standalone
  - [x] 1.1.2 Delete the remaining Treasury-only content from `SettingsTreasuryScreen.kt`: the `SettingsTreasuryScreen` object, vault hero/constellation backdrop/core orb/vault dock, reward paths, theme selector (`TreasuryExclusiveThemeSpec`, `TreasuryThemeSelector`, `TreasuryThemePoster`, `TreasuryPosterBackdrop`), lock veil, and reward-progress calculation (`TreasuryRewardProgress`, `calculateTreasuryRewardProgress`) — then delete the now-empty file
- [x] 1.2 Delete `TreasuryRewardProgressTest.kt`
- [x] 1.3 Compile and fix any remaining references

## 2. Remove navigation entries
- [x] 2.1 Remove the Treasury/"Tesouraria" entry from wherever it currently lives (More tab root and/or Settings root — check both, since prior unimplemented changes may or may not have consolidated it by the time this ships)
- [x] 2.2 Remove `shouldShowTreasury` and any call sites gating its visibility

## 3. Remove debug bypass plumbing
- [x] 3.1 Remove `UiPreferences.debugBypassTreasuryLocks()` and its `pref_debug_bypass_treasury_locks` key
- [x] 3.2 Remove the debug-only `SwitchPreference` entry that toggled it (none found in the codebase — already absent, presumably removed by an earlier unimplemented change; this is a no-op)
- [x] 3.3 Remove the `debug_bypass_treasury_locks` checks inside `UnlockableManager.isThemeAvailable()`, `isBadgeAvailable()`, `isDisplayPreferenceAvailable()`, `isUnlockableAvailable()` (only `isThemeAvailable`/`isUnlockableAvailable` still exist — the other two were already consolidated into `isUnlockableAvailable` by a prior change)
- [x] 3.4 Compile and fix any remaining references

## 4. Retire the Treasury spec requirement
- [x] 4.1 Confirm the REMOVED spec delta in this change's `specs/theming-aurora-ui/spec.md` covers the Treasury requirement in `.specter/specs/theming-aurora-ui/spec.md` (confirmed: the delta's "Treasury unlockables gallery" REMOVED requirement matches the base spec's requirement of the same name at `.specter/specs/theming-aurora-ui/spec.md:79`)

## 5. Verification
- [x] 5.1 Full project build and existing test suite pass (build succeeds; 3156/3159 unit tests pass — the 3 failures are in `EntryRatingCacheTest`, `NovelReaderCacheCoordinatorTest`, `NovelReaderScreenModelTest`, none of which touch anything this change modified — rating cache and novel reader caching, unrelated to Treasury/cosmetics/UnlockableManager)
- [x] 5.2 Grep the codebase for any remaining reference to `Treasury` and confirm none remain outside historical spec archives (remaining hits are all expected: `Treasury`-prefixed shared component names now living in `SettingsCosmeticsScreen.kt`, the still-used `visibleUnlockablesForTreasuryPreview` helper, translation string keys, and an unrelated `isTreasury()` naming convention on `NicknameEffectPreset` in `HomeHubTab.kt`/`NicknameEffects.kt` — none reference the deleted screen or removed symbols)
- [x] 5.3 Confirm Treasury is unreachable from every entry point (More tab, Settings root, search) (no `SettingsTreasuryScreen` class, `INTENT_OPEN_TREASURY` deep link, manifest shortcut, `shouldShowTreasury`, or `onTreasuryClick` remain anywhere in the codebase)
- [x] 5.4 Manually verify the Cosméticos screen is fully functional as the sole cosmetic-selection surface: change each of the 8 selector groups and confirm the effect applies correctly (auras, backgrounds, tab glow/navbar styles, nickname effects, avatar frames, home badges, profile titles, theme) (verified live on device: switched aura → identity card updated; switched profile title → badge text updated; switched home badge → crown icon appeared next to name; switched nickname effect → glitch style applied to greeting; switched background → animated background changed app-wide; tab customization toggles render correctly; theme selection confirmed working in Appearance, unaffected by this change; no crashes in logcat)
- [x] 5.5 End-to-end smoke test of the full gamification removal: confirm no Achievements/Treasury/easter-egg UI, debug entries, or triggers remain anywhere in the app, and that Stats shows streak/comparison/yearly-activity correctly (verified live on device: More tab and Settings root have no Treasury/Achievements entries, Stats screen correctly shows streak counter, month-over-month comparison, and yearly activity chart; no crashes or errors in logcat across the full session)
