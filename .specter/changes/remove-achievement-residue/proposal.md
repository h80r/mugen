# Proposal: Remove Remaining Dead Achievement-System Residue

## Intent
The prior removal (`remove-achievements-system`, archived) already deleted the achievement engine end to end. A follow-up liveness audit (component-by-component, cross-referenced against real navigation) confirms no coherent subsystem remains — everything still touching "achievement"-named code (`UnlockableManager`, `ActivityDataRepository`, `StreakAchievementChecker`, the Stats-tab activity graphs) is live and correctly functioning as reading-activity tracking or cosmetic-unlock gating. What's left is small scattered dead residue from the removal: one orphaned preference/toggle, one orphaned domain model file, one dead repository method, ~150 orphaned locale strings, and one stale spec requirement.

## Scope
- Remove `UiPreferences.showAchievementNotifications()` and its `SettingsAppearanceScreen.kt` toggle (never read by anything else).
- Delete the orphaned `core/common/.../achievement/Achievement.kt` domain model file (zero references anywhere).
- Delete `ActivityDataRepository.recordAchievementUnlock()` (interface + impl) — never called, always writes 0.
- Prune the confirmed-orphaned `achievement_*` locale strings, keeping the subset still used by live Stats-tab activity-graph components.
- Remove the stale "Manual backup triggers achievement tracking" requirement from the `backup-restore` spec (references `AchievementEvent.Feature.BACKUP`, which no longer exists in code).
- Explicitly out of scope (confirmed live — do not touch): `UnlockableManager` and cosmetic-unlock gating, `ActivityDataRepository` itself (only the one dead method), `StreakAchievementChecker`, Stats-tab activity graphs, the `achievements_unlocked` DB column (kept — a schema migration for one always-zero column isn't worth the risk here), and the legacy `achievements` string-key naming in backup/restore screens (displayed text is already correct; pure renaming is tracked separately in the backlog).

## Approach
Grep-verify each deletion target has zero remaining references before removing it, in the same audit style as the prior achievement-removal change. No new persistence/migration needed since nothing live depends on any of these residues.
