# Downloads Specification

## Requirements

### Requirement: Unified download engine facade
The system SHALL aggregate three independent per-media download backends (anime, manga, novel) behind one `DownloadEngineFacade` presenting a shared snapshot, speed tracker, and completion tracker.
Source: `app/.../data/download/engine/DownloadEngineFacade.kt`.

#### Scenario: Novel queue manager is a singleton, not an injected instance
- GIVEN `AnimeDownloadManager` and `MangaDownloadManager` are injected class instances
- WHEN the novel backend is examined
- THEN `NovelDownloadQueueManager` is architecturally different — a singleton `object`, not an injected class

#### Scenario: Global pause/resume/cancel fans out to all three backends
- GIVEN downloads are queued across anime, manga, and novel
- WHEN the user triggers "pause all"
- THEN the pause is applied to the anime, manga, and novel backends in sequence, not just the currently visible one

### Requirement: Novel download throttling
The system SHALL apply a distinct anti-rate-limit throttle subsystem to novel downloads (delay, jitter, timeout, failure cooldown) not present for anime or manga, because novel sources are typically scraped web content more prone to rate-limiting than anime/manga APIs.
Source: `novelDownloadDelayMs`/`novelDownloadJitterMs`/`novelDownloadTimeoutMs`/`novelDownloadFailureCooldownMs`, `NovelDownloadThrottleSettingsDialog.kt`.

#### Scenario: Throttle values are clamped client-side
- GIVEN a user attempts to set `novelDownloadDelayMs` to 999999
- WHEN they save the setting
- THEN the value is clamped to the valid range (0–30000ms) to prevent an unusable downloader configuration

#### Scenario: Defaults apply without user configuration
- GIVEN a user has never opened the novel download throttle settings
- WHEN novel chapters are downloaded
- THEN a 1200ms delay and 400ms jitter are applied between requests by default

### Requirement: Auto-download and cleanup rules
The system SHALL support per-media auto-download of new chapters/episodes (with category include/exclude lists) and automatic removal of downloads after being read/marked-read, excluding bookmarked entries.

#### Scenario: Remove-after-read respects a keep-slot count
- GIVEN "remove after read" is configured to keep 2 slots
- WHEN a chapter is read
- THEN only the oldest downloaded chapters beyond the 2 most recent are removed, not all read chapters

#### Scenario: Bookmarked chapters are excluded from auto-removal
- GIVEN a downloaded chapter is bookmarked and "remove after read" is active
- WHEN that chapter is marked read
- THEN it is not deleted, because bookmarked entries are excluded from removal
