# Cloud Sync (Google Drive) Specification

## Requirements

### Requirement: OAuth client configuration override
The system SHALL load Google Drive OAuth client secrets from a local override file if present, falling back to the bundled placeholder template, and SHALL fail hard if no real client ID is configured.
Source: `GoogleDriveService.loadGoogleClientSecrets`, `app/src/main/assets/client_secrets.local.json` / `client_secrets.json`.

#### Scenario: Local override takes precedence
- GIVEN both `client_secrets.local.json` and `client_secrets.json` exist in assets
- WHEN Drive sync initializes
- THEN the local override file's credentials are used

#### Scenario: Placeholder client ID blocks sync
- GIVEN no local override exists and the bundled template still has the placeholder `"YOUR_..."` client ID
- WHEN Drive sync is attempted
- THEN it throws rather than attempting to sync with an invalid client ID

### Requirement: Device-conflict resolution
The system SHALL, when syncing, push local data directly if this device was the last to sync, or merge local and remote data if a different device synced most recently.
Source: `SyncManager`, `GoogleDriveSyncService`.

#### Scenario: Same-device resync pushes without merging
- GIVEN this device was the last to write the remote sync file
- WHEN sync runs again from this device
- THEN local data is pushed directly, without a merge step

#### Scenario: Cross-device resync merges
- GIVEN a different device wrote the remote sync file most recently
- WHEN this device syncs
- THEN `mergeSyncData` combines local and remote data rather than one overwriting the other

#### Scenario: Merged result triggers a local restore
- GIVEN the synced/merged result differs from the pre-sync local backup
- WHEN sync completes
- THEN the merged backup is restored locally using full default `RestoreOptions()` (all categories)

### Requirement: Optimistic concurrency on push
The system SHALL compare against an expected remote snapshot when pushing sync data, retrying (up to `MAX_SYNC_RETRIES`) if the remote changed underneath it during the sync operation.

#### Scenario: Concurrent remote change triggers retry
- GIVEN another device writes new sync data between this device's read and write
- WHEN this device attempts to push its own data
- THEN a `RemoteChangedException` is raised and the push is retried, up to the configured maximum

### Requirement: Single sync file invariant
The system SHALL keep only one sync file in the Drive `appDataFolder`, deleting all but the newest if duplicates are found. Each file carries `appProperties` including device ID, sync timestamp, and a SHA-256 content hash.

#### Scenario: Duplicate sync files are pruned
- GIVEN two sync files exist in the `appDataFolder` due to a partial failure
- WHEN sync next runs
- THEN all but the newest file are deleted

### Requirement: Serialized sync operations
The system SHALL serialize all sync operations through a global mutex so no two `syncData()` calls run concurrently.

#### Scenario: Overlapping sync triggers do not race
- GIVEN a scheduled sync and a manual sync are triggered at nearly the same time
- WHEN both attempt to run
- THEN one waits for the other to complete via the shared `syncMutex`, rather than both executing concurrently
