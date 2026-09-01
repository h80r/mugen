# Delta for Reading (Manga)

## ADDED Requirements

### Requirement: Page curl transition toggle
The system SHALL provide a reader preference that selects between the current page transition (off) and a page curl animation (on), routing paged reading modes to a Compose curl viewer when enabled and leaving the legacy pager viewer untouched when disabled.
Source: new `ReaderPreferences` boolean, `ReadingMode.toViewer()`, `MangaCurlViewer`.

#### Scenario: Toggle off preserves the existing reader
- GIVEN the page curl preference is disabled
- WHEN the user opens a chapter in a paged reading mode
- THEN the reader uses the existing `PagerViewer` with its current transition behavior, not the curl viewer

#### Scenario: Toggle on routes paged modes to the curl viewer
- GIVEN the page curl preference is enabled and the reading mode is left-to-right, right-to-left, or vertical
- WHEN the user opens a chapter
- THEN pages turn with an animated curl fold that reveals the neighbouring page's real content on the back of the flap

#### Scenario: Webtoon modes ignore the preference
- GIVEN the page curl preference is enabled and the reading mode is webtoon or continuous vertical
- WHEN the user opens a chapter
- THEN the webtoon viewer is used unchanged, because the curl is a paged metaphor that does not apply to continuous scrolling

#### Scenario: Flipping the toggle mid-session keeps the reading position
- GIVEN the user is reading a chapter and changes the page curl preference from the reader settings
- WHEN the viewer is swapped
- THEN the reader reopens on the page that was being read, not at the start of the chapter

### Requirement: Zoom preserved under the curl
The system SHALL keep pinch-to-zoom, pan, double-tap zoom, tiled decoding of large images, `landscapeZoom` and `cropBorders` fully working while the page curl is enabled, by rendering the current settled page as a live image surface and drawing only the animating flaps from captured snapshots.
Source: `MangaPageCurlRenderer` content lambda, `AndroidView` wrapping `ReaderPageImageView`, extracted snapshot renderer.

#### Scenario: Pinch-zoom works on the settled page
- GIVEN the page curl is enabled and a page is displayed at rest
- WHEN the user performs a two-finger pinch gesture on the page
- THEN the page zooms as it does in the current reader, not a page turn

#### Scenario: A very large page still decodes in tiles
- GIVEN the page curl is enabled and a page whose image is far larger than the viewport
- WHEN the page is displayed and zoomed
- THEN it is decoded through the tiled subsampling path at full detail, not downsampled to a single bitmap or failing to allocate

#### Scenario: Zoom level survives a page turn and return
- GIVEN the page curl is enabled and the user has zoomed into a page
- WHEN they turn forward and then back to that page
- THEN the reader presents the page cleanly at its fit scale rather than in a corrupt or partially-zoomed state

### Requirement: Curl yields to an active zoom
The system SHALL disable the curl's drag gesture while the current page is zoomed beyond its fit scale, so that dragging pans the image, and SHALL re-enable the curl once the page returns to fit scale.
Source: `ReaderPageImageView.onScaleChanged` / `scale == minScale` feeding `PageCurlConfig.dragForwardEnabled` and `dragBackwardEnabled`.

#### Scenario: Dragging a zoomed page pans instead of turning
- GIVEN the page curl is enabled and the user has zoomed into a page
- WHEN they drag across the page
- THEN the image pans and no curl fold begins

#### Scenario: Returning to fit scale restores the curl
- GIVEN the user has zoomed in and then returned the page to its fit scale
- WHEN they drag across the page
- THEN a curl fold begins as normal

#### Scenario: Tap zones still turn pages while zoomed
- GIVEN the page curl is enabled and the current page is zoomed in
- WHEN the user taps a next-page or previous-page tap zone
- THEN the reader turns the page, matching how tap navigation behaves in the current reader

### Requirement: Spine-anchored curl fold for joined double pages
The system SHALL render joined double-page spreads under the curl using two independent half-width curl surfaces so the fold is anchored at the spine rather than folding the whole spread as one wide leaf, reusing the existing double-page pairing rules rather than defining new ones.
Source: `groupPagesForDoublePage()` reused verbatim, two-column spread renderer ported from `SpreadPageTurnPageRenderer`.

#### Scenario: Only the turning leaf folds
- GIVEN the page curl is enabled, `joinDoublePages` is enabled, and the device is landscape showing a joined spread
- WHEN the user turns forward
- THEN only the leaf on the turning side animates its fold and the facing page stays still, as a physical book behaves

#### Scenario: Existing pairing rules still hold under the curl
- GIVEN the page curl is enabled and `joinDoublePages` is enabled in landscape
- WHEN pages are grouped for display
- THEN wide pages stand alone, `shiftDoublePages` still gives page 0 its own slot, and right-to-left order still swaps the pair, because the same grouping function is used as in the legacy viewer

#### Scenario: A wide or failed half dissolves the spread
- GIVEN a joined spread is displayed under the curl
- WHEN one half fails to load or decodes as a wide image
- THEN the pages are regrouped so the affected page stands alone, rather than rendering half a fold or a broken spread

#### Scenario: Leaving landscape collapses the spread
- GIVEN a joined spread is displayed under the curl in landscape
- WHEN the device rotates to portrait
- THEN pages are displayed one at a time with a single-page curl, and the fold resets rather than animating across the resize
- NOT IMPLEMENTED: the grouping is orientation-aware, but nothing re-runs it on rotation — `ReaderActivity` declares `android:configChanges="orientation|…"` so it is never recreated. Tracked in the backlog as `manga-curl-orientation-change`.

### Requirement: Curl fold direction follows the reading direction
The system SHALL fold pages in the direction matching the manga's reading direction, so that a right-to-left manga turns pages from the left edge toward the right.
Source: mirrored curl surface (`graphicsLayer(scaleX = -1f)` with per-draw un-mirrored content and `onMirroredSurface`).

#### Scenario: Right-to-left manga folds from the correct edge
- GIVEN the page curl is enabled and the reading direction is right-to-left
- WHEN the user swipes to advance
- THEN the fold originates at the edge appropriate to right-to-left reading and advances to the next page, not the previous one

#### Scenario: Page content is not mirrored
- GIVEN the page curl is enabled and the reading direction is right-to-left
- WHEN a page and the back of a turning flap are drawn on the mirrored surface
- THEN their artwork and any text read the right way round, not reversed

### Requirement: Reader feature parity under the curl
The system SHALL preserve every navigation and image-processing behavior the paged reader already supports when the curl is enabled, including tap zone navigation, chapter transitions, auto-scroll, volume-key navigation, dual page split, and the colour/render filters.
Source: reused `ViewerNavigation.getAction()`, `ChapterTransition` items, `PagerAutoScrollManager`, `Viewer.handleKeyEvent`, `splitInHalf`, container-level filters.

#### Scenario: Tap zones navigate as configured
- GIVEN the page curl is enabled and a tap zone layout is configured
- WHEN the user taps within a navigation region
- THEN the reader performs that region's action, using the same tap zone geometry as the legacy viewer

#### Scenario: Chapter transitions appear between chapters
- GIVEN the page curl is enabled and the user reaches the end of a chapter
- WHEN they continue past the last page
- THEN the chapter transition is shown and continuing from it opens the next chapter

#### Scenario: Auto-scroll and volume keys still advance pages
- GIVEN the page curl is enabled with auto-scroll running or volume-key navigation in use
- WHEN a page advance is triggered
- THEN the reader moves to the next page in reading order, animating the fold

#### Scenario: Colour filter and render effects still apply
- GIVEN the page curl is enabled with a colour filter, grayscale, inverted colours, or a render effect configured
- WHEN a page is displayed
- THEN the effect is applied to the displayed pages exactly as in the legacy viewer
