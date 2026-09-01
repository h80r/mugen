@file:OptIn(ExperimentalPageCurlApi::class)

package eu.kanade.tachiyomi.ui.reader.viewer.curl

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.reader.curl.ExperimentalPageCurlApi
import eu.kanade.presentation.reader.curl.ExternalFold
import eu.kanade.presentation.reader.curl.ExternalFoldDirection
import eu.kanade.presentation.reader.curl.MangaPageCurlRenderer
import eu.kanade.presentation.reader.curl.MangaSpreadCurlRenderer
import eu.kanade.presentation.reader.curl.SpreadColumn
import eu.kanade.presentation.reader.curl.SpreadCurlDiagnostics
import eu.kanade.presentation.reader.curl.resolveMangaPageCurlRendererConfig
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderPreloadManager
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.AutoScrollableViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderImageProcessingConfig
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.AutoScrollTarget
import eu.kanade.tachiyomi.ui.reader.viewer.autoscroll.PagerAutoScrollManager
import eu.kanade.tachiyomi.ui.reader.viewer.pager.indexOfPageOrJoined
import eu.kanade.tachiyomi.ui.reader.viewer.processReaderImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import okio.source
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

/**
 * Reading direction the curl viewer was built for. Mirrors the three pager reading modes
 * ([eu.kanade.tachiyomi.ui.reader.setting.ReadingMode]); webtoon modes never reach this viewer.
 */
enum class ReadingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    VERTICAL,
    ;

    val isR2L: Boolean get() = this == RIGHT_TO_LEFT
    val isVertical: Boolean get() = this == VERTICAL
}

/**
 * A [Viewer] that renders manga pages with the shared Compose page-curl, selected when the
 * `pageCurl` reader preference is on and the reading mode is a pager type. The legacy
 * [eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer] is left untouched and remains what
 * the preference's off state selects.
 *
 * This class is the five-method seam; the curl composition itself lives in [MangaCurlView].
 */
class MangaCurlViewer(
    val activity: ReaderActivity,
    val direction: ReadingDirection,
) : Viewer, AutoScrollableViewer, AutoScrollTarget {

    override val isPagerViewer: Boolean = true
    override val isRtl: Boolean = direction.isR2L

    private val scope = MainScope()

    private val readerPreferences: ReaderPreferences by injectLazy()
    private val uiPreferences: UiPreferences by injectLazy()

    private val navigation = MangaCurlNavigation(
        readerPreferences = readerPreferences,
        isVertical = direction.isVertical,
        scope = scope,
    )

    /** Reused from the pager: a timer-based page-advance manager driving [moveToNext]. */
    private val autoScrollManager = PagerAutoScrollManager(this)

    private val view = MangaCurlView(activity).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        onNavigationTap = ::onNavigationTap
        onLongTap = ::onLongTap
        onCurrentSlotChange = ::onRendererSlotChange
        onOpenPreviousChapter = {
            pendingBoundaryEntry = BoundaryEntry.FROM_NEXT
            scope.launch { activity.viewModel.loadPreviousChapter() }
        }
        onOpenNextChapter = {
            pendingBoundaryEntry = BoundaryEntry.FROM_PREVIOUS
            scope.launch { activity.viewModel.loadNextChapter() }
        }
        onWideDetected = ::onWidePageDetected
        onRequestSplit = ::onRequestSplit
        onIdleChanged = ::onIdleChanged
    }

    /**
     * Flat item list — [ReaderPage], [eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage] and
     * [ChapterTransition] — built by [buildMangaCurlItems], which mirrors
     * `PagerViewerAdapter.setChapters` and delegates pairing to the shared `groupPagesForDoublePage`.
     */
    private var items: List<Any> = emptyList()

    /** Index into [items] of the currently displayed page/spread. */
    private var currentIndex: Int = 0

    /** Mirrors `PagerViewerAdapter.nextTransition`; used for chapter preload in task 2.11. */
    private var nextTransition: ChapterTransition.Next? = null

    /** The chapter whose real pages the renderer is currently laying out as content slots. */
    private var currChapterPages: List<ReaderPage> = emptyList()

    /** The item currently reported as active, for page-change dedupe (mirrors `PagerViewer.currentPage`). */
    private var currentItem: Any? = null

    /** The chapter [currChapterPages] belongs to, so preload can compare against `nextTransition`. */
    private var currentChapter: ReaderChapter? = null

    /** Last chapters set, kept so a mid-chapter re-list (page split) can re-push the render state. */
    private var lastChapters: ViewerChapters? = null

    /** True while a fold is in flight; re-listing is deferred until it settles (see [onIdleChanged]). */
    private var isIdle: Boolean = true

    /** Chapters set while a fold was animating, applied once [isIdle] flips back — like
     * `PagerViewer.awaitingIdleViewerChapters`. */
    private var awaitingIdleChapters: ViewerChapters? = null

    /**
     * How the next chapter is being entered, when the reader walked off a chapter boundary.
     *
     * The legacy pager gets this for free: its adapter keeps the neighbouring chapters' edge pages
     * contiguous, so crossing a boundary is just a scroll and the new chapter is already positioned.
     * The curl rebuilds its slots per chapter instead, and `requestedPage` still holds the position
     * the reader left that chapter at — using it here lands mid-chapter instead of at the edge just
     * crossed.
     */
    private var pendingBoundaryEntry: BoundaryEntry? = null

    /** Which edge of a newly opened chapter to land on. */
    private enum class BoundaryEntry {
        /** Walked forward off the previous chapter's end: land on this chapter's first page. */
        FROM_PREVIOUS,

        /** Walked backward off the next chapter's start: land on this chapter's last page. */
        FROM_NEXT,
    }

    override fun getView(): View = view

    override fun destroy() {
        autoScrollManager.stop()
        view.dispose()
        scope.cancel()
    }

    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            applyChapters(chapters)
        } else {
            // A fold is animating — defer so the item list doesn't shift under the gesture.
            awaitingIdleChapters = chapters
        }
    }

    /** Renderer idle-state changes: fold started (`false`) or settled (`true`). */
    private fun onIdleChanged(idle: Boolean) {
        isIdle = idle
        if (idle) {
            awaitingIdleChapters?.let {
                awaitingIdleChapters = null
                applyChapters(it)
            }
        }
    }

    /**
     * @param keepPage when non-null, stay on this page instead of the chapter's requested page —
     *   used by a mid-chapter rebuild (a wide page dissolving its spread) so the reader does not
     *   jump.
     */
    private fun applyChapters(chapters: ViewerChapters, keepPage: ReaderPage? = null) {
        lastChapters = chapters
        val built = buildMangaCurlItems(
            chapters = chapters,
            config = MangaCurlItemConfig(
                joinDoublePages = readerPreferences.joinDoublePages().get(),
                shiftDoublePages = readerPreferences.shiftDoublePages().get(),
                alwaysShowChapterTransition = readerPreferences.alwaysShowChapterTransition().get(),
            ),
            direction = direction,
            orientation = activity.resources.configuration.orientation,
        )
        items = built.items
        nextTransition = built.nextTransition

        val currChapter = chapters.currChapter
        val currentChapterIdBefore = currentChapter?.chapter?.id
        currentChapter = currChapter
        currentItem = null
        val pages = currChapter.pages.orEmpty()
        currChapterPages = pages
        val keptSlot = keepPage?.let { kept -> pages.indexOfFirst { it === kept } }?.takeIf { it >= 0 }
        // A chapter entered by walking off a boundary starts at the edge just crossed, not at the
        // position the reader last left it at (see pendingBoundaryEntry).
        val boundaryEntry = pendingBoundaryEntry?.takeIf { currChapter.chapter.id != currentChapterIdBefore }
        pendingBoundaryEntry = null
        val startSlot = when {
            pages.isEmpty() -> 0
            keptSlot != null -> keptSlot
            boundaryEntry == BoundaryEntry.FROM_PREVIOUS -> 0
            boundaryEntry == BoundaryEntry.FROM_NEXT -> pages.lastIndex
            else -> min(currChapter.requestedPage, pages.lastIndex).coerceAtLeast(0)
        }
        currentIndex = if (pages.isEmpty()) 0 else indexOfPageOrJoined(items, pages[startSlot]).coerceAtLeast(0)

        pushRenderState(chapters, currChapter, startSlot)
        logcat { "MangaCurlViewer.setChapters: ${items.size} items, startSlot=$startSlot" }
    }

    override fun moveToPage(page: ReaderPage) {
        val slot = currChapterPages.indexOfFirst { it === page }
        if (slot != -1) {
            currentIndex = indexOfPageOrJoined(items, page).coerceAtLeast(0)
            view.moveToSlot(slot)
        } else {
            logcat { "MangaCurlViewer.moveToPage: ${page.number} not in current chapter" }
        }
    }

    /** Advances one slot; the renderer handles chapter-boundary slots itself. Also the auto-scroll tick. */
    override fun moveToNext() {
        // At the last page with no next chapter, PagerViewer lands on a dead-end transition and
        // shows the menu; the curl has no such slot, so surface the menu here instead.
        if (nextTransition?.to == null && currentItem === currChapterPages.lastOrNull()) {
            activity.showMenu()
            return
        }
        view.turn(forward = true)
    }

    /** Steps back one slot. */
    fun moveToPrevious() = view.turn(forward = false)

    // AutoScrollableViewer — delegates to the shared PagerAutoScrollManager, which ticks moveToNext().
    override fun startAutoScroll(speed: Int?) = autoScrollManager.start(speed)

    override fun stopAutoScroll() = autoScrollManager.stop()

    override fun setAutoScrollCooldown(delayMs: Long) = autoScrollManager.setCooldown(delayMs)

    /**
     * Called by the renderer once a real content slot settles. Mirrors `PagerViewer.onPageChange`:
     * dedupes on [currentItem], resolves forward/backward, reports via `activity.onPageSelected`
     * and requests next-chapter preload when near the end of the chapter.
     */
    private fun onRendererSlotChange(slot: Int) {
        val page = currChapterPages.getOrNull(slot) ?: return
        if (currentItem === page) return

        val allowPreload = checkAllowPreload(page)
        currentItem = page
        currentIndex = indexOfPageOrJoined(items, page).coerceAtLeast(0)

        activity.onPageSelected(page)
        if (page !is InsertPage) {
            checkAndPreload(page, allowPreload)
        }
    }

    /** Same allow-list as `PagerViewer.checkAllowPreload`. */
    private fun checkAllowPreload(page: ReaderPage): Boolean {
        val current = currentItem ?: return true
        return when (page.chapter) {
            (current as? ChapterTransition.Next)?.to -> true
            (current as? ReaderPage)?.chapter -> true
            nextTransition?.to -> true
            else -> false
        }
    }

    /** Same threshold check as `PagerViewer.checkAndPreload`. */
    private fun checkAndPreload(page: ReaderPage, allowPreload: Boolean) {
        val pages = page.chapter.pages ?: return
        val inPreloadRange = pages.size - page.number < ReaderPreloadManager.nextChapterPreloadThreshold
        if (readerPreferences.preloadNextChapter().get() &&
            inPreloadRange &&
            allowPreload &&
            page.chapter == currentChapter
        ) {
            nextTransition?.to?.let(activity::requestPreloadChapter)
        }
    }

    /**
     * Resolves a tap at ([x], [y]) — 0..1 of the viewport — through the reader's `ViewerNavigation`
     * (all nav modes, `pagerNavInverted`, custom tap zones) and performs the action. LEFT/RIGHT map
     * to a physical-direction turn; NEXT/PREV to a reading-direction turn.
     */
    private fun onNavigationTap(x: Float, y: Float): Boolean {
        val region = navigation.getAction(x, y)
        // The nav region a tap resolves to, and the turn it becomes. Problem 2 is a drag going the
        // opposite way from a tap at the same place: this line is the tap half of that comparison,
        // and `drag.zone` above is the other half.
        val turn = when (region) {
            NavigationRegion.MENU -> "menu"
            NavigationRegion.NEXT -> "forward"
            NavigationRegion.PREV -> "backward"
            NavigationRegion.RIGHT -> if (direction.isR2L) "backward" else "forward"
            NavigationRegion.LEFT -> if (direction.isR2L) "forward" else "backward"
            NavigationRegion.NONE -> "none"
        }
        SpreadCurlDiagnostics.log(
            "nav.tap",
            "xFrac=${SpreadCurlDiagnostics.f2(x)} yFrac=${SpreadCurlDiagnostics.f2(y)} " +
                "region=$region turn=$turn direction=$direction",
        )
        when (region) {
            NavigationRegion.MENU -> activity.toggleMenu()
            NavigationRegion.NEXT -> moveToNext()
            NavigationRegion.PREV -> moveToPrevious()
            NavigationRegion.RIGHT -> if (direction.isR2L) moveToPrevious() else moveToNext()
            NavigationRegion.LEFT -> if (direction.isR2L) moveToNext() else moveToPrevious()
            NavigationRegion.NONE -> return false
        }
        return true
    }

    private fun onLongTap() {
        if (!activity.viewModel.state.value.menuVisible && !readerPreferences.readWithLongTap().get()) return
        val page = currChapterPages.getOrNull(currChapterSlotOfCurrentIndex()) ?: return
        activity.onPageLongTap(page)
    }

    /** Real content slot the viewer's [currentIndex] (an [items] index) currently points at. */
    private fun currChapterSlotOfCurrentIndex(): Int {
        val item = items.getOrNull(currentIndex) as? ReaderPage ?: return 0
        return currChapterPages.indexOfFirst { it === item }.coerceAtLeast(0)
    }

    /**
     * The current chapter's spread slots, read straight off the grouped [items] so the pairing, the
     * R2L swap and `shiftDoublePages` that `groupPagesForDoublePage` already applied are preserved.
     *
     * `JoinedReaderPage.firstPage` always occupies the left slot and `secondPage` the right, in
     * both directions — `groupPagesForDoublePage` has already applied the R2L swap when building the
     * pair. The R2L list reversal is likewise not undone here: the renderer's own mirroring turns
     * the spread order around, so walking the list back to reading order would invert it twice.
     */
    private fun buildSpreadSlots(currChapter: ReaderChapter): List<MangaCurlSpreadSlot> {
        val chapterId = currChapter.chapter.id
        // Reading order: R2L reversed the whole list at build time, so undo just that ordering.
        val ordered = if (direction.isR2L) items.asReversed() else items
        return ordered.mapNotNull { item ->
            when (item) {
                is JoinedReaderPage -> MangaCurlSpreadSlot(left = item.firstPage, right = item.secondPage)
                is ReaderPage -> MangaCurlSpreadSlot(left = item, right = null)
                else -> null
            }
        }.filter { slot ->
            // Only this chapter's pages: the item list also carries the neighbouring chapters'
            // edge pages, which the renderer represents as boundary slots instead.
            (slot.left ?: slot.right)?.chapter?.chapter?.id == chapterId
        }
    }

    private fun pushRenderState(chapters: ViewerChapters, currChapter: ReaderChapter, startSlot: Int) {
        view.submit(
            MangaCurlRenderState(
                pages = currChapterPages,
                startSlot = startSlot,
                hasPreviousChapter = chapters.prevChapter != null,
                hasNextChapter = chapters.nextChapter != null,
                chapterId = currChapter.chapter.id ?: 0L,
                // Same rule as ViewerConfig.usePageTransitions: transitions are always on unless
                // e-ink mode is active and the user turned them off.
                usePageTransitions = readerPreferences.pageTransitions().get() ||
                    !uiPreferences.eInkProfile().get().isEnabled,
                imageProcessing = ReaderImageProcessingConfig(
                    dualPageSplit = readerPreferences.dualPageSplitPaged().get(),
                    dualPageInvert = readerPreferences.dualPageInvertPaged().get(),
                    dualPageRotateToFit = readerPreferences.dualPageRotateToFit().get(),
                    dualPageRotateToFitInvert = readerPreferences.dualPageRotateToFitInvert().get(),
                    joinDoublePages = readerPreferences.joinDoublePages().get(),
                ),
                isL2R = direction == ReadingDirection.LEFT_TO_RIGHT,
                mirrored = direction.isR2L,
                cropBorders = readerPreferences.cropBorders().get(),
                zoomConfig = MangaCurlZoomConfig(
                    zoomDuration = readerPreferences.doubleTapAnimSpeed().get(),
                    minimumScaleType = readerPreferences.imageScaleType().get(),
                    cropBorders = readerPreferences.cropBorders().get(),
                    zoomStartPosition = resolveZoomStartPosition(readerPreferences.zoomStart().get()),
                    landscapeZoom = readerPreferences.landscapeZoom().get(),
                    enablePinchToZoom = readerPreferences.enablePinchToZoom().get(),
                ),
                // Same condition groupPagesForDoublePage applies: joined pages only exist in
                // landscape with the preference on. Vertical reading never spreads.
                spread = readerPreferences.joinDoublePages().get() &&
                    !direction.isVertical &&
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                spreadSlots = buildSpreadSlots(currChapter),
            ),
        )
    }

    /** Same mapping as `PagerConfig.zoomTypeFromPreference`. */
    private fun resolveZoomStartPosition(value: Int): ReaderPageImageView.ZoomStartPosition = when (value) {
        1 -> when (direction) {
            ReadingDirection.LEFT_TO_RIGHT -> ReaderPageImageView.ZoomStartPosition.LEFT
            ReadingDirection.RIGHT_TO_LEFT -> ReaderPageImageView.ZoomStartPosition.RIGHT
            ReadingDirection.VERTICAL -> ReaderPageImageView.ZoomStartPosition.CENTER
        }
        2 -> ReaderPageImageView.ZoomStartPosition.LEFT
        3 -> ReaderPageImageView.ZoomStartPosition.RIGHT
        else -> ReaderPageImageView.ZoomStartPosition.CENTER
    }

    /**
     * A page turned out to be wide while decoding. `page.isWide` has already been set by
     * `processReaderImage`, so rebuilding the item list re-runs `groupPagesForDoublePage` with that
     * knowledge and dissolves the spread the page was paired into — otherwise it would render as
     * half a fold. Mirrors how the legacy pager reacts by refreshing its adapter.
     *
     * Only meaningful while spreads are being built at all; with `joinDoublePages` off the grouping
     * is a no-op and rebuilding would just churn.
     */
    private fun onWidePageDetected(page: ReaderPage) {
        if (!readerPreferences.joinDoublePages().get()) return
        val chapter = currentChapter ?: return
        if (page.chapter.chapter.id != chapter.chapter.id) return
        val chapters = lastChapters ?: return

        // Never re-list mid-fold: the deferral in onIdleChanged re-applies once the gesture settles,
        // the same guard setChapters uses.
        if (!isIdle) {
            awaitingIdleChapters = chapters
            return
        }

        // Keep the page the reader is on: re-resolve the slot after the rebuild rather than
        // restarting the chapter.
        val keptPage = currChapterPages.getOrNull(settledSlotOfCurrentItem())
        logcat { "MangaCurlViewer.onWidePageDetected: page ${page.number}, regrouping" }
        applyChapters(chapters, keepPage = keptPage)
    }

    /** The slot of [currentItem] within [currChapterPages], or the current index as a fallback. */
    private fun settledSlotOfCurrentItem(): Int {
        val item = currentItem ?: return 0
        return currChapterPages.indexOfFirst { it === item }.coerceAtLeast(0)
    }

    /**
     * A wide page needs a dual-page split: insert an [InsertPage] (the synthetic second half)
     * right after its parent in both the renderer's slot list ([currChapterPages]) and the flat
     * [items] list, then re-push the render state so the extra slot appears. Mirrors
     * `PagerViewerAdapter.onPageSplit`'s placement and endless-cycle guards.
     */
    private fun onRequestSplit(page: ReaderPage) {
        val chapter = currentChapter ?: return
        if (page.chapter.chapter.id != chapter.chapter.id) return

        val slotIndex = currChapterPages.indexOfFirst { it === page }
        if (slotIndex == -1) return
        // Already split — an InsertPage right after the parent means we're done (guards the cycle).
        if (currChapterPages.getOrNull(slotIndex + 1) is InsertPage) return

        val chapters = lastChapters ?: return
        val insert = InsertPage(page)
        currChapterPages = currChapterPages.toMutableList().apply { add(slotIndex + 1, insert) }

        val itemIndex = items.indexOfFirst { it === page }
        if (itemIndex != -1) {
            // In the flat list the InsertPage sits after the parent for L2R/vertical, before it for
            // R2L (the list is reversed there), matching PagerViewerAdapter.onPageSplit.
            val placeAt = (if (direction.isR2L) itemIndex else itemIndex + 1).coerceIn(0, items.size)
            val neighbour = items.getOrNull(if (direction.isR2L) placeAt - 1 else placeAt)
            if (neighbour !is InsertPage) {
                items = items.toMutableList().apply { add(placeAt, insert) }
            }
        }

        // Keep the currently displayed page on screen across the re-list.
        val keptSlot = (currentItem as? ReaderPage)
            ?.let { kept -> currChapterPages.indexOfFirst { it === kept } }
            ?.takeIf { it >= 0 }
            ?: slotIndex
        pushRenderState(chapters, chapter, keptSlot)
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0
        val menuVisible = activity.viewModel.state.value.menuVisible

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (menuVisible) return false
                if (isUp) moveToNext()
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (menuVisible) return false
                if (isUp) moveToPrevious()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) {
                if (ctrlPressed || !direction.isR2L) moveToNext() else moveToPrevious()
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) {
                if (ctrlPressed || !direction.isR2L) moveToPrevious() else moveToNext()
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveToNext()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveToPrevious()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0 &&
            event.action == MotionEvent.ACTION_SCROLL
        ) {
            if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) moveToNext() else moveToPrevious()
            return true
        }
        return false
    }
}

/** Everything the [MangaCurlView] needs to lay out one chapter's worth of curl slots. */
data class MangaCurlRenderState(
    val pages: List<ReaderPage>,
    val startSlot: Int,
    val hasPreviousChapter: Boolean,
    val hasNextChapter: Boolean,
    val chapterId: Long,
    val usePageTransitions: Boolean,
    val imageProcessing: ReaderImageProcessingConfig,
    val isL2R: Boolean,
    /** Right-to-left: the curl surface is mirrored so the fold anchors right and L→R swipe advances. */
    val mirrored: Boolean,
    val cropBorders: Boolean,
    /** The `ReaderPageImageView.Config` the live-view overlay decodes with — same fields the legacy holder uses. */
    val zoomConfig: MangaCurlZoomConfig,
    /**
     * True when pages should be laid out as two-column spreads: `joinDoublePages` is on and the
     * device is in landscape. Selects the spread renderer over the single-page one.
     */
    val spread: Boolean,
    /**
     * The spread slots, built from the grouped item list — one entry per fold, each holding the
     * page shown on the left of the screen and the one on the right (null for a lone page, e.g. a
     * wide page or an unpaired last page).
     *
     * Built here rather than derived in the renderer by index arithmetic: `groupPagesForDoublePage`
     * already resolves pairing, the R2L swap and `shiftDoublePages`, and re-deriving pairs from a
     * flat page list would silently ignore all three.
     */
    val spreadSlots: List<MangaCurlSpreadSlot>,
)

/** One spread: the page drawn on the left half of the screen and the one on the right. */
data class MangaCurlSpreadSlot(
    val left: ReaderPage?,
    val right: ReaderPage?,
)

/** The subset of `ReaderPageImageView.Config` the curl viewer's live overlay needs. */
data class MangaCurlZoomConfig(
    val zoomDuration: Int,
    val minimumScaleType: Int,
    val cropBorders: Boolean,
    val zoomStartPosition: ReaderPageImageView.ZoomStartPosition,
    val landscapeZoom: Boolean,
    val enablePinchToZoom: Boolean,
)

/**
 * The [AbstractComposeView] the [MangaCurlViewer] hands to [ReaderActivity]. Sits directly in
 * `binding.viewerContainer`, so the global colour filter / grayscale / ICC layer paint applies
 * for free. Hosts [MangaPageCurlRenderer].
 *
 * The content lambda here is a static-image placeholder: each slot decodes its [ReaderPage] to
 * an `ImageBitmap` and draws a plain Compose `Image` (no live View, so nothing competes with the
 * curl for touch). The live-view / snapshot hybrid-zoom swap is group 3 (tasks 3.1-3.3).
 */
class MangaCurlView(context: Context) : AbstractComposeView(context) {

    var onNavigationTap: (xFraction: Float, yFraction: Float) -> Boolean = { _, _ -> false }
    var onLongTap: () -> Unit = {}
    var onCurrentSlotChange: (Int) -> Unit = {}
    var onOpenPreviousChapter: () -> Unit = {}
    var onOpenNextChapter: () -> Unit = {}
    var onWideDetected: (ReaderPage) -> Unit = {}
    var onRequestSplit: (ReaderPage) -> Unit = {}
    var onIdleChanged: (Boolean) -> Unit = {}

    private var state: MangaCurlRenderState? by mutableStateOf(null)
    private var turnRequest: Pair<Boolean, Int> by mutableStateOf(false to 0)
    private var seekRequest: Pair<Int, Int> by mutableStateOf(0 to 0)
    private var disposed: Boolean by mutableStateOf(false)

    /** True while the live overlay page is zoomed past fit scale; gates the curl drag off (task 3.4). */
    private var zoomedIn: Boolean by mutableStateOf(false)

    /** The settled content slot, tracked here so the bitmap cache can be trimmed around it. */
    private var settledSlot: Int by mutableStateOf(0)

    fun submit(state: MangaCurlRenderState) {
        if (state.chapterId != this.state?.chapterId) {
            // New chapter: adopt its start slot now, so the cache is trimmed around the right page
            // before the renderer reports a settled slot of its own.
            settledSlot = state.startSlot
        }
        this.state = state
    }

    /** Direct-seek to a real content slot (the chapter progress slider). */
    fun moveToSlot(slot: Int) {
        seekRequest = slot to (seekRequest.second + 1)
    }

    /** Ask the renderer to animate one page turn in [forward] direction. */
    fun turn(forward: Boolean) {
        turnRequest = forward to (turnRequest.second + 1)
    }

    fun dispose() {
        disposed = true
        disposeComposition()
    }

    @Composable
    override fun Content() {
        if (disposed) return
        val current = state ?: return
        // Proves which renderer the current settings actually select. Every other line in this
        // trace is downstream of `spread` being true, so when nothing else appears this says
        // whether the spread path was even taken.
        SpreadCurlDiagnostics.logChanged(
            "renderer",
            "renderer",
            "spread=${current.spread} slots=${current.spreadSlots.size} " +
                "pages=${current.pages.size} mirrored=${current.mirrored} " +
                "chapterId=${current.chapterId} startSlot=${current.startSlot}",
        )
        TachiyomiTheme {
            val config = remember(current.usePageTransitions, current.mirrored) {
                resolveMangaPageCurlRendererConfig(
                    usePageTransitions = current.usePageTransitions,
                    // Single-page mode: the leaf's reverse shows nothing, just a solid tint.
                    solidBackPage = true,
                    mirrored = current.mirrored,
                )
            }

            // Decoded page bitmaps live OUT here, above PageCurl. PageCurl rebuilds its content
            // subtree on every animation frame (it wraps content in key(current, forward, backward)),
            // so anything that decodes inside the content lambda would restart every frame and the
            // fold would render black. The cache decodes each page once, keyed by identity, and the
            // content lambda is a pure read.
            val pageCache = remember { MangaCurlPageBitmapCache() }
            DisposableEffect(Unit) { onDispose { pageCache.clear() } }
            LaunchedEffect(current.pages, current.imageProcessing, current.isL2R) {
                pageCache.retain(current.pages)
            }
            // Release bitmaps the fold can no longer reach: without this the cache keeps one
            // full-resolution bitmap per page visited, which measured as ~165MB of graphics growth
            // over a single burst of page turns.
            LaunchedEffect(settledSlot, current.pages, current.spread) {
                // A spread shows two slots at once and folds a whole spread at a time, so it needs a
                // wider window than the single-page fold.
                val radius = if (current.spread) 4 else 2
                pageCache.trimAround(settledSlot, current.pages, radius = radius)
                // Warm the same window the trim keeps, so the neighbours a turn can reach are
                // already decoded when it reaches them.
                //
                // The cache only ever decoded on first *draw*, which meant the page after next was
                // requested at the very frame it had to be painted — instrumented, both halves of an
                // incoming spread logged `bitmap=PENDING (decode in flight, half draws empty)` and
                // drew as empty boxes for ~40ms after a turn settled.
                pageCache.prefetchAround(
                    slot = settledSlot,
                    pages = current.pages,
                    radius = radius,
                    processingConfig = current.imageProcessing,
                    isL2R = current.isL2R,
                )
            }

            // One slot's artwork, shared by both renderers.
            //
            // The two need different sizing. A spread's fold surface is already the artwork's own
            // rect (the renderer sizes it to `halfAspectRatio`), so the image fills it outright:
            // `fillMaxHeight` alone leaves the node's *width* unconstrained, so it wraps to the
            // bitmap's own pixel width and Fit — which never upscales past that — draws the page at
            // 1:1 inside a larger surface. Instrumented, a 784x1145 bitmap in a 986x1440 surface
            // came out 784x1145: a 202px gap at the spine and a 147px black bar top and bottom.
            //
            // Single-page still folds the whole viewport, where Fit inside `fillMaxHeight` is doing
            // the real letterboxing work, so that path keeps its old sizing.
            val slotContent: @Composable (Int) -> Unit = { slotIndex ->
                val page = current.pages.getOrNull(slotIndex)
                val bitmap = if (page != null) {
                    pageCache.bitmapFor(
                        page = page,
                        processingConfig = current.imageProcessing,
                        isL2R = current.isL2R,
                        onWideDetected = { onWideDetected(page) },
                        onRequestSplit = { onRequestSplit(page) },
                    )
                } else {
                    null
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .then(if (current.spread) Modifier.fillMaxSize() else Modifier.fillMaxHeight())
                            .onGloballyPositioned { coords ->
                                // The drawn image's own rect, in window space, plus the source
                                // bitmap it came from. Read together with layout.half / layout.column
                                // this says whether a half is too narrow (a Fit letterbox) or merely
                                // parked on the wrong side of a correctly sized column.
                                val bounds = coords.boundsInWindow()
                                SpreadCurlDiagnostics.logChanged(
                                    "image.$slotIndex",
                                    "layout.image",
                                    "slot=$slotIndex bitmap=${bitmap.width}x${bitmap.height} " +
                                        "window=[${SpreadCurlDiagnostics.f(bounds.left)}.." +
                                        "${SpreadCurlDiagnostics.f(bounds.right)}] " +
                                        "w=${SpreadCurlDiagnostics.f(bounds.width)} " +
                                        "localSize=${coords.size.width}x${coords.size.height}",
                                )
                            },
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    SpreadCurlDiagnostics.logChanged(
                        "image.$slotIndex",
                        "layout.image",
                        "slot=$slotIndex bitmap=PENDING (decode in flight, half draws empty)",
                    )
                    Box(modifier = Modifier.fillMaxSize())
                }
            }

            if (current.spread) {
                // Two-column spread. The live zoom overlay belongs to the single-page machinery —
                // it hosts one full-viewport image — so the spread draws from the bitmap cache and
                // is not zoomable, matching how the legacy pager treats a joined spread.
                val slots = current.spreadSlots
                // The spread the settled page sits in, and the seek target in spread space.
                val currentSpread = remember(slots, current.startSlot, current.pages) {
                    val page = current.pages.getOrNull(current.startSlot)
                    slots.indexOfFirst { it.left === page || it.right === page }.coerceAtLeast(0)
                }
                val spreadSeek = remember(seekRequest, slots, current.pages) {
                    val page = current.pages.getOrNull(seekRequest.first)
                    slots.indexOfFirst { it.left === page || it.right === page }
                        .coerceAtLeast(0) to seekRequest.second
                }
                // The shape of one half, so the renderer can size each fold surface to the artwork
                // instead of to the column. Read off the settled spread's own decoded bitmap: a
                // chapter is not guaranteed uniform, and folding the previous page's shape would put
                // the sheet's edges slightly off the artwork's.
                //
                // Only the left half is measured. The two halves of a spread are drawn at the same
                // height, so a differing right half would fold at a rect that is not its own — but
                // taking one of the two keeps both columns' sheets identical, which is what makes
                // them meet cleanly at the spine. Null until it decodes; the renderer then falls
                // back to the full column.
                // Which spread the overlay's image view is actually showing. Tracked as the page it
                // was handed, then resolved back to a spread index, because the renderer gates the
                // columns on spread identity rather than on page identity.
                var overlayDisplayedPage by remember(slots) { mutableStateOf<ReaderPage?>(null) }
                val overlayReadySpread = remember(overlayDisplayedPage, slots) {
                    overlayDisplayedPage?.let { p ->
                        slots.indexOfFirst { it.left === p || it.right === p }.takeIf { it >= 0 }
                    }
                }
                val measuredHalfAspectRatio = slots.getOrNull(currentSpread)?.left?.let { page ->
                    pageCache.bitmapFor(
                        page = page,
                        processingConfig = current.imageProcessing,
                        isL2R = current.isL2R,
                        onWideDetected = { onWideDetected(page) },
                        onRequestSplit = { onRequestSplit(page) },
                    )
                }?.let { bmp ->
                    if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else null
                }
                // Hold the last known shape rather than dropping to null between spreads.
                //
                // This is read off the *settled* spread's own bitmap, so the frame a turn commits on
                // asks for a page that has only just become current and may not be decoded yet. The
                // null that comes back is not information — it says "not decoded", not "no longer a
                // portrait page" — but the renderer reads it as "size the fold surfaces to the whole
                // column", and for that one frame the letterbox gap reopens and the half still
                // decoding paints black. Caught in a recording as a single frame with the right half
                // black and both halves pushed apart, either side of it correct.
                //
                // Every page in a chapter is the same shape in practice (every instrumented
                // layout.image in this reader logged 784x1145), so the previous value is always the
                // better guess. It is only ever replaced by a real measurement, never by a null.
                var lastHalfAspectRatio by remember(current.chapterId) { mutableStateOf<Float?>(null) }
                if (measuredHalfAspectRatio != null) lastHalfAspectRatio = measuredHalfAspectRatio
                val halfAspectRatio = measuredHalfAspectRatio ?: lastHalfAspectRatio
                MangaSpreadCurlRenderer(
                    spreadSlotCount = slots.size.coerceAtLeast(1),
                    currentSpreadSlot = currentSpread,
                    hasPreviousChapter = current.hasPreviousChapter,
                    hasNextChapter = current.hasNextChapter,
                    config = config,
                    chapterId = current.chapterId,
                    onNavigationTap = onNavigationTap,
                    onLongTap = onLongTap,
                    onCurrentSpreadSlotChange = { spread ->
                        // Report the first page of the spread, in single-page space, which is what
                        // the activity's page counter and progress slider work in.
                        val slot = slots.getOrNull(spread)
                        val page = slot?.left ?: slot?.right
                        val pageSlot = page?.let { p -> current.pages.indexOfFirst { it === p } } ?: -1
                        if (pageSlot >= 0) {
                            settledSlot = pageSlot
                            onCurrentSlotChange(pageSlot)
                        }
                    },
                    onOpenPreviousChapter = onOpenPreviousChapter,
                    onOpenNextChapter = onOpenNextChapter,
                    onIdleChanged = onIdleChanged,
                    programmaticTurn = turnRequest,
                    seekRequest = spreadSeek,
                    halfAspectRatio = halfAspectRatio,
                    overlayReadySlot = overlayReadySpread,
                    liveOverlay = { spread, gesturesEnabled, foldDriverForScreenX ->
                        val slot = slots.getOrNull(spread)
                        // The overlay spans the viewport in reading order, so the composite's first
                        // half is the left one regardless of the R2L flip applied to the columns.
                        val first = slot?.left
                        val second = slot?.right
                        if (first != null) {
                            MangaCurlLivePage(
                                page = first,
                                secondPage = second,
                                isSpread = true,
                                processingConfig = current.imageProcessing,
                                isL2R = current.isL2R,
                                zoomConfig = current.zoomConfig,
                                interactive = gesturesEnabled,
                                // The overlay sits in screen space, above the spread's R2L flip, so
                                // it is never itself mirrored — but the fold direction still has to
                                // follow reading order, which is passed separately.
                                mirrored = false,
                                readingOrderMirrored = current.mirrored,
                                foldDriverFor = foldDriverForScreenX,
                                artworkAspectRatio = halfAspectRatio,
                                onDisplayedPageChanged = { overlayDisplayedPage = it },
                                onZoomedChanged = { zoomedIn = it },
                                onTap = onNavigationTap,
                                onLongTap = onLongTap,
                            )
                        }
                    },
                ) { spread, column ->
                    val slot = slots.getOrNull(spread)
                    // `column` is the column's identity within the Row, which the R2L flip then
                    // mirrors on screen. The halves belong to screen sides — firstPage always reads
                    // as the left one — so in R2L the identity-to-side mapping inverts.
                    val wantLeftHalf = (column == SpreadColumn.LEFT) != current.mirrored
                    val page = if (wantLeftHalf) slot?.left else slot?.right
                    val pageSlot = page?.let { p -> current.pages.indexOfFirst { it === p } } ?: -1
                    // Which page each column is asked to draw. An unpaired slot (right == null) is
                    // a legitimate reason for one half to be blank, and telling that apart from a
                    // layout fault needs the pairing itself in the trace.
                    SpreadCurlDiagnostics.logChanged(
                        "content.$column.$spread",
                        "content.half",
                        "spread=$spread column=$column wantLeftHalf=$wantLeftHalf " +
                            "mirrored=${current.mirrored} " +
                            "left=${slot?.left?.number} right=${slot?.right?.number} " +
                            "chosen=${page?.number} pageSlot=$pageSlot",
                    )
                    if (pageSlot >= 0) {
                        // Just the artwork at its fitted size. Pulling it against the spine is the
                        // renderer's job — only that level knows the flips between a column's
                        // drawing space and the screen (see MangaSpreadColumnCurl's spineAlignment).
                        slotContent(pageSlot)
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                MangaPageCurlRenderer(
                    slotCount = current.pages.size.coerceAtLeast(1),
                    currentSlot = current.startSlot,
                    hasPreviousChapter = current.hasPreviousChapter,
                    hasNextChapter = current.hasNextChapter,
                    config = config,
                    chapterId = current.chapterId,
                    onNavigationTap = onNavigationTap,
                    onLongTap = onLongTap,
                    onCurrentSlotChange = { slot ->
                        settledSlot = slot
                        onCurrentSlotChange(slot)
                    },
                    onOpenPreviousChapter = onOpenPreviousChapter,
                    onOpenNextChapter = onOpenNextChapter,
                    onIdleChanged = onIdleChanged,
                    dragEnabled = { !zoomedIn },
                    programmaticTurn = turnRequest,
                    seekRequest = seekRequest,
                    liveOverlay = { settledSlot, gesturesEnabled, foldDriver ->
                        val page = current.pages.getOrNull(settledSlot)
                        if (page != null) {
                            MangaCurlLivePage(
                                page = page,
                                secondPage = null,
                                isSpread = false,
                                processingConfig = current.imageProcessing,
                                isL2R = current.isL2R,
                                zoomConfig = current.zoomConfig,
                                interactive = gesturesEnabled,
                                mirrored = current.mirrored,
                                // Single-page carries its direction in `mirrored`, which already
                                // un-mirrors the coordinates; this only labels the diagnostics.
                                readingOrderMirrored = current.mirrored,
                                foldDriverFor = { foldDriver },
                                // Null on purpose: single-page still folds the whole viewport (only
                                // the spread sizes its surfaces to the artwork), and narrowing just
                                // the zones would put them off the surface they drive.
                                artworkAspectRatio = null,
                                onZoomedChanged = { zoomedIn = it },
                                onTap = onNavigationTap,
                                onLongTap = onLongTap,
                            )
                        } else if (zoomedIn) {
                            zoomedIn = false
                        }
                    },
                    slotContent = slotContent,
                )
            }
        }
    }
}

/**
 * Owns the whole touch stream for the settled page and routes each gesture to either the image or
 * the curl.
 *
 * Compose does not propagate an unhandled pointer event between a gesture layer and an
 * [AndroidView] sibling in *either* direction — with the image behind the curl it received nothing,
 * and with the image in front returning `false` the curl received nothing. So exactly one view owns
 * the stream and decides:
 *
 *  - two fingers, or already zoomed → the [ReaderPageImageView] (pinch, pan)
 *  - double tap → [onDoubleTap], which drives the image's own zoom API directly
 *  - long press → [onLongTap]
 *  - horizontal drag → [onFoldStart] / [onFoldUpdate] / [onFoldFinish], following the finger
 *  - plain tap → [onTap] (nav zones / menu)
 */
private class CurlTouchDispatcher(context: Context) : android.widget.FrameLayout(context) {
    val image = ReaderPageImageView(context).also {
        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(it)
    }

    /**
     * Whether the page is zoomed past fit scale, in which case the image owns every gesture so a
     * drag pans instead of turning the page.
     *
     * Read straight off the image at dispatch time rather than mirrored through Compose state: the
     * scale changes mid-gesture (a pinch that ends zoomed in), and a state hop would only reach the
     * dispatcher a frame later — after it had already decided how to route the touch.
     */
    val zoomed: Boolean get() = image.isZoomedIn

    /** False while a fold animates or the settled slot is a chapter boundary. */
    var interactive: Boolean = true

    /** True when the curl surface is drawn mirrored (R2L): x must be un-mirrored for the fold. */
    var mirrored: Boolean = false

    /**
     * True when the reader reads right-to-left, regardless of whether this view's own surface is
     * drawn mirrored — the spread overlay leaves [mirrored] false on purpose, because it needs raw
     * screen x to pick the column under the finger.
     *
     * Nothing in the gesture maths reads this: leaf space is already in reading order for whichever
     * leaf was touched, and the spread's mirrored column is inverted by its own
     * `ExternalFoldDriver.invertDirection`. It is carried only so the diagnostics can state the
     * reader's direction on the same line as the zone decision, which is what makes a trace
     * readable against a screen recording.
     */
    var readingOrderMirrored: Boolean = false

    var dragEnabled: Boolean = true

    /**
     * Fraction of a leaf's width, measured from its free (outer) edge, within which a drag grabs the
     * page. The mirror band of the same width at the leaf's spine grabs the page that turns the
     * other way.
     */
    var edgeFraction: Float = 0.32f

    var onTap: (Float, Float) -> Boolean = { _, _ -> false }
    var onLongTap: () -> Unit = {}
    var onDoubleTap: (Float, Float) -> Unit = { _, _ -> }

    /** True when this view spans a two-column spread, so each half is a leaf of its own. */
    var spread: Boolean = false

    /**
     * Width / height of one leaf's artwork, or null when it is unknown.
     *
     * A leaf is the *page*, not the box it is centred in. A portrait page inside a landscape half
     * leaves a black bar on either side, and those bars are not part of the sheet: the fold surface
     * excludes them (see MangaSpreadCurlRenderer's `halfAspectRatio`), so the drag zones have to as
     * well. Without this a drag had to start out in the margin, off the manga entirely, and the band
     * that should sit on the page's own edge sat beside it.
     */
    var artworkAspectRatio: Float? = null

    /** Width of the leaf the in-flight fold belongs to, in that leaf's own space. */
    private var foldLeafWidth = 0f

    /** Where in that leaf the in-flight fold was grabbed, so the commit test has its anchor. */
    private var foldStartLocalX = 0f

    /**
     * Which half the in-flight fold was grabbed on, latched at start so a finger that crosses the
     * spine keeps driving the leaf it grabbed rather than mirroring back (see [foldLocalX]).
     */
    private var foldStartedOnRightHalf = false

    /** Groups one gesture's diagnostic lines together (temporary instrumentation). */
    private var gestureId = 0

    /** Throttles the per-move fold logs to a readable rate. */
    private var lastUpdateLogAt = 0L

    /**
     * (forward, xInLeaf, y, screenXFraction, leafWidth) -> whether a fold actually started.
     *
     * `xInLeaf` and `leafWidth` are both in the leaf's own space — the artwork's rect, not the box
     * it is centred in — because that is the surface the renderer gives PageCurl to fold.
     */
    var onFoldStart: (Boolean, Float, Float, Float, Int) -> Boolean = { _, _, _, _, _ -> false }
    var onFoldUpdate: (Float, Float) -> Unit = { _, _ -> }
    var onFoldFinish: (Boolean) -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var folding = false
    private var multiTouch = false

    /** Latched on ACTION_DOWN: true when this whole gesture belongs to the image, not the curl. */
    private var imageOwnsGesture = false
    private var longPressed = false
    private var lastUpTime = 0L
    private var lastUpX = 0f
    private var lastUpY = 0f

    private val longPressRunnable = Runnable {
        if (!moved && !folding && !multiTouch) {
            longPressed = true
            onLongTap()
        }
    }

    /**
     * Width of the box the fold surface is laid out in.
     *
     * A spread's fold is driven on the right column, whose surface is one half of the viewport, so
     * the gesture is expressed in that half's width even though the finger sweeps the whole spread.
     */
    private val leafBoxWidth: Float get() = if (spread) width / 2f else width.toFloat()

    /**
     * Width of the leaf itself — the artwork, which is [artworkAspectRatio] fitted to the view's
     * height and so usually narrower than [leafBoxWidth].
     *
     * Falls back to the whole box when the shape is unknown or the page is wider than the box, which
     * is what a landscape/wide page in single-page mode looks like.
     */
    private val leafWidth: Float
        get() {
            val box = leafBoxWidth
            val aspect = artworkAspectRatio ?: return box
            if (aspect <= 0f || height <= 0) return box
            return (height * aspect).coerceAtMost(box)
        }

    /**
     * Maps a screen x into the touched leaf's own surface space.
     *
     * A drag folds the single column under the finger, and each column's surface runs the way
     * PageCurl's own geometry expects: local [leafWidth] is the free edge a page is grabbed by, local
     * 0 is the spine. That direction is not a choice — a FORWARD fold rests at the leaf's *right*
     * edge and animates toward its left, and `progress` is `1 - centerX / width`, so a fold has to be
     * driven from [leafWidth] downwards or it plays backwards.
     *
     * Both halves work out to the same expression, because they are mirror images about the spine
     * and each column's own `scaleX = -1f` (or the Row's, in R2L) already accounts for how they are
     * drawn:
     *
     *     local = |x - width / 2|
     *
     * So a touch at the spine maps to 0 and one at either outer edge to [leafWidth]; out in the
     * letterbox it exceeds [leafWidth], which the caller treats as off the page.
     *
     * The spread overlay deliberately leaves [mirrored] false — it needs raw screen x to pick a
     * column — so this branch must not consult it, and needs no direction flag either: the expression
     * is symmetric, and which column the result addresses is settled by the driver routing.
     */
    private fun leafLocalX(x: Float): Float {
        if (!spread) {
            val fromSpine = if (mirrored) width - x else x
            // Single page centres its Fit box, so half the letterbox sits on the spine side.
            return fromSpine - (leafBoxWidth - leafWidth) / 2f
        }
        return kotlin.math.abs(x - width / 2f)
    }

    /**
     * Leaf-local x for a move or up during an in-flight fold, always in the leaf the gesture
     * *started* on.
     *
     * [leafLocalX] takes an absolute value about the spine, so it folds the two halves onto one
     * axis: a finger dragged past the spine would come back down the same values and the fold would
     * reverse instead of continuing. Anchoring on the half the gesture began on continues the sweep
     * past the spine into negative local x, which is what lets a page be pulled the whole way across.
     */
    private fun foldLocalX(x: Float): Float {
        if (!spread) return leafLocalX(x)
        val spine = width / 2f
        return if (foldStartedOnRightHalf) x - spine else spine - x
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = true

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Decide the owner on the way down and keep it for the whole gesture: a drag that starts on
        // a zoomed page pans it to the end, even if it zooms back out along the way.
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            imageOwnsGesture = zoomed
        }

        // The image owns everything while zoomed, and any multi-pointer gesture is a pinch.
        if (imageOwnsGesture || ev.pointerCount > 1) {
            if (folding) {
                onFoldFinish(false)
                folding = false
            }
            multiTouch = true
            removeCallbacks(longPressRunnable)
            // Handing over mid-gesture: the image never saw this stream start, and would drop a
            // MOVE for a gesture it has no record of. Open one with a synthetic DOWN first.
            if (!imageOwnsGesture) {
                imageOwnsGesture = true
                val down = MotionEvent.obtain(
                    ev.downTime,
                    ev.eventTime,
                    MotionEvent.ACTION_DOWN,
                    ev.getX(0),
                    ev.getY(0),
                    ev.metaState,
                )
                super.dispatchTouchEvent(down)
                down.recycle()
            }
            return super.dispatchTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = System.currentTimeMillis()
                moved = false
                folding = false
                multiTouch = false
                longPressed = false
                postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                if (longPressed) return true
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!moved && kotlin.math.hypot(dx, dy) > touchSlop) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                    // Only a mostly-horizontal drag starting near an edge turns a page.
                    //
                    // A spread turns one sheet across the whole viewport, so the grabbing edges are
                    // the spread's own two outer edges — not each half's. The geometry is resolved
                    // in the fold surface's frame, which spans the whole spread in reading order
                    // (see leafLocalX), and the zones sit at its two ends.
                    if (interactive && dragEnabled && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        val leaf = leafWidth
                        val localX = leafLocalX(downX)
                        // In a spread's leaf space local 0 is the spine and `leaf` the outer edge, so
                        // the grabbing band is the one near `leaf`. `onArtwork` falls out of the same
                        // number: the letterbox lies past the artwork, so a touch there exceeds it.
                        //
                        // Whether grabbing the outer edge turns forward or back depends on which
                        // half it is: a spread's two leaves hinge at the same spine and swing
                        // opposite ways. The screen's leading half in reading order turns back, the
                        // trailing one forward — and the driver routing has already picked the
                        // column whose state counts in the matching direction, so this only has to
                        // state it in screen terms.
                        val onArtwork = spread && localX <= leaf
                        val onOuterEdge = onArtwork && localX > leaf * (1f - edgeFraction)
                        val nearSpine = onArtwork && localX < leaf * edgeFraction
                        val onTrailingHalf = (downX >= width / 2f) != readingOrderMirrored
                        val forward = when {
                            onOuterEdge -> onTrailingHalf
                            nearSpine -> !onTrailingHalf
                            else -> null
                        }
                        // The whole edge-zone decision in one line: where the finger went down in
                        // screen space, what that becomes in leaf space, and which zone of the leaf
                        // it lands in. `forward=null` is the dead zone — if a drag that should fold
                        // reports null here, the zones are the fault and not the direction.
                        gestureId = SpreadCurlDiagnostics.nextGesture()
                        SpreadCurlDiagnostics.log(
                            "drag.zone",
                            "g$gestureId downX=${SpreadCurlDiagnostics.f(downX)} " +
                                "leafLocalX=${SpreadCurlDiagnostics.f(localX)} " +
                                "dx=${SpreadCurlDiagnostics.f(dx)} dy=${SpreadCurlDiagnostics.f(dy)} " +
                                "width=$width spread=$spread mirrored=$mirrored " +
                                "readingOrderMirrored=$readingOrderMirrored " +
                                "leafWidth=${SpreadCurlDiagnostics.f(leaf)} " +
                                "boxWidth=${SpreadCurlDiagnostics.f(leafBoxWidth)} " +
                                "aspect=$artworkAspectRatio onArtwork=$onArtwork " +
                                "ofLeaf=${SpreadCurlDiagnostics.f2(localX / leaf)} " +
                                "edgeFraction=${SpreadCurlDiagnostics.f2(edgeFraction)} " +
                                "onOuterEdge=$onOuterEdge nearSpine=$nearSpine " +
                                "onTrailingHalf=$onTrailingHalf forward=$forward " +
                                "screenXFrac=${SpreadCurlDiagnostics.f2(downX / width.toFloat())}",
                        )
                        // What a *tap* at this same point would have done, resolved through the very
                        // same nav zones the tap path uses. The two problems' shared question is
                        // whether drag and tap agree, and comparing them after the fact across two
                        // separate runs is exactly what has been unreliable so far.
                        SpreadCurlDiagnostics.log(
                            "drag.vs.tap",
                            "g$gestureId tapWouldUse xFrac=" +
                                SpreadCurlDiagnostics.f2(downX / width.toFloat()) +
                                " (leaf space=" + SpreadCurlDiagnostics.f2(localX / leaf) + ")" +
                                " dragForward=$forward",
                        )
                        if (forward != null) {
                            // The driver for a leaf works in that leaf's own coordinate space, so
                            // the fold is fed leaf-local coordinates; the raw screen x only picks
                            // which column's driver that is.
                            foldLeafWidth = leaf
                            foldStartedOnRightHalf = downX >= width / 2f
                            folding = onFoldStart(forward, localX, downY, downX / width.toFloat(), leaf.toInt())
                            SpreadCurlDiagnostics.log(
                                "drag.start",
                                "g$gestureId forward=$forward accepted=$folding " +
                                    "leafWidth=${SpreadCurlDiagnostics.f(leaf)} " +
                                    "start=(${SpreadCurlDiagnostics.f(localX)}," +
                                    "${SpreadCurlDiagnostics.f(downY)})",
                            )
                            if (folding) foldStartLocalX = localX
                        } else {
                            SpreadCurlDiagnostics.log(
                                "drag.start",
                                "g$gestureId no fold: down landed in the leaf's dead zone",
                            )
                        }
                    }
                }
                if (folding) {
                    val foldX = foldLocalX(ev.x)
                    onFoldUpdate(foldX, ev.y)
                    // Sampled, not per-move: enough to see whether the fold tracks the finger in the
                    // same direction it is travelling, without burying the rest of the trace.
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateLogAt > UPDATE_LOG_INTERVAL_MS) {
                        lastUpdateLogAt = now
                        SpreadCurlDiagnostics.log(
                            "drag.move",
                            "g$gestureId rawX=${SpreadCurlDiagnostics.f(ev.x)} " +
                                "foldX=${SpreadCurlDiagnostics.f(foldX)} " +
                                "ofLeaf=${SpreadCurlDiagnostics.f2(foldX / foldLeafWidth)}",
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val now = System.currentTimeMillis()
                // Read the coordinates NOW: the framework recycles this MotionEvent as soon as
                // dispatch returns, so anything deferred below must not touch `ev`.
                val upX = ev.x
                val upY = ev.y
                if (folding) {
                    // Commit once the finger crossed the middle of the leaf being turned — in leaf
                    // space, so a spread measures against the middle of the half it grabbed rather
                    // than the middle of the screen.
                    //
                    // A forward fold is grabbed at the free edge (local ~= leafWidth) and completes
                    // by travelling towards the spine; a backward fold is the reverse. Which of the
                    // two this is falls straight out of where it started.
                    val localUp = foldLocalX(upX)
                    val mid = foldLeafWidth / 2f
                    val startedAtFreeEdge = foldStartLocalX > mid
                    val commit = if (startedAtFreeEdge) localUp < mid else localUp > mid
                    SpreadCurlDiagnostics.log(
                        "drag.finish",
                        "g$gestureId upX=${SpreadCurlDiagnostics.f(upX)} " +
                            "leafLocalUp=${SpreadCurlDiagnostics.f(localUp)} " +
                            "leafLocalStart=${SpreadCurlDiagnostics.f(foldStartLocalX)} " +
                            "startedAtFreeEdge=$startedAtFreeEdge commit=$commit " +
                            "travelled=${SpreadCurlDiagnostics.f(upX - downX)}",
                    )
                    onFoldFinish(commit)
                    folding = false
                } else if (!moved && !longPressed && now - downTime < longPressTimeout) {
                    val isDoubleTap = now - lastUpTime < doubleTapTimeout &&
                        kotlin.math.hypot(upX - lastUpX, upY - lastUpY) < touchSlop * 2
                    if (isDoubleTap) {
                        lastUpTime = 0L
                        onDoubleTap(upX, upY)
                    } else {
                        lastUpTime = now
                        lastUpX = upX
                        lastUpY = upY
                        val w = width.toFloat()
                        val h = height.toFloat()
                        val tapGesture = SpreadCurlDiagnostics.nextGesture()
                        // Defer the nav tap so a second tap can still turn it into a double tap.
                        postDelayed({
                            if (lastUpTime == now && w > 0f && h > 0f) {
                                // The tap's own view of the same geometry the drag logs above, so a
                                // tap and a drag at the same x can be lined up in one trace.
                                SpreadCurlDiagnostics.log(
                                    "tap",
                                    "g$tapGesture x=${SpreadCurlDiagnostics.f(upX)} " +
                                        "xFrac=${SpreadCurlDiagnostics.f2(upX / w)} " +
                                        "yFrac=${SpreadCurlDiagnostics.f2(upY / h)} " +
                                        "leafFrac=${SpreadCurlDiagnostics.f2(leafLocalX(upX) / leafWidth)} " +
                                        "spread=$spread mirrored=$mirrored",
                                )
                                val handled = onTap(upX / w, upY / h)
                                SpreadCurlDiagnostics.log("tap", "g$tapGesture handled=$handled")
                            }
                        }, doubleTapTimeout - tapTimeout / 2)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (folding) {
                    SpreadCurlDiagnostics.log("drag.cancel", "g$gestureId fold cancelled")
                    onFoldFinish(false)
                    folding = false
                }
            }
        }
        return true
    }

    private companion object {
        /** Milliseconds between sampled `drag.move` lines (temporary instrumentation). */
        const val UPDATE_LOG_INTERVAL_MS = 60L
    }
}

/**
 * The one live page of the curl: a real [ReaderPageImageView] (tiled decoding, pinch / pan,
 * `landscapeZoom`) inside a [CurlTouchDispatcher], drawn on top of the curl.
 *
 * The dispatcher owns the whole touch stream and routes each gesture — pinch and post-zoom pan to
 * the image, double tap to the image's zoom API, drag to [foldDriver], tap to [onTap], long press
 * to [onLongTap]. [interactive] is false while a fold animates or the settled slot is a chapter
 * boundary.
 */
@Composable
private fun MangaCurlLivePage(
    page: ReaderPage,
    // When non-null this overlay shows a spread: the two halves are composited into one bitmap so
    // zoom and pan treat the pair as a single image, like the legacy JoinedPagerPageHolder.
    secondPage: ReaderPage?,
    // True when this overlay spans a spread, so the dispatcher treats each half as its own leaf.
    isSpread: Boolean,
    processingConfig: ReaderImageProcessingConfig,
    isL2R: Boolean,
    zoomConfig: MangaCurlZoomConfig,
    interactive: Boolean,
    mirrored: Boolean,
    // The reader's own direction, which the spread overlay needs even though it leaves [mirrored]
    // false — see CurlTouchDispatcher.readingOrderMirrored.
    readingOrderMirrored: Boolean,
    // Picks the fold to drive from where the drag started (0..1 of this view's width). Single-page
    // always returns the one driver; a spread returns the column the gesture is over.
    foldDriverFor: (Float) -> ExternalFold,
    // Shape of one leaf's artwork, so the drag zones sit on the page rather than on the letterbox
    // around it. Null until it decodes — see CurlTouchDispatcher.artworkAspectRatio.
    artworkAspectRatio: Float?,
    // Fires once this overlay's image view actually holds [page]'s pixels. The decode is off the
    // main thread, so the renderer needs this to know when the overlay may replace the columns
    // without flashing the previous spread.
    onDisplayedPageChanged: (ReaderPage) -> Unit = {},
    onZoomedChanged: (Boolean) -> Unit,
    onTap: (Float, Float) -> Boolean,
    onLongTap: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val dispatcher = remember(context) {
        CurlTouchDispatcher(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
    }
    val imageView = dispatcher.image

    // The dispatcher reads the zoom state off the image itself; this only forwards it to the viewer
    // so the curl's own drag stays disabled while zoomed.
    val latestZoomedChanged by rememberUpdatedState(onZoomedChanged)
    // The page whose pixels setImage was last handed, so onImageLoaded can say which one became
    // visible. onImageLoaded carries no argument, and by the time it fires the composable may
    // already have been recomposed for a different page.
    val pendingPage = remember { mutableStateOf<ReaderPage?>(null) }
    val latestDisplayedPageChanged by rememberUpdatedState(onDisplayedPageChanged)
    DisposableEffect(imageView) {
        imageView.onScaleChanged = {
            val zoomed = imageView.isZoomedIn
            // A zoomed page pans instead of turning, so the fold gesture stands down.
            dispatcher.dragEnabled = !zoomed
            latestZoomedChanged(zoomed)
        }
        // onReady, not "setImage returned": setImage only queues the decode, and
        // SubsamplingScaleImageView needs another pass on its own thread before it can paint. That
        // gap measured at 78ms — longer than the compositing that precedes it — and reporting the
        // page ready at the near end of it is what left the hand-off still visible.
        imageView.onImageLoaded = {
            // What the overlay actually looks like at the frame it replaces the columns. The two are
            // supposed to be pixel-identical there — both compute to 1972x1440 at x=[574..2546] on
            // this device — so any difference logged here is the remaining flicker.
            val ssiv = imageView.debugPageView as? com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
            SpreadCurlDiagnostics.log(
                "overlay.ready",
                "page=${pendingPage.value?.number} " +
                    "scale=${ssiv?.scale?.let { SpreadCurlDiagnostics.f2(it) }} " +
                    "minScale=${ssiv?.minScale?.let { SpreadCurlDiagnostics.f2(it) }} " +
                    "source=${ssiv?.sWidth}x${ssiv?.sHeight} " +
                    "view=${imageView.width}x${imageView.height}",
            )
            pendingPage.value?.let { latestDisplayedPageChanged(it) }
        }
        onDispose {
            imageView.onScaleChanged = null
            imageView.onImageLoaded = null
        }
    }

    LaunchedEffect(interactive) { dispatcher.interactive = interactive }
    LaunchedEffect(mirrored) { dispatcher.mirrored = mirrored }
    LaunchedEffect(readingOrderMirrored) { dispatcher.readingOrderMirrored = readingOrderMirrored }
    LaunchedEffect(isSpread) { dispatcher.spread = isSpread }
    LaunchedEffect(artworkAspectRatio) { dispatcher.artworkAspectRatio = artworkAspectRatio }

    val latestTap by rememberUpdatedState(onTap)
    val latestLongTap by rememberUpdatedState(onLongTap)
    val latestDriverFor by rememberUpdatedState(foldDriverFor)
    // The driver picked when the gesture started, so update/finish keep addressing the same column
    // even if the finger crosses the spine mid-drag.
    val activeDriver = remember { mutableStateOf<ExternalFold?>(null) }
    DisposableEffect(dispatcher) {
        dispatcher.onTap = { x, y -> latestTap(x, y) }
        dispatcher.onLongTap = { latestLongTap() }
        dispatcher.onDoubleTap = { x, y -> dispatcher.image.toggleDoubleTapZoom(x, y) }
        dispatcher.onFoldStart = { forward, xInLeaf, y, screenXFraction, leafWidth ->
            val driver = latestDriverFor(screenXFraction)
            activeDriver.value = driver
            val requested = if (forward) ExternalFoldDirection.FORWARD else ExternalFoldDirection.BACKWARD
            // The fold's geometry is the *artwork's* rect, which is what the renderer sizes the curl
            // surface to. Handing the driver the column's full width instead made the library's fold
            // maths work on a sheet wider than the page on screen, so the curl line and the shadow
            // ran out over the letterbox.
            SpreadCurlDiagnostics.log(
                "fold.route",
                "screenXFrac=${SpreadCurlDiagnostics.f2(screenXFraction)} " +
                    "driver=${driver.debugName} requested=$requested " +
                    "size=${leafWidth}x${dispatcher.height} " +
                    "start=(${SpreadCurlDiagnostics.f(xInLeaf)},${SpreadCurlDiagnostics.f(y)})",
            )
            val started = driver.start(
                direction = requested,
                start = Offset(xInLeaf, y),
                size = IntSize(leafWidth, dispatcher.height),
            )
            if (!started) {
                SpreadCurlDiagnostics.log("fold.route", "driver=${driver.debugName} refused: not laid out")
            }
            started
        }
        dispatcher.onFoldUpdate = { x, y -> activeDriver.value?.update(Offset(x, y)) }
        dispatcher.onFoldFinish = { commit ->
            SpreadCurlDiagnostics.log(
                "fold.finish",
                "driver=${activeDriver.value?.debugName} commit=$commit",
            )
            activeDriver.value?.finish(commit)
            activeDriver.value = null
        }
        onDispose {
            activeDriver.value?.cancel()
            activeDriver.value = null
        }
    }

    // A fold moved on (chapter boundary or programmatic turn) while this page was zoomed: report it
    // un-zoomed so the next page's curl drag is not stuck disabled.
    LaunchedEffect(interactive) {
        if (!interactive) {
            onZoomedChanged(false)
        }
    }

    LaunchedEffect(page, secondPage, processingConfig, isL2R, zoomConfig) {
        val streamFn = page.stream ?: return@LaunchedEffect
        val source = withContext(Dispatchers.IO) {
            val raw = streamFn().source().buffer().use { Buffer().apply { writeAll(it) } }
            val first = runCatching {
                processReaderImage(
                    config = processingConfig,
                    page = page,
                    imageSource = raw,
                    isL2R = isL2R,
                    onWideDetected = {},
                    onRequestSplit = {},
                )
            }.getOrNull() ?: return@withContext null

            // A spread composites both halves into one bitmap, so zoom and pan treat it as a single
            // image — the same thing JoinedPagerPageHolder does in the legacy reader. Merging only
            // happens for this zoom overlay; the fold surfaces keep the halves separate.
            val secondStreamFn = secondPage?.stream ?: return@withContext first
            val secondRaw = secondStreamFn().source().buffer().use { Buffer().apply { writeAll(it) } }
            val second = runCatching {
                processReaderImage(
                    config = processingConfig,
                    page = secondPage,
                    imageSource = secondRaw,
                    isL2R = isL2R,
                    onWideDetected = {},
                    onRequestSplit = {},
                )
            }.getOrNull() ?: return@withContext first

            // A half that is itself a wide image is about to dissolve the spread (the viewer's
            // regroup), and an animated half must not be frozen into a composite — show one half
            // meanwhile, matching JoinedPagerPageHolder's fallbacks.
            val animated = ImageUtil.isAnimatedAndSupported(first) || ImageUtil.isAnimatedAndSupported(second)
            val wide = ImageUtil.isWideImage(first) || ImageUtil.isWideImage(second)
            // Whether the overlay is showing a real composite or one lone half. A fallback here means
            // the overlay is half as wide as the columns underneath, which on its own would read as
            // "the spread separates" even with the columns laid out correctly.
            SpreadCurlDiagnostics.log(
                "overlay.compose",
                "page=${page.number} second=${secondPage.number} " +
                    "animated=$animated wideHalf=$wide " +
                    "result=${if (animated || wide) "FIRST_HALF_ONLY" else "MERGED"}",
            )
            when {
                animated -> first
                wide -> first
                else -> runCatching { ImageUtil.mergeHorizontal(first, second) }.getOrNull() ?: first
            }
        } ?: return@LaunchedEffect
        pendingPage.value = page
        imageView.setImage(
            source,
            isAnimated = false,
            config = ReaderPageImageView.Config(
                zoomDuration = zoomConfig.zoomDuration,
                minimumScaleType = zoomConfig.minimumScaleType,
                cropBorders = zoomConfig.cropBorders,
                zoomStartPosition = zoomConfig.zoomStartPosition,
                landscapeZoom = zoomConfig.landscapeZoom,
                enablePinchToZoom = zoomConfig.enablePinchToZoom,
            ),
        )
    }
    DisposableEffect(imageView) { onDispose { imageView.recycle() } }

    AndroidView(
        factory = { dispatcher },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Decodes and holds one [ImageBitmap] per [ReaderPage], surviving [PageCurl]'s per-frame content
 * teardown — the cheap snapshot shown inside the curl for every non-settled slot.
 */
private class MangaCurlPageBitmapCache {

    private val bitmaps = mutableStateMapOf<ReaderPage, ImageBitmap>()
    private val inFlight = mutableSetOf<ReaderPage>()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    /** Reads the bitmap for [page], kicking off a decode on first miss. */
    fun bitmapFor(
        page: ReaderPage,
        processingConfig: ReaderImageProcessingConfig,
        isL2R: Boolean,
        onWideDetected: () -> Unit,
        onRequestSplit: () -> Unit,
    ): ImageBitmap? {
        bitmaps[page]?.let { return it }
        if (inFlight.add(page)) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    val streamFn = page.stream ?: return@withContext null
                    runCatching {
                        val raw = streamFn().source().buffer().use { Buffer().apply { writeAll(it) } }
                        val processed = processReaderImage(
                            config = processingConfig,
                            page = page,
                            imageSource = raw,
                            isL2R = isL2R,
                            onWideDetected = onWideDetected,
                            onRequestSplit = onRequestSplit,
                        )
                        BitmapFactory.decodeStream(processed.inputStream())?.asImageBitmap()
                    }.getOrNull()
                }
                inFlight.remove(page)
                if (decoded != null) bitmaps[page] = decoded
            }
        }
        return null
    }

    /**
     * Decodes the pages within [radius] of [slot] that are not cached yet, so a turn does not have
     * to wait for its own artwork.
     *
     * Reuses [bitmapFor]'s in-flight guard, so a page already being decoded is not started twice and
     * one already cached costs nothing. The wide-image callbacks are deliberately no-ops here: those
     * regroup the whole spread list, and firing them for a page the reader has not reached yet would
     * re-list from under the current one.
     */
    fun prefetchAround(
        slot: Int,
        pages: List<ReaderPage>,
        radius: Int,
        processingConfig: ReaderImageProcessingConfig,
        isL2R: Boolean,
    ) {
        if (pages.isEmpty()) return
        val first = (slot - radius).coerceAtLeast(0)
        val last = (slot + radius).coerceAtMost(pages.lastIndex)
        for (i in first..last) {
            bitmapFor(
                page = pages[i],
                processingConfig = processingConfig,
                isL2R = isL2R,
                onWideDetected = {},
                onRequestSplit = {},
            )
        }
    }

    /** Drops cached bitmaps for pages no longer in [pages] (chapter change / re-list). */
    fun retain(pages: List<ReaderPage>) {
        val keep = pages.toHashSet()
        bitmaps.keys.filterNot { it in keep }.forEach { bitmaps.remove(it) }
    }

    /**
     * Keeps only the pages the fold can actually show — [slot] and [WINDOW_RADIUS] either side.
     *
     * Without this the cache grows by one full-resolution bitmap per page visited: a 1440x2160 page
     * is ~12MB in ARGB_8888, so reading a long chapter parks hundreds of MB in graphics memory even
     * though the curl only ever draws the current page and its immediate neighbours.
     */
    fun trimAround(slot: Int, pages: List<ReaderPage>, radius: Int = WINDOW_RADIUS) {
        if (pages.isEmpty()) return
        val first = (slot - radius).coerceAtLeast(0)
        val last = (slot + radius).coerceAtMost(pages.lastIndex)
        val keep = pages.subList(first, last + 1).toHashSet()
        bitmaps.keys.filterNot { it in keep }.forEach { bitmaps.remove(it) }
    }

    fun clear() {
        scope.cancel()
        bitmaps.clear()
        inFlight.clear()
    }

    private companion object {
        /** Pages kept either side of the current one: the fold never draws further than this. */
        const val WINDOW_RADIUS = 2
    }
}
