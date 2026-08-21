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

### Requirement: Settings drawer has a blurred backdrop
The system SHALL apply a backdrop blur behind the novel reader's settings drawer (in addition to its existing translucent panel background) on devices that support window blur, so content behind the drawer is not sharply readable through it.

#### Scenario: Blur applied on supporting devices
- GIVEN a device running Android 12 (API 31) or later
- WHEN the novel reader settings drawer is opened
- THEN the reader content behind the drawer is blurred, not just dimmed

#### Scenario: Graceful degradation on older devices
- GIVEN a device below API 31 (no `FLAG_BLUR_BEHIND` support)
- WHEN the novel reader settings drawer is opened
- THEN the existing translucent-panel-plus-window-dim behavior is used, without a hard crash or missing dim

### Requirement: Battery and time appear on the persistent progress line
The system SHALL render battery level and time (when the `showBatteryAndTime` preference is enabled) above the always-visible reading progress line, independent of whether the tap-reveal UI chip is currently shown.

#### Scenario: Battery/time visible without tapping the screen
- GIVEN `showBatteryAndTime` is enabled and the reader UI chip is currently hidden (the normal reading state)
- WHEN the user is reading
- THEN battery level and time are shown above the persistent progress line, without requiring a tap to reveal the chip

### Requirement: Text selection works in every novel reading mode
The system SHALL allow text selection (when the `textSelectionEnabled` preference, or an equivalent selection-driven feature like selection-translation or dictionary lookup, is enabled) consistently across the Curl/Book page-turn renderer, the Compose-pager renderer, continuous scroll mode, and BOOK reading mode.

#### Scenario: Selection works under Curl/Book page-turn style
- GIVEN `textSelectionEnabled` is enabled and the page transition style is Curl or Book
- WHEN the user long-presses on chapter text
- THEN a text selection is promoted, matching the behavior already present under Slide/Depth/Instant transition styles

#### Scenario: Selection works in continuous scroll mode
- GIVEN `textSelectionEnabled` is enabled and the reader is in the default continuous scroll reading mode
- WHEN the user long-presses on chapter text
- THEN a text selection is promoted rather than being consumed as a scroll or tap gesture by the hosting list

#### Scenario: Selection works in BOOK reading mode
- GIVEN `textSelectionEnabled` is enabled and the reader is in BOOK reading mode
- WHEN the user long-presses on chapter text
- THEN a text selection is promoted, matching continuous scroll mode's fixed behavior

### Requirement: Translation and dictionary default to the app's UI language
The system SHALL seed the selection-translation and dictionary target-language preferences from the app's current UI language on first use, when the user has not explicitly set them, falling back to English if the UI language isn't among the supported translation/dictionary languages.

#### Scenario: First-run default matches app language
- GIVEN a user has never explicitly configured the novel reader's selection-translation or dictionary target language, and the app's UI language is one of the 10 supported codes
- WHEN the preference is first read
- THEN it resolves to the app's UI language rather than the previous hardcoded Russian default

#### Scenario: Unsupported UI language falls back to English
- GIVEN the app's UI language is not among the 10 languages the translation/dictionary feature supports
- WHEN the target-language preference is first seeded
- THEN it falls back to English rather than failing or defaulting to Russian

### Requirement: Selected-text actions use an Aurora bottom console
The system SHALL replace the floating Android text-selection context menu with a bottom action console matching the home navigation's Aurora visual component in every novel renderer, offering Copy, Share, Expand, Dictionary, and Translate as applicable.

#### Scenario: Console replaces the context menu
- GIVEN text-selection interaction is enabled in any native, page-turn, BOOK, or WebView novel renderer
- WHEN the user selects text
- THEN no floating system context menu is shown and the Aurora action console appears at the bottom of the reader

#### Scenario: Optional actions follow preferences
- GIVEN the selected-text console is visible
- WHEN dictionary or selected-text translation is disabled
- THEN the corresponding action is omitted while Copy, Share, and Expand remain available

#### Scenario: Copy and share complete the selection session
- GIVEN the selected-text console is visible
- WHEN the user copies or shares the selected text
- THEN the requested platform action is performed and the selection plus console are dismissed

### Requirement: Expand adapts from sentence to paragraph
The system SHALL expand a selected range to its containing sentence when smaller than that sentence, otherwise to the full boundaries of every paragraph touched by the range, without reducing the current selection.

#### Scenario: Word expands to sentence
- GIVEN a selection covers less than its containing sentence
- WHEN the user taps Expand
- THEN the selection grows to the complete containing sentence and remains active

#### Scenario: Sentence or multi-block selection expands to paragraphs
- GIVEN a selection covers a complete sentence or crosses paragraph blocks
- WHEN the user taps Expand
- THEN the selection grows from the start of the first touched paragraph to the end of the last touched paragraph and never shrinks

### Requirement: Translation and dictionary are enabled for new users
The system SHALL default selected-text translation and dictionary lookup to enabled only when their preferences have never been explicitly stored.

#### Scenario: Unset features start enabled
- GIVEN the translation and dictionary enabled preferences have never been set
- WHEN novel reader settings are resolved
- THEN both features are enabled

#### Scenario: Explicit opt-out is preserved
- GIVEN a user explicitly disabled either feature
- WHEN novel reader settings are resolved later
- THEN that feature remains disabled

### Requirement: Lookup results open only on explicit request
The system SHALL keep lookup UI hidden for a plain selection and open a blurred Aurora reader sheet containing only the explicitly requested translation or definition.

#### Scenario: Selection alone does not open lookup UI
- GIVEN translation or dictionary lookup is enabled
- WHEN the user selects text without choosing a lookup action
- THEN the bottom action console is shown, both lookup states remain Idle, and no lookup sheet or loading indicator appears

#### Scenario: Explicit action opens one result mode
- GIVEN selected text and its action console are visible
- WHEN the user taps Dictionary or Translate
- THEN only that lookup starts and the Aurora lookup sheet opens directly to the requested content without tabs

#### Scenario: Alternate lookup progressively reveals tabs
- GIVEN the lookup sheet was opened for a definition, or for translation of a single word, and the alternate feature is enabled
- WHEN the user chooses the offered alternate lookup action
- THEN both Definition and Translation tabs appear, the alternate lookup starts, and the user can switch between retained results

#### Scenario: Multi-word translation omits definition shortcut
- GIVEN the lookup sheet was opened to translate a selection containing multiple linguistic words
- WHEN the translation content is displayed
- THEN no View definition action is offered

#### Scenario: Dismissing lookup clears the session
- GIVEN a selected-text lookup sheet is open
- WHEN the user closes or dismisses it
- THEN in-flight lookup work is cancelled and the selection, sheet, and action console are cleared
