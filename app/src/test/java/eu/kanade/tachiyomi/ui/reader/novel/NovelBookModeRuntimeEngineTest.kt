package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookModeRuntimeEngineTest {

    @Test
    fun `dedicated engine loads one prepared spine section as an isolated document`() = runTest {
        val runtime = NovelBookModeRuntime(
            loadRawSection = { chapterId ->
                NovelBookRawSection(
                    chapterId = chapterId,
                    chapterName = "Chapter $chapterId",
                    rawHtml = " <p>chapter $chapterId body</p> ",
                    chapterWebUrl = "https://example.org/chapter/$chapterId/",
                )
            },
            normalizeHtml = { html, _ -> html.trim() },
        )
        runtime.start(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
        )
        val section = runtime.engineSpine.sectionAt(1)!!

        val document = runtime.loadEngineDocument(section)

        document.sectionIndex shouldBe 1
        document.chapterId shouldBe 2L
        document.html shouldContain "chapter 2 body"
        document.baseUrl shouldBe "https://example.org/chapter/2/"
        runtime.location shouldBe NovelBookLocation.START
    }

    @Test
    fun `renderer location moves the runtime in the exact character offset domain`() = runTest {
        val runtime = NovelBookModeRuntime(
            loadRawSection = { chapterId ->
                NovelBookRawSection(
                    chapterId = chapterId,
                    chapterName = "Chapter $chapterId",
                    rawHtml = "<p>chapter $chapterId body</p>",
                )
            },
            normalizeHtml = { html, _ -> html },
        )
        runtime.start(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 400, 2L to 500),
        )

        runtime.moveTo(NovelBookLocation(sectionIndex = 1, charOffset = 137))

        runtime.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 137)
    }

    @Test
    fun `dedicated engine prefetch prepares adjacent sections without moving the location`() = runTest {
        val loadedChapterIds = mutableSetOf<Long>()
        val runtime = NovelBookModeRuntime(
            loadRawSection = { chapterId ->
                loadedChapterIds += chapterId
                NovelBookRawSection(
                    chapterId = chapterId,
                    chapterName = "Chapter $chapterId",
                    // Preparing a section now measures its real text length, so a section body has to
                    // be longer than the offset under test: a 14-character chapter would legitimately
                    // clamp the reading position to the end of its own text.
                    rawHtml = "<p>" + "chapter $chapterId body ".repeat(20) + "</p>",
                )
            },
            normalizeHtml = { html, _ -> html },
            prepareAhead = { 2 },
        )
        runtime.start(
            chapters = (1L..5L).map { chapterId ->
                NovelChapter.create().copy(id = chapterId, name = "Chapter $chapterId")
            },
            measuredCharCounts = (1L..5L).associateWith { 100 },
        )
        runtime.moveTo(NovelBookLocation(sectionIndex = 2, charOffset = 40))
        runtime.loadEngineDocument(runtime.engineSpine.sectionAt(2)!!)

        runtime.prefetchAround(sectionIndex = 2)

        loadedChapterIds shouldBe setOf(1L, 2L, 3L, 4L, 5L)
        runtime.location shouldBe NovelBookLocation(sectionIndex = 2, charOffset = 40)
    }
}
