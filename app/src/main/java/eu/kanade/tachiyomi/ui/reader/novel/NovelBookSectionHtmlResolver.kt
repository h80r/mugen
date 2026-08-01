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
 *
 * Sections are addressed by their spine index; the chapter id is only used to fetch and translate
 * the payload, because a section is not the same thing as a chapter.
 */
internal class NovelBookSectionHtmlResolver(
    private val currentSpine: () -> NovelBookSpine,
    private val loadRawSection: suspend (Long) -> NovelBookRawSection,
    // (chapterId, rawHtml, chapterName) -> reader ready HTML. The chapter id is part of the
    // contract because a section may have to be rendered translated, and translations are keyed by
    // chapter.
    private val normalizeHtml: suspend (Long, String, String) -> String,
    private val showChapterHeadings: () -> Boolean = { true },
) {

    private val resolvedBaseUrls = ConcurrentHashMap<Long, String>()

    fun resolvedBaseUrl(sectionKey: Long): String? = resolvedBaseUrls[sectionKey]

    /**
     * Resolves the section HTML for the section with spine index [sectionKey]. Returns a blank
     * string when the section has no usable content, which the loader reports as a failed section
     * so it can be retried inline.
     */
    suspend fun resolve(sectionKey: Long): String {
        val section = currentSpine().sectionAt(sectionKey.toInt()) ?: return ""
        val raw = loadRawSection(section.chapterId)
        raw.chapterWebUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { resolvedBaseUrls[sectionKey] = it }
            ?: resolvedBaseUrls.remove(sectionKey)
        val bodyHtml = normalizeHtml(
            raw.chapterId,
            raw.rawHtml,
            raw.chapterName.ifBlank { section.name },
        )
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
