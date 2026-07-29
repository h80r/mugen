package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class NovelBookSectionDomTest {

    @Test
    fun `section html carries index and chapter id`() {
        val html = buildBookSectionHtml(
            sectionIndex = 3,
            chapterId = 42L,
            title = "Chapter 4",
            bodyHtml = "<p>Body</p>",
        )

        html shouldContain "id=\"__an_book_section_3\""
        html shouldContain "data-an-section=\"3\""
        html shouldContain "data-an-chapter=\"42\""
        html shouldContain "<p>Body</p>"
    }

    @Test
    fun `first section has no divider but later sections do`() {
        val first = buildBookSectionHtml(0, 1L, "One", "<p>1</p>")
        val second = buildBookSectionHtml(1, 2L, "Two", "<p>2</p>")

        first shouldNotContain BOOK_SECTION_DIVIDER_CLASS
        second shouldContain BOOK_SECTION_DIVIDER_CLASS
    }

    @Test
    fun `heading can be hidden and titles are escaped`() {
        val hidden = buildBookSectionHtml(1, 1L, "Title", "<p>1</p>", showHeading = false)
        val escaped = buildBookSectionHtml(1, 1L, "A <b>&</b>", "<p>1</p>")

        hidden shouldNotContain BOOK_SECTION_TITLE_CLASS
        escaped shouldContain "A &lt;b&gt;&amp;/b&gt;".replace("&amp;/b", "&amp;&lt;/b")
    }

    @Test
    fun `js quoting neutralizes markup and control characters`() {
        quoteBookJsString("</script>") shouldBe "\"\\u003C/script\\u003E\""
        quoteBookJsString("a\"b\\c") shouldBe "\"a\\\"b\\\\c\""
        quoteBookJsString("line\nbreak") shouldBe "\"line\\nbreak\""
        quoteBookJsString("tab\u0001") shouldBe "\"tab\\u0001\""
    }

    @Test
    fun `append script embeds the quoted section and its index`() {
        val script = buildAppendBookSectionJavascript(
            sectionIndex = 2,
            sectionHtml = buildBookSectionHtml(2, 7L, "Two", "<p>Body</p>"),
        )

        script shouldContain "const sectionIndex = 2;"
        script shouldContain "const keepAnchored = true;"
        script shouldContain "__an_book_section_2"
        script shouldNotContain "<p>Body</p>"
        script shouldContain "\\u003Cp\\u003EBody"
    }

    @Test
    fun `append script can skip scroll anchoring`() {
        val script = buildAppendBookSectionJavascript(
            sectionIndex = 0,
            sectionHtml = "<section></section>",
            keepScrollAnchored = false,
        )

        script shouldContain "const keepAnchored = false;"
    }

    @Test
    fun `prune script keeps the measured height as a placeholder`() {
        val script = buildPruneBookSectionJavascript(5)

        script shouldContain "__an_book_section_5"
        script shouldContain BOOK_SECTION_PLACEHOLDER_CLASS
        script shouldContain "data-an-placeholder"
        script shouldContain "placeholder.style.setProperty('height', height + 'px', 'important');"
    }

    @Test
    fun `metrics script reports sections and scroll position`() {
        val script = buildBookSectionMetricsJavascript()

        script shouldContain "section.$BOOK_SECTION_CLASS"
        script shouldContain "scrollTop"
        script shouldContain "contentHeight"
        script shouldContain "JSON.stringify"
    }

    @Test
    fun `scroll script clamps the section fraction`() {
        buildScrollToBookSectionJavascript(4, 2f) shouldContain "rect.height * 1.0"
        buildScrollToBookSectionJavascript(4, -1f) shouldContain "rect.height * 0.0"
        buildScrollToBookSectionJavascript(4, 0.25f) shouldContain "rect.height * 0.25"
    }

    @Test
    fun `scroll script pages horizontally in the paginated flow`() {
        val script = buildScrollToBookSectionJavascript(4, 0.5f)

        // The paginated flow scrolls the body horizontally, so the same command has to write
        // scrollLeft on the paginated scroller instead of scrollTop on the document element.
        script shouldContain BOOK_PAGINATED_CLASS
        script shouldContain "rect.width * 0.5"
        script shouldContain "scroller.scrollLeft ="
    }

    @Test
    fun `book css styles dividers headings and placeholders`() {
        val css = buildBookSectionsCss()

        css shouldContain "div.$BOOK_SECTION_DIVIDER_CLASS"
        css shouldContain "h2.$BOOK_SECTION_TITLE_CLASS"
        css shouldContain "section.$BOOK_SECTION_PLACEHOLDER_CLASS"
    }
}
