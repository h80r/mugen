# Watching (Anime) Specification

## Requirements

### Requirement: Custom player buttons
The system SHALL support user-defined custom action buttons in the anime player, backed by a WebView-injectable script model shared with the manga/novel reader button system.
Source: `domain/.../custombuttons/model/CustomButton.kt`.

#### Scenario: Custom button injects script with placeholders
- GIVEN a custom button has `content` referencing `$id` and `$isPrimary`
- WHEN the button is pressed
- THEN those placeholders are substituted before the script executes in the player WebView context

### Requirement: Auto-download while watching
The system SHALL support automatically downloading upcoming episodes while the user is actively watching, governed by the `autoDownloadWhileWatching` preference.

#### Scenario: Auto-download triggers during playback
- GIVEN `autoDownloadWhileWatching` is enabled
- WHEN the user is watching an episode
- THEN subsequent episodes begin downloading automatically without manual initiation

### Requirement: Filler episode flag
The system SHALL support marking episodes as "filler" and excluding them from downloads when the corresponding download preference is set.

#### Scenario: Filler-marked episodes skipped by filtered downloads
- GIVEN the "download filler-marked" preference is disabled
- WHEN a bulk download is triggered for a series with filler-marked episodes
- THEN those filler episodes are excluded from the download batch
