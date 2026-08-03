package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.BookLocator
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekReason
import eu.kanade.tachiyomi.ui.reader.novel.BookSeekRequest
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookWindowState
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichTextSegment
import io.kotest.matchers.shouldBe
import org.junit.Test

class NovelBookNativeWindowTest {

    private fun sectionOf(index: Int, revision: Long = 0L) = NovelBookNativeSection(
        sectionIndex = index,
        blocks = listOf("section-$index"),
        revision = revision,
    )

    private fun windowOf(
        resident: List<Int>,
        revisions: Map<Int, Long> = emptyMap(),
    ) = NovelBookWindowState(
        sectionCount = 20,
        residentSections = resident,
        sectionRevisions = revisions,
    )

    @Test
    fun `sections that left the window are dropped`() {
        val mounted = listOf(sectionOf(2), sectionOf(3), sectionOf(4))

        val pruned = pruneNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5)))

        pruned.map { it.sectionIndex } shouldBe listOf(3, 4)
    }

    @Test
    fun `a window that holds everything is returned untouched`() {
        val mounted = listOf(sectionOf(3), sectionOf(4))

        val pruned = pruneNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5)))

        // Same instance: an unchanged window must not make the reader recompose its list.
        (pruned === mounted) shouldBe true
    }

    @Test
    fun `missing sections are pulled nearest to the reading position first`() {
        val mounted = listOf(sectionOf(4))

        val missing = missingNovelBookNativeSections(mounted, windowOf(listOf(2, 3, 4, 5, 6)))

        missing shouldBe listOf(3, 5, 2, 6)
    }

    @Test
    fun `a section whose revision moved on is pulled again in place`() {
        val mounted = listOf(sectionOf(3), sectionOf(4, revision = 1L), sectionOf(5))

        val missing = missingNovelBookNativeSections(
            sections = mounted,
            window = windowOf(resident = listOf(3, 4, 5), revisions = mapOf(4 to 2L)),
        )

        missing shouldBe listOf(4)
    }

    @Test
    fun `nothing is pulled while the renderer already holds the window`() {
        val mounted = listOf(sectionOf(3), sectionOf(4), sectionOf(5))

        missingNovelBookNativeSections(mounted, windowOf(listOf(3, 4, 5))) shouldBe emptyList()
    }

    @Test
    fun `a pulled section is inserted in spine order`() {
        val mounted = listOf(sectionOf(3), sectionOf(5))

        val next = withNovelBookNativeSection(mounted, sectionOf(4))

        next.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
    }

    @Test
    fun `re-pulling a section replaces it instead of duplicating it`() {
        val mounted = listOf(sectionOf(3), sectionOf(4), sectionOf(5))

        val next = withNovelBookNativeSection(mounted, sectionOf(4, revision = 2L))

        next.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
        next.first { it.sectionIndex == 4 }.revision shouldBe 2L
    }

    private fun entriesOf(vararg sectionIndices: Int): List<NovelBookNativeEntry> =
        sectionIndices.map { index ->
            NovelBookNativeEntry.Section(
                NovelBookNativeSection(
                    sectionIndex = index,
                    blocks = listOf(
                        NovelRichContentBlock.Paragraph(
                            segments = listOf(NovelRichTextSegment("section-$index")),
                        ),
                    ),
                ),
            )
        }

    private fun seekRequestOf(id: Long, sectionIndex: Int) = BookSeekRequest(
        id = id,
        locator = BookLocator(chapterId = sectionIndex.toLong(), blockIndex = 0, charOffset = 0),
        location = NovelBookLocation(sectionIndex = sectionIndex, charOffset = 0),
        reason = BookSeekReason.Resume,
    )

    @Test
    fun `a seek resolves to the list item holding that section`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 5),
            lastAppliedSeekId = 0L,
            sectionFraction = 0.25f,
        )

        target shouldBe NovelBookNativeSeekTarget(seekRequestId = 7L, itemIndex = 2, fraction = 0.25f)
    }

    @Test
    fun `a seek is applied at most once`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 5),
            lastAppliedSeekId = 7L,
            sectionFraction = 0.25f,
        )

        target shouldBe null
    }

    @Test
    fun `a seek waits until its section is mounted`() {
        val target = resolveNovelBookNativeSeekTarget(
            entries = entriesOf(3, 4, 5),
            request = seekRequestOf(id = 7L, sectionIndex = 9),
            lastAppliedSeekId = 0L,
            sectionFraction = 0f,
        )

        target shouldBe null
    }

    @Test
    fun `a failed section keeps its place in the book`() {
        val entries = buildNovelBookNativeEntries(
            sections = listOf(
                NovelBookNativeSection(sectionIndex = 3, blocks = emptyList()),
                NovelBookNativeSection(sectionIndex = 5, blocks = emptyList()),
            ),
            failedSectionIndices = listOf(4),
        )

        entries.map { it.sectionIndex } shouldBe listOf(3, 4, 5)
        (entries[1] is NovelBookNativeEntry.Failed) shouldBe true
    }
}
