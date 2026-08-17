# Reading (Manga) Specification

## Requirements

### Requirement: Landscape double-page joining
The system SHALL join two consecutive non-wide pages into a single displayed spread when the `joinDoublePages` preference is enabled and the device is in landscape orientation.
Source: `app/.../ui/reader/viewer/pager/PagerViewerAdapter.groupPagesForDoublePage()`.

#### Scenario: Pages joined only in landscape
- GIVEN `joinDoublePages` is enabled
- WHEN the device is in portrait orientation
- THEN pages are displayed one at a time, not joined

#### Scenario: Wide pages are never paired
- GIVEN `joinDoublePages` is enabled and the device is landscape
- WHEN a page is already marked `isWide` (i.e. it is already a spread image)
- THEN that page is displayed alone and is never paired with a neighboring page

#### Scenario: Shift offset skips page 0 before pairing
- GIVEN `shiftDoublePages` is enabled
- WHEN pages are joined
- THEN page 0 is displayed alone and pairing begins at page 1, offsetting all subsequent pairs by one

#### Scenario: Right-to-left order swaps pair rendering
- GIVEN the manga's reading direction is right-to-left and two pages are paired
- WHEN the joined spread is rendered
- THEN the page order within the pair is swapped compared to left-to-right reading direction

### Requirement: Tap zone navigation
The system SHALL provide a 3x3 tap zone grid for page navigation with pinch-to-zoom toggle support.

#### Scenario: Pinch gesture toggles zoom
- GIVEN a user is reading a manga page
- WHEN they perform a two-finger pinch gesture
- THEN zoom is toggled on the current page
