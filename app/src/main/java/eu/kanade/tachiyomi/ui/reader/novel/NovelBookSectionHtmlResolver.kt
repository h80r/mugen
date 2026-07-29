package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.presentation.reader.novel.buildBookSectionHtml
import java.util.concurrent.ConcurrentHashMap

/** One chapter's raw payload, before it gets normalized into reader-ready HTML. */
internal data class NovelBookRawSection(
    val chapterId: Long,
    val chapterName: String,
    val rawHtml: String,
    val chapterWebUrl: String? = null,
)

/**
 * Turns a chapter into the section markup that book mode appends to the reader document.
 *
 * All dependencies are passed as lambdas so the screen model can reuse its existing chapter
 * resolution path (snapshot loading, download/cache lookup, sanitizing) while this stays testable
 * without Android or a source.
 */
internal class NovelBookSectionHtmlResolver(
    private val currentSpine: () -> NovelBookSpine,
    private val loadRawSection: suspend (Long) -> NovelBookRawSection,
    private val normalizeHtml: suspend (String, String) -> String,
    private val showChapterHeadings: () -> Boolean = { true },
) {

    private val resolvedBaseUrls = ConcurrentHashMap<Long, String>()

    fun resolvedBaseUrl(chapterId: Long): String? = resolvedBaseUrls[chapterId]

    /**
     * Resolves the section HTML for [chapterId]. Returns a blank string when the chapter has no
     * usable content, which the loader reports as a failed section so it can be retried inline.
     */
    suspend fun resolve(chapterId: Long): String {
        val section = currentSpine().sectionOf(chapterId) ?: return ""
        val raw = loadRawSection(chapterId)
        raw.chapterWebUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { resolvedBaseUrls[chapterId] = it }
            ?: resolvedBaseUrls.remove(chapterId)
        val bodyHtml = normalizeHtml(raw.rawHtml, raw.chapterName.ifBlank { section.name })
        if (bodyHtml.isBlank()) return ""
        return buildBookSectionHtml(
            sectionIndex = section.index,
            chapterId = section.chapterId,
            title = section.name,
            bodyHtml = bodyHtml,
            showDivider = section.index > 0,
            showHeading = showChapterHeadings(),
        )
    }
}
