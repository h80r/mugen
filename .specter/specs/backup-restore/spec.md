# Backup & Restore Specification

## Requirements

### Requirement: Independently toggleable backup content
The system SHALL let users independently select which content categories to include in a backup: library entries (per-media), categories, app settings, extension repos, custom buttons, source settings, extensions, achievements, and stats.
Source: `BackupOptions`/`RestoreOptions`, `BackupCreator.kt`.

#### Scenario: Restore requires at least one library type if library entries selected
- GIVEN `libraryEntries` is enabled but none of `restoreManga`/`restoreAnime`/`restoreNovel` are selected
- WHEN `RestoreOptions.canRestore()` is evaluated
- THEN it returns false — at least one media type must be selected when library entries are included

#### Scenario: Extensions excluded by default
- GIVEN a user creates a backup with default options
- WHEN the backup is inspected
- THEN installed extensions are not bundled, since `RestoreOptions.extensions` defaults to false

### Requirement: Sister-app-compatible export mode
The system SHALL, when `sisterAppCompatible` is enabled, fold novel entries into the manga backup section and emit empty anime/novel-specific sections, producing a Mihon-shaped backup for cross-app compatibility.

#### Scenario: Novels appear as manga entries in compatible mode
- GIVEN `sisterAppCompatible` is enabled
- WHEN the backup is created
- THEN novel entries are converted via `BackupNovel.toBackupManga()` and included in the manga section, not a separate novel section

#### Scenario: Content-summary validation skips novel/anime counts in compatible mode
- GIVEN a backup was created in sister-app-compatible mode
- WHEN its expected content summary is validated
- THEN novel and anime entry counts are not checked, since they were intentionally folded/omitted

### Requirement: Backup write integrity
The system SHALL write backups to a staging file, decode them back, and compare against an expected summary before replacing the destination file — a corrupt or incomplete write is never allowed to overwrite a good backup.
Source: `BackupWriter`.

#### Scenario: Failed verification rejects the write
- GIVEN a backup write is interrupted or produces corrupt output
- WHEN the staging file fails to decode and match the expected summary
- THEN the destination backup file is left untouched

### Requirement: Auto-backup retention
The system SHALL run automatic backups on a configurable interval (default 12h) and prune older auto-backups to a configured retention count (default 4), pruning only after the new backup is verified written.

#### Scenario: Failed backup does not reduce retained count
- GIVEN an auto-backup attempt fails before verification
- WHEN pruning would normally run
- THEN pruning is skipped, so the number of good existing backups is never reduced by a failed attempt

### Requirement: Restore order and isolation
The system SHALL restore categories, then app/source preferences, then library entries (anime/manga/novel concurrently, then series groupings), then extension stores/repos, then custom buttons, then extensions, then feeds, then achievements/stats — with each individual entry's restore failure isolated from the rest of the batch.
Source: `BackupRestorer.restore()`.

#### Scenario: One entry's failure does not abort the batch
- GIVEN a backup contains 100 manga entries and one has malformed data
- WHEN the restore runs
- THEN the other 99 entries are restored successfully, and the failing entry's error (including its resolved source name) is recorded in the restore report

#### Scenario: Legacy repo entries are skipped when store-format entries exist
- GIVEN a backup contains both legacy extension-repo entries and newer store-format entries for the same repo
- WHEN restore processes extension repos
- THEN the legacy entries are skipped to avoid double-inserting under a synthesized repo URL

### Requirement: Restore completeness verification
The system SHALL track expected vs. restored vs. failed counts per media type and record any mismatch as an explicit restore error rather than silently dropping entries.

#### Scenario: Silently dropped entries are surfaced
- GIVEN a backup claims 50 anime entries but only 48 are successfully restored with no explicit per-entry failure recorded
- WHEN `verifyCompleteness()` runs
- THEN the 2-entry discrepancy is recorded as an error in the restore report

### Requirement: Manual backup triggers achievement tracking
The system SHALL fire a `AchievementEvent.Feature.BACKUP` event only for manually-initiated backups, not automatic scheduled ones.

#### Scenario: Auto-backup does not count toward the achievement
- GIVEN an automatic scheduled backup runs
- WHEN it completes
- THEN no `Feature.BACKUP` achievement event fires — only a user-initiated manual backup fires it
