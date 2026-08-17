# Reading (Novel) Specification

## Requirements

### Requirement: Dual rendering engines
The system SHALL route novel page rendering through one of two engines based on the selected transition style: `COMPOSE_PAGER` (Instant, Slide, Depth, Book-Flip) or `PAGE_TURN_RENDERER` (Book, Curl).
Source: `NovelPageTransitionStyle` (6 values), `app/.../presentation/reader/novel/`.

#### Scenario: Curl style routes to the page-turn renderer
- GIVEN the user selects the "Curl" page transition style
- WHEN a page is rendered
- THEN the `PAGE_TURN_RENDERER` engine is used, not the Compose pager

### Requirement: Two-page landscape spread activation
The system SHALL activate a two-column spread layout only when the `twoPageLandscape` preference is enabled, the viewport is landscape, and the viewport width is at least 600dp.
Source: `NovelReaderInteraction.kt: resolveNovelSpreadColumns`, `NOVEL_SPREAD_MIN_WIDTH_DP = 600`.

#### Scenario: Spread requires all three conditions
- GIVEN `twoPageLandscape` is enabled and the device is landscape
- WHEN the viewport width is 480dp (below the 600dp threshold)
- THEN the reader falls back to a single column, not a spread

#### Scenario: Novel spread has a width floor manga's double-page join does not
- GIVEN the same landscape device
- WHEN comparing manga's `joinDoublePages` (no width floor) against novel's spread mode (600dp floor)
- THEN manga pages may join at any landscape width, but novel spread only activates at tablet-class widths — because unlike page art, joined text becomes unreadably narrow below that width

### Requirement: Spread slot math
The system SHALL map single-column page indices to paired spread slots via pure, unit-tested functions, giving a trailing odd page its own slot rather than dropping or force-pairing it.
Source: `resolveSpreadSlotCount`, `resolveSpreadSlotFirstPageIndex`, `resolveSpreadSlotForPageIndex`, `NovelReaderInteractionTest.kt`.

#### Scenario: Odd page count leaves a dangling half-empty slot
- GIVEN a chapter has an odd number of pages
- WHEN spread slots are computed
- THEN the final slot contains only the last page, with an empty second column, rather than being merged into the previous slot or dropped

#### Scenario: TTS and seekbar address real page indices
- GIVEN spread mode is active
- WHEN text-to-speech navigation or the vertical seekbar reports a position
- THEN it operates on real single-column page indices, unaffected by the paired-slot layout used only for rendering

### Requirement: Spine-anchored curl fold in spread mode
The system SHALL render two-page spreads under the Curl renderer using two independent half-width curl surfaces so the page fold is anchored at the spine rather than spanning the full spread width.
Source: `SpreadPageTurnPageRenderer.kt`.

#### Scenario: Left and right surfaces are independently addressed
- GIVEN a two-page spread is displayed under Curl style
- WHEN the underlying page-curl library measures widget width
- THEN two half-width `PageCurl` surfaces are used side by side — left surface always shows even page indices with its fold at the screen's left edge, right surface always shows odd page indices with its fold at the screen's right edge

#### Scenario: Left surface is horizontally mirrored
- GIVEN the left spread surface must fold at the screen's left edge
- WHEN it is rendered
- THEN it is drawn with `scaleX = -1f` and its content un-mirrored back, because the underlying curl library's drag/animation math always anchors on the widget's own right edge

#### Scenario: Non-animating side swaps instantly
- GIVEN a page turn animates on one surface (e.g. the right surface turning forward)
- WHEN the animating surface's fold settles
- THEN the other (left) surface's content swaps instantly via `snap()`, mirroring how a real book's spine-side leaf is already in place

#### Scenario: Drag-to-turn completion threshold is halved
- GIVEN each spread surface is only half the width of a full single-page surface
- WHEN a user drags to turn a page
- THEN the release/completion fraction (`spineReach = 0.5f`) reflects that a drag across half the width completes the turn, versus the full-width threshold used in single-page mode

#### Scenario: Snapshot cache keys include column offset
- GIVEN both spread surfaces share one snapshot cache
- WHEN a page snapshot is cached
- THEN the cache key includes the column offset (0 or 1) so the two surfaces' cached snapshots do not collide

#### Scenario: Tap zones map to absolute screen coordinates
- GIVEN a spread is displayed across two half-width surfaces
- WHEN the user taps to turn a page
- THEN the tap position is mapped to absolute screen coordinates rather than per-surface-local coordinates, and the tap reliably triggers the page-turn animation (not just an instant content swap) even at the spread's edges

#### Scenario: Backward drag turns pages in the correct direction
- GIVEN the user drags backward across a spread surface
- WHEN the drag is interpreted
- THEN the page turns backward with the animating fold stretching correctly rather than being clipped at the spine

### Requirement: Text replacement rules
The system SHALL support Legado-style text replacement rules applied to novel chapter content during reading.

#### Scenario: Replacement rule alters displayed text
- GIVEN a user has configured a text replacement rule
- WHEN a chapter is displayed
- THEN matching text is replaced according to the rule before rendering
