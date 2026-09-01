# Design: Manga Page Curl

## Constraint summary (verified against code)

| Constraint | Why it matters | File / line |
|---|---|---|
| The manga reader is legacy Views; the curl is Compose | The curl cannot be reached by configuring the existing pager — no `PageTransformer` exists anywhere in the reader | `viewer/pager/Pager.kt:18`, grep `PageTransformer` → 0 hits |
| `PageCurl` composes `content(N)` 3–4× at once, inside `key(current, forward.value, backward.value)` | The key includes live animating `Edge` values, so the content subtree is torn down and rebuilt every animation frame of a fold | `curl/PageCurl.kt:92`, `:124-188` |
| `detectCustomDragGestures` is strictly single-pointer and consumes aggressively | `awaitFirstDown(requireUnconsumed = false)`, tracks one `down.id`, `it.consume()` on every change; `tapGesture` consumes every down too. Pinch cannot coexist unmodified | `curl/DragCommonGesture.kt:103-131`, `curl/TapGesture.kt` |
| The curl has no RTL concept | Forward geometry is hard-anchored to the right edge in three places; grep for `LayoutDirection` in the curl package → 0 hits | `curl/PageCurlState.kt` (`setup`), `curl/DragCommonGesture.kt:137-141`, `curl/CurlDraw.kt` |
| The curl core is content-agnostic | `content: @Composable (Int) -> Unit`; the package imports nothing from `eu.kanade` outside itself | `curl/PageCurl.kt:38-47` |
| `Viewer` is a 5-method interface with only 4 external `PagerViewer` type-checks | A new viewer slots in without touching the legacy path | `viewer/Viewer.kt`, `ReaderActivity.kt:550,555,876`, `ReaderAppBars.kt:290` |
| Double-page pairing is already pure and unit-tested | `groupPagesForDoublePage` and `indexOfPageOrJoined` are top-level `internal` functions, extracted for testability | `viewer/pager/PagerViewerAdapter.kt`, `PagerViewerAdapterTest.kt` |
| Colour filter, grayscale/invert, sharpening, ICC apply globally to the container | A `ComposeView` in `viewerContainer` inherits them with no extra work | `ReaderActivity.kt` (`setLayerPaint`, `ReaderContentOverlay`, `ReaderRenderEffects`) |

## Decision 1: A new `Viewer` implementation, not a transformer on the legacy pager

`MangaCurlViewer : Viewer` hosts a `ComposeView` and is selected when the curl preference is on and the reading mode is a pager type. `PagerViewer` is untouched.

```
MangaCurlViewer (Viewer)                     ← the 5-method seam
  └── ComposeView in binding.viewerContainer ← inherits colour filter / grayscale / ICC
       └── MangaPageCurlRenderer             ← ported from Spread/PageTurnPageRenderer
            └── PageCurl (shared curl pkg)   ← reused verbatim
                 └── content(index):
                      ├── settled + focused → AndroidView(ReaderPageImageView)  ← live zoom
                      └── otherwise         → snapshot / GraphicsLayer          ← cheap
```

Viewer selection currently lives in `ReadingMode.toViewer(preference, activity)`, which switches on reading mode alone. The curl preference is orthogonal to reading direction, so selection must consult both: an L2R/R2L/Vertical pager mode with the curl on yields `MangaCurlViewer` carrying that direction; with the curl off it yields today's `L2RPagerViewer`/`R2LPagerViewer`/`VerticalPagerViewer`. Webtoon modes ignore the preference entirely.

The four external type-check sites must learn about the new viewer: auto-scroll start/stop and the touch cooldown in `ReaderActivity` (`:550`, `:555`, `:876`), and `isRtl`/`isPagerViewer` in `ReaderAppBars` (`:148`, `:290`). Introducing a shared supertype or interface capability for "paged viewer with auto-scroll" is preferable to widening each `when` with a second branch.

### Alternatives rejected
- **A `ViewPager.PageTransformer` on the existing pager.** A transformer only transforms already-laid-out child Views; it cannot produce a curl with a real back-of-page showing the neighbouring page's content, which is the entire visual point. It would also mean writing a second curl implementation in Canvas/OpenGL rather than reusing 1806 lines that already work.
- **Porting the manga reader wholesale to Compose.** Far larger blast radius than the feature justifies, and it would put the currently-working reader at risk behind a toggle most users leave off.

## Decision 2: Hybrid live-view / snapshot swap

Exactly one page — the current, settled one — is a live `ReaderPageImageView` inside an `AndroidView`. Every other composed index (`current ± 1`, the flap backs, and the alpha-0 pass the spread path adds) draws from a snapshot. On drag start the current page swaps to its snapshot; when the fold settles, the live view is restored at the new index.

This is what keeps `AndroidView` out of the `key()` teardown path. It is the same shape the novel reader already ships: interactive text is rendered as a **separate non-curl overlay, gated on `pageCurlState.progress == 0f`**, while everything inside the curl gets `touchHandlingEnabled = false`.

The snapshot mechanism itself is already generic. `NovelPageTurnSnapshotRenderer`'s `preferCachedBitmap = false` branch (~25 lines) records content into a `GraphicsLayer` and replays it via `drawLayer`; its `snapshotKey`/`snapshotCache` parameters are ignored on that path, which is the only path either curl renderer uses. That branch is extracted; `NovelPageTurnSnapshotCache` (38 text-preference fields) is not.

Keeping the live view means tiled decoding, pinch, pan, double-tap, `landscapeZoom` and `cropBorders` all continue to work exactly as they do today, because it is the same widget with the same `Config`.

### Alternatives rejected
- **A Compose-native image path (Coil `AsyncImage` + custom gesture handling).** Sidesteps the `AndroidView` interop concerns entirely, but loses `SubsamplingScaleImageView`'s tiled decoding — a real OOM risk on large manga pages — and would require reimplementing zoom, pan, `cropBorders` and `landscapeZoom` from scratch, with new bugs in each.
- **Keeping the live view composed at all times.** The `key()` teardown recreates it every animation frame; zoom state would be lost on every turn and performance would collapse.

## Decision 3: Zoom gate via the existing drag-enable flags

`ReaderPageImageView` already exposes `onScaleChanged` (`:102`, `:122`) and the fit test `scale == minScale` (`:159`). That signal feeds `PageCurlConfig.dragForwardEnabled` / `dragBackwardEnabled`.

While the page is zoomed in, drag pans the image and the curl is inert; when the user returns to fit, the curl re-enables. Tap zones continue to turn pages at any zoom level, matching how tap navigation behaves today.

This is precisely the mitigation the novel reader already uses for text selection (`dragForwardEnabled = !selectionActive && ...`). Note the limitation that comes with it: the flags gate `isEnabled()` inside `DragConfig` and the `enabledForward`/`enabledBackward` arguments, but the `pointerInput` block is still installed and still consumes the down. If that proves to swallow pan gestures in practice, the fallback is a pointer-count guard in `detectCustomDragGestures` that bails out of `awaitEachGesture` when a second pointer arrives.

### Alternatives rejected
- **Curl active on edge zones even while zoomed.** `StartEndDragInteraction.forward.start` makes this expressible, but it creates ambiguous gestures near the edges and collides with `navigateToPan`.
- **Curl only once pan reaches the edge** (mirroring `navigateToPan`). The most natural for heavy zoom users, but the most complex state machine of the three, and it interacts badly with the snapshot swap. Worth revisiting after the simpler gate ships.

## Decision 4: Right-to-left by mirroring

RTL is expressed with the approach `SpreadColumnCurl(mirrored = true)` already validated on-device: `graphicsLayer(scaleX = -1f)` on the surface, with the content un-mirrored per-draw.

Three caveats, all already discovered and documented in the novel code:
- Gestures mirror too, so a physical right-to-left swipe drives the library's *forward* mechanism. Do **not** additionally swap the interaction `Rect`s.
- Any `GraphicsLayer` recorded *inside* the mirror is captured in flipped space. The un-mirror must be applied **outside** the recording node, as `SpreadColumnCurl` does with `mirroredContentModifier`, or only one turn direction renders correctly.
- `onMirroredSurface = true` must be passed to `PageCurl` when external back layers are in play, or the back page reads backwards.

### Alternatives rejected
- **Adding real RTL to the vendored library** (threading `LayoutDirection` through `PageCurlState.setup`, `NewEdgeCreator.createVectors` and `CurlDraw`). Cleanest long-term, but it forks four files away from upstream 1.5.1, which the vendored headers explicitly preserve diffability against. Reconsider only if mirroring proves insufficient.

## Decision 5: Reuse the double-page pairing verbatim

`groupPagesForDoublePage(pages, joinDoublePages, shiftDoublePages, isLandscape, isR2L)` and `indexOfPageOrJoined(items, page)` are top-level `internal` functions with existing test coverage. The curl viewer calls them to build its item list rather than reimplementing pairing.

This preserves, for free, every rule the current spec already pins down: wide pages are never paired, `shiftDoublePages` gives page 0 its own slot, and the right-to-left swap is applied at build time so `firstPage` is *always* the left slot and `secondPage` the right — meaning the two spread columns need no direction check of their own.

Each `JoinedReaderPage`'s two halves feed the two-column spread renderer directly, one half per column. Note this differs from `JoinedPagerPageHolder`, which composites both halves into one bitmap via `ImageUtil.mergeHorizontal` (a full-resolution allocation plus a lossy JPEG re-encode); the curl needs the halves *separate*, one per fold surface, so it skips that merge entirely.

Manga applies no minimum-width floor. The 600dp floor is novel-only and is documented as such in `.specter/specs/reading-novel/spec.md` — manga pages may join at any landscape width.

### Edge cases
- **A half fails to load or decodes as wide:** `groupPagesForDoublePage` re-runs on `refreshAdapter()` once `isWide` is set, dissolving the spread into standalone slots. The curl viewer must trigger the same regrouping so a wide page never renders as half a fold.
- **Trailing odd page:** gets its own slot with an empty facing column, as `resolveSpreadSlotCount` already does for novels.
- **Chapter boundaries:** the ported virtual-slot helpers add up to two synthetic slots (previous/next chapter) and only fire navigation once the fold has settled (`abs(progress) <= 0.001f`), with a one-shot guard against double-firing.
- **Orientation change:** rotating out of landscape must collapse spreads back to single pages; `PageCurlState.setup` rebuilds its internal state when constraints change, so the fold resets rather than animating across a resize.
- **Toggle flipped mid-session:** changing the preference swaps the viewer through the existing `updateViewer()` path, which destroys the previous viewer and preserves the current page.

## Files touched

New:
- `app/.../presentation/reader/curl/` — the curl package and `SpreadCurlBackContentLayers.kt`, moved from the novel package; plus the extracted generic snapshot renderer and the pure slot/virtual-index helpers.
- `app/.../ui/reader/viewer/curl/MangaCurlViewer.kt` — the `Viewer` implementation.
- `app/.../presentation/reader/curl/MangaPageCurlRenderer.kt` (and a spread variant) — the manga-side renderers.

Modified:
- `app/.../ui/reader/setting/ReaderPreferences.kt` — the new boolean preference.
- `app/.../presentation/reader/settings/GeneralSettingsPage.kt` — the `AuroraToggleRow`.
- `app/.../ui/reader/viewer/pager/PagerConfig.kt` — register the preference.
- `app/.../ui/reader/setting/ReadingMode.kt` — viewer selection consulting the preference.
- `app/.../ui/reader/ReaderActivity.kt` — auto-scroll and cooldown type-switches.
- `app/.../presentation/reader/appbars/ReaderAppBars.kt` — `isRtl` / `isPagerViewer`.
- `app/.../presentation/reader/novel/*` — import updates only, from the package move.
- `i18n-aniyomi/.../base/strings.xml` and `.../pt-rBR/strings.xml` — the new label.
