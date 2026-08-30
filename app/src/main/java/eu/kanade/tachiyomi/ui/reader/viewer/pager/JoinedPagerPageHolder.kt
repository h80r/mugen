package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import dev.h80r.mugen.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.JoinedReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat

/**
 * A page holder that displays a [JoinedReaderPage] spread as a single composited image on one
 * zoomable surface.
 *
 * Both halves are loaded independently; once both are [Page.State.READY] their streams are merged
 * horizontally into one bitmap ([ImageUtil.mergeHorizontal]) and handed to a single
 * [ReaderPageImageView]. One image means the two halves meet at the centerline with no gap, and one
 * gesture surface means pinch/pan/double-tap transform the whole spread as one object, replacing
 * the previous two-child-holder layout and its fragile zoom-synchronisation bridge.
 *
 * [JoinedReaderPage.firstPage] always occupies the left slot and [JoinedReaderPage.secondPage] the
 * right slot; [groupPagesForDoublePage] has already applied the right-to-left swap when building the
 * spread, so no direction check is needed here.
 */
@SuppressLint("ViewConstructor")
class JoinedPagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: JoinedReaderPage,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

    private val firstPage get() = page.firstPage
    private val secondPage get() = page.secondPage

    private var progressIndicator: ReaderProgressIndicator? = null

    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    private var loadJob: Job? = null

    /** Guards against compositing more than once as the two status flows keep emitting. */
    private var composited = false

    init {
        loadJob = scope.launch { loadBothAndProcessStatus() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    /**
     * Loads both halves and shows the progress indicator until both are [Page.State.READY]. If
     * either half ends in [Page.State.ERROR] the spread falls back to showing the other half (or an
     * error if neither is available).
     */
    private suspend fun loadBothAndProcessStatus() {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO { loader.loadPage(firstPage) }
            launchIO { loader.loadPage(secondPage) }

            combine(
                firstPage.statusFlow,
                secondPage.statusFlow,
            ) { first, second -> first to second }
                .collectLatest { (first, second) ->
                    doublePageLog {
                        "JoinedPagerPageHolder(${firstPage.number}+${secondPage.number}): " +
                            "states first=$first second=$second"
                    }
                    when {
                        first == Page.State.READY && second == Page.State.READY -> setImage()
                        first == Page.State.ERROR && second == Page.State.ERROR -> setError()
                        // One half errored: show the other half alone rather than blocking the spread.
                        first == Page.State.ERROR -> setSingleImage(secondPage, "first half ERROR")
                        second == Page.State.ERROR -> setSingleImage(firstPage, "second half ERROR")
                        else -> setLoading()
                    }
                }
        }
    }

    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Both halves are ready: composite them into one image, unless an edge case forces a fallback
     * to a single half (wide page, animated image, or a null stream).
     */
    private suspend fun setImage() {
        if (composited) return
        composited = true

        progressIndicator?.setProgress(0)

        val firstStreamFn = firstPage.stream
        val secondStreamFn = secondPage.stream
        if (firstStreamFn == null || secondStreamFn == null) {
            composited = false
            val available = if (firstStreamFn != null) firstPage else secondPage
            setSingleImage(available, "null stream on the other half")
            return
        }

        try {
            val result = withIOContext {
                val firstSource = firstStreamFn().use { Buffer().readFrom(it) }
                val secondSource = secondStreamFn().use { Buffer().readFrom(it) }

                // Edge cases that make a single composited bitmap wrong:
                // - a half that decodes as wide: PagerPageHolder.process() sets page.isWide and
                //   calls refreshAdapter(), which re-runs groupPagesForDoublePage and dissolves this
                //   spread into standalone holders; render the non-wide half meanwhile.
                // - an animated half: compositing a static frame would freeze the animation.
                when {
                    ImageUtil.isAnimatedAndSupported(firstSource) ||
                        ImageUtil.isAnimatedAndSupported(secondSource) -> {
                        SpreadImage(firstSource, isAnimated = true, fallback = "animated half")
                    }
                    ImageUtil.isWideImage(firstSource) -> {
                        firstPage.isWide = true
                        maybeRefreshAdapter()
                        SpreadImage(secondSource, isAnimated = false, fallback = "first half is wide")
                    }
                    ImageUtil.isWideImage(secondSource) -> {
                        secondPage.isWide = true
                        maybeRefreshAdapter()
                        SpreadImage(firstSource, isAnimated = false, fallback = "second half is wide")
                    }
                    else -> {
                        val merged = ImageUtil.mergeHorizontal(firstSource, secondSource)
                        SpreadImage(merged, isAnimated = false, fallback = null)
                    }
                }
            }

            val background = withIOContext {
                if (!result.isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, result.source.peek().inputStream())
                } else {
                    null
                }
            }

            withUIContext {
                doublePageLog {
                    val branch = result.fallback ?: "composited spread"
                    "JoinedPagerPageHolder(${firstPage.number}+${secondPage.number}): $branch"
                }
                setImage(
                    result.source,
                    result.isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                        enablePinchToZoom = viewer.config.enablePinchToZoom,
                    ),
                )
                if (!result.isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            composited = false
            logcat(LogPriority.ERROR, e)
            withUIContext { setError() }
        }
    }

    /**
     * Render one half of the spread on its own (used for every fallback path).
     */
    private suspend fun setSingleImage(single: ReaderPage, reason: String) {
        if (composited) return
        composited = true

        val streamFn = single.stream
        if (streamFn == null) {
            composited = false
            withUIContext { setError() }
            return
        }

        try {
            val result = withIOContext {
                val source = streamFn().use { Buffer().readFrom(it) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                Triple(source, isAnimated, background)
            }
            withUIContext {
                doublePageLog {
                    "JoinedPagerPageHolder(${firstPage.number}+${secondPage.number}): " +
                        "single half ${single.number} ($reason)"
                }
                setImage(
                    result.first,
                    result.second,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                        enablePinchToZoom = viewer.config.enablePinchToZoom,
                    ),
                )
                if (!result.second) {
                    pageBackground = result.third
                }
                removeErrorLayout()
            }
        } catch (e: Throwable) {
            composited = false
            logcat(LogPriority.ERROR, e)
            withUIContext { setError() }
        }
    }

    private fun maybeRefreshAdapter() {
        if (viewer.config.joinDoublePages) {
            viewer.activity.runOnUiThread { viewer.refreshAdapter() }
        }
    }

    private data class SpreadImage(
        val source: BufferedSource,
        val isAnimated: Boolean,
        val fallback: String?,
    )

    private fun setError() {
        progressIndicator?.hide()
        showErrorLayout()
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator?.hide()
    }

    override fun onImageLoadError() {
        super.onImageLoadError()
        setError()
    }

    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(firstPage)
                page.chapter.pageLoader?.retryPage(secondPage)
            }
        }

        val imageUrl = firstPage.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null && imageUrl.startsWith("http", true)) {
            errorLayout?.actionOpenInWebView?.viewer = viewer
            errorLayout?.actionOpenInWebView?.setOnClickListener {
                val intent = WebViewActivity.newIntent(context, imageUrl)
                context.startActivity(intent)
            }
        }

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
