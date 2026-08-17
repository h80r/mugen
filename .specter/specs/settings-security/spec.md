# Settings & Security Specification

## Requirements

### Requirement: Biometric app lock
The system SHALL support a biometric app-lock, only enableable when the device supports authentication, and SHALL re-trigger an authentication prompt whenever the lock setting is changed.
Source: `SettingsSecurityScreen.kt`, `SecurityPreferences`, `UnlockActivity.kt`.

#### Scenario: Toggle disabled on unsupported devices
- GIVEN `context.isAuthenticationSupported()` returns false
- WHEN the user views the security settings screen
- THEN the biometric lock toggle is disabled/unavailable

#### Scenario: Changing lock settings re-prompts authentication
- GIVEN biometric lock is already enabled
- WHEN the user changes the "lock after" interval
- THEN an authentication prompt is immediately triggered again to confirm the change

#### Scenario: Failed unlock kills the app task
- GIVEN `UnlockActivity` shows a biometric prompt on a locked app
- WHEN authentication errors out (not just a retry-able failure)
- THEN `finishAffinity()` is called, killing the entire app task rather than leaving it in an unlocked or ambiguous state

### Requirement: Lock-after-idle options
The system SHALL support lock-after-idle intervals of Always, 1/2/5/10 minutes, or Never, available only when authentication is supported and enabled.

#### Scenario: Idle options unavailable without auth enabled
- GIVEN biometric lock is disabled
- WHEN the user views the "lock after" setting
- THEN it is not selectable/available

### Requirement: Stats calculations
The system SHALL compute completion status and consumption statistics via shared pure functions reused across anime/manga/novel stats screens, achievements, and backup/stats export.
Source: `StatsCalculations.kt`.

#### Scenario: Consumption-based completion can infer status without an explicit mark
- GIVEN a title's status has not been explicitly set to "completed" but the user has consumed all known content and the effective status is in a terminal-fallback set
- WHEN `isCompletedByUserConsumption` evaluates
- THEN it reports completed, inferring completion from consumption rather than requiring an explicit status change

#### Scenario: Mean title score excludes unscored placeholders
- GIVEN a set of titles where some have no valid score (0 or negative placeholder values)
- WHEN `meanTitleScore` computes the overall average
- THEN only positive, non-NaN per-title mean scores are included — unscored titles do not skew the result toward zero

#### Scenario: Watch duration counts partial progress for in-progress episodes
- GIVEN an episode is unseen/in-progress with some elapsed watch time
- WHEN `watchDurationMillis` sums total watch time
- THEN the partial elapsed duration (clamped to [0, total episode length]) is included for that episode, while fully seen episodes contribute their full duration

#### Scenario: Progress fraction is clamped and zero-safe
- GIVEN `done = 0` and `total = 0`
- WHEN `progressFraction` is computed
- THEN it returns 0 rather than dividing by zero or returning NaN

### Requirement: Per-media storage screens
The system SHALL provide separate storage-usage breakdown screens for anime, manga, and novel, sharing common logic via `CommonStorageScreenModel`.

#### Scenario: Each media type's storage tab shows only its own usage
- GIVEN the user opens the Storage screen
- WHEN they switch between anime/manga/novel tabs
- THEN each tab reflects only that media type's downloaded-content storage usage, computed from the shared `CommonStorageScreenModel` base
