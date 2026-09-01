@file:OptIn(ExperimentalPageCurlApi::class)

package eu.kanade.presentation.reader.curl

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/**
 * The manga-side analogue of the novel reader's `NovelPageTurnRendererConfig`. The manga curl
 * viewer has no style enum, no parchment back-page tint and no configurable activation zone, so
 * this is a flat set of fixed knobs plus the resolved [PageTurnPreset].
 *
 * @property instant when true the reader preference `pageTransitions()` is off — the curl still
 *   drives navigation but tap/drage-driven turns snap instead of animating (mirrors how the
 *   legacy pager passes `smoothScroll = false`).
 * @property solidBackPage when true the turning flap's back is a solid [backPageColor] rather than
 *   the real neighbouring page — the single-page reading mode, where a "next page on the back of
 *   the leaf" has no physical meaning. The two-column spread (group 4) sets this false.
 * @property mirrored when true the whole surface is horizontally flipped (`scaleX = -1f`) so the
 *   fold anchors at the right edge and a physical left→right swipe advances — right-to-left
 *   reading. Page artwork is un-mirrored per draw so it still reads the right way round.
 */
data class MangaPageCurlRendererConfig(
    val preset: PageTurnPreset,
    val instant: Boolean,
    val solidBackPage: Boolean,
    val mirrored: Boolean,
    val backPageColor: Color,
    val shadowColor: Color,
    val shadowRadiusDp: Float,
    val shadowOffsetXDp: Float,
    val dragActivationEdgeFraction: Float,
    val dragTargetReachFraction: Float,
    val centerTapWidthFraction: Float,
)

/** Curl preset used when page transitions are on. Mirrors the novel reader's `CURL` style values. */
private val MangaCurlPreset = PageTurnPreset(
    animationDurationMillis = 380,
    curlAmount = 0.72f,
    shadowAlpha = 0.38f,
    backPageAlpha = 0.32f,
)

/** Instant preset: a near-zero duration so a "turn" resolves in a frame when transitions are off. */
private val MangaInstantPreset = PageTurnPreset(
    animationDurationMillis = 1,
    curlAmount = 0.72f,
    shadowAlpha = 0f,
    backPageAlpha = 0.32f,
)

fun resolveMangaPageCurlRendererConfig(
    usePageTransitions: Boolean,
    solidBackPage: Boolean = true,
    mirrored: Boolean = false,
    backPageColor: Color = Color.Black,
): MangaPageCurlRendererConfig {
    val preset = if (usePageTransitions) MangaCurlPreset else MangaInstantPreset
    val edgeFraction = 0.30f
    return MangaPageCurlRendererConfig(
        preset = preset,
        instant = !usePageTransitions,
        solidBackPage = solidBackPage,
        mirrored = mirrored,
        backPageColor = backPageColor,
        shadowColor = Color.Black,
        shadowRadiusDp = 18f + (preset.shadowAlpha * 24f),
        shadowOffsetXDp = 4f + (preset.curlAmount * 8f),
        dragActivationEdgeFraction = edgeFraction,
        dragTargetReachFraction = (edgeFraction + 0.50f).coerceIn(0.76f, 0.94f),
        centerTapWidthFraction = 0.20f,
    )
}

/** Edge drag zones: a physical swipe from either side starts a fold, ends near the far edge. */
fun MangaPageCurlRendererConfig.dragInteraction(): PageCurlConfig.DragInteraction {
    val edge = dragActivationEdgeFraction
    val reach = dragTargetReachFraction
    return PageCurlConfig.StartEndDragInteraction(
        pointerBehavior = PageCurlConfig.DragInteraction.PointerBehavior.PageEdge,
        backward = PageCurlConfig.StartEndDragInteraction.Config(
            start = Rect(0f, 0f, edge, 1f),
            end = Rect(1f - reach, 0f, 1f, 1f),
        ),
        forward = PageCurlConfig.StartEndDragInteraction.Config(
            start = Rect(1f - edge, 0f, 1f, 1f),
            end = Rect(0f, 0f, reach, 1f),
        ),
    )
}

/** Left/right edge tap zones for page turns; the centre band is left for the UI toggle. */
fun MangaPageCurlRendererConfig.tapInteraction(): PageCurlConfig.TargetTapInteraction {
    val edgeTapWidth = ((1f - centerTapWidthFraction) / 2f).coerceIn(0.18f, 0.4f)
    return PageCurlConfig.TargetTapInteraction(
        backward = PageCurlConfig.TargetTapInteraction.Config(
            Rect(0f, 0f, edgeTapWidth, 1f),
        ),
        forward = PageCurlConfig.TargetTapInteraction.Config(
            Rect(1f - edgeTapWidth, 0f, 1f, 1f),
        ),
    )
}
