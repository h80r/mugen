# Proposal: Bring the Novel Reader's Page Curl to the Manga Reader

## Intent
The novel reader has a polished page-curl page-turn animation, including a two-page landscape spread whose fold is anchored at the spine rather than spanning the whole spread. The manga reader has no equivalent: its only transition preference is `pageTransitions()` (key `pref_enable_transitions_key`), a boolean that maps to `ViewPager.setCurrentItem(pos, smoothScroll)`. A codebase-wide grep for `PageTransformer`/`setPageTransformer` returns zero hits — there is no animation infrastructure in the manga pager to extend.

The gap is architectural, not cosmetic. The manga reader is built on legacy Views (`Pager` → `DirectionalViewPager` → `androidx.viewpager.widget.ViewPager`), while the entire curl implementation is Compose. No amount of configuration bridges those two; the curl can only reach manga through a Compose viewer.

The favorable finding is that the curl is not novel-specific at all. The vendored `curl/` package (12 files, ~1806 lines, forked from `io.github.oleksandrbalan:pagecurl 1.5.1`) exposes `content: @Composable (Int) -> Unit` and imports nothing from `eu.kanade` outside its own package — it knows only page indices, never text. What is novel-specific is the ~40-of-50 renderer parameters carrying fonts, TTS and text selection, not the fold itself.

## Scope
- **In:** a new reader preference toggling between the current page transition (off) and page curl (on); a new `MangaCurlViewer` implementing the existing `Viewer` interface and hosting a Compose curl renderer; hybrid zoom so pinch/pan/double-tap survive under the curl; two-page spread curl reusing the existing `joinDoublePages` conditions; right-to-left fold support; and full behavioral parity under the toggle — tap zones, chapter transitions, auto-scroll, volume keys, colour filter and dual page split.
- **In:** extracting the curl package and the generic parts of the page-turn machinery into a reader-shared location so both readers consume one implementation rather than a fork.
- **Out:** the webtoon and continuous-vertical viewers — the curl is a paged metaphor and does not apply to continuous scrolling.
- **Out:** the novel reader's own behaviour. It keeps its current renderers; only import paths change when the curl package moves.
- **Out:** any change to the double-page pairing rules themselves. `groupPagesForDoublePage` (wide-page exclusion, `shiftDoublePages` offset, right-to-left swap) is reused verbatim, not reimplemented.
- **Out:** the legacy `PagerViewer` path, which stays untouched and remains exactly what the toggle's off state selects.
- **Out:** the pre-existing label/preference mismatch at `ReadingModePage.kt:253-261` (the `pref_dual_page_split` row is bound to `dualPageInvertPaged()`). Noted during research; a separate defect, not this change's concern.

## Approach
A new `MangaCurlViewer : Viewer` hosting a `ComposeView`, selected when the curl preference is on and the reading mode is a pager type. `PagerViewer` is left alone, so the off state carries zero regression risk. `Viewer` is a five-method interface and only four sites outside the pager package type-check `PagerViewer`, so the new viewer slots in beside `PagerViewer` and `WebtoonViewer` without disturbing the legacy path. Because colour filter, grayscale/invert, sharpening and ICC profile all apply globally to `binding.viewerContainer`, a `ComposeView` hosted there inherits them for free.

Two constraints drive the design, both verified in code. First, `PageCurl` composes `content(N)` three to four times simultaneously and wraps it in `key(current, forward.value, backward.value)` — keyed on live animation values, so that subtree is torn down and rebuilt on *every animation frame* of a fold; a `SubsamplingScaleImageView` placed there would be recreated per frame and lose its zoom state. Second, `detectCustomDragGestures` is strictly single-pointer and consumes aggressively, so pinch cannot coexist with it unmodified.

Both are solved with patterns the codebase already proves. The reader renders exactly one live `ReaderPageImageView` — the current, settled page — and draws every other composed index from a snapshot, mirroring how the novel reader renders its interactive text surface as a separate non-curl overlay only when `progress == 0f`. The curl's drag is then gated on zoom state via `dragForwardEnabled`/`dragBackwardEnabled`, exactly as the novel reader already gates it on text selection. Right-to-left reuses the mirroring approach `SpreadColumnCurl(mirrored = true)` already validated on-device.

See `design.md` for the constraint analysis with file references, the five decisions, and the alternatives rejected.
