@file:OptIn(ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.curl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Two-column spread page-curl renderer for the manga reader, ported from the novel reader's
 * `SpreadPageTurnPageRenderer` with every text/TTS/selection/typography parameter dropped.
 *
 * The caller supplies the spreads already paired ([spreadSlotCount] of them) and a [spreadContent]
 * that draws one half — the halves stay separate, one per fold surface, rather than being merged
 * into a single image. Pairing, the right-to-left swap and `shiftDoublePages` are resolved by the
 * caller (they come out of `groupPagesForDoublePage`), so this renderer never derives a pair by
 * index arithmetic of its own.
 *
 * @param spreadSlotCount number of spreads in the current chapter.
 * @param currentSpreadSlot the settled spread to display.
 * @param onCurrentSpreadSlotChange reports the settled spread back to the viewer.
 * @param spreadContent draws the half of spread `slot` on the given [SpreadColumn].
 */
@Composable
fun MangaSpreadCurlRenderer(
    spreadSlotCount: Int,
    currentSpreadSlot: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    config: MangaPageCurlRendererConfig,
    chapterId: Long,
    onNavigationTap: (xFraction: Float, yFraction: Float) -> Boolean,
    onLongTap: () -> Unit,
    onCurrentSpreadSlotChange: (Int) -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    onIdleChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    programmaticTurn: Pair<Boolean, Int>? = null,
    seekRequest: Pair<Int, Int>? = null,
    /**
     * Width / height of one half's artwork, or null while it is still decoding.
     *
     * The fold surface is sized to this rather than to the column, so the curl bends the page and
     * not the letterbox around it. Without it the sheet being turned is the whole half-viewport:
     * the black bars left and right come away with the page, the shadow and the flap back paint
     * over them, and the drag zones sit out in the margin instead of on the artwork's own edges.
     *
     * Null falls back to filling the column — a page that has not decoded yet has no rect to fold,
     * and a full-column surface at least keeps the geometry defined.
     */
    halfAspectRatio: Float? = null,
    // Live-view overlay for the settled spread, drawn across the WHOLE viewport on top of both
    // columns and outside PageCurl's per-frame content teardown, so its zoom/pan state survives
    // folds.
    //
    // One overlay for the pair, not one per column: the legacy reader composites a joined spread
    // into a single bitmap (JoinedPagerPageHolder / ImageUtil.mergeHorizontal) so zoom and pan treat
    // it as one image, and two independently zoomable halves would not match that.
    //
    // The dispatcher inside owns the touch stream; it drives whichever column's fold the gesture
    // belongs to through [foldDriverForScreenX], resolved from the touch's x fraction.
    liveOverlay: (
        @Composable (
            slot: Int,
            gesturesEnabled: Boolean,
            foldDriverForScreenX: (Float) -> ExternalFold,
        ) -> Unit
    )? = null,
    /**
     * The spread the live overlay is actually showing right now, or null while it has none.
     *
     * The overlay decodes and composites its pair off the main thread, so for a frame or two after a
     * fold settles it still holds the *previous* spread's bitmap. Showing it the instant the fold
     * ends therefore covers the columns with that stale pair — measured at ~15ms, seen as a flicker
     * of the old pages between the animation ending and the new ones appearing.
     *
     * It stays transparent until this catches up. The columns underneath already draw the correct
     * new spread, so there is always a complete, current spread on screen.
     */
    overlayReadySlot: Int? = null,
    spreadContent: @Composable (slot: Int, column: SpreadColumn) -> Unit,
) {
    val safeSpreadSlotCount = spreadSlotCount.coerceAtLeast(1)
    val virtualSlotCount = remember(safeSpreadSlotCount, hasPreviousChapter, hasNextChapter) {
        resolvePageTurnRendererVirtualPageCount(
            contentPageCount = safeSpreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
    }

    val settledSpreadSlot = currentSpreadSlot.coerceIn(0, safeSpreadSlotCount - 1)
    val initialVirtualSlot = remember(settledSpreadSlot, hasPreviousChapter) {
        resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = settledSpreadSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
    }

    // The left column is mirrored, so it counts in the opposite direction (see SpreadColumnCurl).
    val leftCurlState = rememberPageCurlState(
        initialCurrent = (virtualSlotCount - 1 - initialVirtualSlot).coerceAtLeast(0),
    )
    val rightCurlState = rememberPageCurlState(initialCurrent = initialVirtualSlot)

    val latestNavigationTap by rememberUpdatedState(onNavigationTap)
    val latestLongTap by rememberUpdatedState(onLongTap)
    val latestSlotChange by rememberUpdatedState(onCurrentSpreadSlotChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestIdleChanged by rememberUpdatedState(onIdleChanged)
    val latestConfig by rememberUpdatedState(config)

    val dragInteraction = remember(config) { config.dragInteraction() }
    val tapInteraction = remember(config) { config.tapInteraction() }

    // Maps a tap inside one column to a viewport-wide fraction for ViewerNavigation. `columnStart`
    // is where this column begins in the viewport (0 for the left half, 0.5 for the right), and
    // `preFlipped` marks a column whose own surface is mirrored, so its pointer coords arrive
    // pre-flip. In R2L the whole spread is mirrored again (see spreadModifier), which the final
    // `1f - x` undoes so nav zones stay in screen space.
    fun columnTapFraction(preFlipped: Boolean, columnStart: Float, size: IntSize, offset: Offset): Pair<Float, Float> {
        val raw = if (size.width > 0) (offset.x / size.width.toFloat()).coerceIn(0f, 1f) else 0.5f
        val withinColumn = if (preFlipped) 1f - raw else raw
        var x = columnStart + withinColumn * 0.5f
        if (latestConfig.mirrored) x = 1f - x
        val y = if (size.height > 0) (offset.y / size.height.toFloat()).coerceIn(0f, 1f) else 0.5f
        return x to y
    }

    // One config per column: each drives its own PageCurl instance.
    val leftPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            val (x, y) = columnTapFraction(preFlipped = true, columnStart = 0f, size = size, offset = offset)
            latestNavigationTap(x, y)
        },
    )
    val rightPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            val (x, y) = columnTapFraction(preFlipped = false, columnStart = 0.5f, size = size, offset = offset)
            latestNavigationTap(x, y)
        },
    )

    SideEffect {
        listOf(leftPageCurlConfig, rightPageCurlConfig).forEach { cfg ->
            cfg.backPageColor = config.backPageColor
            cfg.backPageContentAlpha = 0f
            // The spread shows the real neighbouring page on the flap back, unlike single-page mode.
            cfg.independentBackPageEnabled = true
            cfg.shadowColor = config.shadowColor
            cfg.shadowAlpha = config.preset.shadowAlpha
            cfg.shadowRadius = config.shadowRadiusDp.dp
            cfg.shadowOffset = DpOffset(config.shadowOffsetXDp.dp, 0.dp)
            cfg.tapBackwardEnabled = false
            cfg.tapForwardEnabled = false
            // With a live overlay the dispatcher inside it owns the touch stream and drives the fold
            // through ExternalFoldDriver, so the library's own gestures stay off — two owners of the
            // same fold would fight.
            cfg.tapCustomEnabled = liveOverlay == null
            cfg.dragInteraction = dragInteraction
            cfg.tapInteraction = tapInteraction
        }
        val dragOn = liveOverlay == null
        leftPageCurlConfig.dragBackwardEnabled = dragOn && leftCurlState.current > 0
        leftPageCurlConfig.dragForwardEnabled = dragOn && leftCurlState.current < virtualSlotCount - 1
        rightPageCurlConfig.dragBackwardEnabled = dragOn && rightCurlState.current > 0
        rightPageCurlConfig.dragForwardEnabled = dragOn && rightCurlState.current < virtualSlotCount - 1
    }

    // Keep the two columns showing the same spread.
    //
    // Neither column is a permanent source of truth, and the *direction* of a disagreement does not
    // disambiguate it either: the left column's `current` runs opposite to reading order, so "left is
    // now the lower one" is exactly what a genuine left-column turn produces. Comparing the two
    // against each other snaps a real backward drag straight back and makes going back impossible.
    // The only reliable signal is each column against its own previous value.
    //
    // This runs at composition time rather than in a LaunchedEffect: an effect lands a frame late and
    // the outgoing page visibly flashes.
    var lastSyncedLeft by remember(leftCurlState) { mutableIntStateOf(leftCurlState.current) }
    var lastSyncedRight by remember(rightCurlState) { mutableIntStateOf(rightCurlState.current) }
    val leftChanged = leftCurlState.current != lastSyncedLeft
    val rightChanged = rightCurlState.current != lastSyncedRight
    if (leftChanged && !rightChanged) {
        val rightTarget = (virtualSlotCount - 1 - leftCurlState.current.coerceIn(0, virtualSlotCount - 1))
            .coerceAtLeast(0)
        SpreadCurlDiagnostics.log(
            "sync",
            "left drove: $lastSyncedLeft -> ${leftCurlState.current}, " +
                "right ${rightCurlState.current} -> $rightTarget (virtualSlotCount=$virtualSlotCount)",
        )
        if (rightCurlState.current != rightTarget) rightCurlState.setCurrentImmediately(rightTarget)
    } else if (rightChanged && !leftChanged) {
        val leftTarget = (virtualSlotCount - 1 - rightCurlState.current.coerceIn(0, virtualSlotCount - 1))
            .coerceAtLeast(0)
        SpreadCurlDiagnostics.log(
            "sync",
            "right drove: $lastSyncedRight -> ${rightCurlState.current}, " +
                "left ${leftCurlState.current} -> $leftTarget (virtualSlotCount=$virtualSlotCount)",
        )
        if (leftCurlState.current != leftTarget) leftCurlState.setCurrentImmediately(leftTarget)
    } else if (leftChanged && rightChanged) {
        // Both moved in the same composition. The sync block deliberately does nothing here, so if
        // the two ended up on different spreads this is where the divergence became invisible.
        SpreadCurlDiagnostics.log(
            "sync",
            "both changed: left $lastSyncedLeft -> ${leftCurlState.current}, " +
                "right $lastSyncedRight -> ${rightCurlState.current} (no sync applied)",
        )
    }
    lastSyncedLeft = leftCurlState.current
    lastSyncedRight = rightCurlState.current

    // Reset both columns when the chapter (or its slot geometry) changes.
    LaunchedEffect(chapterId, settledSpreadSlot, safeSpreadSlotCount, hasPreviousChapter) {
        val target = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = settledSpreadSlot.coerceIn(0, safeSpreadSlotCount - 1),
            hasPreviousChapter = hasPreviousChapter,
        )
        if (rightCurlState.current != target) rightCurlState.snapTo(target)
        val leftTarget = (virtualSlotCount - 1 - target).coerceAtLeast(0)
        if (leftCurlState.current != leftTarget) leftCurlState.snapTo(leftTarget)
    }

    // Report the settled spread, and hand chapter boundaries back to the viewer.
    LaunchedEffect(rightCurlState, safeSpreadSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { rightCurlState.current.coerceIn(0, virtualSlotCount - 1) }
            .distinctUntilChanged()
            .collect { targetVirtualSlot ->
                when (
                    resolvePageTurnRendererBoundaryChapterTarget(
                        currentPage = targetVirtualSlot,
                        contentPageCount = safeSpreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    PageTurnBoundaryTarget.PREVIOUS -> latestOpenPreviousChapter()
                    PageTurnBoundaryTarget.NEXT -> latestOpenNextChapter()
                    PageTurnBoundaryTarget.NONE -> {
                        latestSlotChange(
                            resolvePageTurnRendererProgressPageIndex(
                                currentPage = targetVirtualSlot,
                                contentPageCount = safeSpreadSlotCount,
                                hasPreviousChapter = hasPreviousChapter,
                            ).coerceIn(0, safeSpreadSlotCount - 1),
                        )
                    }
                }
            }
    }

    LaunchedEffect(rightCurlState, leftCurlState) {
        snapshotFlow { rightCurlState.progress == 0f && leftCurlState.progress == 0f }
            .distinctUntilChanged()
            .collect { latestIdleChanged(it) }
    }

    // Slider seek: jump both columns to that spread.
    LaunchedEffect(seekRequest?.second) {
        val (slot, token) = seekRequest ?: return@LaunchedEffect
        if (token == 0) return@LaunchedEffect
        val spreadSlot = slot.coerceIn(0, safeSpreadSlotCount - 1)
        val target = resolvePageTurnRendererVirtualPageIndex(spreadSlot, hasPreviousChapter)
        rightCurlState.snapTo(target)
        leftCurlState.snapTo((virtualSlotCount - 1 - target).coerceAtLeast(0))
    }

    // Programmatic turns (a tap, volume keys, auto-scroll). The column sync block above carries
    // whichever column this does not drive.
    LaunchedEffect(programmaticTurn?.second) {
        val (forward, token) = programmaticTurn ?: return@LaunchedEffect
        if (token == 0) return@LaunchedEffect
        // Turn on the column whose *forward* flap is the sheet being lifted, exactly as a drag does.
        //
        // A drag routes to the column under the finger, and that column's own `invertDirection`
        // turns a backward gesture into a FORWARD fold of its state — so a drag only ever animates a
        // forward flap. Driving the right column for both directions instead made a backward tap
        // animate the right column's *backward* flap, which hinges on the opposite side: the page
        // bulged left where a drag bulges right, and the flap had the current spread behind it
        // rather than the one it was uncovering.
        //
        // The left column counts against reading order, so a backward turn is a forward turn of its
        // state — which is the same mapping ExternalFoldDriver applies with invertDirection.
        val turnLeftColumn = !forward
        val state = if (turnLeftColumn) leftCurlState else rightCurlState
        SpreadCurlDiagnostics.log(
            "turn.programmatic",
            "forward=$forward token=$token via=${if (turnLeftColumn) "leftColumn" else "rightColumn"} " +
                "rightCurrent=${rightCurlState.current} leftCurrent=${leftCurlState.current}",
        )
        // Always a forward turn of the chosen state: the column choice above already encodes the
        // reading direction.
        state.animateTurn(latestConfig, forward = true)
        SpreadCurlDiagnostics.log(
            "turn.programmatic",
            "settled rightCurrent=${rightCurlState.current} leftCurrent=${leftCurlState.current}",
        )
    }

    // With a live overlay, its dispatcher detects the long press itself (it owns the stream).
    val longPressModifier = if (liveOverlay != null) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // Does not consume, so a tap or drag still reaches the columns' own gesture handlers.
                val down = awaitFirstDown(requireUnconsumed = false)
                awaitLongPressOrCancellation(down.id)?.let { latestLongTap() }
            }
        }
    }

    // Each column's draw phase reads both columns' progress and current, so a change on either side
    // invalidates both draws on the same frame — the dragging column for its own animation, and the
    // idle sibling so it re-records the exact page the drag now needs. Without this the non-dragged
    // column never re-records and its sibling draws a blank flap.
    val leftProgress = leftCurlState.progress
    val rightProgress = rightCurlState.progress
    val leftCurrent = leftCurlState.current
    val rightCurrent = rightCurlState.current
    val crossInvalidatingModifier = Modifier.graphicsLayer {
        @Suppress("UNUSED_EXPRESSION")
        leftProgress
        rightProgress
        leftCurrent
        rightCurrent
    }

    val backContentLayerCache = rememberSpreadCurlBackContentLayerCache()

    // One fold driver per column. A drag folds the column under the finger, and the column sync
    // block carries the sibling's `current` along when the turn commits.
    val foldScope = rememberCoroutineScope()
    val leftFoldDriver = remember(leftCurlState, foldScope) {
        // The left column is the mirrored one: its state counts against reading order.
        ExternalFoldDriver(leftCurlState, foldScope, invertDirection = true, debugName = "left")
    }
    val rightFoldDriver = remember(rightCurlState, foldScope) {
        ExternalFoldDriver(rightCurlState, foldScope, debugName = "right")
    }

    val settledForOverlay = leftCurlState.progress == 0f && rightCurlState.progress == 0f
    val leftGesturesEnabled = settledForOverlay || leftFoldDriver.isActive
    val rightGesturesEnabled = settledForOverlay || rightFoldDriver.isActive

    // Right-to-left: mirror the whole spread so it advances in the correct direction. The columns'
    // artwork is un-mirrored per draw by their own contentModifier, and the interaction Rects are
    // deliberately NOT swapped — the flip already inverts gestures.
    val spreadModifier = if (config.mirrored) {
        Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
    } else {
        Modifier.fillMaxSize()
    }

    // The spread the columns have settled on. Resolved here rather than only inside the overlay
    // branch, because the hand-off below has to compare it against what the overlay is showing.
    val settledSpread = resolveMangaSpreadColumnSlot(
        virtualPage = rightCurlState.current,
        virtualSlotCount = virtualSlotCount,
        spreadSlotCount = safeSpreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        invertPage = false,
    )

    // Whether the overlay is covering the columns with this spread — not a previous one.
    //
    // The columns are deliberately NOT hidden when it is. Both draw the same spread at the same
    // rect: instrumented on device, the columns' halves span [574..2546] at 1972x1440, and the
    // overlay's merged bitmap fits to exactly 1972x1440 centred on the same span, at scale ==
    // minScale. The composite is a JPEG, so it is fully opaque and hides the columns behind it
    // completely; drawing them underneath costs one overdraw of an already-decoded bitmap and
    // nothing else, and only while settled.
    //
    // Cross-fading the two instead is what produced the last flicker. The columns are Compose nodes
    // and the overlay is an AndroidView, so flipping one node's alpha to 0 and the other's to 1 in
    // the same composition does not guarantee both reach the screen on the same frame — and the
    // frame where neither is painted is the flash seen at the end of every turn. Leaving the lower
    // layer up means there is no frame without a complete spread on it.
    val overlayShowsSettledSpread = overlayReadySlot != null && overlayReadySlot == settledSpread
    val overlayCovering = liveOverlay != null && settledForOverlay && overlayShowsSettledSpread

    // The hand-off itself: the frame `overlayCovering` flips is where the composited overlay gives
    // way to the two columns. Logging it next to both columns' progress marks that frame in the
    // trace, so the `layout.half` rects either side of it can be compared.
    SpreadCurlDiagnostics.logChanged(
        "handoff",
        "layout.handoff",
        "overlayCovering=$overlayCovering settledForOverlay=$settledForOverlay " +
            "settledSpread=$settledSpread overlayReadySlot=$overlayReadySlot " +
            "overlayShowsSettled=$overlayShowsSettledSpread " +
            "leftProgress=${SpreadCurlDiagnostics.f2(leftCurlState.progress)} " +
            "rightProgress=${SpreadCurlDiagnostics.f2(rightCurlState.progress)} " +
            "leftCurrent=${leftCurlState.current} rightCurrent=${rightCurlState.current} " +
            "leftActive=${leftFoldDriver.isActive} rightActive=${rightFoldDriver.isActive} " +
            "mirrored=${config.mirrored}",
    )

    Box(modifier = modifier.fillMaxSize().then(longPressModifier)) {
        Row(modifier = spreadModifier) {
            MangaSpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .zIndex(if (leftCurlState.progress > 0f) 1f else 0f)
                    .then(crossInvalidatingModifier),
                mirrored = true,
                invertPage = true,
                column = SpreadColumn.LEFT,
                pageCurlState = leftCurlState,
                pageCurlConfig = leftPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = safeSpreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                backContentLayerCache = backContentLayerCache,
                spreadMirrored = config.mirrored,
                halfAspectRatio = halfAspectRatio,
                spreadContent = spreadContent,
            )
            MangaSpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (rightCurlState.progress > 0f) 1f else 0f)
                    .then(crossInvalidatingModifier),
                mirrored = false,
                invertPage = false,
                column = SpreadColumn.RIGHT,
                pageCurlState = rightCurlState,
                pageCurlConfig = rightPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = safeSpreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                backContentLayerCache = backContentLayerCache,
                spreadMirrored = config.mirrored,
                halfAspectRatio = halfAspectRatio,
                spreadContent = spreadContent,
            )
        }

        // The live zoom overlay sits above both columns, spanning the whole viewport in screen space
        // — deliberately outside the R2L spreadModifier, since the dispatcher inside works in real
        // screen coordinates and the composited spread is already in reading order.
        // Kept composed while a fold its own dispatcher started is still running, so the gesture is
        // not torn out from under the finger; it is transparent meanwhile (see overlayCovering).
        val overlayEnabled = settledForOverlay || leftFoldDriver.isActive || rightFoldDriver.isActive
        if (liveOverlay != null && overlayEnabled) {
            if (settledSpread != null) {
                // The column under the finger, and only that one — a drag lifts the leaf it grabbed,
                // and its sibling stays put until the turn commits.
                //
                // In R2L the Row is flipped, so the screen's left half is the right column's leaf
                // and vice-versa; `onScreenLeft != mirrored` resolves that.
                //
                // Which column this is also settles the hinge. Each column's own surface anchors a
                // fold at the edge its state counts from — the mirrored left column's local 0 sits
                // on the *screen right*, which is exactly where a backward turn is grabbed in R2L —
                // so routing to the column under the finger puts the hinge under the finger and the
                // bulge on the correct side. Folding both columns at once also gets the bulge right,
                // but animates the half nobody is touching, which is not what a real page does.
                val foldDriverForScreenX: (Float) -> ExternalFold = { xFraction ->
                    val onScreenLeft = xFraction < 0.5f
                    val wantLeftColumn = onScreenLeft != config.mirrored
                    if (wantLeftColumn) leftFoldDriver else rightFoldDriver
                }
                Box(
                    // Shown on exactly the condition the columns are hidden on, so the two swap in
                    // the same composition and neither a gap nor a double-draw can appear between
                    // them. `settledForOverlay` alone would fade the overlay in while it still held
                    // the previous spread's bitmap.
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (overlayCovering) 1f else 0f },
                ) {
                    liveOverlay(settledSpread, overlayEnabled, foldDriverForScreenX)
                }
            }
        }
    }
}

/**
 * One column of the spread. The left column is [mirrored] so the library's right-referenced fold
 * geometry lands on the screen's actual left edge and anchors at the spine; its content is
 * un-mirrored per draw.
 */
@Composable
private fun MangaSpreadColumnCurl(
    modifier: Modifier,
    mirrored: Boolean,
    invertPage: Boolean,
    column: SpreadColumn,
    pageCurlState: PageCurlState,
    pageCurlConfig: PageCurlConfig,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    backContentLayerCache: SpreadCurlBackContentLayerCache,
    // True when the whole spread is mirrored for R2L, on top of this column's own [mirrored] flip.
    spreadMirrored: Boolean,
    // Width / height of the artwork this column folds, or null while it is still decoding.
    halfAspectRatio: Float?,
    spreadContent: @Composable (slot: Int, column: SpreadColumn) -> Unit,
) {
    // Probes the column itself, so a half's window rect can be read against the column that holds
    // it. A half sitting at the column's outer edge and a column that is itself the wrong width are
    // different faults with the same symptom on screen.
    val columnProbe = Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        SpreadCurlDiagnostics.logChanged(
            "column.$column",
            "layout.column",
            "column=$column mirrored=$mirrored aspect=$halfAspectRatio " +
                "window=[${SpreadCurlDiagnostics.f(bounds.left)}..${SpreadCurlDiagnostics.f(bounds.right)}] " +
                "w=${SpreadCurlDiagnostics.f(bounds.width)} " +
                "localSize=${coords.size.width}x${coords.size.height}",
        )
    }

    // The fold surface is the artwork, not the column.
    //
    // `PageCurl` folds the node it is given: its `drawCurl` reads that node's size for the curl
    // line, the shadow and the flap back. Handing it the whole half-viewport made the sheet include
    // the Fit letterbox, so the black bars came away with the page, the flap painted over them, and
    // the drag zones — measured off the same surface — sat out in the margin rather than on the
    // page's own edges.
    //
    // `fillMaxHeight().aspectRatio(...)` reproduces exactly what `ContentScale.Fit` does to the
    // artwork inside a column that is always the wider of the two ratios, so the surface lands on
    // the artwork's rect and the content inside can then simply fill it.
    val curlSizeModifier = if (halfAspectRatio != null && halfAspectRatio > 0f) {
        Modifier.fillMaxHeight().aspectRatio(halfAspectRatio)
    } else {
        Modifier.fillMaxSize()
    }
    // Probes the fold surface itself — the node PageCurl folds. Placed before the flip so it reports
    // the surface's placement inside the column, which is what the spine alignment controls.
    val surfaceProbe = Modifier.onGloballyPositioned { coords ->
        val bounds = coords.boundsInWindow()
        SpreadCurlDiagnostics.logChanged(
            "surface.$column",
            "layout.surface",
            "column=$column mirrored=$mirrored spreadMirrored=$spreadMirrored " +
                "window=[${SpreadCurlDiagnostics.f(bounds.left)}..${SpreadCurlDiagnostics.f(bounds.right)}] " +
                "w=${SpreadCurlDiagnostics.f(bounds.width)} " +
                "localSize=${coords.size.width}x${coords.size.height}",
        )
    }

    val curlModifier = if (mirrored) {
        curlSizeModifier.then(surfaceProbe).graphicsLayer(scaleX = -1f)
    } else {
        curlSizeModifier.then(surfaceProbe)
    }
    // Un-mirrors the artwork so it draws upright. Both flips in the chain count: this column's own
    // (the mirrored left column) and the R2L flip applied to the whole spread. Two flips cancel, so
    // the content is only un-mirrored when exactly one is active. Applied per draw, inside the
    // recording, so a page replayed as the sibling's back-of-page also comes out upright.
    val contentModifier = if (mirrored != spreadMirrored) {
        Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
    } else {
        Modifier.fillMaxSize()
    }

    /** The spread this column shows at virtual slot [slot], or null on a boundary placeholder. */
    fun spreadSlotAt(slot: Int): Int? = resolveMangaSpreadColumnSlot(
        virtualPage = slot,
        virtualSlotCount = virtualSlotCount,
        spreadSlotCount = spreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
        invertPage = invertPage,
    )

    // A sheet in a bound book carries one spread half on each face: turning the left half of spread
    // N reveals spread N-1, and the back of that leaf is N-1's *right* half — the opposite column of
    // the spread being revealed. So a back layer is keyed by (spread, column), and the column is
    // always this column's opposite.
    //
    // Each flap turns a different sheet, so forward and backward need their own layer: resolving
    // both from `current` hands the backward flap a sheet this column never composes, which draws
    // as a blank flap.
    val externalBackContentLayers = if (pageCurlConfig.independentBackPageEnabled) {
        fun backLayerForTurningSlot(turningSlot: Int): GraphicsLayer? {
            val revealedSpread = spreadSlotAt(turningSlot + 1) ?: return null
            // getOrCreate, not get: the left column composes before the right one, so on the first
            // frame after a change the sibling that owns this half has not recorded yet. Creating
            // the entry hands both columns the same stable layer, which the owning column fills in
            // during its own draw pass later in the same frame.
            return registeredSpreadCurlBackContentLayer(
                backContentLayerCache,
                spreadBackLayerKey(revealedSpread, column.opposite),
            )
        }

        // Which spread half each flap paints on its reverse. With the under-layer now correct, this
        // is the only remaining layer a backward turn can be showing the wrong page from.
        SpreadCurlDiagnostics.logChanged(
            "backlayer.$column",
            "curl.backlayer",
            "column=$column current=${pageCurlState.current} " +
                "fwdBack=spread${spreadSlotAt(pageCurlState.current + 1)}/${column.opposite} " +
                "bwdBack=spread${spreadSlotAt(pageCurlState.current)}/${column.opposite}",
        )

        ExternalBackContentLayers(
            forward = backLayerForTurningSlot(pageCurlState.current),
            backward = backLayerForTurningSlot(pageCurlState.current - 1),
        )
    } else {
        null
    }

    // Pull the fold surface against the spine — the middle of the screen, where the two halves meet.
    //
    // This Box sits *outside* `curlModifier`, so the column's own `scaleX = -1f` does not apply to
    // it; the only flip between here and the screen is `spreadModifier` on the Row, and that moves
    // both columns together, so it cancels out of the comparison entirely. What is left is just the
    // column's side of the Row: LEFT is bound on its right, RIGHT is bound on its left.
    //
    // Measured, not derived — two earlier attempts got this wrong by reasoning about the flip chain
    // on paper. With End on both columns the instrumented rects came back as
    //
    //   LEFT  column [1560..3120], surface [2134..3120]   (574px from the spine)
    //   RIGHT column [0..1560],    surface [0..986]       (574px from the spine)
    //
    // — both flush against the screen's *outer* edge, the two 574px insets meeting in the middle as
    // one 1148px black band. `layout.surface` reports these directly, which is what finally
    // separated "the alignment is wrong" from "the alignment never reaches the surface".
    val spineAlignment = if (column == SpreadColumn.LEFT) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = modifier.then(columnProbe).fillMaxSize(), contentAlignment = spineAlignment) {
        PageCurl(
            count = virtualSlotCount,
            key = { it },
            state = pageCurlState,
            config = pageCurlConfig,
            externalBackContentLayers = externalBackContentLayers,
            // The flag only decides whether drawCurl adds its own horizontal flip before painting an
            // externally supplied back-content layer. Here that layer is always the *sibling*
            // column's recording, and the two columns record with opposite handedness: the recording
            // happens inside `contentModifier`, whose condition is `mirrored != spreadMirrored`, and
            // that is true for exactly one of the two columns whatever the reading direction. So a
            // layer crossing from one column to the other already arrives carrying one flip, and
            // which column receives it decides whether that flip helps or hurts — hence `!mirrored`
            // rather than a constant.
            //
            // This briefly read `true`, which was verified only against forward turns while every
            // drag was routed to the right column. Once a drag folded the column under the finger, a
            // backward turn landed on the mirrored column and its flap back read backwards.
            //
            // Passing `mirrored` happened to hold while the surface was the whole column and the
            // spine offset inside the recording absorbed the difference. Once the surface became the
            // artwork's own rect that offset went away and the extra flip became visible as a
            // mirrored back face on the folding sheet.
            onMirroredSurface = !mirrored,
            curlDebugName = column.toString(),
            modifier = curlModifier,
        ) { page ->
            val spreadSlot = spreadSlotAt(page)
            // Virtual slot -> spread, per composed page. Read against curl.stack this says which
            // spread each of a column's three layers is actually showing, which is what "the same
            // page appears under the flap" has to be checked against.
            SpreadCurlDiagnostics.logChanged(
                "slotmap.$column.$page",
                "curl.slotmap",
                "column=$column virtual=$page -> spread=$spreadSlot current=${pageCurlState.current}",
            )
            if (spreadSlot != null) {
                // Record this half's pixels into the shared cache as it draws, so the *other* column
                // can paint it on the back of the sheet it is turning. Without a recording the cache
                // would hand out a real but empty layer, which draws as a blank flap.
                //
                // The flip goes OUTSIDE the recording: PageTurnSnapshotLayer replays its layer with
                // drawLayer on its own (unflipped) geometry, so a flip applied inside would be
                // captured into the recording and then drawn unmirrored — leaving the half the wrong
                // way round.
                //
                // No alignment here any more: this surface *is* the artwork's rect (see
                // curlSizeModifier), so the half simply fills it and the spine alignment that used
                // to live here has moved out to the column Box that places this surface.
                Box(modifier = contentModifier) {
                    PageTurnSnapshotLayer(
                        externalGraphicsLayer = registeredSpreadCurlBackContentLayer(
                            backContentLayerCache,
                            spreadBackLayerKey(spreadSlot, column),
                        ),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().onGloballyPositioned { coords ->
                                // Where this half actually lands in *window* space, which is the
                                // only frame both columns and the composited overlay share. Two
                                // halves parked at the viewport's outer edges show up here as a
                                // large gap between the left half's right edge and the right
                                // half's left edge; a half wider than the artwork shows up as a
                                // width that does not match `layout.image`'s.
                                val bounds = coords.boundsInWindow()
                                SpreadCurlDiagnostics.logChanged(
                                    "half.$column",
                                    "layout.half",
                                    "column=$column slot=$spreadSlot " +
                                        "mirroredColumn=$mirrored spreadMirrored=$spreadMirrored " +
                                        "contentFlipped=${mirrored != spreadMirrored} " +
                                        "alignment=$spineAlignment aspect=$halfAspectRatio " +
                                        "window=[${SpreadCurlDiagnostics.f(bounds.left)}.." +
                                        "${SpreadCurlDiagnostics.f(bounds.right)}] " +
                                        "w=${SpreadCurlDiagnostics.f(bounds.width)} " +
                                        "h=${SpreadCurlDiagnostics.f(bounds.height)} " +
                                        "localSize=${coords.size.width}x${coords.size.height}",
                                )
                            },
                        ) {
                            spreadContent(spreadSlot, column)
                        }
                    }
                }
            } else {
                Box(modifier = contentModifier)
            }
        }
    }
}

/** Which half of a spread a column shows. */
enum class SpreadColumn {
    LEFT,
    RIGHT,
    ;

    /** The other face of the same sheet. */
    val opposite: SpreadColumn get() = if (this == LEFT) RIGHT else LEFT
}

/**
 * Cache key for one spread half's recorded pixels. The back-content cache is keyed by a single Int,
 * so the spread index and the column are packed together — keying by spread alone would make the two
 * halves collide and each would draw the other's artwork on its flap back.
 */
private fun spreadBackLayerKey(spreadSlot: Int, column: SpreadColumn): Int =
    spreadSlot * 2 + if (column == SpreadColumn.LEFT) 0 else 1

/**
 * The spread shown at [virtualPage] in one column, or null when it falls on a chapter-boundary
 * placeholder or outside the valid range.
 */
internal fun resolveMangaSpreadColumnSlot(
    virtualPage: Int,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    invertPage: Boolean,
): Int? {
    if (virtualPage < 0 || virtualPage >= virtualSlotCount) return null
    val actualPage = if (invertPage) (virtualSlotCount - 1 - virtualPage).coerceAtLeast(0) else virtualPage
    val boundary = resolvePageTurnRendererBoundaryChapterTarget(
        currentPage = actualPage,
        contentPageCount = spreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
        hasNextChapter = hasNextChapter,
    )
    if (boundary != PageTurnBoundaryTarget.NONE) return null
    return resolvePageTurnRendererProgressPageIndex(
        currentPage = actualPage,
        contentPageCount = spreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
    )
}
