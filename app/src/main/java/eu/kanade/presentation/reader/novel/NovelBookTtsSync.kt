package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAdapter
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsNavigationAnchor
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsSegment

/**
 * Follow-along script for the book document.
 *
 * The chapter reader syncs TTS through [buildWebReaderTtsSyncJavascript], which addresses the
 * chapter WebView. Book mode never mounts it: the novel is streamed as sections into the book
 * engine document, so the chapter script had nothing to scroll and no node to highlight. This is the
 * same idea, expressed against the book document: locate the spoken snippet inside the resident
 * sections, mark it and bring it into the viewport the engine owns.
 */
internal fun buildBookTtsSyncJavascript(
    snippet: String,
    sectionIndex: Int?,
): String {
    val escapedSnippet = snippet
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .take(BOOK_TTS_SNIPPET_MAX_CHARS)
    val sectionSelector = sectionIndex
        ?.let { "'#" + bookSectionElementId(it) + "'" }
        ?: "null"
    return listOf(
        "(function() {",
        "  var snippet = '$escapedSnippet';",
        "  var sectionSelector = $sectionSelector;",
        "  var root = (sectionSelector && document.querySelector(sectionSelector)) ||",
        "    document.getElementById('an-book-content') || document.body;",
        "  if (!root) { return 'no-root'; }",
        "  var previous = document.querySelectorAll('[data-an-tts-highlight]');",
        "  for (var i = 0; i < previous.length; i++) {",
        "    previous[i].removeAttribute('data-an-tts-highlight');",
        "  }",
        "  if (!snippet) { return 'cleared'; }",
        "  var needle = snippet.replace(/\\s+/g, ' ').trim().toLowerCase();",
        "  if (!needle) { return 'cleared'; }",
        "  var candidates = root.querySelectorAll('p, li, blockquote, h1, h2, h3, h4, h5, h6, div');",
        "  var target = null;",
        "  for (var j = 0; j < candidates.length; j++) {",
        "    var text = (candidates[j].textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();",
        "    if (!text) { continue; }",
        "    if (text.indexOf(needle) !== -1 || needle.indexOf(text) !== -1) {",
        "      target = candidates[j];",
        "      break;",
        "    }",
        "  }",
        "  if (!target) { return 'not-found'; }",
        "  target.setAttribute('data-an-tts-highlight', 'true');",
        "  var engine = window.__anBookEngine;",
        "  var viewport = document.getElementById('an-book-viewport');",
        "  if (viewport && (!engine || typeof engine.scrollBy !== 'function')) {",
        "    var offset = target.getBoundingClientRect().top - viewport.getBoundingClientRect().top;",
        "    viewport.scrollTop = Math.max(0, viewport.scrollTop + offset - (viewport.clientHeight / 3));",
        "    return 'scrolled';",
        "  }",
        "  if (viewport) {",
        "    var delta = target.getBoundingClientRect().top - viewport.getBoundingClientRect().top -",
        "      (viewport.clientHeight / 3);",
        "    if (Math.abs(delta) > 4) { engine.scrollBy(Math.round(delta)); }",
        "    return 'scrolled';",
        "  }",
        "  target.scrollIntoView({ block: 'center' });",
        "  return 'scrolled';",
        "})()",
    ).joinToString("\n")
}

private const val BOOK_TTS_SNIPPET_MAX_CHARS = 160

/**
 * [NovelTtsNavigationAdapter] for book mode.
 *
 * Book mode used to fall through to the native-scroll adapter, which drives the per-chapter lazy
 * list. That list is empty over a book, so "follow along with the voice" moved nothing at all.
 */
internal class BookTtsNavigationAdapter(
    private val surface: () -> NovelBookScrollSurface?,
    private val sectionIndexForSpeech: () -> Int?,
) : NovelTtsNavigationAdapter {

    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        val target = surface() ?: return
        target.evaluate(
            buildBookTtsSyncJavascript(
                snippet = segment.text,
                sectionIndex = sectionIndexForSpeech(),
            ),
        )
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        // The book has one continuous document, so a page or block index means nothing here; the
        // book session already persists the reading location on its own.
        return NovelTtsNavigationAnchor(scrollOffsetPx = scrollOffsetPx)
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        val target = surface() ?: return
        target.evaluate(buildBookTtsSyncJavascript(snippet = "", sectionIndex = null))
    }
}
