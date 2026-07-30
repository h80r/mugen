package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * In the paginated flow a section that spans many columns only reports the box of its first column
 * fragment. Positions inside it then collapsed onto the section start, which is why paging forward
 * never advanced (or persisted) the reading position.
 */
class NovelBookMetricsDerivedExtentsTest {

    private fun metrics(
        scrollTopPx: Int,
        contentHeightPx: Int,
        sections: List<NovelBookSectionMetrics>,
    ) = NovelBookDocumentMetrics(
        scrollTopPx = scrollTopPx,
        viewportHeightPx = 411,
        contentHeightPx = contentHeightPx,
        sections = sections,
    )

    @Test
    fun `a fragmented section is extended up to the next section start`() {
        val derived = metrics(
            scrollTopPx = 0,
            contentHeightPx = 30_000,
            sections = listOf(
                NovelBookSectionMetrics(index = 0, chapterId = -1L, topPx = 0, heightPx = 389, isPruned = false),
                NovelBookSectionMetrics(index = 1, chapterId = -2L, topPx = 17_662, heightPx = 389, isPruned = false),
            ),
        ).withDerivedSectionExtents()

        derived.sections.map { it.heightPx } shouldBe listOf(17_662, 12_338)
    }

    @Test
    fun `the position inside a paged section is no longer pinned to its start`() {
        val derived = metrics(
            scrollTopPx = 8_831,
            contentHeightPx = 17_662,
            sections = listOf(
                NovelBookSectionMetrics(index = 0, chapterId = -1L, topPx = 0, heightPx = 389, isPruned = false),
            ),
        ).withDerivedSectionExtents()

        val current = derived.currentSection()!!
        current.index shouldBe 0
        derived.fractionInside(current) shouldBe 0.5f
    }

    @Test
    fun `scrolled flow measurements are left untouched`() {
        val sections = listOf(
            NovelBookSectionMetrics(index = 0, chapterId = -1L, topPx = 0, heightPx = 12_000, isPruned = false),
            NovelBookSectionMetrics(index = 1, chapterId = -2L, topPx = 12_000, heightPx = 22_002, isPruned = false),
        )

        metrics(scrollTopPx = 0, contentHeightPx = 34_002, sections = sections)
            .withDerivedSectionExtents()
            .sections shouldBe sections
    }

    @Test
    fun `book blocks with synthetic ids are reported as measured sections`() {
        val measured = metrics(
            scrollTopPx = 0,
            contentHeightPx = 34_002,
            sections = listOf(
                NovelBookSectionMetrics(index = 0, chapterId = -1L, topPx = 0, heightPx = 12_000, isPruned = false),
                NovelBookSectionMetrics(index = 1, chapterId = -2L, topPx = 12_000, heightPx = 900, isPruned = true),
            ),
        ).measuredSections()

        measured.map { it.index } shouldBe listOf(0)
    }
}
