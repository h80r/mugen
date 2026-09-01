package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.CallSuper
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import coil3.BitmapImage
import coil3.asDrawable
import coil3.dispose
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.ViewSizeResolver
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_IN_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.EASE_OUT_QUAD
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.cropBorders
import eu.kanade.tachiyomi.data.coil.customDecoder
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonBorderDetector
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonSubsamplingImageView
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * A wrapper view for showing page image.
 *
 * Animated image will be drawn by [PhotoView] while [SubsamplingScaleImageView] will take non-animated image.
 *
 * @param isWebtoon if true, [WebtoonSubsamplingImageView] will be used instead of [SubsamplingScaleImageView]
 * and [AppCompatImageView] will be used instead of [PhotoView]
 */
open class ReaderPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttrs: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    private val isWebtoon: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttrs, defStyleRes) {

    private val alwaysDecodeLongStripWithSSIV by lazy {
        Injekt.get<BasePreferences>().alwaysDecodeLongStripWithSSIV().get()
    }

    private var pageView: View? = null

    /**
     * The inner image view, for diagnostics only (temporary instrumentation): the curl viewer needs
     * the live scale at the frame it swaps this overlay in for its own columns.
     */
    internal val debugPageView: View? get() = pageView

    private var config: Config? = null

    private var scope: CoroutineScope? = null
    private var smartFitJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        scope = null
        smartFitJob?.cancel()
        smartFitJob = null
    }

    var onImageLoaded: (() -> Unit)? = null
    var onImageLoadError: (() -> Unit)? = null
    var onScaleChanged: ((newScale: Float) -> Unit)? = null
    var onViewClicked: (() -> Unit)? = null

    /**
     * For automatic background. Will be set as background color when [onImageLoaded] is called.
     */
    var pageBackground: Drawable? = null

    @CallSuper
    open fun onImageLoaded() {
        onImageLoaded?.invoke()
        background = pageBackground
    }

    @CallSuper
    open fun onImageLoadError() {
        onImageLoadError?.invoke()
    }

    @CallSuper
    open fun onScaleChanged(newScale: Float) {
        onScaleChanged?.invoke(newScale)
    }

    @CallSuper
    open fun onViewClicked() {
        onViewClicked?.invoke()
    }

    open fun onPageSelected(forward: Boolean) {
        with(pageView as? SubsamplingScaleImageView) {
            if (this == null) return
            if (isReady) {
                landscapeZoom(forward)
            } else {
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(config)
                            landscapeZoom(forward)
                            this@ReaderPageImageView.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageLoadError()
                        }
                    },
                )
            }
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean) {
        if (config != null &&
            config!!.landscapeZoom &&
            config!!.minimumScaleType == SCALE_TYPE_CENTER_INSIDE &&
            sWidth > sHeight &&
            scale == minScale
        ) {
            handler?.postDelayed(500) {
                // Everything this callback touches was validated 500ms ago, and the page can be
                // recycled in between — which the curl viewer does routinely, turning pages faster
                // than this delay. A recycled SubsamplingScaleImageView reports isReady false and
                // makes animateScaleAndCenter return null, so the `!!` below crashed with an NPE on
                // a page that is already gone. Re-check readiness and bail instead.
                if (!isReady) return@postDelayed
                val zoomStartPosition = config?.zoomStartPosition ?: return@postDelayed
                val point = when (zoomStartPosition) {
                    ZoomStartPosition.LEFT -> if (forward) {
                        PointF(0F, 0F)
                    } else {
                        PointF(
                            sWidth.toFloat(),
                            0F,
                        )
                    }
                    ZoomStartPosition.RIGHT -> if (forward) {
                        PointF(sWidth.toFloat(), 0F)
                    } else {
                        PointF(
                            0F,
                            0F,
                        )
                    }
                    ZoomStartPosition.CENTER -> center
                }

                val targetScale = height.toFloat() / sHeight.toFloat()
                // landscapeZoom deliberately rests the page zoomed in; that becomes its resting
                // scale, so a drag on a wide page still turns the page instead of panning.
                restingScale = targetScale
                // Nullable rather than `!!`: the builder is null whenever the view is not ready, and
                // the isReady check above cannot rule out a recycle landing between the two.
                animateScaleAndCenter(targetScale, point)
                    ?.withDuration(500)
                    ?.withEasing(EASE_IN_OUT_QUAD)
                    ?.withInterruptible(true)
                    ?.start()
            }
        }
    }

    fun setImage(drawable: Drawable, config: Config) {
        this.config = config
        restingScale = null
        smartFitJob?.cancel()
        smartFitJob = null
        if (drawable is Animatable) {
            prepareAnimatedImageView()
            setAnimatedImage(drawable, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(drawable, config)
        }
    }

    fun setImage(source: BufferedSource, isAnimated: Boolean, config: Config) {
        this.config = config
        restingScale = null
        smartFitJob?.cancel()
        smartFitJob = null
        if (isAnimated) {
            prepareAnimatedImageView()
            setAnimatedImage(source, config)
        } else {
            prepareNonAnimatedImageView()
            setNonAnimatedImage(source, config)
        }
    }

    fun recycle() {
        restingScale = null
        smartFitJob?.cancel()
        smartFitJob = null
        pageView?.let {
            when (it) {
                is SubsamplingScaleImageView -> it.recycle()
                is AppCompatImageView -> it.dispose()
            }
            it.isVisible = false
        }
    }

    /**
     * The scale the page settles at once laid out, which is NOT always `minScale`: `landscapeZoom`
     * and a non-centre `zoomStartPosition` deliberately rest the page zoomed in. Recorded so
     * [isZoomedIn] can tell "the user zoomed" from "this is how the page rests".
     */
    private var restingScale: Float? = null

    /**
     * True when the user has zoomed the page in past the scale it rests at.
     *
     * Used by the manga curl viewer to decide whether a drag pans the image or turns the page, so it
     * must stay false for a page that merely rests zoomed in via `landscapeZoom` — otherwise every
     * drag on a wide page would be swallowed by the image.
     */
    val isZoomedIn: Boolean
        get() = (pageView as? SubsamplingScaleImageView)?.let {
            it.scale > (restingScale ?: it.minScale) * 1.01f
        } ?: ((pageView as? PhotoView)?.let { it.scale > 1.01f } ?: false)

    /**
     * Toggles double-tap zoom at the given view coordinates, the way the view's own double-tap
     * handler would.
     *
     * Exposed for the manga curl viewer, where a touch dispatcher owns the whole event stream and
     * has to drive the zoom itself instead of letting the gesture reach this view — see
     * `CurlTouchDispatcher`. Zooms out to fit when already zoomed in.
     */
    fun toggleDoubleTapZoom(viewX: Float, viewY: Float) {
        val view = pageView as? SubsamplingScaleImageView ?: return
        if (view.sWidth <= 0 || view.sHeight <= 0) return

        val zoomedIn = view.scale > view.minScale * 1.0001f
        val targetScale = if (zoomedIn) view.minScale else view.maxScale.coerceAtMost(view.minScale * 2f)
        val target = if (zoomedIn) {
            view.center ?: return
        } else {
            view.viewToSourceCoord(viewX, viewY) ?: return
        }

        view.animateScaleAndCenter(targetScale, target)
            ?.withDuration((config?.zoomDuration ?: 500).getSystemScaledDuration().toLong())
            ?.withEasing(EASE_IN_OUT_QUAD)
            ?.withInterruptible(true)
            ?.start()
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 1
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->

            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun prepareNonAnimatedImageView() {
        if (pageView is SubsamplingScaleImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            WebtoonSubsamplingImageView(context)
        } else {
            SubsamplingScaleImageView(context)
        }.apply {
            setMaxTileSize(ImageUtil.hardwareBitmapThreshold)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumTileDpi(180)
            if (isWebtoon) {
                // Defer high-res tile decoding until gestures/flings settle. This
                // reduces decode churn while scrolling through long strips, which
                // is a major source of dropped frames on high refresh rate
                // displays (90/120Hz+).
                setEagerLoadingEnabled(false)
            }
            setOnStateChangedListener(
                object : SubsamplingScaleImageView.OnStateChangedListener {
                    override fun onScaleChanged(newScale: Float, origin: Int) {
                        this@ReaderPageImageView.onScaleChanged(newScale)
                    }

                    override fun onCenterChanged(newCenter: PointF?, origin: Int) {
                        // Not used
                    }
                },
            )
            setOnClickListener { this@ReaderPageImageView.onViewClicked() }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun SubsamplingScaleImageView.setupZoom(config: Config?) {
        // 5x zoom
        maxScale = scale * MAX_ZOOM_SCALE
        setDoubleTapZoomScale(scale * 2)
        // How this page rests, before the user zooms anything (see [restingScale]).
        restingScale = scale

        when (config?.zoomStartPosition) {
            ZoomStartPosition.LEFT -> setScaleAndCenter(scale, PointF(0F, 0F))
            ZoomStartPosition.RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0F))
            ZoomStartPosition.CENTER -> setScaleAndCenter(scale, center)
            null -> {}
        }
    }

    private fun setNonAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? SubsamplingScaleImageView)?.apply {
        setZoomEnabled(config.enablePinchToZoom)
        setDoubleTapZoomDuration(config.zoomDuration.getSystemScaledDuration())
        setMinimumScaleType(config.minimumScaleType)
        setMinimumDpi(1) // Just so that very small image will be fit for initial load
        setCropBorders(config.cropBorders)
        setOnImageEventListener(
            object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    setupZoom(config)
                    if (isVisibleOnScreen()) landscapeZoom(true)
                    this@ReaderPageImageView.onImageLoaded()
                }

                override fun onImageLoadError(e: Exception) {
                    this@ReaderPageImageView.onImageLoadError()
                }
            },
        )

        when (data) {
            is BitmapDrawable -> {
                val bitmap = data.bitmap
                if (config.webtoonSmartFit && scope != null) {
                    smartFitJob = scope?.launch {
                        val bounds = withContext(Dispatchers.Default) {
                            WebtoonBorderDetector.detectContentBounds(bitmap)
                        }
                        setImage(ImageSource.bitmap(bitmap).region(bounds))
                        isVisible = true
                    }
                } else {
                    setImage(ImageSource.bitmap(bitmap))
                    isVisible = true
                }
            }
            is BufferedSource -> {
                val supportsHardwareBitmap = config.canUseHardwareBitmap
                    ?: ImageUtil.canUseHardwareBitmap(data)
                if (
                    !isWebtoon ||
                    alwaysDecodeLongStripWithSSIV ||
                    (config.isTallImage ?: ImageUtil.isTallImage(data)) ||
                    !supportsHardwareBitmap
                ) {
                    setHardwareConfig(supportsHardwareBitmap)
                    if (config.webtoonSmartFit && scope != null) {
                        smartFitJob = scope?.launch {
                            val bounds = withContext(Dispatchers.IO) {
                                WebtoonBorderDetector.detectContentBounds(data.peek().inputStream())
                            }
                            setImage(ImageSource.inputStream(data.inputStream()).region(bounds))
                            isVisible = true
                        }
                    } else {
                        setImage(ImageSource.inputStream(data.inputStream()))
                        isVisible = true
                    }
                    return@apply
                }

                ImageRequest.Builder(context)
                    .data(data)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .target(
                        onSuccess = { result ->
                            val image = result as BitmapImage
                            val bitmap = image.bitmap
                            if (config.webtoonSmartFit && scope != null) {
                                smartFitJob = scope?.launch {
                                    val bounds = withContext(Dispatchers.Default) {
                                        WebtoonBorderDetector.detectContentBounds(bitmap)
                                    }
                                    setImage(ImageSource.bitmap(bitmap).region(bounds))
                                    isVisible = true
                                }
                            } else {
                                setImage(ImageSource.bitmap(bitmap))
                                isVisible = true
                            }
                        },
                        onError = {
                            onImageLoadError()
                        },
                    )
                    .size(ViewSizeResolver(this@ReaderPageImageView))
                    .precision(Precision.INEXACT)
                    .cropBorders(config.cropBorders)
                    .customDecoder(true)
                    .crossfade(false)
                    .build()
                    .let(context.imageLoader::enqueue)
            }
            else -> {
                throw IllegalArgumentException("Not implemented for class ${data::class.simpleName}")
            }
        }
    }

    private fun prepareAnimatedImageView() {
        if (pageView is AppCompatImageView) return
        removeView(pageView)

        pageView = if (isWebtoon) {
            AppCompatImageView(context)
        } else {
            PhotoView(context)
        }.apply {
            adjustViewBounds = true

            if (this is PhotoView) {
                setScaleLevels(1F, 2F, MAX_ZOOM_SCALE)
                // Force 2 scale levels on double tap
                setOnDoubleTapListener(
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            if (scale > 1F) {
                                setScale(1F, e.x, e.y, true)
                            } else {
                                setScale(2F, e.x, e.y, true)
                            }
                            return true
                        }

                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            this@ReaderPageImageView.onViewClicked()
                            return super.onSingleTapConfirmed(e)
                        }
                    },
                )
                setOnScaleChangeListener { _, _, _ ->
                    this@ReaderPageImageView.onScaleChanged(scale)
                }
            }
        }
        addView(pageView, MATCH_PARENT, MATCH_PARENT)
    }

    private fun setAnimatedImage(
        data: Any,
        config: Config,
    ) = (pageView as? AppCompatImageView)?.apply {
        if (this is PhotoView) {
            setZoomTransitionDuration(config.zoomDuration.getSystemScaledDuration())
            isZoomable = config.enablePinchToZoom
        }

        val request = ImageRequest.Builder(context)
            .data(data)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .target(
                onSuccess = { result ->
                    val drawable = result.asDrawable(context.resources)
                    setImageDrawable(drawable)
                    (drawable as? Animatable)?.start()
                    isVisible = true
                    this@ReaderPageImageView.onImageLoaded()
                },
                onError = {
                    this@ReaderPageImageView.onImageLoadError()
                },
            )
            .crossfade(false)
            .build()
        context.imageLoader.enqueue(request)
    }

    private fun Int.getSystemScaledDuration(): Int {
        return (this * context.animatorDurationScale).toInt().coerceAtLeast(1)
    }

    /**
     * All of the config except [zoomDuration] will only be used for non-animated image.
     */
    data class Config(
        val zoomDuration: Int,
        val minimumScaleType: Int = SCALE_TYPE_CENTER_INSIDE,
        val cropBorders: Boolean = false,
        val webtoonSmartFit: Boolean = false,
        val zoomStartPosition: ZoomStartPosition = ZoomStartPosition.CENTER,
        val landscapeZoom: Boolean = false,
        val enablePinchToZoom: Boolean = true,
        /**
         * Image traits precomputed off the UI thread by callers that already inspect the image
         * on IO. When null, they are computed on demand (which touches image headers on the
         * calling thread).
         */
        val isTallImage: Boolean? = null,
        val canUseHardwareBitmap: Boolean? = null,
    )

    enum class ZoomStartPosition {
        LEFT,
        CENTER,
        RIGHT,
    }

    fun getImageView(): View? = pageView
}

private const val MAX_ZOOM_SCALE = 5F
