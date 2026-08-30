# Delta for Reading (Manga)

## ADDED Requirements

### Requirement: Joined-spread navigation resolution
The system SHALL resolve a target `ReaderPage` to its on-screen position even when that page has been merged into a `JoinedReaderPage` spread, so that page-targeted navigation (chapter open, page slider, next/previous-chapter jumps, preprocessed split-page moves) lands on the correct spread instead of silently doing nothing.
Source: `PagerViewerAdapter.indexOfPageOrJoined()`, used by `PagerViewer.moveToPage()` and `PagerViewerAdapter.getItemPosition()`.

#### Scenario: Opening an unread chapter lands on its first page
- GIVEN `joinDoublePages` is enabled, the device is landscape, reading direction is right-to-left, and the chapter's first two pages are joined into one spread
- WHEN the chapter is opened with no saved progress
- THEN the reader displays the spread containing page 1, not the end-of-chapter transition and not a neighboring chapter's page

#### Scenario: Backward navigation from the first spread reaches the previous-chapter transition
- GIVEN the reader is showing the spread containing page 1 of the current chapter, in the joined-spread right-to-left landscape configuration
- WHEN the user swipes backward once
- THEN the reader shows the transition from the current chapter to the previous chapter, not a page of the next chapter

#### Scenario: Page slider moves to a joined spread
- GIVEN the current on-screen item is a `JoinedReaderPage` spread
- WHEN the user drags the page-navigator slider thumb to a different page value
- THEN the reader moves to the item (page or spread) containing that page

#### Scenario: Resolution falls back cleanly for absent pages
- GIVEN a `ReaderPage` that is neither present as a standalone adapter item nor contained in any `JoinedReaderPage`
- WHEN its position is requested
- THEN the lookup reports "not found" rather than matching an unrelated item

### Requirement: Joined spread renders as a single unified image
The system SHALL render a `JoinedReaderPage` as one composited image on a single zoomable surface, so the two page halves are joined at the screen centerline with no gap between them and any zoom or pan gesture transforms the whole spread as one object.
Source: `JoinedPagerPageHolder` compositing both halves into one `ReaderPageImageView`.

#### Scenario: No gap between the two halves
- GIVEN two non-wide pages joined into a spread in landscape
- WHEN the spread is displayed at fit scale
- THEN the right edge of one half meets the left edge of the other at the centerline with no empty band between them

#### Scenario: Pinch-zoom scales the whole spread
- GIVEN a joined spread is displayed
- WHEN the user performs a pinch-zoom gesture anywhere on the spread
- THEN both halves scale together as a single image around the gesture focus, not each half independently around its own center

#### Scenario: Right-to-left order is preserved in the composite
- GIVEN reading direction is right-to-left and two pages are joined
- WHEN the composited spread is built
- THEN the earlier page appears on the right and the later page on the left

#### Scenario: A half that fails or is wide does not corrupt the spread
- GIVEN two pages are joined into a spread
- WHEN one half fails to load, is missing, or decodes as a wide image
- THEN the reader falls back gracefully (showing the available page or re-grouping so the wide page stands alone) rather than rendering a broken composite
