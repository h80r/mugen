package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterEntry
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterNormalizer
import eu.kanade.tachiyomi.data.book.novel.NovelBookIndex
import eu.kanade.tachiyomi.data.book.novel.NovelBookMeta
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Builds a real two-chapter book artifact on disk (same on-disk shape [NovelBookBuilder] writes)
 * to test [NovelBookArtifactSource.locatorOfQuoteInChapter] against actual normalized chapter
 * HTML, rather than a hand-rolled fixture that could drift from what the normalizer really emits.
 */
class NovelBookArtifactSourceQuoteSeekTest {

    @TempDir
    lateinit var tempDir: File

    private fun buildArtifact(
        chapter1Html: String,
        chapter2Html: String,
    ): NovelBookArtifactSource {
        val section1 = NovelBookChapterNormalizer.normalize(
            rawHtml = chapter1Html,
            chapterId = 1L,
            chapterName = "Chapter One",
            startOffset = 0,
        )
        val section2 = NovelBookChapterNormalizer.normalize(
            rawHtml = chapter2Html,
            chapterId = 2L,
            chapterName = "Chapter Two",
            startOffset = section1.charCount,
        )
        val bodyBytes1 = section1.html.toByteArray(StandardCharsets.UTF_8)
        val bodyBytes2 = section2.html.toByteArray(StandardCharsets.UTF_8)

        val bodyFile = NovelBookArtifact.bodyFile(tempDir)
        bodyFile.parentFile?.mkdirs()
        bodyFile.writeBytes(bodyBytes1 + bodyBytes2)

        val index = NovelBookIndex(
            chapters = listOf(
                NovelBookChapterEntry(
                    chapterId = 1L,
                    order = 0,
                    title = "Chapter One",
                    anchorId = "chapter-1",
                    charStart = 0,
                    charLength = section1.charCount,
                    byteStart = 0L,
                    byteLength = bodyBytes1.size,
                ),
                NovelBookChapterEntry(
                    chapterId = 2L,
                    order = 1,
                    title = "Chapter Two",
                    anchorId = "chapter-2",
                    charStart = section1.charCount,
                    charLength = section2.charCount,
                    byteStart = bodyBytes1.size.toLong(),
                    byteLength = bodyBytes2.size,
                ),
            ),
        )
        NovelBookArtifact.writeIndex(tempDir, index)
        NovelBookArtifact.writeMeta(
            tempDir,
            NovelBookMeta(
                totalChars = section1.charCount + section2.charCount,
                totalBytes = (bodyBytes1.size + bodyBytes2.size).toLong(),
                chapterCount = 2,
                complete = true,
            ),
        )

        val blocks = NovelBookBlockPlanner.plan(index)
        return NovelBookArtifactSource(
            directory = tempDir,
            index = index,
            meta = NovelBookArtifact.readMeta(tempDir)!!,
            blocks = blocks,
        )
    }

    @Test
    fun `finds the quote and returns a locator anchored to its chapter`() {
        val artifact = buildArtifact(
            chapter1Html = "<p>The first paragraph of chapter one.</p><p>A second paragraph here.</p>",
            chapter2Html = "<p>The opening line of chapter two.</p><p>This paragraph holds the memorable quote.</p>",
        )

        val locator = requireNotNull(artifact.locatorOfQuoteInChapter(2L, "the memorable quote"))

        locator.chapterId shouldBe 2L
    }

    @Test
    fun `data-o offsets are whole-book, not chapter-relative`() {
        // Load-bearing for locatorOfQuoteInChapter's correctness: it reads a matched block's
        // data-o attribute directly as the whole-book char offset. If a future normalizer change
        // ever made data-o chapter-relative instead, this test would catch the regression by the
        // resolved locator landing in the wrong chapter for a quote in chapter 2.
        val artifact = buildArtifact(
            chapter1Html = "<p>Chapter one has some text to offset chapter two's start.</p>",
            chapter2Html = "<p>Chapter two's quoted sentence for this offset check.</p>",
        )
        val chapter1 = artifact.index.chapters.first { it.chapterId == 1L }
        val chapter2 = artifact.index.chapters.first { it.chapterId == 2L }
        (chapter2.charStart > 0) shouldBe true
        (chapter2.charStart >= chapter1.charLength) shouldBe true

        val locator = requireNotNull(artifact.locatorOfQuoteInChapter(2L, "quoted sentence for this offset"))
        val globalOffset = artifact.globalCharOffsetOf(locator)

        (globalOffset >= chapter2.charStart) shouldBe true
    }

    @Test
    fun `the resolved locator's global offset lands inside the matched chapter`() {
        val artifact = buildArtifact(
            chapter1Html = "<p>Chapter one text that is not the quote.</p>",
            chapter2Html = "<p>Chapter two intro.</p><p>Here is the exact quoted sentence to find.</p>",
        )

        val locator = requireNotNull(artifact.locatorOfQuoteInChapter(2L, "the exact quoted sentence"))
        val globalOffset = artifact.globalCharOffsetOf(locator)
        val chapter2 = artifact.index.chapters.first { it.chapterId == 2L }

        (globalOffset >= chapter2.charStart) shouldBe true
        (globalOffset < chapter2.charStart + chapter2.charLength) shouldBe true
    }

    @Test
    fun `returns null when the quote text is not present in the chapter`() {
        val artifact = buildArtifact(
            chapter1Html = "<p>Chapter one.</p>",
            chapter2Html = "<p>Chapter two, unrelated to the search text.</p>",
        )

        artifact.locatorOfQuoteInChapter(2L, "text that was never in this chapter") shouldBe null
    }

    @Test
    fun `returns null for a chapter id outside the book`() {
        val artifact = buildArtifact(
            chapter1Html = "<p>Chapter one.</p>",
            chapter2Html = "<p>Chapter two.</p>",
        )

        artifact.locatorOfQuoteInChapter(99L, "chapter") shouldBe null
    }

    @Test
    fun `blank quote text returns null without matching every block`() {
        val artifact = buildArtifact(
            chapter1Html = "<p>Chapter one.</p>",
            chapter2Html = "<p>Chapter two.</p>",
        )

        artifact.locatorOfQuoteInChapter(1L, "   ") shouldBe null
    }
}
