package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookRenderCoordinatorTest {

    private fun spine(sectionCount: Int): NovelBookSpine {
        val chapters = (0 until sectionCount).map { index ->
            NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
        }
        return NovelBookSpine.fromChapters(chapters)
    }

    @Test
    fun `empty spine has nothing to do`() {
        val plan = NovelBookRenderCoordinator.resolve(NovelBookSpine.EMPTY, currentSectionIndex = 0)

        plan shouldBe NovelBookRenderPlan.EMPTY
        plan.isIdle shouldBe true
    }

    @Test
    fun `only prepared resident sections are rendered, closest first`() {
        val plan = NovelBookRenderCoordinator.resolve(
            spine = spine(10),
            currentSectionIndex = 5,
            renderedSections = emptySet(),
            preparedSections = setOf(4, 5, 7),
        )

        plan.render.map { it.sectionIndex } shouldBe listOf(5, 4)
        plan.render.map { it.chapterId } shouldBe listOf(6L, 5L)
    }

    @Test
    fun `already rendered sections are not rendered again`() {
        val plan = NovelBookRenderCoordinator.resolve(
            spine = spine(10),
            currentSectionIndex = 5,
            renderedSections = setOf(4, 5),
            preparedSections = setOf(4, 5, 6),
        )

        plan.render.map { it.sectionIndex } shouldBe listOf(6)
        plan.release shouldBe emptyList()
    }

    @Test
    fun `sections outside the resident window are released farthest first`() {
        val plan = NovelBookRenderCoordinator.resolve(
            spine = spine(10),
            currentSectionIndex = 5,
            renderedSections = setOf(0, 3, 4, 5),
            preparedSections = setOf(0, 3, 4, 5),
        )

        plan.release.map { it.sectionIndex } shouldBe listOf(0, 3)
    }

    @Test
    fun `prepare respects the concurrency budget and skips in-flight sections`() {
        val spine = spine(10)

        NovelBookRenderCoordinator.resolve(spine, currentSectionIndex = 5)
            .prepare.map { it.sectionIndex } shouldBe listOf(5, 4)

        NovelBookRenderCoordinator.resolve(
            spine = spine,
            currentSectionIndex = 5,
            preparedSections = setOf(4, 5),
            inFlightSections = setOf(6),
        ).prepare.map { it.sectionIndex } shouldBe listOf(7)

        NovelBookRenderCoordinator.resolve(
            spine = spine,
            currentSectionIndex = 5,
            inFlightSections = setOf(5, 4),
        ).prepare shouldBe emptyList()
    }

    @Test
    fun `commands render before releasing and preparing`() {
        val plan = NovelBookRenderCoordinator.resolve(
            spine = spine(10),
            currentSectionIndex = 5,
            renderedSections = setOf(0),
            preparedSections = setOf(0, 5),
        )

        plan.commands.first() shouldBe NovelBookRenderCommand.Render(sectionIndex = 5, chapterId = 6L)
        plan.commands.map { it::class.simpleName }.distinct() shouldBe
            listOf("Render", "Release", "Prepare")
        plan.isIdle shouldBe false
    }

    @Test
    fun `out of range positions are clamped to the spine`() {
        val spine = spine(4)

        NovelBookRenderCoordinator.resolve(spine, currentSectionIndex = 99)
            .prepare.map { it.sectionIndex } shouldBe listOf(3, 2)
        NovelBookRenderCoordinator.resolve(spine, currentSectionIndex = -7)
            .prepare.map { it.sectionIndex } shouldBe listOf(0, 1)
    }

    @Test
    fun `resident window follows the configured radius`() {
        val spine = spine(10)

        NovelBookRenderCoordinator.residentSections(spine, currentSectionIndex = 5) shouldBe listOf(4, 5, 6)
        NovelBookRenderCoordinator.residentSections(
            spine = spine,
            currentSectionIndex = 5,
            config = NovelBookWindowConfig(residentRadius = 2),
        ) shouldBe listOf(3, 4, 5, 6, 7)
    }
}
