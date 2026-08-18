# Verification: migrate-stats-and-cosmetic-selectors

## Verdict
PASS

## CRITICAL
(none)

## WARNING
- [correctness] `specs/stats/spec.md`'s "Yearly activity calendar" scenario describes a per-day heatmap ("each day's cell reflects that day's recorded activity level"), but both the ported source (`AchievementActivityGraph.kt`) and the new `SharedYearlyActivityGraph` are a 12-month bar chart, not a day-grid. Task 3.4 explicitly directed reusing the existing bar-chart approach, and design.md said to reuse it "where applicable" — the implementation is faithful to the real source; the spec scenario's wording doesn't match what was ever actually built on the achievements side. Spec wording should be corrected on next touch, not the code.
- [correctness] `specs/stats/spec.md`'s streak scenario states "an inactive 'today' does not break an existing streak," but the ported algorithm (`AchievementScreenModel.calculateCurrentStreak`, and the new `SharedActivityStatsScreenModel.currentStreakFlow`) breaks the streak at 0 immediately if today has no activity — this is the actual, unchanged behavior of the source being ported, not a new bug. Spec wording should be corrected on next touch, not the code.

## SUGGESTION
- [completeness] Full `./gradlew build` fails on `spotlessKotlinCheck` due to pre-existing formatting drift in files this change never touches (`SettingsAdvancedScreen.kt`, `SettingsNovelReaderScreen.kt`, `AppModule.kt`, etc.) — confirmed present on `develop` before this change. Unrelated to this change's scope; worth a separate cleanup pass.
- [completeness] `./gradlew testDebugUnitTest` shows a handful of flaky failures that shift between runs (`AuroraPrimeOverlayTest`, `NovelReaderCacheCoordinatorTest`, `NovelReaderScreenModelTest`, `MultilingualQueryHelperTest`, `AnimeLibraryScreenModelLanguageFilterTest`) — none touch activity/stats/cosmetics code, several reproduce identically on `develop`, and the rest pass cleanly when run in isolation (consistent with cross-test MockK/coroutine-timing flakiness, not a real regression). Worth stabilizing separately.

## Notes
This change spans two branches per its task groups' `**Branch:**` overrides, both merged together into `appearance-cosmetic-selectors` (the branch this verification runs from) so nothing shipped disconnected:
- `activity-database-sourceset` — task groups 1–3 (ActivityDatabase sourceSet, copy migration, Stats screen additions)
- `appearance-cosmetic-selectors` — task groups 4–6 (Cosméticos selector screen, post-verification revision)

Task group 6 was added after live-app review surfaced three scope corrections: the shared activity block was moved out of the three media Stats tabs into a single new "Geral" tab; the redundant Theme selector was dropped from Cosméticos (Appearance already has one); and the remaining 7 Cosméticos groups were rebuilt to reuse Treasury's actual visual components (card grids, icons, descriptions) plus a fixed identity preview card, instead of plain `ListPreference` dialogs. `NovelTabsConfigurationTest` was updated to match the new tab order this introduced.

**Completeness** — every Scope item in `proposal.md` has corresponding work, including the task-group-6 revisions:
1. New `ActivityDatabase` sqldelight sourceSet, copy migration, repository — done (task group 1–2).
2. Stats screen additions (streak, month comparison, yearly activity) — done, now living in a single Geral tab rather than duplicated across media tabs (task group 3, revised in 6).
3. Cosmetic selectors in Settings > Appearance (7 groups — theme intentionally excluded, no lock-state logic) — done, rendered via Treasury's own visual components with a fixed identity preview card (task group 4, revised in 6).

**Correctness** — every delta scenario was checked against the implementation; the two flagged discrepancies are spec-wording issues against faithfully-ported source behavior, not implementation defects. All other scenarios (activity data isolation, streak display, month comparison without achievements-unlocked, cosmetic selection applying identically to Treasury, all options shown without lock/unlock checks, visual-card presentation) were verified correct via live on-device checks: the Geral tab matches Achievements' displayed values and the media tabs no longer duplicate it; Cosméticos has no Theme group, all 7 groups render as Treasury-style card grids with no lock indicators, the identity preview card updates live and stays pinned through scroll, and selecting an option round-trips identically through Treasury.

**Coherence** — the code reflects `design.md`'s decisions and the task-group-6 revisions built on top of them without contradicting the original architecture: `ActivityDatabase` isolated as its own sqldelight sourceSet; the copy migration follows the guarded-by-preference-flag pattern and is idempotent by upsert-on-primary-key; Cosméticos stays a dedicated sub-screen; Treasury's selector components (`TreasuryToggleSelector`/`TreasuryArtifactShard`, `TreasuryAuraSelector`/`TreasuryAuraChannel`, `TreasurySectionStage`, and the extracted `TreasuryIdentityPreviewCard`) were reused by widening their visibility to `internal`, not forked — Treasury's own rendering is byte-for-byte unchanged; lock/unlock gating is neutralized via a synthetic all-unlocked input, matching design.md's "no lock-state logic" requirement without touching the reused components' logic.
