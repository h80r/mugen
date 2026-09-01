@file:OptIn(ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.curl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single-page manga page-curl renderer, ported from the novel reader's `PageTurnPageRenderer` with
 * every text/TTS/selection/atmosphere parameter dropped. Keeps the curl [PageCurlConfig], the shared
 * virtual-slot math and the chapter-boundary handling.
 *
 * The renderer's index space is virtual pages: `[previous chapter?] + [slotCount] + [next chapter?]`.
 * With `spreadColumns = 1` (this group) a slot is one manga page; group 4 raises the column count for
 * joined double-page spreads.
 *
 * @param slotCount number of real content slots (single-column: one manga page/transition each).
 * @param currentSlot the settled content slot to display (already resolved by the viewer).
 * @param onCurrentSlotChange reports the settled real slot index back to the viewer.
 * @param slotContent draws slot `i`'s content; boundary slots are handled by the renderer itself.
 */
@Composable
fun MangaPageCurlRenderer(
    slotCount: Int,
    currentSlot: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    config: MangaPageCurlRendererConfig,
    chapterId: Long,
    // Resolves a tap at (xFraction, yFraction) — 0..1 of the viewport — through the reader's
    // ViewerNavigation (mode + inversion + custom zones) and performs the resulting action.
    // Returns true if the tap was consumed.
    onNavigationTap: (xFraction: Float, yFraction: Float) -> Boolean,
    onLongTap: () -> Unit,
    onCurrentSlotChange: (Int) -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    // Reports whether a fold is settled (`true`) or in flight (`false`), so the viewer can defer
    // re-listing mid-gesture — the curl analogue of `PagerViewer.isIdle`.
    onIdleChanged: (Boolean) -> Unit = {},
    // Gate the drag gesture on caller-owned state (e.g. the current page being zoomed in): when
    // this returns false, dragForward/BackwardEnabled are forced off so a drag pans instead.
    dragEnabled: () -> Boolean = { true },
    modifier: Modifier = Modifier,
    // (forward, token): each token bump animates one programmatic page turn — drives volume keys,
    // auto-scroll and pointer scroll through the same fold the drag gesture produces.
    programmaticTurn: Pair<Boolean, Int>? = null,
    // (slot, token): each token bump snaps straight to that real content slot — the chapter
    // progress slider seek.
    seekRequest: Pair<Int, Int>? = null,
    // Live-view overlay for the settled page, drawn ON TOP of the curl and OUTSIDE its per-frame
    // content teardown, so an AndroidView(ReaderPageImageView) keeps its zoom/pan state across
    // folds. It is composed only when [foldSettled].
    //
    // The overlay owns the whole touch stream (Compose does not pass unhandled pointer events
    // between a gesture layer and an AndroidView sibling in either direction), so it is handed the
    // [ExternalFoldDriver] that drives the fold from raw view coordinates. The curl's own drag and
    // tap gestures stay off whenever an overlay is present.
    //
    // [settledSlot] is the real content slot at rest; [gesturesEnabled] says whether the overlay may
    // accept new gestures — a fold its own dispatcher is driving deliberately keeps it true.
    liveOverlay: (
        @Composable (settledSlot: Int, gesturesEnabled: Boolean, foldDriver: ExternalFoldDriver) -> Unit
    )? = null,
    slotContent: @Composable (slotIndex: Int) -> Unit,
) {
    val safeSlotCount = slotCount.coerceAtLeast(1)
    val virtualPageCount = remember(safeSlotCount, hasPreviousChapter, hasNextChapter) {
        resolvePageTurnRendererVirtualPageCount(
            contentPageCount = safeSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
    }
    val settledSlot = currentSlot.coerceIn(0, safeSlotCount - 1)
    val initialVirtualPage = remember(settledSlot, hasPreviousChapter) {
        resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = settledSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
    }

    val pageCurlState = rememberPageCurlState(initialCurrent = initialVirtualPage)
    val currentPage = pageCurlState.current.coerceIn(0, virtualPageCount - 1)

    val dragInteraction = remember(config) { config.dragInteraction() }
    val tapInteraction = remember(config) { config.tapInteraction() }

    val latestNavigationTap by rememberUpdatedState(onNavigationTap)
    val latestLongTap by rememberUpdatedState(onLongTap)
    val latestCurrentSlotChange by rememberUpdatedState(onCurrentSlotChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestIdleChanged by rememberUpdatedState(onIdleChanged)
    val latestDragEnabled by rememberUpdatedState(dragEnabled)
    val latestConfig by rememberUpdatedState(config)

    val pageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            // Every tap goes through the reader's ViewerNavigation so all nav modes, inversion and
            // custom tap zones behave exactly as with the legacy pager. tapForward/BackwardEnabled
            // are held off (see SideEffect) so the library's built-in edge taps never compete.
            var x = if (size.width > 0) (offset.x / size.width.toFloat()).coerceIn(0f, 1f) else 0.5f
            val y = if (size.height > 0) (offset.y / size.height.toFloat()).coerceIn(0f, 1f) else 0.5f
            // The surface is drawn flipped for R2L (see curlModifier); pointer coords arrive in the
            // pre-flip space, so undo the flip before handing the tap to screen-space nav zones.
            if (latestConfig.mirrored) x = 1f - x
            latestNavigationTap(x, y)
        },
    )

    SideEffect {
        pageCurlConfig.backPageColor = config.backPageColor
        pageCurlConfig.backPageContentAlpha = 0f
        // Single-page mode uses a solid back (no "next page on the leaf's reverse" metaphor). When
        // solidBackPage is false (the two-column spread, group 4), PageCurl records `content(current±1)`
        // into a GraphicsLayer on that page's own draw pass and paints it on the flap back.
        pageCurlConfig.independentBackPageEnabled = !config.solidBackPage
        pageCurlConfig.shadowColor = config.shadowColor
        pageCurlConfig.shadowAlpha = config.preset.shadowAlpha
        pageCurlConfig.shadowRadius = config.shadowRadiusDp.dp
        pageCurlConfig.shadowOffset = DpOffset(config.shadowOffsetXDp.dp, 0.dp)
        // With a live overlay the dispatcher inside it owns the whole touch stream and drives the
        // fold through ExternalFoldDriver, so every built-in gesture stays off — two owners of the
        // same fold would fight. Without one, the library's own drag handles it.
        val dragOn = latestDragEnabled() && liveOverlay == null
        pageCurlConfig.dragBackwardEnabled = dragOn && currentPage > 0
        pageCurlConfig.dragForwardEnabled = dragOn && currentPage < virtualPageCount - 1
        // All taps route through onCustomTap → ViewerNavigation; the library's own edge taps stay off.
        pageCurlConfig.tapBackwardEnabled = false
        pageCurlConfig.tapForwardEnabled = false
        pageCurlConfig.tapCustomEnabled = liveOverlay == null
        pageCurlConfig.dragInteraction = dragInteraction
        pageCurlConfig.tapInteraction = tapInteraction
    }

    // Reset the curl state whenever the chapter (or its slot geometry) changes, even if the numeric
    // values happen to coincide with the previous chapter's.
    LaunchedEffect(chapterId, settledSlot, safeSlotCount, hasPreviousChapter) {
        val target = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = settledSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        if (pageCurlState.current != target) {
            pageCurlState.snapTo(target)
        }
    }

    // One-shot guard: reset on chapter / geometry change so stale boundary state can't re-fire a
    // chapter switch before Compose finishes rebuilding.
    var consumedBoundary by remember(chapterId, safeSlotCount, hasPreviousChapter, hasNextChapter) {
        mutableStateOf<PageTurnBoundaryTarget?>(null)
    }
    LaunchedEffect(pageCurlState, safeSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { pageCurlState.current.coerceIn(0, virtualPageCount - 1) to pageCurlState.progress }
            .distinctUntilChanged()
            .collect { (targetVirtualPage, progress) ->
                when (
                    resolvePageTurnRendererSettledBoundaryChapterTarget(
                        currentPage = targetVirtualPage,
                        progress = progress,
                        contentPageCount = safeSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    PageTurnBoundaryTarget.PREVIOUS -> if (consumedBoundary != PageTurnBoundaryTarget.PREVIOUS) {
                        consumedBoundary = PageTurnBoundaryTarget.PREVIOUS
                        latestOpenPreviousChapter()
                    }
                    PageTurnBoundaryTarget.NEXT -> if (consumedBoundary != PageTurnBoundaryTarget.NEXT) {
                        consumedBoundary = PageTurnBoundaryTarget.NEXT
                        latestOpenNextChapter()
                    }
                    PageTurnBoundaryTarget.NONE -> Unit
                }
            }
    }

    // Report the settled real slot back to the viewer, once no fold is in flight and we're not
    // parked on a synthetic boundary slot.
    LaunchedEffect(pageCurlState, safeSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { pageCurlState.current.coerceIn(0, virtualPageCount - 1) }
            .distinctUntilChanged()
            .collect { targetVirtualPage ->
                val boundary = resolvePageTurnRendererBoundaryChapterTarget(
                    currentPage = targetVirtualPage,
                    contentPageCount = safeSlotCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
                if (boundary == PageTurnBoundaryTarget.NONE) {
                    latestCurrentSlotChange(
                        resolvePageTurnRendererProgressPageIndex(
                            currentPage = targetVirtualPage,
                            contentPageCount = safeSlotCount,
                            hasPreviousChapter = hasPreviousChapter,
                        ),
                    )
                }
            }
    }

    // Idle reporting: a fold is "in flight" whenever progress is non-zero.
    LaunchedEffect(pageCurlState) {
        snapshotFlow { pageCurlState.progress == 0f }
            .distinctUntilChanged()
            .collect { latestIdleChanged(it) }
    }

    // Slider seek: snap straight to the requested real content slot.
    LaunchedEffect(seekRequest?.second) {
        val (slot, token) = seekRequest ?: return@LaunchedEffect
        if (token == 0) return@LaunchedEffect
        val target = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = slot.coerceIn(0, safeSlotCount - 1),
            hasPreviousChapter = hasPreviousChapter,
        )
        if (pageCurlState.current != target) pageCurlState.snapTo(target)
    }

    // Programmatic turns (volume keys / auto-scroll / pointer scroll): one fold per token bump.
    LaunchedEffect(programmaticTurn?.second) {
        val (forward, token) = programmaticTurn ?: return@LaunchedEffect
        if (token == 0) return@LaunchedEffect
        pageCurlState.animateTurn(latestConfig, forward)
    }

    // With a live overlay, its dispatcher detects the long press itself (it owns the stream).
    val longPressModifier = if (liveOverlay != null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Fires the long-press without consuming, so a short tap or a drag still reaches the
                // curl's own gesture handlers underneath.
                awaitLongPressOrCancellation(down.id)?.let { latestLongTap() }
            }
        }
    }

    val foldScope = rememberCoroutineScope()
    val foldDriver = remember(pageCurlState, foldScope) { ExternalFoldDriver(pageCurlState, foldScope) }

    // Right-to-left: flip the whole surface so the fold anchors at the right edge and a physical
    // left→right swipe drives the library's forward mechanism. Page artwork is un-flipped per draw
    // (mirroredContentModifier), and the drag/tap Rects are deliberately NOT swapped — the flip
    // already inverts gestures. Matches SpreadColumnCurl's approach for the spread (group 4.5).
    val curlModifier = if (config.mirrored) {
        Modifier.fillMaxSize().graphicsLayer(
            scaleX = -1f,
        )
    } else {
        Modifier.fillMaxSize()
    }
    val mirroredContentModifier = if (config.mirrored) {
        Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
    } else {
        Modifier.fillMaxSize()
    }

    val liveSettledSlot = resolvePageTurnRendererProgressPageIndex(
        currentPage = currentPage,
        contentPageCount = safeSlotCount,
        hasPreviousChapter = hasPreviousChapter,
    )
    val onSettledBoundary = resolvePageTurnRendererBoundaryChapterTarget(
        currentPage = currentPage,
        contentPageCount = safeSlotCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    ) != PageTurnBoundaryTarget.NONE
    val foldSettled = pageCurlState.progress == 0f && !onSettledBoundary

    // Whether the overlay may accept new gestures. A fold the overlay's own dispatcher is driving
    // must NOT disable it — that would cut the gesture off mid-drag and leave it unable to start
    // another one. Only a chapter boundary, or a fold started elsewhere (a programmatic turn),
    // closes the gate.
    val gesturesEnabled = !onSettledBoundary && (foldSettled || foldDriver.isActive)

    Box(modifier = modifier.fillMaxSize().then(longPressModifier)) {
        PageCurl(
            count = virtualPageCount,
            state = pageCurlState,
            config = pageCurlConfig,
            modifier = curlModifier,
            onMirroredSurface = config.mirrored,
        ) { page ->
            val boundary = resolvePageTurnRendererBoundaryChapterTarget(
                currentPage = page,
                contentPageCount = safeSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
            )
            Box(modifier = mirroredContentModifier) {
                when (boundary) {
                    PageTurnBoundaryTarget.PREVIOUS, PageTurnBoundaryTarget.NEXT -> {
                        // Handoff placeholder — the settled-boundary effect above triggers the real
                        // chapter load. A styled ChapterTransition view can replace this in a later task.
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                if (boundary == PageTurnBoundaryTarget.PREVIOUS) {
                                    "Previous chapter"
                                } else {
                                    "Next chapter"
                                },
                            )
                        }
                    }
                    PageTurnBoundaryTarget.NONE -> {
                        val pageSlot = resolvePageTurnRendererProgressPageIndex(
                            currentPage = page,
                            contentPageCount = safeSlotCount,
                            hasPreviousChapter = hasPreviousChapter,
                        )
                        slotContent(pageSlot)
                    }
                }
            }
        }

        // Live overlay, on top of the curl. Its dispatcher owns the touch stream and drives the
        // fold through foldDriver, so it must stay composed while a fold it started is still in
        // flight — tearing it down mid-drag would drop the very gesture driving the fold. It is
        // hidden (not removed) while folding so the snapshot inside the curl shows through.
        if (liveOverlay != null && (foldSettled || foldDriver.isActive)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (foldSettled) 1f else 0f },
            ) {
                liveOverlay(liveSettledSlot, gesturesEnabled, foldDriver)
            }
        }
    }
}

/**
 * Animates a programmatic page turn on [state] in [forward] direction with [config]'s timing —
 * used to drive taps / auto-scroll / volume keys through the same fold the drag gesture produces.
 */
suspend fun PageCurlState.animateTurn(config: MangaPageCurlRendererConfig, forward: Boolean) {
    val block = createPageTurnAnimation(
        animationDurationMillis = config.preset.animationDurationMillis,
        forward = forward,
        curlAmount = config.preset.curlAmount,
    )
    if (forward) next(block) else prev(block)
}
