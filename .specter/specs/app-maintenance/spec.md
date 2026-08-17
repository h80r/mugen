# App Maintenance Specification

## Requirements

### Requirement: Release version comparison
The system SHALL determine update availability differently for preview vs. stable builds: preview builds compare raw commit counts, stable builds compare version segments left-to-right and consider an update available on the first segment where the available version exceeds the installed version.
Source: `AppUpdateVersionComparator.isUpdateAvailable`.

#### Scenario: Preview build compares commit counts
- GIVEN the app is a preview build with `installedCommitCount = 500`
- WHEN checking `availableVersion = "510"`
- THEN an update is considered available since 510 > 500

#### Scenario: Stable build stops at first differing segment
- GIVEN the app is a stable build at version 1.2.9 and the available version is 1.3.0
- WHEN comparing segments left-to-right
- THEN an update is available as soon as the second segment (3 > 2) differs, without needing to compare the third segment

### Requirement: Versioned internal data migration
The system SHALL run only the migrations whose declared version falls within `(installedVersion+1..newVersion)` on a normal upgrade, run all migrations once on a fresh install, and no-op on a same-version or downgrade launch.
Source: `mihon/core/migration/Migrator.initialize`, `MigrationStrategyFactory`.

#### Scenario: Fresh install runs all migrations
- GIVEN the app's stored version is 0 (fresh install)
- WHEN `Migrator.initialize` runs
- THEN `InitialMigrationStrategy` executes, running every registered migration once to establish defaults

#### Scenario: Downgrade or same version is a no-op
- GIVEN the stored version is greater than or equal to the new version
- WHEN `Migrator.initialize` runs
- THEN `NoopMigrationStrategy` applies and no migrations execute

#### Scenario: Normal upgrade runs only the intervening range
- GIVEN the stored version is 150 and the new version is 155
- WHEN `Migrator.initialize` runs
- THEN only migrations declared for versions 151 through 155 execute, not the full migration history

#### Scenario: Catch-up migration recovers users who missed steps
- GIVEN a user's stored version indicates they skipped an intermediate migration window
- WHEN migrations run
- THEN `ForceMissedMigrations187` re-applies the effects of migrations that would otherwise have been skipped by the version-range logic
