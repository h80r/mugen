package eu.kanade.presentation.reader.novel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * How often the reader samples the book document layout while book mode is active. Fast enough to
 * keep the sliding window ahead of the reader, cheap enough to stay invisible during scrolling.
 */
internal const val BOOK_MODE_METRICS_POLL_INTERVAL_MS = 400L

/**
 * How long the reader waits for appended/pruned sections to lay out before asking the document for a
 * relocate event. Only used after DOM work; ordinary position updates are pushed by the document.
 */
internal const val BOOK_MODE_RELOCATE_SETTLE_DELAY_MS = 120L

/**
 * Document identity used while book mode is active. The reader keeps a single document alive for the
 * whole novel, so it must not be reloaded when the current chapter changes.
 */
internal const val BOOK_MODE_DOCUMENT_TAG = "an-book-document"

/** One book section as it is currently laid out in the reader document. */
internal data class NovelBookSectionMetrics(
    val index: Int,
    val chapterId: Long,
    val topPx: Int,
    val heightPx: Int,
    val isPruned: Boolean,
)

/**
 * Layout snapshot of the continuous book document, used to translate a raw WebView scroll position
 * into a book location (section + how far into that section the reader is).
 */
internal data class NovelBookDocumentMetrics(
    val scrollTopPx: Int,
    val viewportHeightPx: Int,
    val contentHeightPx: Int,
    val sections: List<NovelBookSectionMetrics>,
) {

    val isEmpty: Boolean get() = sections.isEmpty()

    /** The section the reader is currently looking at, or the closest one when between sections. */
    fun currentSection(): NovelBookSectionMetrics? {
        if (sections.isEmpty()) return null
        val anchor = scrollTopPx
        return sections.firstOrNull { anchor >= it.topPx && anchor < it.topPx + it.heightPx.coerceAtLeast(1) }
            ?: sections.minByOrNull { section ->
                if (anchor < section.topPx) {
                    section.topPx - anchor
                } else {
                    anchor - (section.topPx + section.heightPx)
                }
            }
    }

    /** How far into [section] the reader is, as a 0..1 fraction. */
    fun fractionInside(section: NovelBookSectionMetrics): Float {
        val height = section.heightPx
        if (height <= 0) return 0f
        return ((scrollTopPx - section.topPx).toFloat() / height.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Sections whose real rendered height is known.
     *
     * Heights are reported to the spine as layout heights only. They must never become section weights:
     * they change with font size, margins and theme, so using them as weights made whole-book progress
     * rescale on every re-measure.
     */
    fun measuredSections(): List<NovelBookSectionMetrics> =
        sections.filter { !it.isPruned && it.heightPx > 0 && it.chapterId > 0L }
}

@Serializable
private data class BookSectionMetricsPayload(
    val index: Int = -1,
    val chapterId: String = "",
    val top: Double = 0.0,
    val height: Double = 0.0,
    val pruned: Boolean = false,
)

@Serializable
private data class BookDocumentMetricsPayload(
    val scrollTop: Double = 0.0,
    val viewportHeight: Double = 0.0,
    val contentHeight: Double = 0.0,
    val sections: List<BookSectionMetricsPayload> = emptyList(),
)

private val bookMetricsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Parses the result of [buildBookSectionMetricsJavascript].
 *
 * `evaluateJavascript` hands back a JSON encoded value, so the payload arrives as a quoted JSON
 * string that has to be unwrapped first. Malformed or missing results are reported as `null` instead
 * of throwing, because a detached or reloading WebView must never crash the reader.
 */
internal fun parseNovelBookDocumentMetrics(rawResult: String?): NovelBookDocumentMetrics? {
    val raw = rawResult?.trim().orEmpty()
    if (raw.isEmpty() || raw == "null" || raw == "undefined") return null
    return runCatching {
        val element = bookMetricsJson.parseToJsonElement(raw)
        val payloadJson = if (element is JsonPrimitive && element.isString) element.content else raw
        val payload = bookMetricsJson.decodeFromString<BookDocumentMetricsPayload>(payloadJson)
        NovelBookDocumentMetrics(
            scrollTopPx = payload.scrollTop.toSafePx(),
            viewportHeightPx = payload.viewportHeight.toSafePx(),
            contentHeightPx = payload.contentHeight.toSafePx(),
            sections = payload.sections
                .filter { it.index >= 0 }
                .map { section ->
                    NovelBookSectionMetrics(
                        index = section.index,
                        chapterId = section.chapterId.toLongOrNull() ?: 0L,
                        topPx = section.top.toSafePx(),
                        heightPx = section.height.toSafePx(),
                        isPruned = section.pruned,
                    )
                }
                .sortedBy { it.index },
        )
    }.getOrNull()
}

private fun Double.toSafePx(): Int = when {
    isNaN() -> 0
    this <= 0.0 -> 0
    this >= Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE
    else -> toInt()
}
