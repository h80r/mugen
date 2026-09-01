# Verification: add-manga-page-curl

## Verdict
PASS (with deferrals — see WARNING)

## CRITICAL
None.

## WARNING
- [completeness] Task 4.7 (orientation change) was **not implemented**. It is checked off to close the change and tracked in the backlog as `manga-curl-orientation-change`. The grouping logic is orientation-aware — `applyChapters` passes `activity.resources.configuration.orientation` to `buildMangaCurlItems`, and `spread` is gated on `ORIENTATION_LANDSCAPE` — but nothing re-runs it on rotation, because `ReaderActivity` declares `android:configChanges="orientation|screenLayout|screenSize|..."` and is therefore not recreated. Rotating mid-chapter leaves the previous orientation's layout on screen.
- [completeness] Task 5.2 (unit tests for the extracted pure helpers: virtual page count/index, progress page index, boundary chapter target, spread slot math) was not done. These helpers were moved into the shared curl package by group 1 and are exercised only indirectly, through the two renderers.
- [coherence] The debug instrumentation added during this change is still in the code: `SpreadCurlDiagnostics` plus ~92 call sites across 7 files. Part of it leaked into the vendored `pagecurl` API (`PageCurl(curlDebugName)`, `Modifier.drawCurl(debugName)`, `ExternalFold.debugName`, `ReaderPageImageView.debugPageView`). Kept at the user's explicit request during debugging; tracked in the backlog as `remove-spread-curl-diagnostics`.

## SUGGESTION
- [correctness] Task 5.3 names `./gradlew :app:testReleaseUnitTest`, which does not exist in this project — there are no product flavors and the release variant has no unit-test task. The real task is `:app:testDebugUnitTest`. Worth correcting in future task templates.
- [completeness] `leftGesturesEnabled` / `rightGesturesEnabled` in `MangaSpreadCurlRenderer.kt` are computed and never read; the real gating is `overlayCovering`. Confirmed via `git stash` to predate this change, so left untouched here and folded into the instrumentation-removal backlog item.

## Notes
- Unit tests: **3177 completed, 3 failed, 4 skipped** (`:app:testDebugUnitTest`). The three failures — `NovelReaderScreenModelTest`, `NovelReaderCacheCoordinatorTest`, `NovelSelectedTextTranslationScreenModelTest` — were reproduced on `develop` in a clean worktree and fail identically there, so they are pre-existing and unrelated to this change. This branch touches novel files only through the group 1 curl extraction, and none of the three failing subjects.
- `:app:compileDebugKotlin`, `./gradlew spotlessCheck` and `:app:assembleDebug` are all clean (task 5.5).
- `PagerViewerAdapterTest` passes unchanged (task 5.1) — the curl viewer reuses `groupPagesForDoublePage` and `indexOfPageOrJoined`, so the legacy pager's coverage still protects both.
- Manual verification on a physical device (tasks 4.8, 4.9) covered the landscape double-page flow end to end, over many iterations driven by screen recordings and instrumented logs. Confirmed working: only the turning leaf folds; the flap back shows the real neighbouring half, upright; R2L advances in the correct direction with unmirrored artwork; the two halves meet flush at the spine with no gutter; the fold surface is the artwork rather than the column, so the curl, its shadow and its drag zones all sit on the page and not on the letterbox; drag and tap turn the same way and animate the same way, both bulging from the grabbed edge; and a turn settles without flicker.
- Bugs found and fixed during that verification, each diagnosed from instrumented measurements rather than inspection: spine alignment (the rule keyed off the wrong flag, opening a 1148px black band in R2L); the drag's coordinate frame (measured from the box's outer edge, which is the spine on only one half); the fold axis (driven against PageCurl's own `progress = 1 - centerX/width`, so the curl undid itself as the finger advanced); the flap's back-page flip (per-column, not constant); a settle flicker with three distinct causes (stale overlay, readiness reported at `setImage` instead of `onImageLoaded`, lazily-decoded column bitmaps); a one-frame gap-and-black-half from `halfAspectRatio` momentarily reading null; a blank column from folds landing exactly on `drawCurl`'s parked fast path (both the drag and tap routes); and a tapped backward turn using the wrong column and flap, which bulged the wrong way and showed the current spread behind the returning page.
- One pre-existing crash was fixed along the way: `landscapeZoom`'s 500ms `postDelayed` dereferenced a recycled `SubsamplingScaleImageView` via `!!`, which the curl's faster page turns exposed as an NPE.
