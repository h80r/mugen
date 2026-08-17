# Library Specification

## Requirements

### Requirement: Parallel per-media libraries
The system SHALL maintain three independent libraries — anime, manga, and novel — each with its own sort, display, grouping, and filter state.

#### Scenario: Sorting one media type does not affect others
- GIVEN a user viewing the manga library sorted alphabetically
- WHEN they switch to the anime library
- THEN the anime library retains its own independently-configured sort order

### Requirement: Sort options
The system SHALL support per-media sort types, with anime including an airing-time option not available to manga/novel.
Source: `domain/.../library/{anime,manga,novel}/model/Library*Sort.kt`.

#### Scenario: Anime-only sort type
- GIVEN a user viewing the anime library sort menu
- WHEN they open the sort options
- THEN "Airing Time" is available as a sort type, and it is not present in the manga or novel sort menus

#### Scenario: Random sort uses a stable seed
- GIVEN a user selects "Random" sort for a library
- WHEN the library screen recomposes without the user requesting a reshuffle
- THEN the random order stays stable, driven by a persisted per-media seed rather than reshuffling on every recomposition

#### Scenario: Novel sort reuses manga's bitflag codec
- GIVEN the novel library's sort state is persisted
- WHEN it is encoded/decoded
- THEN `NovelLibrarySort` delegates its (de)serialization to `MangaLibrarySort`'s bitflag scheme

### Requirement: Display mode
The system SHALL support four display modes (Compact Grid, Comfortable Grid, List, Cover-Only Grid), shared across all three media types unless the user enables per-media display modes.

#### Scenario: Shared display mode by default
- GIVEN `separateDisplayModePerMedia` is disabled (default)
- WHEN the user changes display mode while viewing the manga library
- THEN the anime and novel libraries also switch to that display mode

#### Scenario: Independent display mode when enabled
- GIVEN `separateDisplayModePerMedia` is enabled
- WHEN the user changes display mode while viewing the manga library
- THEN the anime and novel libraries keep their own previously-set display modes

### Requirement: Grouping
The system SHALL support grouping library entries by default categories, source, publication status, or track status, or leaving them ungrouped, with an optional global override applying one grouping mode across all media types.

#### Scenario: Status-based grouping creates pseudo-categories
- GIVEN a user selects "Group by Status" for the manga library
- WHEN the library is displayed
- THEN entries are bucketed into synthetic negative-ID pseudo-categories (IDs -20..-26) keyed off publication status (ongoing, completed, etc.) instead of the user's real categories

#### Scenario: Global grouping override
- GIVEN `globalGroupLibrary` is enabled with mode `ALL`
- WHEN the user changes the grouping mode from any one media library screen
- THEN the same grouping mode is applied to all three media libraries, overriding their individual `mangaGroupLibraryBy`/etc. settings

### Requirement: Filtering
The system SHALL support per-media tri-state filters (downloaded, unread/unseen, started, bookmarked, completed, per-tracker, language) plus a global download-only override.

#### Scenario: Series matches downloaded filter if any member is downloaded
- GIVEN a manga library item is a `Series` grouping multiple entries
- WHEN the "Downloaded" filter is active
- THEN the series matches if at least one of its member entries is downloaded or local, not requiring all members to match

### Requirement: Series grouping (manga and novel only)
The system SHALL allow multiple library entries to be grouped into a series with a resolved stacked cover, for manga and novel libraries only — anime has no equivalent feature.

#### Scenario: No series grouping in anime library
- GIVEN a user viewing the anime library
- WHEN they look for a "group into series" action
- THEN no such feature exists — series grouping is only available for manga and novel entries (`domain/.../series/{manga,novel}/`)

### Requirement: Favoriting and duplicate detection
The system SHALL check for an existing duplicate library entry before adding a new one to the library, and offer to delete downloads when removing an entry from the library.

#### Scenario: Duplicate detected on add to library
- GIVEN a manga is not yet in the library
- WHEN the user favorites it and a duplicate library entry already exists (matched via `getDuplicateLibraryManga`)
- THEN a duplicate-manga dialog is shown instead of silently adding it

#### Scenario: Default category applied automatically
- GIVEN a user has configured a default manga category and no duplicate is found
- WHEN they favorite a manga
- THEN it is added directly to that default category without prompting for category selection

#### Scenario: Category picker shown when no default is set
- GIVEN no default manga category is configured and no duplicate is found
- WHEN the user favorites a manga
- THEN a category picker is opened before the entry is added

#### Scenario: Removing from library offers download cleanup
- GIVEN a favorited manga has downloaded chapters
- WHEN the user removes it from the library
- THEN a snackbar offers to delete the downloaded chapters

### Requirement: Pinning
The system SHALL let pinned library series/items sort first regardless of the currently selected sort order.

#### Scenario: Pinned series appears first under any sort
- GIVEN a series is pinned and the library is sorted alphabetically
- WHEN the library is displayed
- THEN the pinned series appears before unpinned series regardless of alphabetical order
