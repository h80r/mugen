# Design: Novel Reader Behavior Settings Fixes

## 1. Chevron-up button removal

`NovelReaderBottomPanel.kt:193-195`'s `IconButton(onClick = onScrollToTop)` is deleted outright, along with the `onScrollToTop` parameter and its wiring at `NovelReaderContentHost.kt:3945-3955`. The underlying scroll-to-top logic (mode-aware: WebView `scrollTo(0,0)`, pager `animateScrollToPage(0)`, list `animateScrollToItem(0)`) is deleted with it unless another call site is found during implementation to still need it — checked, none currently exists.

No replacement affordance is added. The user judged the action unnecessary (scrolling to the top of a chapter mid-read has limited value; users can already scroll manually), not merely poorly discoverable.

## 2. Settings drawer backdrop blur

**Decision:** add a blur behind the sheet, keep the existing translucency, rather than raising the panel's opacity to solid.

**Why not just raise opacity:** the "Aurora glass" visual language is intentional (see the doc comment at `NovelReaderSettingsDialog.kt:68-72` — "progressive window blur ... same chrome language as manga `ReaderSettingsDialog`"). Going fully opaque would fix legibility but abandon that visual identity. Blur preserves translucency (you can still tell there's reader content behind the sheet) while making it unreadable, which is what "hard to visualize" in the feedback actually calls for.

**Mechanism:** `applyNovelSheetWindowFx` (`NovelReaderSettingsDialog.kt:148-165`) already detects blur capability (Android 12+/API 31+, `FLAG_BLUR_BEHIND`) and applies a small window dim (0.18–0.26 max) tied to reveal progress. Extend this existing blur-capable path to apply a stronger blur radius specifically behind the sheet content (not just the window dim), rather than introducing a second, separate blur mechanism. On pre-API-31 devices where `FLAG_BLUR_BEHIND` isn't available, fall back to the current higher-dim-only behavior (0.26f) — no blur, but the existing translucency-plus-dim combination is an acceptable degradation for older devices.

**Alternative rejected:** a Compose-level `Modifier.blur()` on a duplicated background layer was considered, but the sheet is already rendered via a platform `Dialog` (`AdaptiveSheet`), not a Composable overlay directly on top of the reader content — a Compose-side blur modifier can't reach content that lives in a separate window. The window-level `FLAG_BLUR_BEHIND` approach already in use is the only mechanism that can blur the reader content behind a separate dialog window, so extending it is the only viable path, not just the preferred one.

## 3. Clock/battery: chip → persistent progress line

Straightforward relocation, no new mechanism. Add a battery/time `Text` (reusing the formatting logic already in `NovelReaderInfoOverlay.kt:60-63`) positioned above the persistent progress line's `Box` elements in `NovelReaderContentHost.kt:3834-3850`, gated only on `settings.showBatteryAndTime` — decoupled from the `showReaderUi` condition that currently ties it to the tap-reveal chip. The chip's own battery/time rendering can be removed once the persistent version ships (no need for both), but the chip itself stays for its other content (time-to-end, word count) if `showKindleInfoBlock` is enabled.

## 4. Text selection: two independent fixes required

**4a. Page-turn renderer (`touchHandlingEnabled` hardcode):** `PageTurnPageRenderer.kt:1028,1056` and `SpreadPageTurnPageRenderer.kt:904` pass `touchHandlingEnabled = false` unconditionally. Thread the real flag through instead — likely the same value `ComposePagerPageRenderer.kt` implicitly uses (default `true`), gated by whatever originally motivated hardcoding it false here (if there was a genuine reason — e.g. avoiding gesture conflicts with the curl drag — that reason needs to be re-verified during implementation; if the curl drag and text selection can't coexist, the fix may need to conditionally enable touch handling only when selection-relevant preferences are on, similar to how `selectionInteractionEnabled` already ORs together `textSelectionEnabled`/`selectedTextTranslationEnabled`/`novelDictionaryEnabled`).

**4b. Scroll-mode gesture arbitration:** this is the harder problem. `NovelPageReaderTextView`'s long-press-to-select mechanism calls `parent?.requestDisallowInterceptTouchEvent(true)` (`NovelPageReaderPageContent.kt:428`) — a View-system API that `LazyColumn`'s Compose-native `pointerInput`/`scrollable` gesture pipeline never consults, so the list's scroll/tap/swipe detectors race and win against the ~500ms long-press promotion timer.

**Decision:** give the hosting `LazyColumn` (`NovelReaderContentHost.kt:2628-2805` for scroll mode, `NovelBookContentHost.kt:320-348` for BOOK mode) a way to suspend its own gesture detectors during an in-progress long-press, via a callback surfaced from `NovelPageReaderTextView`/`NovelPageReaderTextBlock` at the start of `scheduleSelectionPromotion()` (`NovelPageReaderPageContent.kt:415`). Concretely: a mutable "selection gesture pending/active" state hoisted to the host composable, toggling `LazyColumn(userScrollEnabled = !selectionPending)` and disabling the horizontal/vertical chapter-swipe `pointerInput`s for the same duration.

**Alternatives considered:**
- *Compose `SelectionContainer` instead of the native `TextView`:* would sidestep the whole View/Compose gesture-interop problem, but is a much larger rewrite of the novel reader's text rendering (currently entirely `AndroidView`-hosted for reasons presumably including custom paragraph-level styling/translation overlays not easily done in pure Compose text). Rejected as out of scope for a bug-fix change — worth a future investigation if the interop fix proves fragile in practice.
- *`NestedScrollConnection`:* considered as the "proper" Compose-idiomatic coordination mechanism, but `NestedScrollConnection` coordinates two Compose scrollables, not a Compose scrollable vs. an interop `AndroidView`'s internal gesture state — doesn't directly solve the View-vs-Compose arbitration problem here, though it may still be useful for the swipe-to-change-chapter gestures specifically. The callback-based `userScrollEnabled` toggle is simpler and directly targets the actual conflict (long-press-to-select vs. scroll/tap/swipe).

Apply the identical fix to both `NovelReaderContentHost.kt` (scroll/rich-text paths) and `NovelBookContentHost.kt` (BOOK mode) since both have the same structural gap.

## 5. Default translation/dictionary target language

`selectedTextTranslationTargetLanguage()`/`novelDictionaryTargetLanguage()` (`NovelReaderPreferences.kt:707-720`) use `PreferenceStore.getString(key, "ru")` — a static default, evaluated once per call, not lazily computed from current app state.

**Decision:** use a one-time first-run resolution rather than making the default computed on every read. Add to the existing `init {}` migration block (`NovelReaderPreferences.kt:459-464` already has this pattern for other legacy migrations) a check: if the preference has never been explicitly set by the user, seed it from `AppCompatDelegate.getApplicationLocales().get(0)?.language`, falling back to `"en"` if that language isn't among the 10 codes `BehaviorTab.kt`'s hardcoded map supports (`en`, `ru`, `ja`, `zh`, `ko`, `es`, `fr`, `de`, `it`, `pt`).

**Why not a dynamic default on every read:** `PreferenceStore.getString(key, default)` returns a `Preference<String>` object once; making the default itself reactive to locale changes would require either wrapping every read site or changing the `PreferenceStore` API shape — disproportionate for a one-time default-value improvement. A first-run seed matches the existing migration pattern in the same file and is sufficient: if a user changes their app language later, this default isn't expected to follow them (they'd change the reader-specific preference directly, same as any other explicit setting).

## 6. Selected-text action console

**Decision:** replace Android's floating `ActionMode` toolbar with an Aurora bottom action console in every novel renderer (native scroll, Compose pager, Curl/Book, BOOK mode, and WebView). The console contains Copy, Share, Expand, Dictionary, and Translate in that order; Dictionary and Translate are omitted when their preference is disabled.

The home navigation's Aurora shell and item styling are currently private and coupled to tabs in `HomeScreen.kt`. Extract a reusable internal bottom-bar surface/item component whose inputs are icon content, label, selected/pressed state, availability, and `onClick`. Migrate the home navigation to that component, then use the same component for the reader console with no persistent selected item. The reader supplies a haze source where supported and uses the same translucent fallback in WebView/E-Ink paths, so the console preserves the home bar's shape, rim, spacing, colors, and system-navigation inset behavior.

Do not put live `View`/`WebView` references into `NovelReaderScreenModel`. The reader host owns a transient renderer action contract for the active selection (`expand` and `clear`) while the screen model continues to own immutable selection/lookup state. Copy and Share operate from the selected text in Compose and then clear through that contract. Expand dispatches to the active renderer and keeps the updated selection visible. A tap outside, Back, chapter change, Copy, Share, or closing a lookup sheet clears both the renderer selection and controller state.

Native renderers stop starting the floating action mode and continue using the custom selection highlight/handles built in groups 4–5. WebView suppresses its contextual toolbar while retaining native handles and extends the existing JavaScript selection bridge with expand/clear commands.

**Expand policy:** expansion never reduces the current range. If the selection is strictly smaller than its containing sentence, expand to that sentence. Once it covers a complete sentence or spans blocks, expand from the beginning of the first touched paragraph to the end of the last touched paragraph. Native text uses `BreakIterator`; WebView uses DOM `Range` plus `Intl.Segmenter` when available, with a punctuation-boundary fallback.

## 7. On-demand Aurora lookup sheet

**Decision:** separate “there is an active selection” from “a lookup was requested.” A plain selection shows only the action console and leaves both lookup states Idle. Dictionary or Translate hides the console, records the requested mode, opens the sheet, and immediately starts only that operation. This removes the current empty/infinite-loading drawer caused by rendering Idle as a spinner whenever `selection != null`.

Extract the platform-dialog chrome from `NovelReaderSettingsDialog` into a reusable `NovelReaderAuroraSheet`: `AdaptiveSheet`, Aurora material mapping, rounded top shape, rim, progressive API 31+ window blur, and the older-device dim fallback. Reader settings and selected-text lookup both consume it, preventing their blur behavior from drifting.

The lookup sheet initially has no tabs and renders only the requested result. Translation of exactly one linguistic word offers “View definition” when dictionary lookup is enabled; a definition offers “View translation” when translation is enabled. Choosing the alternate action reveals both Aurora tabs, switches to and starts the alternate lookup, and retains already-loaded results while switching. Multi-word translation does not offer definition. Dismissing the sheet always cancels in-flight work and clears the selection.

Translation and dictionary preference getters/data-model defaults change to enabled for unset preferences. Explicitly stored `false` values remain authoritative, so existing user choices are preserved.
