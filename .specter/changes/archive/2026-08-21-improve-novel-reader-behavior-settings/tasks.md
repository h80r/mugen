# Tasks

## 1. Remove chevron-up scroll-to-top button
- [x] 1.1 Delete the `IconButton(onClick = onScrollToTop)` block from `NovelReaderBottomPanel.kt:193-195`
- [x] 1.2 Remove the `onScrollToTop` parameter from `NovelReaderBottomPanel`'s signature (line ~86) and its call-site wiring in `NovelReaderContentHost.kt:3945-3955`
- [x] 1.3 Confirm no other call site references the removed scroll-to-top logic; delete the now-dead `webViewInstance?.scrollTo(0,0)`/`pagerState.animateScrollToPage(0)`/`textListState.animateScrollToItem(0)` lambda if nothing else needs it

## 2. Blur the settings drawer backdrop
- [x] 2.1 Extend `applyNovelSheetWindowFx` (`NovelReaderSettingsDialog.kt:148-165`) to apply a stronger content blur (not just window dim) behind the sheet on API 31+ devices, alongside the existing `FLAG_BLUR_BEHIND` detection
- [x] 2.2 Confirm the pre-API-31 fallback path (currently max 0.26f dim, no blur) still degrades gracefully without the new blur call
- [x] 2.3 Manually verify on an API 31+ device/emulator: open novel reader settings over dense chapter text and confirm the text is blurred, not just tinted, behind the sheet

## 3. Move clock/battery to the persistent progress line
- [x] 3.1 Extract the battery/time text formatting from `NovelReaderInfoOverlay.kt:60-64` into a small reusable function/composable if not already separable
- [x] 3.2 Add a battery/time `Text` positioned above the persistent progress line's `Box` elements in `NovelReaderContentHost.kt:3834-3850`, gated only on `settings.showBatteryAndTime` (not `showReaderUi`)
- [x] 3.3 Remove the now-redundant battery/time rendering from `NovelReaderInfoOverlay.kt`'s chip (keep the chip itself for time-to-end/word-count content when `showKindleInfoBlock` is enabled)
- [x] 3.4 Manually verify: enable "exibir hora e bateria" and confirm it now shows persistently above the progress line, not only when tapping the screen

## 4. Fix text selection — page-turn renderer
**Branch:** bugfix/novel-selection-page-turn
- [x] 4.1 Investigate why `touchHandlingEnabled = false` was hardcoded in `PageTurnPageRenderer.kt:1028,1056` and `SpreadPageTurnPageRenderer.kt:904` (check git blame/history for the original reason — likely gesture-conflict avoidance with the curl drag)
- [x] 4.2 Thread the real touch-handling flag through (matching `ComposePagerPageRenderer.kt`'s implicit `true` default), resolving any genuine gesture conflict with the curl/page-turn drag found in 4.1 (e.g. only enabling touch handling when a selection-relevant preference is on, consistent with `selectionInteractionEnabled`'s existing OR logic)
- [x] 4.3 Manually verify: enable "seleção de texto", switch page transition style to Curl, and confirm long-press selects text without breaking the page-turn drag gesture
- [x] 4.4 Replace the misleading "Select all" action with "Select sentence" and "Select paragraph"
- [x] 4.5 Make the custom selection handles draggable so either boundary can be adjusted
- [x] 4.6 Manually verify both range actions and dragging both handles in Curl mode
- [x] 4.7 Allow a long-press selection to extend with the same finger's subsequent drag
- [x] 4.8 Allow a continuous long-press drag to select across paragraph blocks
- [x] 4.9 Show selection handles only at the global start and end of a multi-paragraph selection

## 5. Fix text selection — scroll mode gesture arbitration
**Branch:** bugfix/novel-selection-scroll-mode

Validation (2026-08-19) against the now-completed page-turn implementation (group 4) found the original 5.1-5.3 plan incomplete on two fronts: (a) page-turn's real arbitration mechanism is not a `userScrollEnabled`-style suspension — `PageTurnPageRenderer.kt:1122`/`SpreadPageTurnPageRenderer.kt:909` only enable `touchHandlingEnabled` on the static at-rest page layer, never on the animated curl layers, so selection and the drag gesture never actually share a touch stream; scroll mode has no such layer split, so a genuine pending/active gesture-suspension mechanism is still the right tool here, per design.md. (b) `NovelPageReaderSelectionCoordinator` (`NovelPageReaderPageContent.kt:1272`) — which group 4.5-4.9 built handles, cross-paragraph dragging, and sentence/paragraph actions on top of — is `remember(contentPage.pageIndex)`-scoped inside `NovelPageReaderPageContent` (`NovelPageReaderPageContent.kt:1550`) and only registers the `NovelPageReaderTextView`s of one page's `Column`. Both `NovelReaderContentHost.kt` (`NovelPageReaderTextBlock` calls at lines ~2909, ~2944) and `NovelBookContentHost.kt` (via `NovelRichNativeScrollItem` → `NovelPageReaderTextBlock`, `NovelRichTextCompose.kt:107`) call `NovelPageReaderTextBlock` directly per `LazyColumn` item with no `selectionCoordinator` passed — every item is an island. Reaching true parity (not just "long-press selects one paragraph without the list scrolling") requires giving scroll/BOOK mode a shared coordinator across the `LazyColumn`'s visible window, not just gesture arbitration. Tasks below are expanded accordingly; 5.1-5.3's original scope is preserved as 5.1-5.3 with 5.4-5.7 added before verification.

- [x] 5.1 Add a "selection gesture pending/active" callback/state hoisted from `NovelPageReaderTextView`'s `scheduleSelectionPromotion()`/handle-drag start (`NovelPageReaderPageContent.kt:415` promotion runnable, plus handle-drag entry at `NovelPageReaderPageContent.kt:769` `beginHandleDrag`) up through `NovelPageReaderTextBlock` to the hosting composable
- [x] 5.2 Wire that state into `NovelReaderContentHost.kt`'s scroll-mode `LazyColumn` (~lines 2666-2843): toggle `userScrollEnabled` and suspend the chapter-swipe `pointerInput`s (the tap detector at ~2669, horizontal-swipe at ~2693, vertical-swipe at ~2736) while a selection gesture is pending/active
- [x] 5.3 Apply the identical fix to `NovelBookContentHost.kt`'s `LazyColumn` (lines 320-348) for BOOK reading mode
- [x] 5.4 Give scroll mode a `NovelPageReaderSelectionCoordinator` that spans the `LazyColumn`'s currently-visible items instead of one-per-item: hoist a coordinator to `NovelReaderContentHost.kt`'s scroll-mode composable and `NovelBookContentHost.kt`'s native-book composable, pass it (with a stable `selectionBlockOrder` derived from item index) into every `NovelPageReaderTextBlock` call currently omitting it (`NovelReaderContentHost.kt:2909,2944`; `NovelRichTextCompose.kt:107`'s `NovelRichNativeScrollItem` and its call sites in `NovelBookContentHost.kt`)
- [x] 5.5 Verify the coordinator survives `LazyColumn` item recycling/windowing: a paragraph that scrolls out of the composed window mid-selection must not silently drop its endpoint or corrupt the coordinator's view registry (`register`/`unregister` at `NovelPageReaderPageContent.kt:1283-1294` already run from `onDetachedFromWindow`/`init`, confirm this still yields correct behavior — e.g. clamping/collapsing the selection to the still-composed range — rather than a crash or stale view reference when a coordinator-tracked view leaves composition)
- [x] 5.6 Manually verify: enable "seleção de texto" in the default continuous scroll mode (not page-turn) and confirm long-press selects text without the list scrolling or swiping chapters instead, AND that handles/select-sentence/select-paragraph/cross-paragraph drag work exactly as in Curl mode. Follow-up verification also covered fast, slow, accelerating, decelerating, and pause-then-drag scroll gestures on-device; the latter exposed and then verified the long-press arbitration fix in `NovelPageReaderTextView`.
- [x] 5.7 Repeat verification in BOOK reading mode (deferred to `.specter/backlog/validate-book-mode-text-selection.md` at the user's request; entering BOOK currently requires compiling every chapter of a novel.)

## 6. Default translation/dictionary language to app UI language
- [x] 6.1 Add a first-run seed check to `NovelReaderPreferences.kt`'s existing `init {}` migration block (~lines 459-464): if `selectedTextTranslationTargetLanguage`/`novelDictionaryTargetLanguage` have never been explicitly set, resolve from `AppCompatDelegate.getApplicationLocales().get(0)?.language`, falling back to `"en"` if not among the 10 supported codes in `BehaviorTab.kt`'s language map
- [x] 6.2 Manually verify: fresh install (or reset preferences) with app language set to Portuguese, confirm the novel reader's default translation/dictionary target language resolves to Portuguese, not Russian
- [x] 6.3 Manually verify: app language set to an unsupported language (e.g. Turkish), confirm the default falls back to English

## 7. Replace selected-text context menu and lookup drawer
- [x] 7.1 Integrate the completed group 1–5 history from `bugfix/novel-selection-scroll-mode` into the active change branch, preserving group 6's locale-default commits and resolving the Specter artifacts as the source of truth
- [x] 7.2 Extract the Aurora home bottom-navigation shell/item styling into a reusable internal component driven by icon content, label, selection/pressed state, availability, and `onClick`; migrate `HomeScreen.kt` to it without visual or navigation regressions
- [x] 7.3.1 Define the renderer-neutral selection action model and build the reusable Aurora bottom-console composable with Copy, Share, Expand, Dictionary, and Translate in order; feature-gate Dictionary and Translate and make narrow layouts horizontally scrollable
- [x] 7.3.2 Mount the console for an active selection above the reader navigation inset, keeping it hidden while a lookup sheet is active
- [x] 7.3.3 Route Dictionary and Translate through the existing selected-text controller; defer Copy, Share, and Expand dispatch to task 7.4's renderer contract
- [x] 7.4.1 Define and hoist a transient renderer-action contract in `NovelReaderContentHost`, keeping clear/expand callbacks out of `NovelReaderScreenModel`
- [x] 7.4.2 Suppress native `ActionMode` toolbars while retaining the existing native selection highlights and draggable handles
- [x] 7.4.3.1 Register renderer clear callbacks for native scroll, pager/Curl, and BOOK mode; route Back and outside-tap dismissal through the transient contract
- [x] 7.4.3.2 Register native expand callbacks for scroll, pager/Curl, and BOOK selection coordinators; route the console Expand action through the contract
- [x] 7.4.3.3 Route Copy and Share through the contract and complete the selection session after either action
- [x] 7.5.1 Suppress the WebView contextual toolbar without clearing the browser selection or native handles
- [x] 7.5.2 Add WebView bridge commands to clear and publish the active DOM `Range`
- [x] 7.5.3.1 Route WebView clear/expand through the transient host contract and retain the updated DOM range for the console
- [x] 7.5.3.2 Replace paragraph-only DOM expansion with sentence-first adaptive expansion using `Intl.Segmenter` and a punctuation fallback
- [x] 7.6.1 Add native single-block adaptive expansion: grow a partial sentence to its sentence boundary, otherwise to the full paragraph without shrinking
- [x] 7.6.2 Extend coordinated native selections so a complete sentence or multi-block selection expands to all touched paragraph boundaries
- [x] 7.6.3 Verify the WebView and native expansion paths preserve the active selection and never reduce its range (compile coverage complete; device verification remains bundled with 7.12)
- [x] 7.7.1 Keep the renderer selection and console active after Expand while Copy/Share complete the session
- [x] 7.7.2 Route chapter changes and lookup-sheet dismissal through the renderer clear contract as well as the controller
- [x] 7.7.3 Verify Back and outside taps clear the active renderer selection in every renderer (source-path review and compile coverage complete; device verification remains bundled with 7.12)
- [x] 7.8 Default `selectedTextTranslationEnabled` and `novelDictionaryEnabled` to true only when unset, including resolved/data-model defaults, while preserving explicitly stored false values
- [x] 7.9 Extract `NovelReaderAuroraSheet` from reader settings (`AdaptiveSheet`, Aurora scheme/shape/rim, reveal animation, API 31+ blur, and pre-31 dim fallback) and migrate `NovelReaderSettingsDialog` to it without visual regression (compile coverage complete; device verification remains bundled with 7.12)
- [x] 7.10 Refactor `SelectedTextTranslationOverlay` into an explicitly-requested lookup sheet: plain selection shows no drawer/spinner; Dictionary or Translate starts only its own lookup and initially renders one mode without tabs
- [x] 7.11 Add progressive alternate lookup: single-word translation offers View definition when enabled; definition offers View translation when enabled; activating it reveals both Aurora tabs, starts the alternate request, and retains loaded results; omit definition for multi-word translations
- [x] 7.12 Add/update localized strings, content descriptions, and unit tests for action order/gating, unset defaults, word classification, adaptive expansion, lookup visibility/mode transitions, and dismissal; compile and manually verify the full flow in scroll, pager/Curl, BOOK, and WebView modes, including API 31+ blur and the pre-31 fallback (focused unit suites and compile complete; manually verified the native scroll flow and API 31+ blur on Android)
