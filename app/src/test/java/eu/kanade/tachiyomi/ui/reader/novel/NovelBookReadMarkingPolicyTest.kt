package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookReadMarkingPolicyTest {

    private fun spine(sectionCount: Int, charCount: Int = 1_000): NovelBookSpine {
        val chapters = (0 until sectionCount).map { index ->
            NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
        }
        return NovelBookSpine.fromChapters(
            chapters = chapters,
            measuredCharCounts = chapters.associate { it.id to charCount },
        )
    }

    @Test
    fun `no chapters are marked read for an empty spine`() {
        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = NovelBookSpine.EMPTY,
            location = NovelBookLocation.START,
        ) shouldBe emptyList()
    }

    @Test
    fun `passed sections are marked read and the current one only past the threshold`() {
        val spine = spine(4)

        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 2, charOffset = 500),
        ) shouldBe listOf(1L, 2L)

        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 2, charOffset = 950),
        ) shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `already read chapters are skipped`() {
        val spine = spine(4)

        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 3, charOffset = 0),
            alreadyReadChapterIds = setOf(1L, 2L),
        ) shouldBe listOf(3L)
    }

    @Test
    fun `the first section is not marked read at the very start`() {
        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = spine(3),
            location = NovelBookLocation.START,
        ) shouldBe emptyList()
    }

    @Test
    fun `a location survives an encode and decode round trip`() {
        val spine = spine(5)
        val location = NovelBookLocation(sectionIndex = 3, charOffset = 400)

        val encoded = NovelBookReadMarkingPolicy.encodeLocation(spine, location)
        val decoded = NovelBookReadMarkingPolicy.decodeLocation(spine, encoded)

        decoded?.sectionIndex shouldBe 3
        (decoded?.charOffset ?: -1) shouldBe 400
    }

    @Test
    fun `decoding a non book progress value returns null`() {
        NovelBookReadMarkingPolicy.decodeLocation(spine(3), 42L) shouldBe null
    }

    @Test
    fun `a stored book location wins over the legacy chapter fallback`() {
        val spine = spine(5)
        val stored = NovelBookReadMarkingPolicy.encodeLocation(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 4, charOffset = 100),
        )

        val resumed = NovelBookReadMarkingPolicy.resolveResumeLocation(
            spine = spine,
            progressValue = stored,
            fallbackChapterId = 1L,
        )

        resumed.sectionIndex shouldBe 4
    }

    @Test
    fun `a legacy chapter position resumes as a book location`() {
        val spine = spine(5)

        val resumed = NovelBookReadMarkingPolicy.resolveResumeLocation(
            spine = spine,
            progressValue = 0L,
            fallbackChapterId = 3L,
            fallbackChapterFraction = 0.5f,
        )

        resumed.sectionIndex shouldBe 2
        resumed.charOffset shouldBe 500
    }

    @Test
    fun `an unknown fallback chapter resumes at the start of the book`() {
        NovelBookReadMarkingPolicy.resolveResumeLocation(
            spine = spine(3),
            progressValue = 0L,
            fallbackChapterId = 99L,
        ) shouldBe NovelBookLocation.START
    }
}
