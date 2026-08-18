# Tasks

## 1. Enumerate all achievement reward IDs
- [x] 1.1 Grep `achievements.json` for SECRET-category entries whose unlock condition or name ties them to Aurora Heart, Lattice Resonance, or Void Broadcast; list their `unlockableId` and `rewards[].id` values
- [x] 1.2 Cross-check `UnlockableManager.getUnlockableNameRes()` (lines 253-311) for every ID that reads as easter-egg-flavored (e.g. `theme_AURORA_PRIME`, `theme_LATTICE_PROTOCOL`, `aura_void_broadcast_red`, `avatar_frame_glitch_red`, `profile_nickname_effect_glitch_rune_red`, `profile_nickname_effect_glitch_rune`, `special_navbar_aurora_celestial`, `special_navbar_lattice_circuit`) and confirm each against its source achievement/quest
- [x] 1.3 Grep `App.kt`'s bootstrap hooks (lines ~182-213, 216, 269) and `ReaderActivity.kt`'s Void Broadcast insertion for any `unlockableId`/reward ID referenced directly outside the achievement JSON flow
- [x] 1.4 Produce a final, explicit list of reward IDs to mark default-unlocked (documented in the PR/commit, not just inline in code, for future auditability)
- [x] 1.5 **Scope expanded** (user decision, see below): enumerate `unlockableId`/`rewards[].id` across *all* 114 entries in `achievements.json`, not just the three easter eggs, since `remove-achievements-system`'s own proposal assumes this change already covers every achievement-granted cosmetic

**Scope expansion rationale:** `remove-achievements-system` (a later, already-planned change) deletes the entire achievement engine but explicitly keeps `UnlockableManager.isXAvailable()`/`isDefaultUnlockable()` alive for the Cosméticos selector and Treasury, stating those checks become "permanently true for every ID via the default-unlock allowlist from `unlock-easter-egg-cosmetics-by-default`." Once the achievement engine is gone, no reward — easter-egg or regular — has any other path to be earned, so scoping this change to only the three easter eggs would strand every other achievement cosmetic behind an unreachable unlock condition. Confirmed with the user before implementing.

**Enumerated reward IDs — full set (80 unique IDs across 114 achievements, from `unlockableId` + `rewards[].id`):**

Easter eggs (previously enumerated, task 1.1-1.4):
- `aurora_heart`: `theme_AURORA_PRIME`, `special_navbar_aurora_celestial`
- `lattice_resonance`: `theme_LATTICE_PROTOCOL`, `special_navbar_lattice_circuit`
- `void_broadcast_unlocked`: `theme_void_red`, `profile_nickname_effect_glitch_rune_red`, `aura_void_broadcast_red`, `avatar_frame_glitch_red`, `special_background_void_weeping_red`

Regular/secret achievements (remaining 71 IDs), by type:
- **Themes:** `theme_SAKURA_NOIR`, `theme_ONYX_GOLD`, `theme_NEBULA_TIDE`, `theme_EVENT_HORIZON`
- **Auras:** `aura_level_up`, `aura_harem`, `aura_matrix`, `aura_trinity_orbit`, `aura_deep_focus`, `aura_shadow_monarch`, `aura_ascendant_gold`
- **Titles:** `title_trinity_initiate`, `title_trinity_master`, `title_trinity_legend`, `title_three_realms_collector`, `title_event_horizon_cartographer`, `title_finisher`, `title_closer`, `title_romance`, `title_horror`, `title_isekai`, `title_sol`, `title_shadow_monarch`, `title_weeb`, `title_focus_reader`, `title_deep_reader`, `title_immersion_adept`, `title_immersion_master`, `title_hybrid_reader`, `title_cross_format_scholar`, `title_anime_novel_master`, `title_cross_media_beginner`, `title_cross_media_enthusiast`, `title_cross_media_champion`, `title_rank_1` .. `title_rank_10` (10 IDs)
- **Avatar frames:** `avatar_frame_hologram`, `avatar_frame_neon`, `avatar_frame_prismatic`, `avatar_frame_trinity_orbit`, `avatar_frame_deep_archive`, `avatar_frame_hybrid_scroll`, `avatar_frame_ascendant`
- **Home badges:** `home_badge_shuriken`, `home_badge_orbit`, `home_badge_crown`, `home_badge_trinity`, `home_badge_finisher`, `home_badge_immersion`, `home_badge_ascendant`
- **Profile nickname effects:** `profile_nickname_effect_aurora_crown`, `profile_nickname_effect_glitch_rune`, `profile_nickname_effect_cipher`, `profile_nickname_effect_trinity_prism`, `profile_nickname_effect_shadow_crown`, `profile_nickname_effect_rank_sigils`
- **Special backgrounds/tab/navbar:** `special_background_petal_storm`, `special_background_neon_orbit`, `special_background_event_horizon_library`, `special_background_trinity_constellation`, `special_background_shadow_realm`, `special_background_deep_space_archive`, `special_tab_glow`

**Explicitly excluded:**
- `REMOVED_UNLOCKABLE_IDS` (`theme_achievement_gold`, `theme_achievement_sapphire`) — confirmed absent from the 80-ID set (task 2.2); tombstoned ids must never be re-granted.

## 2. Extend UnlockableManager
- [x] 2.1 Extend `isDefaultUnlockable()` (`UnlockableManager.kt:243`) to also return true for the enumerated reward ID set — either as an explicit `Set<String>` allowlist checked alongside the existing prefix checks, or by another mechanism that doesn't change the function's call sites or signature
- [x] 2.2 Confirm `REMOVED_UNLOCKABLE_IDS` (`theme_achievement_gold`, `theme_achievement_sapphire`) are not part of the reward-ID set and remain excluded/removed as before
- [x] 2.3 Update the allowlist set added in 2.1 from the easter-egg-only list to the full 80-ID set enumerated in 1.5, and rename it to reflect that it now covers all achievement rewards, not just easter eggs
- [x] 2.4 **Bug found during verification pass, fixed here:** `getUnlockedUnlockables()`/`observeUnlockedUnlockables()` only scanned stored `unlocked_*` prefs and never consulted `isDefaultUnlockable()`. Default-unlocked ids are never written to prefs, so every real consumer of these two methods — `AppThemePreferenceWidget` (the actual theme picker), `SettingsTreasuryScreen`, `HomeHubTab`, `AuroraNavbarCelestial`, `LatticeNavbarCircuit` — would have kept rendering these cosmetics as locked/hidden despite `isXAvailable()` correctly returning true. Fixed by unioning `ACHIEVEMENT_UNLOCKABLE_IDS` into both methods' return/emitted sets.

## 3. Verification
- [x] 3.1 Fresh install (no achievement/quest progress): confirm every enumerated reward ID now returns `true` from its relevant `isXAvailable()` check — covered by `UnlockableManagerTest > every achievement reward id is available on a fresh install`
- [x] 3.2 Existing account with achievements already completed: confirm no visible change — rewards remain available, no re-grant side effects — covered by `UnlockableManagerTest > previously unlocked achievement rewards remain available`
- [x] 3.3 Confirm the Cosméticos selector screen (from `migrate-stats-and-cosmetic-selectors`) shows these cosmetics as selectable on a fresh install — by inspection, this screen already hardcodes its own `allUnlockedForPreview` set bypassing `UnlockableManager` entirely (see `SettingsCosmeticsScreen.kt:129-152`), so it was already unaffected/unconditional; no code change needed here
- [x] 3.4 Confirm Treasury (still present at this point) shows these rewards as unlocked/no-longer-locked, since it also reads through `UnlockableManager` — required the 2.4 fix above; covered by `UnlockableManagerTest > getUnlockedUnlockables includes default-unlocked achievement rewards on a fresh install` and `> observeUnlockedUnlockables emits default-unlocked achievement rewards on a fresh install` (the two methods Treasury's `unlockedUnlockables` state is actually built from)
- [x] 3.5 Full project build and existing test suite pass, including `UnlockableManager`-related unit tests — `:data:testDebugUnitTest` (full suite) and `:app:testDebugUnitTest` for `ThemeUniquenessTest`, `LatticeVaultTest`, `TreasuryRewardProgressTest` all pass; `:data:compileDebugKotlin` and `:app:compileDebugKotlin` compile clean
