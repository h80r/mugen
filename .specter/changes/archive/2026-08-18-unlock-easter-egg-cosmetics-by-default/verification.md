# Verification: unlock-easter-egg-cosmetics-by-default

## Verdict
PASS

## CRITICAL
(none)

## WARNING
(none)

## SUGGESTION
- [completeness] `ACHIEVEMENT_UNLOCKABLE_IDS` is a static, hand-transcribed snapshot of `achievements.json`'s reward ids. It will silently drift if a new achievement/reward is added to the JSON before `remove-achievements-system` deletes the JSON entirely. Low risk given the JSON's remaining lifespan is one more change, but worth a quick re-diff against `achievements.json` immediately before that change lands.

## Notes
- **Completeness:** every Scope item in `proposal.md` has corresponding work — full 80-id reward enumeration (task 1.5), `isDefaultUnlockable()` extension with unchanged signature/call sites (task 2.1, 2.3), `REMOVED_UNLOCKABLE_IDS` non-overlap confirmed (task 2.2), `debugBypassTreasuryLocks` left untouched (verified via diff — no hits).
- **Correctness:** both delta scenarios in `specs/easter-eggs/spec.md` are exercised by tests — "reward available without completing the quest" via `UnlockableManagerTest > every achievement reward id is available on a fresh install`, and "previously-completed users see no change" via `> previously unlocked achievement rewards remain available`. A real gap was found during this pass: `getUnlockedUnlockables()`/`observeUnlockedUnlockables()` only scanned stored `unlocked_*` prefs and never consulted `isDefaultUnlockable()`, so the real UI consumers (`AppThemePreferenceWidget`'s theme picker, `SettingsTreasuryScreen`, `HomeHubTab`, Aurora/Lattice navbar cosmetics) would have kept rendering default-unlocked cosmetics as locked despite `isXAvailable()` correctly returning true. Fixed in the same task group (2.4) by unioning the allowlist into both methods' output, with regression tests added. The Cosméticos selector screen was confirmed by inspection to already bypass `UnlockableManager` via its own hardcoded preview set, so it was unaffected either way.
- **Coherence:** no `design.md` for this change. The implementation matches the proposal's stated approach exactly — no new infrastructure, `isDefaultUnlockable()` extended via an adjacent allowlist, signature/call sites unchanged.
- Full build/test verification: `:data:compileDebugKotlin`, `:app:compileDebugKotlin` compile clean; `:data:testDebugUnitTest` (full module suite) passes; `:app:testDebugUnitTest` for `UnlockableManagerTest`, `ThemeUniquenessTest`, `LatticeVaultTest`, `TreasuryRewardProgressTest` all pass (24 tests total across the two runs, 0 failures).
- Scope was expanded mid-execution from "the three easter eggs only" to "every achievement-granted cosmetic," per explicit user confirmation, because the already-planned `remove-achievements-system` change's own proposal text assumes this change already covers the full set. `proposal.md` and `specs/easter-eggs/spec.md` were updated accordingly before implementation.
