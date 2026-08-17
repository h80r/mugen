# Library Updates Specification

## Requirements

### Requirement: Per-media background update jobs
The system SHALL run separate WorkManager jobs for anime, manga, and novel library updates, each skipping entirely if that media type's UI section is disabled.
Source: `MangaLibraryUpdateJob`, `AnimeLibraryUpdateJob`, `NovelLibraryUpdateJob`.

#### Scenario: Disabled media section skips its update job
- GIVEN `showNovelSection` is disabled
- WHEN the novel library update job would normally run
- THEN it no-ops immediately without fetching anything

### Requirement: Manual vs. automatic run deduplication
The system SHALL tag runs as manual or automatic and defer an automatic run if a manual run of the same media type is already in progress.

#### Scenario: Auto run defers to an in-progress manual run
- GIVEN the user manually triggers a manga library update
- WHEN a scheduled automatic manga update would start at the same time
- THEN the automatic run returns `Result.retry()` instead of running concurrently

### Requirement: Entry selection for a run
The system SHALL select entries to update via explicit entry IDs (for retries), a specific category, or the default global run using include/exclude category preferences, including synthetic pseudo-categories for ungrouped, untracked, tracked-status, publication-status, and per-source buckets.
Source: `addMangaToQueue`, pseudo-category ID ranges (0, no tracker, -10..-17, -20..-26, < -1000).

#### Scenario: Include-list wins over exclude-list
- GIVEN both an include-category list and an exclude-category list are configured and overlap
- WHEN the global update run selects entries
- THEN the non-empty include-list takes precedence, and the exclude-list is only applied to subtract from it

#### Scenario: Retry resolves stale errors for removed entries
- GIVEN a previously-failed entry ID is targeted for retry but has since been removed from the library
- WHEN the retry run processes entry IDs
- THEN that entry's stale error record in `LibraryUpdateErrorStore` is automatically marked resolved

### Requirement: Update pacing
The system SHALL insert a configurable delay after updating each entry from a source flagged for pacing, to avoid rate-limiting.
Source: `LibraryUpdatePacingPolicy`, `libraryUpdatePacingTimeoutSeconds`, `libraryUpdatePacingSourceKeys`.

#### Scenario: Zero timeout disables pacing
- GIVEN a source's configured pacing timeout is 0 or negative
- WHEN entries from that source are updated
- THEN no delay is inserted between them

### Requirement: Update errors screen
The system SHALL show per-entry update errors grouped by error message, auto-removing errors for entries no longer favorited, and support bulk retry that reconciles retry state against live error updates.
Source: `LibraryUpdateErrorScreenModel`.

#### Scenario: Unfavorited entry's error is dropped
- GIVEN an entry has a recorded update error and is then removed from the library
- WHEN the update errors screen refreshes
- THEN that entry's error record is automatically deleted

#### Scenario: Retry that fails again clears the stale retrying flag
- GIVEN a "retrying" entry fails again with a new error message
- WHEN the error stream updates
- THEN the local "retrying" flag for that entry is reconciled and cleared rather than persisting incorrectly
