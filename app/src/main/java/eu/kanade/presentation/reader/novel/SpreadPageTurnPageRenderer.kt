@file:OptIn(eu.kanade.presentation.reader.curl.ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.novel

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.reader.curl.Edge
import eu.kanade.presentation.reader.curl.ExternalBackContentLayers
import eu.kanade.presentation.reader.curl.PageCurl
import eu.kanade.presentation.reader.curl.PageCurlConfig
import eu.kanade.presentation.reader.curl.PageCurlState
import eu.kanade.presentation.reader.curl.SpreadCurlBackContentLayerCache
import eu.kanade.presentation.reader.curl.createPageTurnAnimation
import eu.kanade.presentation.reader.curl.registeredSpreadCurlBackContentLayer
import eu.kanade.presentation.reader.curl.rememberPageCurlConfig
import eu.kanade.presentation.reader.curl.rememberPageCurlState
import eu.kanade.presentation.reader.curl.rememberSpreadCurlBackContentLayerCache
import eu.kanade.presentation.reader.curl.resolvePageTurnRendererProgressPageIndex
import eu.kanade.presentation.reader.curl.resolvePageTurnRendererVirtualPageCount
import eu.kanade.presentation.reader.curl.resolvePageTurnRendererVirtualPageIndex
import eu.kanade.presentation.reader.curl.resolveSpreadSlotCount
import eu.kanade.presentation.reader.curl.resolveSpreadSlotFirstPageIndex
import eu.kanade.presentation.reader.curl.resolveSpreadSlotForPageIndex
import eu.kanade.presentation.reader.curl.startEdge
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderTapZoneAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign
import eu.kanade.tachiyomi.ui.reader.novel.setting.parseNovelReaderTapZoneActions
import eu.kanade.tachiyomi.ui.reader.novel.setting.resolveConfiguredNovelReaderTapAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Two-page spread variant of [PageTurnPageRenderer].
 *
 * eu.wewox.pagecurl.page.PageCurl always folds across its own full measured width: there is no
 * config knob to confine the fold to half of a wider container, and a live drag tracks the finger
 * across that whole width regardless of what Rects are configured (verified on-device: a
 * full-width PageCurl behind a two-column spread curls the entire spread as if it were one wide
 * leaf). The only way to get a fold anchored at the spine is to give the fold its own half-width
 * PageCurl instance.
 *
 * So this renderer runs two independent PageCurl surfaces side by side, each exactly half the
 * screen: the left one always shows even real-page indices and only ever turns backward (its fold
 * lives at the left edge, spine on its right); the right one always shows odd real-page indices
 * and only ever turns forward (its fold lives at the right edge, spine on its left). Both track
 * the same spread slot; only the side facing the turn direction plays an animated fold, the other
 * side's content swaps the instant that fold settles, driven off the animating side's own
 * progress via snapshotFlow. This mirrors a real book: only the leaf being turned animates.
 */
@Composable
internal fun SpreadPageTurnPageRenderer(
    pagerState: PagerState,
    chapterId: Long,
    contentPages: List<NovelPageContentPage>,
    transitionStyle: NovelPageTransitionStyle,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    chapterTitleTextColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    backgroundImageModel: Any?,
    backgroundModeIdentity: String,
    isBackgroundMode: Boolean,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    hasPreviousChapter: Boolean,
    previousChapterName: String?,
    hasNextChapter: Boolean,
    nextChapterName: String?,
    previousChapterLabel: String,
    nextChapterLabel: String,
    boundaryChapterHint: String,
    showBoundaryChapterPages: Boolean = true,
    onToggleUi: () -> Unit,
    requestedPage: Int,
    onRequestedPageConsumed: () -> Unit,
    onCurrentPageChange: (Int) -> Unit,
    onOpenPreviousChapter: () -> Unit,
    onOpenNextChapter: () -> Unit,
    chapterNavigationRequest: PageTurnChapterNavigationRequest? = null,
    onChapterNavigationRequestConsumed: () -> Unit = {},
    onTextTap: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> onToggleUi() },
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onSelectionRendererActionsChanged: (
        eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRendererActions,
    ) -> Unit = {},
    selectionClearRequestToken: Int = 0,
    selectionExpandRequestToken: Int = 0,
) {
    val hasPreviousChapterNavigation = hasPreviousChapter
    val hasNextChapterNavigation = hasNextChapter

    @Suppress("NAME_SHADOWING")
    val hasPreviousChapter = hasPreviousChapter && showBoundaryChapterPages

    @Suppress("NAME_SHADOWING")
    val hasNextChapter = hasNextChapter && showBoundaryChapterPages

    val safeContentPages = remember(contentPages) {
        contentPages.ifEmpty { listOf(NovelPageContentPage(emptyList())) }
    }
    val spreadSlotCount = resolveSpreadSlotCount(safeContentPages.size, 2)
    val virtualSlotCount = remember(spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        resolvePageTurnRendererVirtualPageCount(
            contentPageCount = spreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
    }
    val pagerCurrentSlot = pagerState.currentPage.coerceIn(0, spreadSlotCount - 1)
    val initialVirtualSlot = remember(pagerCurrentSlot, hasPreviousChapter) {
        resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
    }

    // Both surfaces are seeded at the same virtual slot and only ever move together (see the two
    // LaunchedEffects below), so they never observe each other's page content out of step.
    val leftCurlState =
        rememberPageCurlState(initialCurrent = (virtualSlotCount - 1 - initialVirtualSlot).coerceAtLeast(0))
    val rightCurlState = rememberPageCurlState(initialCurrent = initialVirtualSlot)

    val currentVirtualSlot = (virtualSlotCount - 1 - leftCurlState.current).coerceIn(0, virtualSlotCount - 1)
    val currentSpreadSlot = resolvePageTurnRendererProgressPageIndex(
        currentPage = currentVirtualSlot,
        contentPageCount = spreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
    )

    val tapCoroutineScope = rememberCoroutineScope()
    val rendererConfig = remember(
        transitionStyle,
        readerSettings.pageTurnSpeed,
        readerSettings.pageTurnIntensity,
        readerSettings.pageTurnShadowIntensity,
        readerSettings.pageTurnActivationZone,
        textBackground,
    ) {
        resolveNovelPageTurnRendererConfig(
            style = transitionStyle,
            speed = readerSettings.pageTurnSpeed,
            intensity = readerSettings.pageTurnIntensity,
            shadowIntensity = readerSettings.pageTurnShadowIntensity,
            activationZone = readerSettings.pageTurnActivationZone,
            textBackground = textBackground,
            canMoveBackward = true,
            canMoveForward = true,
        )
    }
    val tapInteraction = remember(rendererConfig) {
        createPageTurnTapInteraction(rendererConfig)
    }

    val latestRequestedPage by rememberUpdatedState(requestedPage)
    val latestRequestedPageConsumed by rememberUpdatedState(onRequestedPageConsumed)
    val latestCurrentPageChange by rememberUpdatedState(onCurrentPageChange)
    val latestOpenPreviousChapter by rememberUpdatedState(onOpenPreviousChapter)
    val latestOpenNextChapter by rememberUpdatedState(onOpenNextChapter)
    val latestChapterNavigationRequest by rememberUpdatedState(chapterNavigationRequest)
    val latestChapterNavigationRequestConsumed by rememberUpdatedState(onChapterNavigationRequestConsumed)
    val latestRendererConfig by rememberUpdatedState(rendererConfig)
    val latestToggleUi by rememberUpdatedState(onToggleUi)
    val latestTapToScrollEnabled by rememberUpdatedState(readerSettings.tapToScroll)
    val latestCustomTapZonesEnabled by rememberUpdatedState(readerSettings.customTapZones)
    val latestTapZoneActions by rememberUpdatedState(
        parseNovelReaderTapZoneActions(readerSettings.tapZoneActions),
    )
    val latestHasPreviousChapter by rememberUpdatedState(hasPreviousChapterNavigation)
    val latestHasNextChapter by rememberUpdatedState(hasNextChapterNavigation)

    // Advances both surfaces together: the one facing the turn direction plays the real fold, the
    // other jumps straight to its new page with no visible animation of its own (snap()), the same
    // way the spine-side page of a real book is simply "already there" once you see it.
    //
    // eu.wewox.pagecurl.page.PageCurl's own drag/animation geometry always references the widget's
    // own size.width (NewEdgeCreator anchors on it, and PageCurlState.setup's forward Animatable
    // always starts at the right edge): using prev()/backward on an unmirrored widget opens the
    // current page from its own left edge outward, not a new page sliding in over it like a book
    // leaf. The left surface is mirrored (see SpreadColumnCurl) so the library's right-referenced
    // geometry lands on the screen's actual left edge — which means the left surface always calls
    // next()/forward internally, for both book directions; only which surface plays the real
    // animation and which one just snaps changes.
    fun turnForward() {
        tapCoroutineScope.launch {
            rightCurlState.next(
                createPageTurnAnimation(
                    animationDurationMillis = latestRendererConfig.preset.animationDurationMillis,
                    forward = true,
                    curlAmount = latestRendererConfig.preset.curlAmount,
                ),
            )
        }
    }
    fun turnBackward() {
        tapCoroutineScope.launch {
            leftCurlState.next(
                createPageTurnAnimation(
                    animationDurationMillis = latestRendererConfig.preset.animationDurationMillis,
                    forward = true,
                    curlAmount = latestRendererConfig.preset.curlAmount,
                ),
            )
        }
    }

    val leftPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            handleSpreadCustomTap(
                size = size,
                offset = offset,
                isRightSide = false,
                currentSpreadSlot = currentSpreadSlot,
                spreadSlotCount = spreadSlotCount,
                latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                latestTapZoneActions = latestTapZoneActions,
                latestTapToScrollEnabled = latestTapToScrollEnabled,
                latestRendererConfig = latestRendererConfig,
                transitionStyle = transitionStyle,
                latestToggleUi = latestToggleUi,
                onTurnForward = ::turnForward,
                onTurnBackward = ::turnBackward,
                latestOpenPreviousChapter = latestOpenPreviousChapter,
                latestOpenNextChapter = latestOpenNextChapter,
            )
        },
    )
    val rightPageCurlConfig = rememberPageCurlConfig(
        onCustomTap = { size, offset ->
            handleSpreadCustomTap(
                size = size,
                offset = offset,
                isRightSide = true,
                currentSpreadSlot = currentSpreadSlot,
                spreadSlotCount = spreadSlotCount,
                latestCustomTapZonesEnabled = latestCustomTapZonesEnabled,
                latestTapZoneActions = latestTapZoneActions,
                latestTapToScrollEnabled = latestTapToScrollEnabled,
                latestRendererConfig = latestRendererConfig,
                transitionStyle = transitionStyle,
                latestToggleUi = latestToggleUi,
                onTurnForward = ::turnForward,
                onTurnBackward = ::turnBackward,
                latestOpenPreviousChapter = latestOpenPreviousChapter,
                latestOpenNextChapter = latestOpenNextChapter,
            )
        },
    )

    SideEffect {
        leftPageCurlConfig.backPageColor = rendererConfig.backPageColor
        leftPageCurlConfig.backPageContentAlpha = 0f
        // The two-page spread is the only mode meant to show the real neighbouring page on the
        // flap's back; always on here, unlike the single-page renderer.
        leftPageCurlConfig.independentBackPageEnabled = true
        leftPageCurlConfig.shadowColor = rendererConfig.shadowColor
        leftPageCurlConfig.shadowAlpha = rendererConfig.preset.shadowAlpha
        leftPageCurlConfig.shadowRadius = rendererConfig.shadowRadiusDp.dp
        leftPageCurlConfig.shadowOffset = DpOffset(rendererConfig.shadowOffsetXDp.dp, 0.dp)
        // The library's own drag-success target (dragTargetReachFraction) is tuned for a
        // full-width single page (it can sit well past the halfway point). Each spread surface is
        // only half that width, so the fold's release target has to be the spine itself — the
        // reach a drag needs to travel to at most doubles compared to a full-width page.
        val spineReach = 0.5f
        // The left surface is mirrored (see SpreadColumnCurl), so the library's own forward
        // mechanism — which always references size.width internally — is what turnBackward() drives
        // here; the fold still lives at the outer (left) edge of the screen once un-mirrored. The
        // library's backward mechanism is unused on this surface.
        leftPageCurlConfig.dragForwardEnabled = currentVirtualSlot > 0
        leftPageCurlConfig.dragBackwardEnabled = false
        leftPageCurlConfig.tapBackwardEnabled = false
        leftPageCurlConfig.tapForwardEnabled = latestTapToScrollEnabled && currentSpreadSlot > 0
        leftPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        leftPageCurlConfig.dragInteraction = PageCurlConfig.StartEndDragInteraction(
            pointerBehavior = rendererConfig.dragPointerBehavior,
            backward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(0f, 0f, 0f, 1f),
                end = Rect(0f, 0f, 0f, 1f),
            ),
            forward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(1f - rendererConfig.dragActivationEdgeFraction, 0f, 1f, 1f),
                end = Rect(0f, 0f, spineReach, 1f),
            ),
        )
        leftPageCurlConfig.tapInteraction = tapInteraction

        rightPageCurlConfig.backPageColor = rendererConfig.backPageColor
        rightPageCurlConfig.backPageContentAlpha = 0f
        rightPageCurlConfig.independentBackPageEnabled = true
        rightPageCurlConfig.shadowColor = rendererConfig.shadowColor
        rightPageCurlConfig.shadowAlpha = rendererConfig.preset.shadowAlpha
        rightPageCurlConfig.shadowRadius = rendererConfig.shadowRadiusDp.dp
        rightPageCurlConfig.shadowOffset = DpOffset(rendererConfig.shadowOffsetXDp.dp, 0.dp)
        // The right surface only ever turns forward: its fold lives at the outer (right) edge.
        rightPageCurlConfig.dragForwardEnabled = currentVirtualSlot < virtualSlotCount - 1
        rightPageCurlConfig.dragBackwardEnabled = false
        rightPageCurlConfig.tapBackwardEnabled = false
        rightPageCurlConfig.tapForwardEnabled = latestTapToScrollEnabled && currentSpreadSlot < spreadSlotCount - 1
        rightPageCurlConfig.tapCustomEnabled = rendererConfig.tapCustomEnabled
        rightPageCurlConfig.dragInteraction = PageCurlConfig.StartEndDragInteraction(
            pointerBehavior = rendererConfig.dragPointerBehavior,
            backward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(0f, 0f, 0f, 1f),
                end = Rect(0f, 0f, 0f, 1f),
            ),
            forward = PageCurlConfig.StartEndDragInteraction.Config(
                start = Rect(1f - rendererConfig.dragActivationEdgeFraction, 0f, 1f, 1f),
                end = Rect(0f, 0f, spineReach, 1f),
            ),
        )
        rightPageCurlConfig.tapInteraction = tapInteraction
    }

    // Chapter/content changes reset both surfaces to the same slot, exactly like the single-page
    // renderer's equivalent effect.
    LaunchedEffect(chapterId, pagerCurrentSlot, spreadSlotCount, hasPreviousChapter) {
        val targetVirtualSlot = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = pagerCurrentSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        val invertedTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
        if (leftCurlState.current != invertedTarget) leftCurlState.snapTo(invertedTarget)
        if (rightCurlState.current != targetVirtualSlot) rightCurlState.snapTo(targetVirtualSlot)
    }

    var consumedBoundaryNavigation by remember(chapterId, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        mutableStateOf<HorizontalChapterSwipeAction?>(null)
    }
    // Drives boundary detection and the paired-surface snap off the LEFT surface's settled state:
    // both surfaces always share one virtual slot, so either one would do, but exactly one has to
    // own it to avoid double-firing chapter navigation.
    LaunchedEffect(leftCurlState, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) to leftCurlState.progress }
            .distinctUntilChanged()
            .collectLatest { (targetVirtualSlot, progress) ->
                val realTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
                when (
                    resolveNovelPageTurnRendererSettledBoundaryChapterTarget(
                        currentPage = realTarget,
                        progress = progress,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                        hasNextChapter = hasNextChapter,
                    )
                ) {
                    HorizontalChapterSwipeAction.PREVIOUS -> {
                        if (consumedBoundaryNavigation != HorizontalChapterSwipeAction.PREVIOUS) {
                            consumedBoundaryNavigation = HorizontalChapterSwipeAction.PREVIOUS
                            latestOpenPreviousChapter()
                        }
                    }
                    HorizontalChapterSwipeAction.NEXT -> {
                        if (consumedBoundaryNavigation != HorizontalChapterSwipeAction.NEXT) {
                            consumedBoundaryNavigation = HorizontalChapterSwipeAction.NEXT
                            latestOpenNextChapter()
                        }
                    }
                    HorizontalChapterSwipeAction.NONE -> {}
                }
            }
    }

    // A live drag on the right surface (forward turn) is the library's own gesture handling, not
    // the turnForward()/turnBackward() helpers above, so the left surface would never learn the
    // drag happened. Watching `current` here keeps it in lockstep regardless of what drove the
    // move: a drag, a tap, or requestedPage.
    //
    // Deliberately keyed on `current` alone, without also waiting for `progress` to fall back to 0.
    // `current` only ever changes once a turn has committed (see DragCommonGesture's onDragEnd,
    // which calls onChange() before snapping the edge back), so there is no risk of syncing
    // mid-animation — but gating on progress made the sibling lag a frame behind, which showed up
    // as a visible flicker: the flap finished, the stale page flashed back in, and only then did
    // this column catch up.
    // Applied during composition rather than from a LaunchedEffect/snapshotFlow: those only run after the frame
    // that changed `current` has already been composed, so the sibling column rendered one frame with its previous
    // page still on screen. That one-frame lag is the flicker where the turn completes, the outgoing page flashes
    // back on the other half of the spread, and only then does it catch up. Reconciling here means both columns
    // agree within the same frame, before anything is drawn.
    //
    // The reconciliation has to be symmetric. Forward turns are driven by rightCurlState.next() and backward turns
    // by leftCurlState.next() (see turnForward()/turnBackward()) — but *both* only ever increment their own
    // `current` (dragBackwardEnabled is false on both surfaces below; the left one's forward drag is what plays a
    // backward book turn, since it is mirrored). So neither column is the permanent source of truth, and — the
    // mistake in an earlier version of this block — the *direction* of the disagreement doesn't disambiguate it
    // either: the left column's `current` runs opposite to reading order (it is inverted, see leftReadingSlot
    // below), so "left's reading slot is now the lower one" is exactly what a genuine left-column turn produces,
    // not a sign that the right column is the one that moved. Comparing reading-order slots looked plausible but
    // silently reintroduced the original bug: the instant a real backward drag committed, this block read the
    // left column as "behind" and snapped it right back to where it started, which is why going back stayed
    // impossible even after that rewrite.
    //
    // The only reliable signal is remembering each column's own previously-synced value and asking which one is
    // no longer equal to what it was last frame — that works regardless of which slot space either column counts
    // in, because it never compares the two against each other, only each against its own history.
    var lastSyncedLeft by remember(leftCurlState) { mutableIntStateOf(leftCurlState.current) }
    var lastSyncedRight by remember(rightCurlState) { mutableIntStateOf(rightCurlState.current) }
    val leftChanged = leftCurlState.current != lastSyncedLeft
    val rightChanged = rightCurlState.current != lastSyncedRight
    if (leftChanged && !rightChanged) {
        val rightTarget = (virtualSlotCount - 1 - leftCurlState.current.coerceIn(0, virtualSlotCount - 1))
            .coerceAtLeast(0)
        if (rightCurlState.current != rightTarget) rightCurlState.setCurrentImmediately(rightTarget)
    } else if (rightChanged && !leftChanged) {
        val leftTarget = (virtualSlotCount - 1 - rightCurlState.current.coerceIn(0, virtualSlotCount - 1))
            .coerceAtLeast(0)
        if (leftCurlState.current != leftTarget) leftCurlState.setCurrentImmediately(leftTarget)
    }
    lastSyncedLeft = leftCurlState.current
    lastSyncedRight = rightCurlState.current

    LaunchedEffect(leftCurlState, pagerState, spreadSlotCount, hasPreviousChapter, hasNextChapter) {
        snapshotFlow { leftCurlState.current.coerceIn(0, virtualSlotCount - 1) }
            .distinctUntilChanged()
            .collectLatest { targetVirtualSlot ->
                val realTarget = (virtualSlotCount - 1 - targetVirtualSlot).coerceAtLeast(0)
                val boundaryTarget = resolveNovelPageTurnRendererBoundaryChapterTarget(
                    currentPage = realTarget,
                    contentPageCount = spreadSlotCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
                if (boundaryTarget == HorizontalChapterSwipeAction.NONE) {
                    val targetSpreadSlot = resolvePageTurnRendererProgressPageIndex(
                        currentPage = realTarget,
                        contentPageCount = spreadSlotCount,
                        hasPreviousChapter = hasPreviousChapter,
                    )
                    latestCurrentPageChange(resolveSpreadSlotFirstPageIndex(targetSpreadSlot, 2))
                    if (targetSpreadSlot != pagerState.currentPage) {
                        pagerState.scrollToPage(targetSpreadSlot)
                    }
                }
            }
    }

    LaunchedEffect(latestRequestedPage, spreadSlotCount, hasPreviousChapter) {
        val targetPage = latestRequestedPage
        if (targetPage < 0 || safeContentPages.isEmpty()) return@LaunchedEffect
        val targetSpreadSlot = resolveSpreadSlotForPageIndex(
            targetPage.coerceIn(0, safeContentPages.lastIndex),
            2,
        )
        val clampedTarget = resolvePageTurnRendererVirtualPageIndex(
            actualPageIndex = targetSpreadSlot,
            hasPreviousChapter = hasPreviousChapter,
        )
        val invertedTarget = (virtualSlotCount - 1 - clampedTarget).coerceAtLeast(0)
        if (leftCurlState.current != invertedTarget) leftCurlState.snapTo(invertedTarget)
        if (rightCurlState.current != clampedTarget) rightCurlState.snapTo(clampedTarget)
        latestRequestedPageConsumed()
    }

    LaunchedEffect(latestChapterNavigationRequest, transitionStyle) {
        val request = latestChapterNavigationRequest ?: return@LaunchedEffect
        if (transitionStyle != NovelPageTransitionStyle.CURL) {
            latestChapterNavigationRequestConsumed()
            return@LaunchedEffect
        }
        when (request.direction) {
            PageTurnChapterNavigationDirection.PREVIOUS -> turnBackward()
            PageTurnChapterNavigationDirection.NEXT -> turnForward()
        }
        latestChapterNavigationRequestConsumed()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val halfPageSize = IntSize(
            width = with(density) { (maxWidth / 2).roundToPx() }.coerceAtLeast(1),
            height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1),
        )
        val contentPaddingPx = with(density) { contentPadding.roundToPx() }
        val statusBarTopPaddingPx = with(density) { statusBarTopPadding.roundToPx() }
        val snapshotCache = remember(
            halfPageSize,
            transitionStyle,
            readerSettings.fontFamily,
            readerSettings.fontSize,
            readerSettings.lineHeight,
            readerSettings.margin,
            readerSettings.textAlign,
            readerSettings.forceBoldText,
            readerSettings.forceItalicText,
            readerSettings.textShadow,
            readerSettings.textShadowColor,
            readerSettings.textShadowBlur,
            readerSettings.textShadowX,
            readerSettings.textShadowY,
            readerSettings.bionicReading,
            textColor,
            textBackground,
            backgroundTexture,
            nativeTextureStrengthPercent,
            backgroundImageModel,
            backgroundModeIdentity,
            isBackgroundMode,
            activeBackgroundTexture,
            activeOledEdgeGradient,
            isDarkTheme,
            chapterTitleTextColor,
            textTypeface,
            chapterTitleTypeface,
            safeContentPages,
            rendererConfig.backPageColor,
        ) {
            NovelPageTurnSnapshotCache<ImageBitmap>(maxSize = 6)
        }
        // The physical back of a spread page lives in the *other* column's own PageCurl instance (see the class
        // doc). This cache lets each column leave a layer behind, keyed by real page index, for its sibling to
        // pick up when drawing that page's back.
        val backContentLayerCache = rememberSpreadCurlBackContentLayerCache()

        // Forces both columns' draw phases to re-run whenever *either* PageCurlState changes, independent of
        // recomposition. A column that isn't itself being dragged has no read inside its own composition or draw
        // scope that changes when its sibling's `current` does, so Compose correctly sees nothing to redraw —  but
        // that draw pass is also what records that column's own pages into the shared back-content-layer cache
        // (see NovelPageTurnSnapshotRenderer's drawWithContent). A static column that never redraws never
        // (re-)records, so when the dragging column later needs one of its pages as a back layer, the cache holds a
        // GraphicsLayer that was created but never had record{} called into it: a real GraphicsLayer instance
        // (non-null, so the "no layer available" fallback never kicks in) that is simply empty — a 0×0 recording,
        // confirmed on-device via logcat (`drawCurl externalBackContentLayer size=0 x 0`) — which draws as a blank
        // flap. This is the "verso sem conteúdo" bug: not a wrong page index, but a page whose owning column simply
        // never got a chance to paint it during the frames the drag needed it.
        //
        // Reading both states inside a graphicsLayer{} block (a draw-phase lambda) on *each* column's own modifier
        // subscribes that column's draw phase to both, so a change on either side invalidates both columns' draws
        // on the same frame — the dragging column for its own animation, and the idle sibling so it (re-)records
        // the exact page the drag now needs.
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

        Row(modifier = Modifier.fillMaxSize()) {
            SpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .zIndex(if (leftCurlState.progress > 0f) 1f else 0f)
                    .then(crossInvalidatingModifier),
                mirrored = true,
                invertPage = true,
                columnOffset = 0,
                pageCurlState = leftCurlState,
                pageCurlConfig = leftPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                showBoundaryChapterPages = showBoundaryChapterPages,
                previousChapterLabel = previousChapterLabel,
                nextChapterLabel = nextChapterLabel,
                previousChapterName = previousChapterName,
                nextChapterName = nextChapterName,
                boundaryChapterHint = boundaryChapterHint,
                safeContentPages = safeContentPages,
                backContentLayerCache = backContentLayerCache,
                rendererConfig = rendererConfig,
                pageSize = halfPageSize,
                snapshotCache = snapshotCache,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = textBackground,
                chapterTitleTextColor = chapterTitleTextColor,
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                backgroundImageModel = backgroundImageModel,
                backgroundModeIdentity = backgroundModeIdentity,
                isBackgroundMode = isBackgroundMode,
                activeBackgroundTexture = activeBackgroundTexture,
                activeOledEdgeGradient = activeOledEdgeGradient,
                isDarkTheme = isDarkTheme,
                pageEdgeShadow = pageEdgeShadow,
                pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                contentPadding = contentPadding,
                statusBarTopPadding = statusBarTopPadding,
                ttsHighlightState = ttsHighlightState,
                ttsHighlightColor = ttsHighlightColor,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onSelectionRendererActionsChanged = onSelectionRendererActionsChanged,
                selectionClearRequestToken = selectionClearRequestToken,
                selectionExpandRequestToken = selectionExpandRequestToken,
                onTextTap = onTextTap,
            )
            SpreadColumnCurl(
                modifier = Modifier
                    .fillMaxWidth(1f)
                    .zIndex(if (rightCurlState.progress > 0f) 1f else 0f)
                    .then(crossInvalidatingModifier),
                mirrored = false,
                invertPage = false,
                columnOffset = 1,
                pageCurlState = rightCurlState,
                pageCurlConfig = rightPageCurlConfig,
                virtualSlotCount = virtualSlotCount,
                spreadSlotCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
                hasNextChapter = hasNextChapter,
                showBoundaryChapterPages = showBoundaryChapterPages,
                previousChapterLabel = previousChapterLabel,
                nextChapterLabel = nextChapterLabel,
                previousChapterName = previousChapterName,
                nextChapterName = nextChapterName,
                boundaryChapterHint = boundaryChapterHint,
                safeContentPages = safeContentPages,
                backContentLayerCache = backContentLayerCache,
                rendererConfig = rendererConfig,
                pageSize = halfPageSize,
                snapshotCache = snapshotCache,
                contentPaddingPx = contentPaddingPx,
                statusBarTopPaddingPx = statusBarTopPaddingPx,
                readerSettings = readerSettings,
                textColor = textColor,
                textBackground = textBackground,
                chapterTitleTextColor = chapterTitleTextColor,
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                backgroundImageModel = backgroundImageModel,
                backgroundModeIdentity = backgroundModeIdentity,
                isBackgroundMode = isBackgroundMode,
                activeBackgroundTexture = activeBackgroundTexture,
                activeOledEdgeGradient = activeOledEdgeGradient,
                isDarkTheme = isDarkTheme,
                pageEdgeShadow = pageEdgeShadow,
                pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                textTypeface = textTypeface,
                chapterTitleTypeface = chapterTitleTypeface,
                contentPadding = contentPadding,
                statusBarTopPadding = statusBarTopPadding,
                ttsHighlightState = ttsHighlightState,
                ttsHighlightColor = ttsHighlightColor,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                onSelectionRendererActionsChanged = onSelectionRendererActionsChanged,
                selectionClearRequestToken = selectionClearRequestToken,
                selectionExpandRequestToken = selectionExpandRequestToken,
                onTextTap = onTextTap,
            )
        }
    }
}

/** A snap()-driven jump: the paired, non-animating side of a turn lands on its new page instantly. */
private fun instantPageTurnAnimation(): suspend Animatable<Edge, AnimationVector4D>.(Size) -> Unit {
    return { size ->
        animateTo(targetValue = size.startEdge(), animationSpec = snap())
    }
}

private fun handleSpreadCustomTap(
    size: IntSize,
    offset: Offset,
    isRightSide: Boolean,
    currentSpreadSlot: Int,
    spreadSlotCount: Int,
    latestCustomTapZonesEnabled: Boolean,
    latestTapZoneActions: List<NovelReaderTapZoneAction>,
    latestTapToScrollEnabled: Boolean,
    latestRendererConfig: NovelPageTurnRendererConfig,
    transitionStyle: NovelPageTransitionStyle,
    latestToggleUi: () -> Unit,
    onTurnForward: () -> Unit,
    onTurnBackward: () -> Unit,
    latestOpenPreviousChapter: () -> Unit,
    latestOpenNextChapter: () -> Unit,
): Boolean {
    val fullWidth = size.width * 2f
    val absoluteTapX = if (isRightSide) {
        size.width + offset.x
    } else {
        size.width - offset.x
    }

    val customTapAction = if (latestCustomTapZonesEnabled) {
        resolvePageTurnConfiguredTapAction(
            zoneAction = resolveConfiguredNovelReaderTapAction(
                tapX = absoluteTapX,
                tapY = offset.y,
                width = fullWidth,
                height = size.height.toFloat(),
                customTapZonesEnabled = true,
                tapZoneActions = latestTapZoneActions,
                tapToScrollEnabled = latestTapToScrollEnabled,
            ),
            currentPage = currentSpreadSlot,
            pageCount = spreadSlotCount.coerceAtLeast(1),
            hasPreviousChapter = latestRendererConfig.dragBackwardEnabled,
            hasNextChapter = latestRendererConfig.dragForwardEnabled,
            animateBoundaryTransition = transitionStyle == NovelPageTransitionStyle.CURL,
        )
    } else {
        resolvePageTurnCustomTapAction(
            tapXFraction = if (fullWidth > 0) absoluteTapX / fullWidth else 0.5f,
            currentPage = currentSpreadSlot,
            pageCount = spreadSlotCount.coerceAtLeast(1),
            centerTapWidthFraction = latestRendererConfig.centerTapWidthFraction,
            hasPreviousChapter = latestRendererConfig.dragBackwardEnabled,
            hasNextChapter = latestRendererConfig.dragForwardEnabled,
            tapToScrollEnabled = latestTapToScrollEnabled,
            animateBoundaryTransition = transitionStyle == NovelPageTransitionStyle.CURL,
        )
    }
    return when (customTapAction) {
        PageTurnCustomTapAction.TOGGLE_UI -> {
            latestToggleUi()
            true
        }
        PageTurnCustomTapAction.MOVE_PREVIOUS_PAGE -> {
            onTurnBackward()
            true
        }
        PageTurnCustomTapAction.MOVE_NEXT_PAGE -> {
            onTurnForward()
            true
        }
        PageTurnCustomTapAction.OPEN_PREVIOUS_CHAPTER -> {
            latestOpenPreviousChapter()
            true
        }
        PageTurnCustomTapAction.OPEN_NEXT_CHAPTER -> {
            latestOpenNextChapter()
            true
        }
        PageTurnCustomTapAction.NONE -> false
    }
}

/**
 * Real (flat, 0-indexed into safeContentPages) content page index shown at [virtualPage] within one
 * [SpreadColumnCurl] column, or null when [virtualPage] falls on a chapter-boundary placeholder slot (no real
 * page) or outside the valid virtual range.
 */
private fun resolveSpreadColumnRealPageIndex(
    virtualPage: Int,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    showBoundaryChapterPages: Boolean,
    invertPage: Boolean,
    columnOffset: Int,
): Int? {
    if (virtualPage < 0 || virtualPage >= virtualSlotCount) return null
    val actualPage = if (invertPage) (virtualSlotCount - 1 - virtualPage).coerceAtLeast(0) else virtualPage
    if (showBoundaryChapterPages) {
        val boundaryTarget = resolveNovelPageTurnRendererBoundaryChapterTarget(
            currentPage = actualPage,
            contentPageCount = spreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
        )
        if (boundaryTarget != HorizontalChapterSwipeAction.NONE) return null
    }
    val spreadSlot = resolvePageTurnRendererProgressPageIndex(
        currentPage = actualPage,
        contentPageCount = spreadSlotCount,
        hasPreviousChapter = hasPreviousChapter,
    )
    return resolveSpreadSlotFirstPageIndex(spreadSlot, 2) + columnOffset
}

@Composable
private fun SpreadColumnCurl(
    modifier: Modifier,
    // eu.wewox.pagecurl.page.PageCurl's own drag/animation geometry always references the widget's
    // right edge (NewEdgeCreator anchors on size.width, PageCurlState.setup's forward Animatable
    // always runs right-to-left): using the library's backward mechanism unmirrored opens the
    // current page from its own left edge outward instead of bringing a new page in from the left
    // like a book leaf. Mirroring the whole widget horizontally (and un-mirroring its content) makes
    // the library's own right-referenced geometry land on the screen's actual left edge, so this
    // surface's fold anchors at the spine the same way the unmirrored right surface already does.
    mirrored: Boolean,
    invertPage: Boolean,
    columnOffset: Int,
    pageCurlState: PageCurlState,
    pageCurlConfig: PageCurlConfig,
    virtualSlotCount: Int,
    spreadSlotCount: Int,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    showBoundaryChapterPages: Boolean,
    previousChapterLabel: String,
    nextChapterLabel: String,
    previousChapterName: String?,
    nextChapterName: String?,
    boundaryChapterHint: String,
    safeContentPages: List<NovelPageContentPage>,
    backContentLayerCache: SpreadCurlBackContentLayerCache,
    rendererConfig: NovelPageTurnRendererConfig,
    pageSize: IntSize,
    snapshotCache: NovelPageTurnSnapshotCache<ImageBitmap>,
    contentPaddingPx: Int,
    statusBarTopPaddingPx: Int,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    chapterTitleTextColor: Color,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    backgroundImageModel: Any?,
    backgroundModeIdentity: String,
    isBackgroundMode: Boolean,
    activeBackgroundTexture: NovelReaderBackgroundTexture,
    activeOledEdgeGradient: Boolean,
    isDarkTheme: Boolean,
    pageEdgeShadow: Boolean,
    pageEdgeShadowAlpha: Float,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    ttsHighlightState: NovelReaderTtsHighlightState?,
    ttsHighlightColor: Color,
    selectionSessionIdProvider: () -> Long,
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    onSelectionRendererActionsChanged: (eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRendererActions) -> Unit,
    selectionClearRequestToken: Int,
    selectionExpandRequestToken: Int,
    onTextTap: (Float, Float, Float, Float) -> Unit,
) {
    val curlModifier = if (mirrored) {
        modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
    } else {
        modifier.fillMaxSize()
    }

    // The physical back of a page is fixed by its identity, like the two sides of the same sheet of paper in a
    // real book — real page N (odd, right column) always has real page N + 1 (even, left column) printed on its
    // back, and vice-versa. This holds regardless of which direction the page is being turned.
    //
    // Both slots are populated. Which flap a gesture drives is not fixed the way an earlier version of this comment
    // assumed: a real finger-drag goes through the library's own DragStartEnd/detectCurlGestures path rather than
    // the turnForward()/turnBackward() helpers, so relying on "only the forward flap is ever shown" left the
    // backward flap with a null layer and rendered it fully transparent. Since the physical back of a sheet is
    // fixed by the sheet's identity — real page N always has N+1 (right column) or N-1 (left column) printed on its
    // reverse, whichever way it is being turned — the same layer is correct for both slots.
    //
    // Resolved off this instance's own current virtual page, since externalBackContentLayers has to be ready
    // before PageCurl composes its content(Int) lambda.
    val externalBackContentLayers = if (pageCurlConfig.independentBackPageEnabled) {
        // Each flap turns a *different* sheet, so each needs its own back page — they are not the same layer.
        // In PageCurl the forward flap wraps content(current) while the backward flap wraps content(current - 1)
        // (see PageCurl.kt), so resolving both from `current` handed the backward flap the wrong sheet's reverse:
        // a page this column never composes, whose layer therefore stays empty and draws as a blank flap. That is
        // the "verso sem conteúdo" on backward turns.
        //
        // Resolve every endpoint through the same slot→real-page mapping rather than deriving one from another
        // with a ±1 on the *real* index: on the mirrored left column `invertPage` flips the slot space, so slot
        // `current + 1` is the previous spread, and a raw ±1 on the real index lands on a page the column never
        // registers.
        fun realPageAtSlot(slot: Int): Int? = resolveSpreadColumnRealPageIndex(
            virtualPage = slot,
            virtualSlotCount = virtualSlotCount,
            spreadSlotCount = spreadSlotCount,
            hasPreviousChapter = hasPreviousChapter,
            hasNextChapter = hasNextChapter,
            showBoundaryChapterPages = showBoundaryChapterPages,
            invertPage = invertPage,
            columnOffset = columnOffset,
        )

        // The reverse of a turning sheet is whichever real page sits next to it in reading order, on the side away
        // from the page revealed underneath: the revealed one is across the spine, so the sheet's own back is the
        // page between the two.
        //
        // getOrCreate, not get: within a Row the left column composes before the right one, so on the first frame
        // after any change the sibling that owns this page has not recorded into the cache yet. A plain read would
        // return null (transparent flap) and never recover — the cache only mutates when a key is first inserted,
        // so no later write would invalidate this read and force a recomposition. Creating the entry here hands
        // both columns the same stable GraphicsLayer object: this column draws it, the owning column fills it in
        // during its own draw pass later in the very same frame.
        fun backLayerForTurningSlot(turningSlot: Int): GraphicsLayer? {
            val turningRealPage = realPageAtSlot(turningSlot) ?: return null
            val revealedRealPage = realPageAtSlot(turningSlot + 1) ?: return null
            val backRealPage = if (revealedRealPage > turningRealPage) {
                turningRealPage + 1
            } else {
                turningRealPage - 1
            }
            return backRealPage
                .takeIf { it in safeContentPages.indices }
                ?.let { registeredSpreadCurlBackContentLayer(backContentLayerCache, it) }
        }

        ExternalBackContentLayers(
            forward = backLayerForTurningSlot(pageCurlState.current),
            backward = backLayerForTurningSlot(pageCurlState.current - 1),
        )
    } else {
        null
    }

    PageCurl(
        count = virtualSlotCount,
        key = { it },
        state = pageCurlState,
        config = pageCurlConfig,
        externalBackContentLayers = externalBackContentLayers,
        // curlModifier flips this whole column when `mirrored`, which already supplies the flip the back-page
        // layer needs — so drawCurl must not apply its own on top of it.
        onMirroredSurface = mirrored,
        modifier = curlModifier,
    ) { page ->
        val actualPage = if (invertPage) (virtualSlotCount - 1 - page).coerceAtLeast(0) else page
        val boundaryPreview = if (!showBoundaryChapterPages) {
            null
        } else {
            when (
                resolveNovelPageTurnRendererBoundaryChapterTarget(
                    currentPage = actualPage,
                    contentPageCount = spreadSlotCount,
                    hasPreviousChapter = hasPreviousChapter,
                    hasNextChapter = hasNextChapter,
                )
            ) {
                HorizontalChapterSwipeAction.PREVIOUS,
                HorizontalChapterSwipeAction.NEXT,
                -> {
                    createNovelPageBoundaryPreviewData(
                        chapterLabel = if (actualPage <= 0) previousChapterLabel else nextChapterLabel,
                        chapterName = if (actualPage <= 0) previousChapterName else nextChapterName,
                        chapterHint = boundaryChapterHint,
                    )
                }
                HorizontalChapterSwipeAction.NONE -> null
            }
        }
        val contentPage = if (boundaryPreview == null) {
            val spreadSlot = resolvePageTurnRendererProgressPageIndex(
                currentPage = actualPage,
                contentPageCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            val firstPage = resolveSpreadSlotFirstPageIndex(spreadSlot, 2)
            safeContentPages.getOrElse(firstPage + columnOffset) { NovelPageContentPage(emptyList()) }
        } else {
            NovelPageContentPage(emptyList())
        }
        val pageTexture = if (isBackgroundMode) activeBackgroundTexture else backgroundTexture
        val pageTextureStrengthPercent = if (isBackgroundMode) 0 else nativeTextureStrengthPercent
        val pageSurfaceColor = if (isBackgroundMode) null else rendererConfig.backPageColor
        val pageContentIdentity = boundaryPreview ?: contentPage
        val pageSnapshotKey = resolveNovelPageTurnSnapshotKey(
            style = rendererConfig.style,
            // The two surfaces address independent PageCurl instances with their own page-index
            // spaces, so columnOffset has to be part of the key or they would collide in the
            // shared cache despite showing different halves of the spread.
            pageIndex = actualPage * 2 + columnOffset,
            pageCount = virtualSlotCount * 2,
            pageContentHash = pageContentIdentity.hashCode(),
            pageSize = pageSize,
            fontFamilyKey = readerSettings.fontFamily,
            chapterTitleFontFamilyKey = chapterTitleTypeface?.hashCode()?.toString().orEmpty(),
            chapterTitleTextColor = chapterTitleTextColor,
            fontSize = readerSettings.fontSize,
            lineHeight = readerSettings.lineHeight,
            margin = readerSettings.margin,
            contentPaddingPx = contentPaddingPx,
            statusBarTopPaddingPx = statusBarTopPaddingPx,
            textAlign = if (boundaryPreview != null) {
                TextAlign.CENTER
            } else {
                readerSettings.textAlign
            },
            textColor = textColor,
            textBackground = textBackground,
            pageSurfaceColor = pageSurfaceColor ?: Color.Transparent,
            isBackgroundMode = isBackgroundMode,
            backgroundImageIdentity = backgroundModeIdentity,
            backgroundTextureName = pageTexture.name,
            nativeTextureStrengthPercentEffective = pageTextureStrengthPercent,
            oledEdgeGradient = if (isBackgroundMode) activeOledEdgeGradient else false,
            isDarkTheme = isDarkTheme,
            pageEdgeShadow = pageEdgeShadow,
            pageEdgeShadowAlpha = pageEdgeShadowAlpha,
            backgroundTexture = pageTexture,
            nativeTextureStrengthPercent = pageTextureStrengthPercent,
            forceBoldText = readerSettings.forceBoldText,
            forceItalicText = readerSettings.forceItalicText,
            textShadow = readerSettings.textShadow,
            textShadowColor = readerSettings.textShadowColor,
            textShadowBlur = readerSettings.textShadowBlur,
            textShadowX = readerSettings.textShadowX,
            textShadowY = readerSettings.textShadowY,
            bionicReading = readerSettings.bionicReading,
        )
        // Deliberately NOT mirrored here, even on the mirrored (left) column. The whole column is already flipped
        // by SpreadColumnCurl's curlModifier (graphicsLayer(scaleX = -1f) on the PageCurl itself), and this inner
        // flip existed to cancel that back out so the text reads the right way round on screen. But
        // NovelPageTurnSnapshotRenderer records its GraphicsLayer from *inside* whatever modifier chain it is
        // given, so an inner flip here means the left column's cached layer is captured in flipped space — and
        // that cache is exactly what the sibling column paints as its back-of-page. The right column's own layers
        // are captured unflipped, which is why turning forward (right column's flap, reading the left column's
        // layer) looked perfect while turning backward (left column's flap, reading the right column's layer) did
        // not: only one of the two directions had a flip to cancel.
        //
        // Un-mirroring is instead applied per-draw in mirroredContentModifier below, which leaves the recorded
        // layer in the same upright space for both columns.
        val contentModifier = Modifier.fillMaxSize()
        // Real page index this column is about to draw as front content, or null on a boundary placeholder slot.
        // Captured (after un-mirroring, so it holds the same upright pixels a native, unmirrored draw of that page
        // would produce) into the shared cache so the sibling column can paint it as a back-of-page layer.
        val frontRealPageIndex = if (boundaryPreview == null) {
            val spreadSlot = resolvePageTurnRendererProgressPageIndex(
                currentPage = actualPage,
                contentPageCount = spreadSlotCount,
                hasPreviousChapter = hasPreviousChapter,
            )
            resolveSpreadSlotFirstPageIndex(spreadSlot, 2) + columnOffset
        } else {
            null
        }
        // NovelPageTurnSnapshotRenderer(preferCachedBitmap = false) already records this page's draw output into
        // its own GraphicsLayer every frame. Rather than wrapping it in a second, independently-recorded layer
        // (which doubled the capture work and was a source of stale/torn frames), hand it the shared cache's layer
        // directly via externalGraphicsLayer so there is exactly one recording of this page's pixels, which the
        // sibling column can also read.
        val backContentLayer = if (pageCurlConfig.independentBackPageEnabled && frontRealPageIndex != null) {
            registeredSpreadCurlBackContentLayer(
                cache = backContentLayerCache,
                realPageIndex = frontRealPageIndex,
            )
        } else {
            null
        }
        // The un-mirror lives here, *outside* NovelPageTurnSnapshotRenderer, so it applies to how this page is
        // displayed without ever entering the layer that renderer records — see the note on contentModifier above.
        val mirroredContentModifier = if (mirrored) {
            Modifier.fillMaxSize().graphicsLayer(scaleX = -1f)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = mirroredContentModifier) {
            NovelPageTurnSnapshotRenderer(
                snapshotKey = pageSnapshotKey,
                snapshotCache = snapshotCache,
                preferCachedBitmap = false,
                externalGraphicsLayer = backContentLayer,
                modifier = contentModifier,
            ) {
                NovelAtmosphereBackground(
                    backgroundColor = textBackground,
                    backgroundTexture = pageTexture,
                    nativeTextureStrengthPercent = pageTextureStrengthPercent,
                    oledEdgeGradient = activeOledEdgeGradient,
                    isDarkTheme = isDarkTheme,
                    pageEdgeShadow = pageEdgeShadow,
                    pageEdgeShadowAlpha = pageEdgeShadowAlpha,
                    backgroundImageModel = backgroundImageModel,
                )
                if (boundaryPreview != null) {
                    NovelPageBoundaryPreviewContent(
                        preview = boundaryPreview,
                        textColor = textColor,
                        chapterTitleTextColor = chapterTitleTextColor,
                        textBackground = textBackground,
                        contentPadding = contentPadding,
                        statusBarTopPadding = statusBarTopPadding,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                    )
                } else {
                    NovelPageReaderPageContent(
                        contentPage = contentPage,
                        readerSettings = readerSettings,
                        textColor = textColor,
                        textBackground = textBackground,
                        pageSurfaceColor = pageSurfaceColor,
                        backgroundTexture = pageTexture,
                        nativeTextureStrengthPercent = pageTextureStrengthPercent,
                        chapterTitleTextColor = chapterTitleTextColor,
                        textTypeface = textTypeface,
                        chapterTitleTypeface = chapterTitleTypeface,
                        textShadowEnabled = readerSettings.textShadow,
                        textShadowColor = readerSettings.textShadowColor,
                        textShadowBlur = readerSettings.textShadowBlur,
                        textShadowX = readerSettings.textShadowX,
                        textShadowY = readerSettings.textShadowY,
                        contentPadding = contentPadding,
                        statusBarTopPadding = statusBarTopPadding,
                        ttsHighlightState = ttsHighlightState,
                        ttsHighlightColor = ttsHighlightColor,
                        selectionSessionIdProvider = selectionSessionIdProvider,
                        onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                        onSelectionRendererActionsChanged = onSelectionRendererActionsChanged,
                        selectionClearRequestToken = selectionClearRequestToken,
                        selectionExpandRequestToken = selectionExpandRequestToken,
                        onPlainTap = onTextTap,
                        touchHandlingEnabled = readerSettings.textSelectionEnabled ||
                            readerSettings.selectedTextTranslationEnabled ||
                            readerSettings.novelDictionaryEnabled,
                    )
                }
            }
        }
    }
}
