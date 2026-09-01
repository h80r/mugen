package eu.kanade.presentation.reader.curl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * How far short of a target edge a settling fold stops, as a fraction of the travel.
 *
 * drawCurl short-circuits to drawing nothing when a flap's edge sits exactly on the surface corner,
 * which leaves the whole column blank for the frame or two before `current` advances. Only enough is
 * needed to miss that comparison, which is an exact Offset equality — a thousandth of the page is
 * far under a pixel of visible difference.
 */
internal const val SETTLE_EPSILON: Float = 0.001f

/** This edge moved [fraction] of the way back towards [other]. */
internal fun Edge.pulledBackTowards(other: Edge, fraction: Float): Edge = Edge(
    top = top + (other.top - top) * fraction,
    bottom = bottom + (other.bottom - bottom) * fraction,
)

internal data class PageTurnAnimationTiming(
    val durationMillis: Int,
    val midpointMillis: Int,
)

internal fun resolvePageTurnAnimationTiming(durationMillis: Int): PageTurnAnimationTiming {
    val safeDurationMillis = durationMillis.coerceAtLeast(1)
    val midpointMillis = if (safeDurationMillis == 1) {
        0
    } else {
        (safeDurationMillis / 3).coerceIn(1, safeDurationMillis - 1)
    }
    return PageTurnAnimationTiming(
        durationMillis = safeDurationMillis,
        midpointMillis = midpointMillis,
    )
}

/**
 * Builds the tap-driven page-turn animation: a three-keyframe [Edge] sweep from the settled edge, through a
 * curved mid-edge (so the leaf visibly bends rather than sliding flat), to the opposite settled edge.
 * [curlAmount] shapes how far the mid-edge bows; [forward] picks the direction.
 */
@OptIn(ExperimentalPageCurlApi::class)
internal fun createPageTurnAnimation(
    animationDurationMillis: Int,
    forward: Boolean,
    curlAmount: Float,
): suspend Animatable<Edge, AnimationVector4D>.(Size) -> Unit {
    val timing = resolvePageTurnAnimationTiming(animationDurationMillis)
    return { size ->
        val startEdge = size.startEdge()
        val middleEdge = resolvePageTurnCurlMidEdge(size, forward, curlAmount)
        val endEdge = size.endEdge()
        // A forward turn ends on startEdge, which is exactly the top-left / bottom-left pair that
        // drawCurl treats as "parked" and short-circuits to drawing nothing. Landing on it leaves
        // both of a column's flaps blank until `current` advances a frame or two later, and that
        // column paints nothing meanwhile — traced with curl.branch as `NOTHING (parked left)` 3ms
        // before the turn settled, which is the flash at the end of a tapped turn.
        //
        // Stopping a thousandth of the travel short keeps the flap on the real curl path to the
        // last frame. The fast path compares Offsets exactly, so that is enough, and by then the
        // page has left the screen — far less than a pixel of difference. The backward turn ends on
        // endEdge, which is not a parked value, and is left alone.
        val forwardTarget = startEdge.pulledBackTowards(endEdge, SETTLE_EPSILON)
        animateTo(
            targetValue = if (forward) forwardTarget else endEdge,
            animationSpec = keyframes {
                durationMillis = timing.durationMillis
                if (forward) {
                    endEdge at 0
                    middleEdge at timing.midpointMillis
                } else {
                    startEdge at 0
                    middleEdge at (timing.durationMillis - timing.midpointMillis)
                }
            },
        )
    }
}

internal fun resolvePageTurnCurlMidEdge(
    size: Size,
    forward: Boolean,
    curlAmount: Float,
): Edge {
    val normalizedCurl = ((curlAmount - 0.28f) / 0.64f).coerceIn(0f, 1f)
    val topX = size.width * (0.94f - (0.16f * normalizedCurl))
    val topY = size.height * (0.18f + (0.04f * normalizedCurl))
    val bottomX = size.width * (0.56f - (0.18f * normalizedCurl))
    val bottomY = size.height * (0.98f - (0.02f * normalizedCurl))

    return if (forward) {
        Edge(
            top = Offset(topX.coerceIn(0f, size.width), topY.coerceIn(0f, size.height)),
            bottom = Offset(bottomX.coerceIn(0f, size.width), bottomY.coerceIn(0f, size.height)),
        )
    } else {
        Edge(
            top = Offset((size.width - topX).coerceIn(0f, size.width), topY.coerceIn(0f, size.height)),
            bottom = Offset((size.width - bottomX).coerceIn(0f, size.width), bottomY.coerceIn(0f, size.height)),
        )
    }
}

internal fun Size.startEdge(): Edge {
    return Edge(
        top = Offset(0f, 0f),
        bottom = Offset(0f, height),
    )
}

internal fun Size.endEdge(): Edge {
    return Edge(
        top = Offset(width, height),
        bottom = Offset(width, height),
    )
}
