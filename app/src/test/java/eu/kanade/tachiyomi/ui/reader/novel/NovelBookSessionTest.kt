package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookSessionTest {

    private val renderedHtml = mutableMapOf<Int, String>()
    private val releasedSections = mutableListOf<Int>()

    private fun spine(sectionCount: Int, charCount: Int = 1_000): NovelBookSpine {
        val chapters = (0 until sectionCount).map { index ->
            NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
        }
        return NovelBookSpine.fromChapters(
            chapters = chapters,
            measuredCharCounts = chapters.associate { it.id to charCount },
        )
    }

    private fun loader(): NovelBookSectionLoader {
        val disk = mutableMapOf<Long, String>()
        val store = NovelBookSectionStore(
            diskRead = { disk[it] },
            diskWrite = { chapterId, html -> disk[chapterId] = html },
        )
        return NovelBookSectionLoader(store = store) { chapterId -> "<p>chapter $chapterId</p>" }
    }

    private suspend fun NovelBookSession.syncOnce(): NovelBookRenderPlan = sync(
        renderSection = { section, html -> renderedHtml[section.index] = html },
        releaseSection = { section -> releasedSections += section.index },
        prepareSection = { section -> prepareSectionInline(section) },
    )

    @Test
    fun `an empty session has nothing to do`() = runTest {
        val session = NovelBookSession(loader())

        session.isEmpty shouldBe true
        session.syncOnce().isIdle shouldBe true
        renderedHtml shouldBe emptyMap()
    }

    @Test
    fun `repeated syncs prepare and render the window around the reading position`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(6), NovelBookLocation(sectionIndex = 2, charOffset = 0))

        // First round only prepares: nothing is on disk yet.
        session.syncOnce()
        renderedHtml.keys shouldBe emptySet()

        // Later rounds render whatever became available, closest section first.
        repeat(6) { session.syncOnce() }

        session.renderedSectionIndices.contains(2) shouldBe true
        renderedHtml[2] shouldBe "<p>chapter 3</p>"
    }

    @Test
    fun `scrolling forward prunes sections that left the resident window`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(8), NovelBookLocation(sectionIndex = 0, charOffset = 0))
        repeat(10) { session.syncOnce() }

        session.moveTo(NovelBookLocation(sectionIndex = 5, charOffset = 0))
        repeat(10) { session.syncOnce() }

        session.renderedSectionIndices.contains(0) shouldBe false
        releasedSections.contains(0) shouldBe true
        session.renderedSectionIndices.contains(5) shouldBe true
    }

    @Test
    fun `moving to a chapter reports success only for chapters in the spine`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(4))

        session.moveToChapter(chapterId = 3L, fraction = 0.5f) shouldBe true
        session.location.sectionIndex shouldBe 2
        session.location.charOffset shouldBe 500
        session.moveToChapter(chapterId = 99L) shouldBe false
        session.location.sectionIndex shouldBe 2
    }

    @Test
    fun `measuring a section keeps the position inside the book`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(3), NovelBookLocation(sectionIndex = 1, charOffset = 900))

        session.measureSection(chapterId = 2L, charCount = 100)

        // The section shrank below the stored offset, so the position is clamped to its last char.
        session.location.sectionIndex shouldBe 1
        session.location.charOffset shouldBe 99
    }

    @Test
    fun `crossed sections are reported as read`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(5), NovelBookLocation(sectionIndex = 3, charOffset = 0))

        session.chaptersToMarkRead() shouldBe listOf(1L, 2L, 3L)
        session.chaptersToMarkRead(alreadyReadChapterIds = setOf(1L)) shouldBe listOf(2L, 3L)
    }

    @Test
    fun `progress is encoded so it can be restored later`() = runTest {
        val session = NovelBookSession(loader())
        val spine = spine(5)
        session.reset(spine, NovelBookLocation(sectionIndex = 2, charOffset = 250))

        val restored = NovelBookReadMarkingPolicy.decodeLocation(spine, session.encodedProgress())

        restored?.sectionIndex shouldBe 2
        restored?.charOffset shouldBe 250
    }

    @Test
    fun `ui state reflects the reading position and rendered sections`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(4), NovelBookLocation(sectionIndex = 1, charOffset = 500))
        repeat(8) { session.syncOnce() }

        val uiState = session.uiState(showChapterHeadings = false)

        uiState.isEnabled shouldBe true
        uiState.isReady shouldBe true
        uiState.sectionCount shouldBe 4
        uiState.currentSectionIndex shouldBe 1
        uiState.currentSectionFraction shouldBe 0.5f
        uiState.bookProgressFraction shouldBe 0.375f
        uiState.showChapterHeadings shouldBe false
        uiState.failedSectionIndices shouldBe emptyList()
        uiState.renderedSectionIndices.contains(1) shouldBe true
    }

    @Test
    fun `rendering records real text lengths without rescaling the book`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(4), NovelBookLocation(sectionIndex = 1, charOffset = 500))
        val totalBefore = session.spine.totalCharCount
        val fractionsBefore = session.spine.sectionFractions

        repeat(8) { session.syncOnce() }

        // Section weights stay fixed for the whole session, so progress never jumps as sections render.
        session.spine.totalCharCount shouldBe totalBefore
        session.spine.sectionFractions shouldBe fractionsBefore
        session.uiState().bookProgressFraction shouldBe 0.375f

        // The real lengths are still observed, ready to seed the next session's spine.
        session.measuredTextLengths[2L] shouldBe novelBookSectionTextLength("<p>chapter 2</p>")
    }

    @Test
    fun `layout heights are recorded outside the progress domain`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(3), NovelBookLocation(sectionIndex = 1, charOffset = 500))
        val progressBefore = session.uiState().bookProgressFraction

        session.measureLayoutHeight(chapterId = 2L, heightPx = 12_345)

        session.spine.layoutHeightOf(1) shouldBe 12_345
        session.spine.sectionAt(1)?.charCount shouldBe 1_000
        session.uiState().bookProgressFraction shouldBe progressBefore
        session.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 500)
    }

    @Test
    fun `a reloaded document re-renders the resident window without losing the position`() = runTest {
        val session = NovelBookSession(loader())
        session.reset(spine(6), NovelBookLocation(sectionIndex = 3, charOffset = 250))
        repeat(10) { session.syncOnce() }
        val renderedBefore = session.renderedSectionIndices
        renderedBefore.contains(3) shouldBe true

        // The reader document was rebuilt underneath the session: everything it appended is gone.
        renderedHtml.clear()
        session.forgetRenderedSections()
        session.renderedSectionIndices shouldBe emptyList()

        // The next round puts the same window back, from the already prepared HTML, at the same spot.
        session.syncOnce()

        session.renderedSectionIndices shouldBe renderedBefore
        renderedHtml[3] shouldBe "<p>chapter 4</p>"
        session.location shouldBe NovelBookLocation(sectionIndex = 3, charOffset = 250)
    }
}
