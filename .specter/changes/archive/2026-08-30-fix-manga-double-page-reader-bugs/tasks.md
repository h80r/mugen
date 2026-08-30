# Tasks

Repro config for every manual-verification step: **One Piece ch. 1188, landscape, paginated, right-to-left, "unir páginas duplas" enabled** (add `shiftDoublePages` on/off as a second pass where noted). Per project memory, bump `versionCode` +1 and set `versionName` (minor = this change's ordinal, patch = current task number) before each debug install.

## 1. Joined-spread navigation resolution (Bugs 1 & 4)

- [x] 1.1 Add `PagerViewerAdapter.indexOfPageOrJoined(page: ReaderPage): Int` — return `items.indexOf(page)` when found, else the index of the first `JoinedReaderPage` whose `firstPage === page || secondPage === page`, else -1.
- [x] 1.2 In `PagerViewer.moveToPage(page)`, replace `adapter.items.indexOf(page)` with `adapter.indexOfPageOrJoined(page)`; keep the existing `currentPosition == position` manual-`onPageChange` fallback.
- [x] 1.3 In `PagerViewerAdapter.getItemPosition(view)`, when `view.item` is a `ReaderPage` not directly in `items`, resolve via joined membership (reuse the 1.1 helper) so the joined holder survives `notifyDataSetChanged` on chapter append; fall back to `POSITION_NONE` only when truly absent.
- [x] 1.4 Add debug-guarded `logcat { }` logging: `groupPagesForDoublePage` (inputs + produced-list type counts); `PagerViewer.setChaptersInternal` (`requestedPage`, chosen index, resolved adapter position, item type there); `moveToPage` / `indexOfPageOrJoined` (page number; resolved directly / via joined / **not found** as a warning).
- [x] 1.5 Add the same style of logging to `ReaderActivity.moveToPageIndex` (requested index, page number, resolved-or-not) and to `ChapterNavigator`'s `Slider` and `VerticalSlider` `onValueChange` (old vs new value).
- [x] 1.6 Manually verify: open ch. 1188 → reader lands on the spread containing **page 1** (not the end-of-chapter transition, not ch. 1189).
- [x] 1.7 Manually verify: from that first spread, swipe backward once → the ch. 1188 → 1187 transition shows (not a ch. 1189 page); continuing forward returns to ch. 1188 page 1 without extra chapter hops.
- [x] 1.8 Manually verify: open the navigator, drag the blue slider thumb → the page changes and the thumb tracks the current spread. Repeat with `shiftDoublePages` enabled.

## 2. Render joined spread as one unified image (Bugs 2 & 3)

- [x] 2.1 Add `ImageUtil.mergeHorizontal(left: BufferedSource, right: BufferedSource): BufferedSource` following the `splitAndMerge` pattern (`decodeBitmap` → `createBitmap(w1 + w2, maxOf(h1, h2))` → `applyCanvas { drawBitmap(left …); drawBitmap(right …) }` → `compress(JPEG, 100)` → `Buffer`); top-align and pad the shorter half to the taller height; recycle intermediates. Document the height-mismatch choice inline.
- [x] 2.2 Rewrite `JoinedPagerPageHolder` to a single `ReaderPageImageView` (mirror `PagerPageHolder`'s structure): launch `loader.loadPage` for both `page.firstPage` and `page.secondPage`, combine their `statusFlow`s, show the progress indicator until **both** are `READY`.
- [x] 2.3 On both-ready: build the merged source via `mergeHorizontal(firstPage.stream, secondPage.stream)` (`firstPage` → left slot, `secondPage` → right slot — `groupPagesForDoublePage` already applies the R2L swap), then `setImage(...)` with the same `Config` block `PagerPageHolder.setImage()` derives from `viewer.config` (scale type, crop borders, zoom start, `landscapeZoom`, `enablePinchToZoom`).
- [x] 2.4 Delete `setupZoomSynchronization()`, `syncZoom()`, `isSyncing`, the two child `PagerPageHolder`s, and the `LinearLayout` / `orientation = HORIZONTAL` scaffolding.
- [x] 2.5 Handle edge cases: if either source is `ImageUtil.isWideImage`, render only the non-wide half (first page) and rely on `PagerPageHolder.process()`'s existing `refreshAdapter()` to re-group; if either half ends `ERROR` or has a null stream, show the single available half via the normal error path; if either half `isAnimatedAndSupported`, show `firstPage` alone. Compute `automaticBackground` once from the merged image (or `firstPage`).
- [x] 2.6 Add debug-guarded logging in the holder: both halves' page numbers + load states, composite start/finish, and which fallback branch (if any) was taken.
- [x] 2.7 Manually verify: joined spreads show the two halves meeting at the centerline with **no gap** (the ch. 1188 spread from the bug report).
- [x] 2.8 Manually verify: pinch-zoom anywhere on a spread scales **both halves together as one image** around the gesture focus; pan moves the whole spread; double-tap zoom behaves as on a single page.
- [x] 2.9 Manually verify no regression: portrait (spreads disabled), a chapter containing a genuinely wide page (stands alone, not merged), and L2R direction (earlier page on the left).

## 3. Tests, spec, build gate

- [x] 3.1 Extend `PagerViewerAdapterTest`: `indexOfPageOrJoined` returns the `JoinedReaderPage` index when given `firstPage` or `secondPage`; returns the direct index for a standalone page; returns -1 for a page present in neither; unaffected for `InsertPage` / transition items.
- [x] 3.2 Run `./gradlew :app:testReleaseUnitTest --tests "*PagerViewerAdapterTest*"` and the `ImageUtil` test class if one exists; fix failures.
- [x] 3.3 Reconcile `.specter/changes/fix-manga-double-page-reader-bugs/specs/reading-manga/spec.md` against the final implementation (adjust scenario wording only if behavior landed differently).
- [x] 3.4 Run `./gradlew spotlessCheck` and a full build (`./gradlew :app:assembleRelease` or the debug variant used for device testing); fix any lint/build errors before finishing.
