# Proposal: Fix Manga Double-Page ("Unir Páginas Duplas") Reader Bugs

## Intent
The pager reader's custom "unir páginas duplas" feature (join two consecutive non-wide pages into one landscape spread, added in commit `90cb39787`) has four distinct bugs, all reproducible in landscape + R2L + paginated mode with the option enabled (e.g. One Piece ch. 1188):
1. Opening an unread chapter lands on the end-of-chapter transition instead of page 1, and navigating backward from there hops through neighboring chapters before the intended chapter's first page is reachable.
2. Joined spreads render with a large empty gap between the two page halves instead of a seamless join.
3. Pinch-zoom on a joined spread zooms each half independently around its own center instead of zooming the spread as one object.
4. The page-navigator slider does nothing when the current item is a joined spread — dragging the thumb never changes the page.

## Scope
- **In:** joined-spread navigation resolution (fixes 1 & 4, shared root cause), and joined-spread rendering as a single zoomable image surface (fixes 2 & 3). Diagnostic logging (behind the existing debug guard) across all four flows, kept in the codebase.
- **Out:** the non-joined pager path, webtoon/vertical viewers, `dualPageSplit` (the unrelated upstream "split a wide page in two" feature), the `shiftDoublePages` offset logic (only its interaction with the fixes is verified, not changed), and any redesign of the navigator slider component itself.

## Approach
1. **Navigation (1 & 4):** `Page`/`ReaderPage`/`JoinedReaderPage` use identity equality, so `adapter.items.indexOf(rawPage)` returns -1 whenever the target page is wrapped inside a `JoinedReaderPage`, silently no-opping `moveToPage`. Add `PagerViewerAdapter.indexOfPageOrJoined(page)` that falls back to matching a `JoinedReaderPage` by `firstPage === page || secondPage === page`, and use it in `moveToPage` and `getItemPosition`. This fixes chapter-open positioning, `moveToPageIndex`, the slider, and preprocessed-insert moves at once.
2. **Rendering (2 & 3):** Replace `JoinedPagerPageHolder`'s two independent `PagerPageHolder` children (each letterboxed in its own 50% column, each with its own gesture surface and a fragile zoom-sync bridge) with a single `ReaderPageImageView` fed one horizontally-composited bitmap built from both halves (respecting R2L order). One image → seamless centerline join; one gesture surface → unified zoom; existing image config applies unchanged; `setupZoomSynchronization`/`isSyncing` are deleted. Reuse the `ImageUtil` `applyCanvas`/`Buffer`/`compress` pattern (`splitInHalf`, `splitAndMerge`) for the merge helper, and the `PagerPageHolder.loadPageAndProcessStatus` status-flow pattern to wait for both halves. Handle: one half errors/missing, a half that decodes as `isWide` (already re-groups via `refreshAdapter()`), and animated images (fall back).
