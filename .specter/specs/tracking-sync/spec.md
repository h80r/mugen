# Tracking & Sync Specification

## Requirements

### Requirement: Multi-tracker support
The system SHALL support linking library entries to external trackers (MyAnimeList, AniList, Kitsu, Shikimori, Bangumi, Komga, MangaUpdates, Kavita, Suwayomi, NovelUpdates, NovelList, Simkl, Jellyfin, Trakt), each with parallel per-media (anime/manga/novel) domain models and interactors.
Source: `app/.../data/track/TrackerManager.kt`, `domain/.../track/{manga,anime,novel}/`.

#### Scenario: Login state derived from stored credentials
- GIVEN a tracker's username and password (or OAuth token) are both stored
- WHEN `isLoggedIn` is checked
- THEN it returns true; if either is empty, it returns false

### Requirement: Binding a library entry to a tracker
The system SHALL, on binding, look up an existing remote list entry and copy its state in, or create a new remote entry if none exists — never overwriting a remote COMPLETED status by binding alone.
Source: `AddAnimeTracks.bind` and manga/novel equivalents.

#### Scenario: Existing remote entry is adopted
- GIVEN a remote tracker list entry already exists for this title
- WHEN the user binds the tracker
- THEN remote score/dates are copied in, and status is set to WATCHING/READING only if not already COMPLETED and not currently re-watching/re-reading, and only if the user has seen/read at least one episode/chapter locally

#### Scenario: No remote entry creates one with inferred status
- GIVEN no remote list entry exists for this title
- WHEN the user binds the tracker
- THEN a new entry is created with status WATCHING/READING if episodes/chapters are already seen locally, otherwise PLAN_TO_WATCH/PLAN_TO_READ

#### Scenario: Local progress ahead of remote is pushed after binding
- GIVEN the user has watched further locally than the newly-bound remote track shows
- WHEN binding completes
- THEN local progress is pushed up to the remote tracker, and the tracker's start date is backfilled from the earliest local history entry if unset

#### Scenario: Enhanced trackers auto-bind without search
- GIVEN a source is tagged as an `EnhancedAnimeTracker`/`EnhancedMangaTracker`
- WHEN the entry is added to the library
- THEN it is automatically matched and bound to that tracker without the user performing a manual search

### Requirement: Progress push on local mark-as-read
The system SHALL push progress to every logged-in tracker attached to an entry only when the new episode/chapter number exceeds the tracker's last recorded value, queuing the update for retry if offline.
Source: `TrackEpisode`/`TrackChapter`, `DelayedAnimeTrackingStore`, `DelayedAnimeTrackingUpdateJob`.

#### Scenario: Backward or duplicate marks are not pushed
- GIVEN a tracker's last recorded episode is 10
- WHEN the user marks episode 5 as seen
- THEN no push occurs, since 5 is not greater than the tracker's last recorded value

#### Scenario: Offline mark queues for retry
- GIVEN the device is offline when an episode is marked seen
- WHEN the push would normally occur
- THEN the update is queued in `DelayedAnimeTrackingStore` and a WorkManager job (network-constrained, exponential backoff, max 3 attempts) retries it later

### Requirement: Progress sync resolution on open/refresh
The system SHALL resolve tracking sync direction using a pure decision function based on the trigger type and the relative local/remote progress values.
Source: `ResolveTrackProgressSync`, `SyncEpisodeProgressWithTrack`.

#### Scenario: Opening an entry pulls remote progress down if ahead
- GIVEN `autoSyncProgressFromTracker` is enabled and remote progress exceeds local progress
- WHEN the user opens the entry or manually refreshes
- THEN local episodes/chapters are marked read up to the remote value

#### Scenario: Opening an entry pushes local progress up if ahead
- GIVEN `autoSyncProgressFromTracker` is enabled and local progress exceeds remote progress
- WHEN the user opens the entry or manually refreshes
- THEN the local value is pushed to the remote tracker

#### Scenario: Sync disabled means no-op regardless of trigger
- GIVEN `autoSyncProgressFromTracker` is disabled
- WHEN the user opens the entry
- THEN no sync action occurs in either direction

#### Scenario: Local mark never pulls down
- GIVEN the user marks a chapter read locally and local progress does not exceed remote
- WHEN sync resolution runs for this `LOCAL_MARK` trigger
- THEN the result is always no-op — a local mark event never pulls remote progress down, even if remote is ahead

### Requirement: AniList completion status transition
The system SHALL, for AniList only, automatically set status to COMPLETED and record a finish date when the last watched/read number equals the total episode/chapter count, otherwise advance to WATCHING/READING (unless currently rewatching/rereading) and record a start date on episode/chapter 1.
Source: `Anilist.update`.

#### Scenario: Final episode marks completion
- GIVEN a title has 12 total episodes and status is not already COMPLETED
- WHEN episode 12 is marked watched
- THEN AniList status becomes COMPLETED with a finish date recorded

#### Scenario: Rewatching status is preserved
- GIVEN a title's AniList status is REWATCHING
- WHEN an episode short of the total is marked watched
- THEN status remains REWATCHING rather than being overwritten to WATCHING

### Requirement: Tracked-status library filtering
The system SHALL map each tracker's native status codes to a common `LibraryTrackStatus` set for use as pseudo-categories in library filtering.
Source: `MapAnimeTrackStatusToLibrary`/`MapMangaTrackStatusToLibrary`, pseudo-category IDs -10..-17.

#### Scenario: Different trackers map to the same pseudo-category
- GIVEN one entry is tracked on AniList with status "CURRENT" and another on MyAnimeList with status "watching"
- WHEN the library is filtered by the "Reading/Watching" tracked-status pseudo-category
- THEN both entries appear, since both native statuses map to the same `LibraryTrackStatus.READING`
