# Design: Activity Data Migration and Cosmetic Selector Placement

## 1. `ActivityDatabase` sqldelight sourceSet

### Why a new sourceSet instead of folding into the main DB or keeping it in `AchievementsDatabase`
- Keeping it in `AchievementsDatabase` is not an option: that whole sourceSet is deleted in `remove-achievements-system`.
- Folding into the main app database was considered and rejected: `activity_log` has its own lifecycle (a single-purpose, append-mostly time series table) and mixing it into the main schema risks migration coupling with unrelated main-DB changes going forward. A dedicated sourceSet keeps the blast radius of any future activity-log schema change isolated, mirroring how `AchievementsDatabase` itself was already isolated from the main DB for the same reason.

### Structure
Mirror `data/build.gradle.kts`'s existing declaration:
```kotlin
sqldelight {
    create("AchievementsDatabase") { ... } // existing, removed later
    create("ActivityDatabase") {
        packageName.set("tachiyomi.data.activity")
        srcDirs("src/main/sqldelightactivity")
    }
}
```
New file: `data/src/main/sqldelightactivity/tachiyomi/data/activity/activity_log.sq` — copy the table definition and all queries verbatim from the existing `activity_log.sq` (same column set, same indices, same query names). No schema changes; this is a relocation, not a redesign.

### Copy migration
A one-time `Migration` (following the existing `mihon.core.migration.migrations` pattern, e.g. `RecomputeGenreAchievementsMigration.kt`'s structure: guarded by a boolean preference flag, `version = Migration.ALWAYS`, no-op on dryrun):
1. Read all rows from `AchievementsDatabase.activity_log` via the existing `Activity_logQueries.selectAllActivityLog`
2. Upsert each row into the new `ActivityDatabase.activity_log` via `upsertActivity`
3. Set a completion preference flag (e.g. `activity_log_migrated_to_activity_db_v1`) so the copy runs exactly once
4. Idempotent by construction (upsert on primary key `date`), so a partial failure and retry is safe

This migration must run and be verified before `remove-achievements-system` deletes `AchievementsDatabase` — enforced by sequencing (this change ships and is verified first), not by a runtime dependency.

### Repository and DI
New `ActivityLogRepository` (or reuse naming conventions from wherever achievement data currently exposes `activity_log` reads — check `AchievementHandler`/related interactor for the existing read-side shape and mirror its method signatures: `getActivityForDateRange`, `getMonthActivity`, `getActivityStats`). Wire into the DI graph (Koin module, following the existing pattern for `AchievementsDatabase`'s driver/queries injection) so Stats screen models can consume it without depending on anything achievement-related.

### Writers
This change does **not** move the *write* side (increments on chapter/episode/app-open events) — those still target `AchievementsDatabase.activity_log` via existing achievement event-handling code until `remove-achievements-system` cuts over. That means for the window between this change shipping and `remove-achievements-system` landing, the new `ActivityDatabase` copy is a point-in-time snapshot that goes stale. This is acceptable because Stats' new UI is not expected to be user-facing/enabled until `remove-achievements-system` also repoints the *write* path — see task ordering below: this change adds the read infrastructure and UI, but the actual cutover of writes happens in `remove-achievements-system` as part of decommissioning `AchievementsDatabase`, with a final re-run of the copy migration (or a direct rename/keep-data-file approach if simpler at that point) immediately before the old database is dropped.

## 2. Cosmetic selector placement in Settings > Appearance

### Why a sub-screen, not inline additions to the existing Appearance list
`SettingsAppearanceScreen.kt` already covers theme mode, layout, colors, fonts, and other display settings. Adding 8 more preference groups (Theme, Aura, Background, Tabs, Titles, Effects, Frames, Home Badges) directly to that list would roughly double its length and mix "how the app looks structurally" with "which unlocked cosmetic to wear" — two different mental models for the user. A dedicated "Cosméticos" sub-screen (reached via a single `PreferenceItem` nav-card from Appearance, same pattern `reorganize-more-section-ia` uses for its new domain-grouping screens) keeps both screens scannable.

### Internal structure of the Cosméticos sub-screen
Follow the existing tabbed-screen shell pattern (`StorageTab`/`AnimeStorageTab`, also reused by `reorganize-more-section-ia` for its Novel Reader and Advanced splits) only if the 8 groups prove too long for a single scroll in practice — default to a single scrollable screen with 8 `PreferenceGroup`s first (simplest option, matches how Treasury already presents them as sequential sections), and only introduce tabs if the implementation task finds the combined length unwieldy. This is a judgment call to be made during implementation (task 3 below), not decided upfront, since the actual control count per group (e.g. how many auras exist) determines real screen length.

### Selector component
Each of the 8 groups becomes a `Preference.PreferenceItem.CustomPreference` (or `ListPreference` where the underlying preference is a single string, e.g. `specialBackgroundStyle()`) rendering the available options as a simple selectable list/grid — not Treasury's bespoke `TreasuryThemeSelector`/`TreasuryAuraSelector`/`TreasuryToggleSelector` visual components (vault posters, glow channels, spring-press animations). Those components are Treasury-specific chrome; the replacement should look and behave like every other Settings selector in the app (consistent with plain `ListPreference` visuals elsewhere in `SettingsAppearanceScreen.kt`).

### No lock-state logic
Every option is rendered as selectable, unconditionally. This is safe to build now (ahead of `unlock-easter-egg-cosmetics-by-default`) because the underlying `UnlockableManager.isUnlockableAvailable()`/`isThemeAvailable()` checks are Treasury-only concerns — the new selector screen simply doesn't call them, showing the full option list regardless of unlock state until the next change makes that state moot everywhere.
