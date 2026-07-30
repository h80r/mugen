package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterEntry
import eu.kanade.tachiyomi.data.book.novel.NovelBookIndex
import eu.kanade.tachiyomi.data.book.novel.NovelBookMeta
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Read marking over a compiled book.
 *
 * Marking everything before the caret meant every new session re-marked the whole book prefix, which
 * silently undid a chapter the user had unmarked on the novel screen.
 */
class NovelBookSessionReadMarkingTest {

    private fun chapter(id: Long, start: Int, length: Int) = NovelBookChapterEntry(
        chapterId = id,
        order = id.toInt(),
        title = "Chapter $id",
        anchorId = "ch$id",
        charStart = start,
        charLength = length,
        byteStart = start.toLong(),
        byteLength = length,
    )

    private fun source(): NovelBookArtifactSource {
        val chapters = listOf(
            chapter(id = 1L, start = 0, length = 1_000),
            chapter(id = 2L, start = 1_000, length = 1_000),
            chapter(id = 3L, start = 2_000, length = 1_000),
            chapter(id = 4L, start = 3_000, length = 1_000),
        )
        return NovelBookArtifactSource(
            directory = File("/does/not/exist"),
            index = NovelBookIndex(chapters = chapters),
            meta = NovelBookMeta(totalChars = 4_000, chapterCount = chapters.size),
            blocks = listOf(
                NovelBookBlock(
                    index = 0,
                    charStart = 0,
                    charEnd = 4_000,
                    byteStart = 0L,
                    byteLength = 4_000,
                    firstChapterId = 1L,
                    lastChapterId = 4L,
                ),
            ),
        )
    }

    @Test
    fun `marks chapters passed inside the session only`() {
        source().chaptersFullyReadBetween(fromCharOffset = 1_000, toCharOffset = 3_500) shouldBe
            listOf(2L, 3L)
    }

    @Test
    fun `does not re-mark chapters read before the session started`() {
        source().chaptersFullyReadBetween(fromCharOffset = 2_000, toCharOffset = 3_500) shouldBe
            listOf(3L)
    }

    @Test
    fun `marks nothing while still inside the first chapter of the session`() {
        source().chaptersFullyReadBetween(fromCharOffset = 1_000, toCharOffset = 1_500) shouldBe
            emptyList()
    }

    @Test
    fun `session anchor snaps back to the start of the chapter being resumed`() {
        source().chapterStartAt(1_500) shouldBe 1_000
    }

    @Test
    fun `resuming mid chapter still marks that chapter once it is passed`() {
        val artifact = source()
        val sessionStart = artifact.chapterStartAt(1_500)
        artifact.chaptersFullyReadBetween(fromCharOffset = sessionStart, toCharOffset = 2_500) shouldBe
            listOf(2L)
    }
}
