# Verification: fix-manga-double-page-reader-bugs

## Verdict
PASS

## CRITICAL
_None._

## WARNING
- [correctness] `tasks.md` 3.2 specifies `./gradlew :app:testReleaseUnitTest`, but this project has no release unit-test variant. The equivalent `:app:testDebugUnitTest --tests "*PagerViewerAdapterTest*"` was run instead (11 tests pass, including the 4 new `indexOfPageOrJoined` cases). `ImageUtilTest` also passes; its bitmap-dependent cases self-skip without a native decoder, so `mergeHorizontal` has no JVM-level unit coverage — the same gap that already exists for `splitInHalf`/`splitAndMerge`.
- [coherence] `design.md` proposed `indexOfPageOrJoined` as a single `PagerViewerAdapter` method. It shipped as a top-level `indexOfPageOrJoined(items, page)` with the public method delegating to it, because the adapter's `init` touches Injekt (`createReaderThemeContext`) and cannot be constructed in a plain JVM unit test. Net behavior is identical and the public method the spec references still exists; the split is a testability accommodation, noted here so the design/impl divergence is on record.

## SUGGESTION
- [correctness] `JoinedPagerPageHolder.setImage()` calls `maybeRefreshAdapter()` from inside `withIOContext` when a half decodes as wide. `refreshAdapter()` is itself dispatched onto the UI thread via `viewer.activity.runOnUiThread`, so this is safe, but a future reader might expect the call at the UI-context boundary alongside the other post-composite work. Consider hoisting the wide-page detection out of the IO block if it is ever refactored.
- [completeness] The `onPageChange` / `getPageHolder` interaction with `JoinedPagerPageHolder` was left as-is per `design.md` (the joined holder is a `ReaderPageImageView`, not a `PagerPageHolder`, so `getPageHolder` will not match it and pan-on-navigate is unsupported for spreads). This is documented intent, not a defect; flagged only so archive readers know it was a conscious scope boundary.

## Notes
- **Completeness:** every Scope item in `proposal.md` has landing work — navigation resolution (bugs 1 & 4) via `indexOfPageOrJoined` used by `moveToPage` and `getItemPosition`; single composited surface (bugs 2 & 3) via the `JoinedPagerPageHolder` rewrite and `ImageUtil.mergeHorizontal`; debug logging (`DoublePageDebugLog`, `"DoublePage"` tag) across `groupPagesForDoublePage`, `setChaptersInternal`, `moveToPage`/`indexOfPageOrJoined`, `ReaderActivity.moveToPageIndex`, and both `ChapterNavigator` sliders. Out-of-scope items (non-joined path, webtoon, `dualPageSplit`, `shiftDoublePages` logic, slider redesign) were not touched.
- **Correctness:** all delta scenarios were exercised on-device by the user on One Piece ch. 1188 (landscape, paginated, R2L, join enabled, both `shiftDoublePages` states): chapter open lands on the page-1 spread; backward nav reaches the 1188->1187 transition without next-chapter hops; the slider moves to and tracks joined spreads; spreads join at the centerline with no gap; pinch/pan/double-tap transform the whole spread as one image; and the portrait / genuine-wide-page / L2R regression checks pass. The `indexOfPageOrJoined` -1 "not found" path is covered by unit test.
- **Coherence:** the code follows `design.md` — `indexOfPageOrJoined` scans joined membership by identity (`===`) rather than an `equals` override or a back-reference; `JoinedPagerPageHolder` extends `ReaderPageImageView` and mirrors `PagerPageHolder`'s load/status/setImage shape with one gesture surface; `setupZoomSynchronization`/`syncZoom`/`isSyncing`/the two child holders/the `LinearLayout` scaffolding are deleted; edge cases (wide half -> `isWide` + `refreshAdapter`, errored/null-stream half -> show the other, animated half -> `firstPage` alone, `automaticBackground` computed once) are all handled.
- Build gate: `./gradlew spotlessCheck` clean; `./gradlew :app:assembleRelease` succeeds (one unrelated G1 GC JVM segfault in the Gradle daemon on the first attempt, passed on retry); debug build installed and verified on device (versionCode 300, `0.70.2`).
