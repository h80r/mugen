package eu.kanade.presentation.reader.novel

import android.view.View

/**
 * The scrollable surface the book renderer actually shows.
 *
 * The reader chrome (auto-scroll, volume keys, tap zones, TTS follow-along) was written against the
 * three chapter surfaces: the chapter WebView, the chapter pager and the chapter lazy list. Book mode
 * renders none of them - [NovelBookReader] owns a private WebView - so every one of those features
 * addressed a view that is not on screen and silently did nothing. The book renderer publishes this
 * small surface instead, and the chrome drives whatever is mounted.
 */
internal interface NovelBookScrollSurface {

    /** True while the book advances by discrete pages instead of a continuous scroll. */
    fun isPaginated(): Boolean

    /** True while the surface can still scroll down inside the mounted document. */
    fun canScrollForward(): Boolean

    /** Scrolls by [distancePx] and returns how much of it the surface consumed. */
    fun scrollBy(distancePx: Int): Int

    /** Steps one page (paginated) or one viewport (scrolled). */
    fun step(forward: Boolean)

    /**
     * Runs [script] inside the mounted book document.
     *
     * TTS follow-along used to address the chapter WebView, which book mode never mounts, so it
     * scrolled a view that is not on screen. The book renderer owns its own document and this is the
     * only channel the reader chrome has into it.
     */
    fun evaluate(script: String) = Unit
}

/** What the book engine answered to one scroll request. */
internal data class NovelBookScrollReport(
    val consumedPx: Int,
    val canScrollForward: Boolean,
)

/**
 * Scrolls the book viewport and asks the engine what actually happened.
 *
 * `window.__anBookEngine.scrollBy(px)` already returns the clamped, really scrolled pixel amount and
 * `canScrollForward()` knows whether the document can still move down. The previous script threw
 * both away, which is why auto-scroll could never tell "scrolled" from "stuck at the end".
 */
internal fun buildBookScrollByJavascript(distancePx: Int): String {
    return listOf(
        "(function() {",
        "  var engine = window.__anBookEngine;",
        "  if (engine && typeof engine.scrollBy === 'function') {",
        "    var consumed = engine.scrollBy($distancePx);",
        "    var forward = typeof engine.canScrollForward === 'function' ? engine.canScrollForward() : true;",
        "    return JSON.stringify({ consumed: consumed, forward: forward });",
        "  }",
        "  var viewport = document.getElementById('an-book-viewport');",
        "  if (!viewport) { return JSON.stringify({ consumed: 0, forward: false }); }",
        "  var before = viewport.scrollTop;",
        "  var maximum = Math.max(0, viewport.scrollHeight - viewport.clientHeight);",
        "  viewport.scrollTop = Math.max(0, Math.min(maximum, before + ($distancePx)));",
        "  return JSON.stringify({",
        "    consumed: Math.round(viewport.scrollTop - before),",
        "    forward: viewport.scrollTop < (maximum - 1)",
        "  });",
        "})()",
    ).joinToString("\n")
}

/**
 * Parses the engine answer.
 *
 * `evaluateJavascript` hands the returned JSON back as a quoted and escaped JSON string, so the
 * payload is reduced to bare `key:value` pairs before reading the two fields.
 */
internal fun parseBookScrollReport(rawResult: String?): NovelBookScrollReport? {
    if (rawResult.isNullOrBlank() || rawResult == "null") return null
    val fields = rawResult
        .filter { it.isLetterOrDigit() || it == ':' || it == ',' || it == '-' }
        .split(',')
        .mapNotNull { field ->
            val key = field.substringBefore(':', "")
            val value = field.substringAfter(':', "")
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
    val consumed = fields["consumed"]?.toIntOrNull() ?: return null
    val forward = fields["forward"]?.toBooleanStrictOrNull() ?: true
    return NovelBookScrollReport(consumedPx = consumed, canScrollForward = forward)
}

/**
 * [NovelBookScrollSurface] backed by the renderer's own view.
 *
 * Paginated flow deliberately consumes nothing from [scrollBy]: the document lays itself out in
 * columns, so pixel scrolling would tear the page instead of turning it. Callers step pages with
 * [step] in that flow.
 */
internal class ViewNovelBookScrollSurface(
    private val view: View,
    private val paginated: () -> Boolean,
    private val onStep: (Boolean) -> Unit,
) : NovelBookScrollSurface {

    @Volatile
    private var isScrollPending = false

    /** Pixels the engine reported for the last finished scroll, reused while one is in flight. */
    @Volatile
    private var lastConsumedPx = 0

    /** Whether the engine still has room below the viewport. Optimistic until it answers once. */
    @Volatile
    private var engineCanScrollForward = true

    override fun isPaginated(): Boolean = paginated()

    // Paginated flow turns pages instead of scrolling, so forward scrollability is never the reason
    // to stop there. In scrolled flow the engine's own answer decides, instead of the previous
    // hardcoded `true` that made the end of the book unreachable for auto-scroll.
    override fun canScrollForward(): Boolean = paginated() || engineCanScrollForward

    override fun scrollBy(distancePx: Int): Int {
        if (paginated() || distancePx == 0) return 0
        val webView = view as? android.webkit.WebView ?: return 0
        if (isScrollPending) return lastConsumedPx
        isScrollPending = true
        webView.evaluateJavascript(buildBookScrollByJavascript(distancePx)) { result ->
            isScrollPending = false
            parseBookScrollReport(result)?.let { report ->
                lastConsumedPx = report.consumedPx
                engineCanScrollForward = report.canScrollForward
            }
        }
        // The round trip is asynchronous, so the frame that starts it cannot know how much the
        // engine really moved. Returning the full requested distance here would make auto-scroll
        // believe it progressed even at the very end of the book (where the engine moves nothing),
        // so the end was never detected. Returning 0 keeps the first frame conservative: auto-scroll
        // just asks again next frame, and every later frame reports what the engine actually moved.
        return lastConsumedPx
    }

    override fun step(forward: Boolean) = onStep(forward)

    override fun evaluate(script: String) {
        val webView = view as? android.webkit.WebView ?: return
        webView.post { webView.evaluateJavascript(script, null) }
    }
}

/**
 * [NovelBookScrollSurface] backed by the native book list ([LazyListState]).
 *
 * The native renderer hosts the resident book sections in the reader's LazyColumn, so the chrome
 * (auto-scroll, volume keys, TTS follow-along) used to find no surface there and silently did
 * nothing. This surface translates the same calls onto the list state the native renderer already
 * owns.
 */
internal class LazyListNovelBookScrollSurface(
    private val listState: androidx.compose.foundation.lazy.LazyListState,
) : NovelBookScrollSurface {

    override fun isPaginated(): Boolean = false

    override fun canScrollForward(): Boolean = listState.canScrollForward

    override fun scrollBy(distancePx: Int): Int {
        if (distancePx == 0) return 0
        // dispatchRawDelta moves the list synchronously without a coroutine, which keeps this
        // surface usable from the auto-scroll frame loop. The list exposes its real scrollability
        // through canScrollForward, so "moved" is reported only while there was actually room: at
        // the end of the book the list stops moving, canScrollForward turns false and auto-scroll
        // reads the end instead of looping forever.
        val couldMove = listState.canScrollForward
        if (couldMove) {
            runCatching { listState.dispatchRawDelta(distancePx.toFloat()) }
        }
        return if (couldMove) distancePx else 0
    }

    override fun step(forward: Boolean) {
        // One "page" in the native list is roughly one viewport, mirroring the WebView flow where a
        // step scrolls ~90% of the viewport. dispatchRawDelta keeps this synchronous.
        val viewportSize = listState.layoutInfo.viewportEndOffset
        val distance = if (forward) viewportSize.coerceAtLeast(1) else -viewportSize.coerceAtLeast(1)
        runCatching { listState.dispatchRawDelta(distance.toFloat()) }
    }

    override fun evaluate(script: String) = Unit
}
