package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelBookEngineDocumentTest {

    @Test
    fun `paginated document owns a fixed viewport and columnizes only its section content`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 3,
                chapterId = 44L,
                html = "<p>Visible page text</p><img src=\"cover.jpg\">",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "Visible page text"
        html shouldContain "id=\"an-book-viewport\""
        html shouldContain "id=\"an-book-content\""
        html shouldContain "#an-book-viewport {"
        html shouldContain "overflow: hidden;"
        html shouldContain "#an-book-content {"
        html shouldContain "column-width: 100vw;"
        html shouldContain "column-fill: auto;"
        html shouldContain "#an-book-content img"
        html shouldContain "object-fit: contain;"
        html shouldContain "break-inside: avoid;"
        html shouldNotContain "body { column-width:"
    }

    @Test
    fun `paginated document exposes renderer owned page turns instead of browser panning`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Page one</p><p>Page two</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "touch-action: none;"
        html shouldContain "window.__anBookEngine = Object.freeze({"
        html shouldContain "next: function()"
        html shouldContain "kind: 'end'"
        html shouldContain "viewport.scrollLeft = targetPage * pageSize();"
        html shouldNotContain "document.body.scrollLeft"
    }

    @Test
    fun `book document stabilizes images before exposing renderer readiness`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Text before image</p><img src=\"novelimg://source/cover\">",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "const images = Array.from(content.querySelectorAll('img'));"
        html shouldContain "image.decode()"
        html shouldContain "Promise.all(images.map(waitForImage))"
        html shouldContain "requestAnimationFrame"
        html shouldContain "charCount: totalCharCount(textNodes())"
        html shouldContain "ready: ready"
    }

    @Test
    fun `book document exposes text offset relocation and restoration`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 2,
                chapterId = 3L,
                html = "<p>First paragraph</p><p>Second paragraph</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        html shouldContain "document.createTreeWalker"
        html shouldContain "const charOffsetAtViewportStart = function()"
        html shouldContain "const goToCharOffset = function(charOffset)"
        html shouldContain "charOffset: charOffsetAtViewportStart()"
        html shouldContain "goTo: goToCharOffset"
        html shouldContain "relocate: relocate"
    }

    @Test
    fun `scrolled document uses the same exact location api`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Long chapter text</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        html shouldContain "window.__anBookEngine = Object.freeze({"
        html shouldContain "next: function()"
        html shouldContain "previous: function()"
        html shouldContain "goTo: goToCharOffset"
        html shouldContain "relocate: relocate"
        html shouldContain "viewport.scrollTop"
    }

    @Test
    fun `book document applies the active reader appearance css`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 0,
                chapterId = 1L,
                html = "<p>Styled chapter text</p>",
            ),
            flow = NovelBookEngineFlow.PAGINATED,
            readerCss = """
                :root { --an-reader-fg: #e8e1d5; --an-reader-bg: #171411; }
                #an-book-content { padding: 18px 28px; font-size: 21px; line-height: 1.65; }
            """.trimIndent(),
        )

        html shouldContain "--an-reader-fg: #e8e1d5"
        html shouldContain "--an-reader-bg: #171411"
        html shouldContain "#an-book-content { padding: 18px 28px; font-size: 21px; line-height: 1.65; }"
    }

    @Test
    fun `document pushes readiness and coalesced relocation events to its native renderer`() {
        val html = buildNovelBookEngineDocumentHtml(
            document = NovelBookDocument(
                sectionIndex = 4,
                chapterId = 55L,
                html = "<p>Push renderer events</p>",
            ),
            flow = NovelBookEngineFlow.SCROLLED,
            documentGeneration = 27L,
        )

        html shouldContain "window.AnBookNative"
        html shouldContain "onReady(27"
        html shouldContain "viewport.addEventListener('scroll'"
        html shouldContain "requestAnimationFrame"
        html shouldContain "onRelocated(27"
    }
}
