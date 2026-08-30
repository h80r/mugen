# Design: Fixing Manga Double-Page Reader Bugs

## Root-cause summary (verified against code)

| Bug | Root cause | File / line |
|---|---|---|
| 1. Opens on transition; backward nav hops chapters | `PagerViewer.moveToPage` does `adapter.items.indexOf(page)`; `Page`/`ReaderPage`/`JoinedReaderPage` have **no `equals` override** (identity equality — `source-api/.../model/Page.kt`), so a raw `ReaderPage` that got wrapped in a `JoinedReaderPage` is never found → `indexOf` returns -1 → `moveToPage` silently no-ops → pager stays at ViewPager position 0. R2L reverses `items` (`PagerViewerAdapter.kt` `newItems.reverse()`), so position 0 is the chapter's *end* / next transition. | `PagerViewer.kt:375-386`, `PagerViewer.kt:358` |
| 2. Gap between halves | `JoinedPagerPageHolder` lays out two `PagerPageHolder`s as `LayoutParams(0, MATCH_PARENT, 1f)` — each a `SubsamplingScaleImageView` that fits its portrait page into a 50%-width column, centered, so both pages letterbox *away* from the centerline. | `JoinedPagerPageHolder.kt:30-41` |
| 3. Independent per-column zoom | `setupZoomSynchronization()` bridges two separate `SubsamplingScaleImageView`s via `OnStateChangedListener` + `setScaleAndCenter` behind an `isSyncing` reentrancy flag; listeners are (re)attached inside an `addOnLayoutChangeListener`. Two coordinate spaces, two gesture detectors — a pinch on one half scales only that bitmap. | `JoinedPagerPageHolder.kt:43-95` |
| 4. Slider dead on joined spreads | `ChapterNavigator` slider → `onPageIndexChange` → `ReaderActivity.moveToPageIndex` → `currentChapter.pages?.getOrNull(index)` (raw `ReaderPage`) → `viewer.moveToPage` → same -1 no-op as bug 1. | `ReaderActivity.kt:985-989`, `ChapterNavigator.kt` (both `Slider` and `VerticalSlider` `onValueChange`) |

Bugs 1 and 4 are the **same defect** reached through two call paths. Bugs 2 and 3 are two symptoms of the **same design choice** (two independent image surfaces instead of one).

## Decision 1: Resolve pages inside joined spreads (bugs 1 & 4)

Add to `PagerViewerAdapter`:

```kotlin
fun indexOfPageOrJoined(page: ReaderPage): Int {
    val direct = items.indexOf(page)
    if (direct != -1) return direct
    return items.indexOfFirst { it is JoinedReaderPage && (it.firstPage === page || it.secondPage === page) }
}
```

Use it in:
- `PagerViewer.moveToPage(page)` — replace `adapter.items.indexOf(page)`.
- `PagerViewerAdapter.getItemPosition(view)` — when `view.item` is a `ReaderPage` not directly in `items`, check joined membership so `notifyDataSetChanged` keeps the joined holder attached across chapter appends instead of destroying/recreating it (which is part of why backward nav feels erratic).

**`onPageChange` / progress:** `PagerViewer.onPageChange(position)` reads `adapter.items.getOrNull(position)`. When that item is a `JoinedReaderPage`, it already **is** a `ReaderPage` (subclass) whose `chapter`/`index`/`number` come from `firstPage`, so `onReaderPageSelected` and `activity.onPageSelected` already report the first page of the spread — acceptable and unchanged. `getPageHolder(page)` filters `PagerPageHolder` instances and will not match the `JoinedPagerPageHolder`; that only feeds `onPageSelected(forward)` for pan-on-navigate, which the joined holder doesn't support anyway. Leave as-is for this change; note it in logging.

### Alternatives rejected

- **Give `JoinedReaderPage` an `equals` that matches either inner page.** Breaks the symmetry `equals` requires (a raw page would not equal the joined page back), poisons every other `indexOf`/`contains`/set membership on `items`, and `JoinedReaderPage(index = firstPage.index)` already collides with `firstPage` on any index-based equality. Too broad a blast radius.
- **Unwrap joined pages before every `moveToPage` caller.** Spreads the special-case across `ReaderActivity`, `PagerViewer`, and the adapter instead of containing it in one adapter method.
- **Store a back-reference `ReaderPage.joinedParent`.** Extra mutable state to keep in sync across re-grouping (`refreshAdapter`, `setChapters`); the scan is O(items) over a list that is at most a few dozen entries.

## Decision 2: One composited image surface for the spread (bugs 2 & 3)

Rewrite `JoinedPagerPageHolder` to extend `ReaderPageImageView` (like `PagerPageHolder`) — or to host exactly one — and:

1. **Load both halves.** Reuse the `PagerPageHolder.loadPageAndProcessStatus()` shape: launch `loader.loadPage(firstPage)` and `loader.loadPage(secondPage)`, then combine both `statusFlow`s; show the progress indicator until **both** are `READY`, surface an error if either ends `ERROR`.
2. **Composite.** New `ImageUtil.mergeHorizontal(left: BufferedSource, right: BufferedSource): BufferedSource` following the existing `splitAndMerge` pattern (`decodeBitmap` → `createBitmap(w1 + w2, max(h1, h2))` → `applyCanvas { drawBitmap(...) }` → `compress(JPEG, 100)` → `Buffer`). Caller passes left/right already ordered for the viewer direction — `groupPagesForDoublePage` already puts `firstPage`/`secondPage` in R2L-swapped order, so `firstPage` → left slot, `secondPage` → right slot regardless of direction.
   - If heights differ, align to top and pad the shorter side to the taller height (transparent/black), or scale the shorter to match — pick the simpler that avoids vertical drift between halves; document the choice inline.
3. **Display.** Feed the merged `BufferedSource` to `setImage(...)` with the same `Config` block `PagerPageHolder.setImage()` builds from `viewer.config` (scale type, crop borders, zoom start, `landscapeZoom`, `enablePinchToZoom`). One `SubsamplingScaleImageView`, one gesture surface → unified zoom/pan; fit-scaling one wide bitmap into the landscape screen puts the seam at the centerline with no gap.
4. **Delete** `setupZoomSynchronization()`, `isSyncing`, `syncZoom()`, the two `PagerPageHolder` children, and the `LinearLayout`/`HORIZONTAL` scaffolding.

### Edge cases

- **A half decodes as `isWide`:** `PagerPageHolder.process()` already sets `page.isWide = true` and calls `viewer.refreshAdapter()` when `joinDoublePages` is on; `refreshAdapter` re-runs `groupPagesForDoublePage`, which skips wide pages from pairing — the spread dissolves into standalone holders on its own. In the merged holder, guard compositing: if either source `ImageUtil.isWideImage(...)`, render just the non-wide half (or the first) and let the refresh re-group.
- **A half errors / stream is null:** show the single available half via the normal error/next-in-webview path; don't block the whole spread.
- **Animated (GIF) half:** `ImageUtil.isAnimatedAndSupported` — compositing a static frame would freeze the animation. Fall back to showing `firstPage` alone in this holder (rare; joined mode on animated manga is an edge of an edge).
- **`automaticBackground`:** compute once from the merged image (or from `firstPage`) rather than per-half.
- **Memory:** the merged bitmap is ~2× a page; `compress(JPEG, 100)` to a `Buffer` then hand to subsampling decode, matching how `splitInHalf`/`splitAndMerge` already round-trip. Recycle intermediate bitmaps.

### Alternative rejected

- **Keep two views, fix the gap with alignment + fix zoom with a better sync.** Still two gesture detectors, two fling/edge behaviors, two `onImageLoaded` timings; "zoom as one object" with independent `SubsamplingScaleImageView`s means continuously mirroring scale+center+animation state — exactly the fragile path being removed. A single image is less code and structurally correct.

## Logging (kept in, behind the existing debug guard)

Match the codebase's `logcat { ... }` (`tachiyomi.core.common.util.system.logcat`) usage already in `PagerViewer`/`PagerViewerAdapter`.

- `groupPagesForDoublePage`: inputs (`joinDoublePages`, `shiftDoublePages`, `isLandscape`, `isR2L`, page count) and a summary of the produced list (counts of `JoinedReaderPage` / `ReaderPage` / other).
- `PagerViewer.setChaptersInternal`: `requestedPage`, chosen page index, the resolved adapter position, and the item type at that position.
- `PagerViewer.moveToPage` / `indexOfPageOrJoined`: page number, whether it resolved directly, via a joined spread, or **not at all** (warn).
- `ReaderActivity.moveToPageIndex`: requested index, page number, resolved-or-not.
- `ChapterNavigator` `onPageIndexChange` (both sliders): old vs. new value.
- `JoinedPagerPageHolder`: both halves' page numbers + load states, composite start/finish, and every fallback branch taken.

## Files touched

- `app/.../ui/reader/viewer/pager/PagerViewerAdapter.kt` — `indexOfPageOrJoined`, `getItemPosition`, `groupPagesForDoublePage` logging.
- `app/.../ui/reader/viewer/pager/PagerViewer.kt` — `moveToPage` resolution + logging; `setChaptersInternal` logging.
- `app/.../ui/reader/viewer/pager/JoinedPagerPageHolder.kt` — full rewrite to single composited surface.
- `app/.../ui/reader/model/JoinedReaderPage.kt` — accessors if needed (child pages are already `val`).
- `core/common/.../util/system/ImageUtil.kt` — `mergeHorizontal` helper.
- `app/.../ui/reader/ReaderActivity.kt` — `moveToPageIndex` logging.
- `app/.../presentation/reader/components/ChapterNavigator.kt` — slider logging.
- `app/src/test/.../ui/reader/viewer/pager/PagerViewerAdapterTest.kt` — `indexOfPageOrJoined` cases.
