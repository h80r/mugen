package eu.kanade.presentation.reader.curl

// Not part of upstream pagecurl 1.5.1. Added for the manga curl viewer, where a live
// ReaderPageImageView sits on top of the curl and owns the whole touch stream: Compose does not
// propagate an unhandled pointer event between a gesture layer and an AndroidView sibling in either
// direction, so the fold cannot be driven by the library's own pointerInput modifiers. This drives
// the very same Animatable edges from raw view coordinates instead.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Which way an externally driven fold is going.
 */
public enum class ExternalFoldDirection { FORWARD, BACKWARD }

/**
 * A fold a host can drive from raw pointer coordinates.
 *
 * The lifecycle mirrors a drag gesture: [start] once the host has decided the gesture is a page
 * turn, [update] for every move, [finish] on up / cancel.
 *
 * Implemented by [ExternalFoldDriver]. The interface exists so the host can hold "the fold this
 * gesture owns" without depending on how it is built.
 */
@ExperimentalPageCurlApi
public interface ExternalFold {

    /** True between [start] and the end of the settle animation started by [finish]. */
    public val isActive: Boolean

    /** Names this fold in the diagnostics trace (temporary). */
    public val debugName: String

    /** Begins a fold; returns false when the surface is not laid out yet. */
    public fun start(direction: ExternalFoldDirection, start: Offset, size: IntSize): Boolean

    /** Moves the fold to follow [current]. */
    public fun update(current: Offset)

    /** Ends the fold, committing the turn or springing back. */
    public fun finish(commit: Boolean)

    /** Drops any in-flight fold without animating. */
    public fun cancel()
}

/**
 * Drives a [PageCurlState]'s fold from raw pointer coordinates, for hosts that own the touch stream
 * themselves (see the file header).
 *
 * All coordinates are in the curl surface's own pixel space, top-left origin — the caller is
 * responsible for un-mirroring them when the surface is drawn mirrored for R2L.
 */
@ExperimentalPageCurlApi
public class ExternalFoldDriver(
    private val state: PageCurlState,
    private val scope: CoroutineScope,
    /**
     * True when this state counts against reading order, as the spread's mirrored column does: a
     * gesture that reads as "forward" on screen is then a backward turn for this state. Carried here
     * rather than left to callers, so a screen-space gesture and a programmatic turn cannot end up
     * going opposite ways.
     */
    private val invertDirection: Boolean = false,
    /** Names this driver in the diagnostics trace, e.g. "left" / "right" (temporary). */
    override val debugName: String = "single",
) : ExternalFold {

    private val edgeCreator = NewEdgeCreator.PageEdge()

    // Observable so the host can keep the overlay composed for as long as a fold is in flight.
    private var direction: ExternalFoldDirection? by mutableStateOf(null)
    private var settling: Boolean by mutableStateOf(false)
    private var startOffset: Offset = Offset.Zero
    private var size: IntSize = IntSize.Zero
    private var job: Job? = null

    /** Sampling counter for the per-move diagnostics (temporary). */
    private var updateCount: Int = 0

    /** True between [start] and the end of the settle animation started by [finish]. */
    override val isActive: Boolean get() = direction != null || settling

    /**
     * Begins a fold in [direction] anchored at [start], within a surface of [size].
     *
     * Returns false when the state is not laid out yet, in which case the host should treat the
     * gesture as a plain scroll and not call [update] / [finish].
     */
    override fun start(direction: ExternalFoldDirection, start: Offset, size: IntSize): Boolean {
        val internal = state.internalState ?: run {
            SpreadCurlDiagnostics.log("driver.$debugName", "start refused: no internalState (not laid out)")
            return false
        }
        this.direction = if (!invertDirection) {
            direction
        } else {
            when (direction) {
                ExternalFoldDirection.FORWARD -> ExternalFoldDirection.BACKWARD
                ExternalFoldDirection.BACKWARD -> ExternalFoldDirection.FORWARD
            }
        }
        this.startOffset = start
        this.size = size
        // The inversion is the whole of problem 2's suspect chain: a screen-space "forward" becomes
        // a BACKWARD turn on the mirrored column. Logging both ends of the mapping, next to the
        // state it will act on, says whether the flip is applied where it was intended.
        SpreadCurlDiagnostics.log(
            "driver.$debugName",
            "start requested=$direction invert=$invertDirection effective=${this.direction} " +
                "current=${state.current} max=${state.max} " +
                "size=${size.width}x${size.height} " +
                "start=(${SpreadCurlDiagnostics.f(start.x)},${SpreadCurlDiagnostics.f(start.y)}) " +
                "constraints=${internal.constraints.maxWidth}x${internal.constraints.maxHeight}",
        )

        job?.cancel()
        job = scope.launch {
            internal.animateJob?.cancel()
            internal.reset()
        }
        return true
    }

    /** Moves the fold to follow [current]. No-op unless a fold is in flight. */
    override fun update(current: Offset) {
        val direction = direction ?: return
        val internal = state.internalState ?: return
        val edge = internal.edgeFor(direction)
        val target = edgeCreator.createNew(size, startOffset, current)
        // Sampled every few moves: the edge the fold is being driven towards. Its centre walking
        // away from the finger instead of with it is the signature of an inverted fold.
        if (updateCount++ % UPDATE_LOG_EVERY == 0) {
            SpreadCurlDiagnostics.log(
                "driver.$debugName",
                "update dir=$direction pointerX=${SpreadCurlDiagnostics.f(current.x)} " +
                    "targetTopX=${SpreadCurlDiagnostics.f(target.top.x)} " +
                    "targetBottomX=${SpreadCurlDiagnostics.f(target.bottom.x)} " +
                    "progress=${SpreadCurlDiagnostics.f2(state.progress)}",
            )
        }
        job?.cancel()
        job = scope.launch { edge.animateTo(target) }
    }

    /**
     * Ends the fold. When [commit] is true the page settles on the next / previous page, otherwise
     * it springs back to where it started.
     */
    override fun finish(commit: Boolean) {
        val direction = this.direction ?: run {
            SpreadCurlDiagnostics.log("driver.$debugName", "finish ignored: no fold in flight")
            return
        }
        this.direction = null

        val internal = state.internalState ?: return
        val edge = internal.edgeFor(direction)
        val from = internal.restingEdgeFor(direction)
        val to = internal.targetEdgeFor(direction)
        val delta = if (direction == ExternalFoldDirection.FORWARD) +1 else -1
        // The page this fold will actually land on. Read against the drag's on-screen direction,
        // this is where an inverted turn becomes undeniable: a rightward drag that reports
        // delta=-1 turned backward.
        SpreadCurlDiagnostics.log(
            "driver.$debugName",
            "finish dir=$direction commit=$commit delta=$delta " +
                "current=${state.current} -> ${if (commit) state.current + delta else state.current}",
        )

        job?.cancel()
        settling = true
        job = scope.launch {
            try {
                if (commit) {
                    try {
                        // Stop just short of the very end. A fold that reaches the target edge
                        // exactly hits drawCurl's `parked at left` fast path, which draws *nothing*
                        // — and both of a column's flaps are then blank at once, so that column
                        // paints no pixels at all until `current` advances and the edge is snapped
                        // back. Instrumented with `curl.branch`, that gap measured 16-26ms on every
                        // turn, always on the column that had just folded: the flicker at the end of
                        // the animation.
                        //
                        // The last sliver is invisible anyway — the page is off-screen by then — so
                        // finishing the travel a hair early costs nothing and keeps the flap on the
                        // `CURLING` path right up to the frame the new page replaces it.
                        edge.animateTo(to.pulledBackTowards(from, SETTLE_EPSILON))
                    } finally {
                        SpreadCurlDiagnostics.log(
                            "driver.$debugName",
                            "settle.begin current=${state.current} -> ${state.current + delta} " +
                                "progress=${SpreadCurlDiagnostics.f2(state.progress)}",
                        )
                        state.current = (state.current + delta).coerceIn(0, (state.max - 1).coerceAtLeast(0))
                        edge.snapTo(from)
                        SpreadCurlDiagnostics.log(
                            "driver.$debugName",
                            "settled current=${state.current} " +
                                "progress=${SpreadCurlDiagnostics.f2(state.progress)}",
                        )
                    }
                } else {
                    try {
                        edge.animateTo(from)
                    } finally {
                        edge.snapTo(from)
                    }
                }
            } finally {
                settling = false
            }
        }
    }

    /** Drops any in-flight fold without animating, e.g. when the page changes underneath. */
    override fun cancel() {
        if (direction != null || settling) {
            SpreadCurlDiagnostics.log("driver.$debugName", "cancel dir=$direction settling=$settling")
        }
        direction = null
        settling = false
        job?.cancel()
        job = null
    }

    private companion object {
        /** One in every N `update` calls is logged (temporary instrumentation). */
        const val UPDATE_LOG_EVERY = 4
    }
}

@ExperimentalPageCurlApi
private fun PageCurlState.InternalState.edgeFor(
    direction: ExternalFoldDirection,
): Animatable<Edge, AnimationVector4D> = when (direction) {
    ExternalFoldDirection.FORWARD -> forward
    ExternalFoldDirection.BACKWARD -> backward
}

@ExperimentalPageCurlApi
private fun PageCurlState.InternalState.restingEdgeFor(direction: ExternalFoldDirection): Edge = when (direction) {
    ExternalFoldDirection.FORWARD -> rightEdge
    ExternalFoldDirection.BACKWARD -> leftEdge
}

@ExperimentalPageCurlApi
private fun PageCurlState.InternalState.targetEdgeFor(direction: ExternalFoldDirection): Edge = when (direction) {
    ExternalFoldDirection.FORWARD -> leftEdge
    ExternalFoldDirection.BACKWARD -> rightEdge
}
