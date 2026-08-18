# Proposal: Remove Easter Eggs

## Intent
With `unlock-easter-egg-cosmetics-by-default` shipped, every cosmetic reward the three easter eggs grant is now unconditionally available regardless of quest completion. Nothing downstream depends on the quest mechanisms themselves anymore. This change deletes the trigger logic, dedicated UI, managers, and vaults for Aurora Heart, Lattice Resonance, and Void Broadcast — the hidden riddle/puzzle/search-trigger systems — while leaving the achievement system itself intact (removed separately in `remove-achievements-system`, so that change's diff stays focused on one system).

## Scope

### Aurora Heart
- Delete `eu/kanade/domain/easteregg/aurora/` (10 files): `AuroraHeartManager`, `AuroraQuest`, `AuroraNight`, `AuroraVault`, `AuroraEchoBus`, and siblings
- Delete `eu/kanade/presentation/easteregg/aurora/` (13 files): `AuroraRiddleScreen`, `AuroraUnlockedScreen`, `AuroraCodexScreen`, `AuroraEchoOverlay`, shaders, and siblings

### Lattice Resonance
- Delete `eu/kanade/domain/easteregg/lattice/` (7 files): `LatticeProtocolManager`, `LatticeVault`, `LatticeBoardModel`, `LatticeSignalBus`, and siblings
- Delete `eu/kanade/presentation/easteregg/lattice/` (8 files): `LatticeOverlayHost`, `LatticeGridScreen`, `LatticeRewardsScreen`, and siblings
- Delete the debug-only entries this system exposes: Lattice Grid and Force Lattice Breach debug menu items (called out as explicitly out of scope in `reorganize-more-section-ia`, since that change chose not to touch easter-egg debug entries — this change removes them outright since their underlying system is gone)

### Void Broadcast (undocumented in specs prior to this change)
- Delete `MeltdownRitual.kt`, `RealityBreach.kt`, `GlitchStack.kt`, and sibling components (8 files, ~186KB) under `presentation/components/`
- Delete the "Glitch Rift" debug entry (also called out as out of scope in `reorganize-more-section-ia`)
- Delete the `pref_meltdown_stage` preference and its usage (`UiPreferences.meltdownStage()`, `App.kt`/`ReaderActivity.kt` references)

### Shared integration points
- `App.kt` bootstrap hooks (lines ~182-213, 216, 269): remove the direct achievement-DB-insert calls tied to easter-egg completion; leave the rest of bootstrap untouched
- `ReaderActivity.kt`: remove the direct Void Broadcast achievement insertion
- `AchievementCard.kt`: remove `isAuroraHeart`/`isLatticeResonance` branches from the shared conditional block (the achievement card itself survives until `remove-achievements-system` — only the easter-egg-specific rendering branches go)
- "Reset Aurora Heart" debug entry: removed alongside the manager it resets
- About-screen logo tap sequence (the hidden trigger gesture called out in `reorganize-more-section-ia`'s out-of-scope list): removed, since it has no destination once Aurora Heart is gone

### achievements.json content cleanup
- Remove SECRET-category entries whose sole purpose is representing completion of one of these three quests (the same entries enumerated during `unlock-easter-egg-cosmetics-by-default`'s task 1.1-1.4) — they have no trigger left to fire them, so leaving them in dead JSON content serves no purpose
- Non-easter-egg achievement entries (the other ~100+ entries) are untouched — the achievement system itself is still live until the next change

### Spec retirement
- Retire the `easter-eggs` spec entirely (REMOVED delta covering every requirement currently in `.specter/specs/easter-eggs/spec.md`)

## Out of scope
- The achievement event bus, `AchievementHandler`, `AchievementsDatabase`, non-easter-egg achievement content, and the Achievements screen — all `remove-achievements-system`
- Treasury — `remove-treasury-screen`
- Re-verifying cosmetic default-unlock correctness — already verified in the prior change; this change only needs to confirm it still holds after the trigger code is gone (nothing in this change touches `UnlockableManager`)

## Approach
File-by-file deletion mirroring the prior uproot analysis's file inventory, compiling after each logical group (Aurora, then Lattice, then Void Broadcast, then shared integration points) to catch broken references early — same mechanical approach `remove-dead-standard-more-ui` used for its ~16-site cleanup. Finish with a full build, a manual pass confirming no dangling references to deleted packages remain, and confirmation that every easter-egg cosmetic is still selectable via the Cosméticos screen (from `migrate-stats-and-cosmetic-selectors`) and still shows as unlocked in Treasury (still present at this point).
