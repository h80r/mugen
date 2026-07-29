package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookSectionHtmlResolverTest {

    private val spine = NovelBookSpine.fromChapters(
        chapters = (0 until 3).map { index ->
            NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
        },
    )

    private fun resolver(
        rawHtml: (Long) -> String = { chapterId -> "<p>chapter $chapterId</p>" },
        showChapterHeadings: Boolean = true,
    ) = NovelBookSectionHtmlResolver(
        currentSpine = { spine },
        loadRawSection = { chapterId ->
            NovelBookRawSection(
                chapterId = chapterId,
                chapterName = "Chapter $chapterId",
                rawHtml = rawHtml(chapterId),
            )
        },
        normalizeHtml = { html, _ -> html.trim() },
        showChapterHeadings = { showChapterHeadings },
    )

    @Test
    fun `a section carries its index chapter id and body`() = runTest {
        val html = resolver().resolve(2L)

        html shouldContain "data-an-section=\"1\""
        html shouldContain "data-an-chapter=\"2\""
        html shouldContain "<p>chapter 2</p>"
        html shouldContain "Chapter 2"
    }

    @Test
    fun `the first section has no divider and later sections do`() = runTest {
        resolver().resolve(1L) shouldNotContain "an-book-divider"
        resolver().resolve(3L) shouldContain "an-book-divider"
    }

    @Test
    fun `chapter headings can be turned off`() = runTest {
        val html = resolver(showChapterHeadings = false).resolve(2L)

        html shouldNotContain "an-book-section-title"
        html shouldContain "<p>chapter 2</p>"
    }

    @Test
    fun `blank content resolves to nothing so the loader can fail the section`() = runTest {
        resolver(rawHtml = { "   " }).resolve(2L) shouldBe ""
    }

    @Test
    fun `a chapter outside the spine resolves to nothing`() = runTest {
        resolver().resolve(99L) shouldBe ""
    }
}
