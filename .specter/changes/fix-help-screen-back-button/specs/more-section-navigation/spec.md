# Delta for More Section Navigation

## ADDED Requirements

### Requirement: Ajuda always has a working back button
The system SHALL show a functional back button on the Ajuda (Help) screen's app bar in every navigation context, falling back to `navigator.pop()` when no explicit back-press handler is provided by the composition.

#### Scenario: Back button present regardless of LocalBackPress
- GIVEN a user opens Mais > Ajuda
- WHEN the screen renders, regardless of whether `LocalBackPress.current` is null or non-null in that composition
- THEN a back button is shown in the app bar and tapping it navigates back to the More tab
