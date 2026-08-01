package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookPrefetchPlannerTest {

    @Test
    fun `empty spine produces an empty plan`() {
        val plan = NovelBookPrefetchPlanner.plan(NovelBookSpine.EMPTY, currentSectionIndex = 0)

        plan shouldBe NovelBookWindowPlan.EMPTY
        plan.isIdle shouldBe true
    }

    @Test
    fun `plan keeps neighbours resident and orders queue current then ahead then behind`() {
        val plan = NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 5)

        plan.residentSections shouldBe listOf(4, 5, 6)
        plan.prefetchQueue shouldBe listOf(5, 4, 6, 7, 8, 9, 3)
        plan.pruneSections shouldBe emptyList<Int>()
    }

    @Test
    fun `plan skips already loaded and in flight sections`() {
        val plan = NovelBookPrefetchPlanner.plan(
            spine = spine(10),
            currentSectionIndex = 5,
            loadedSections = setOf(4, 5),
            inFlightSections = setOf(6),
        )

        plan.residentSections shouldBe listOf(4, 5, 6)
        plan.prefetchQueue shouldBe listOf(7, 8, 9, 3)
    }

    @Test
    fun `plan prunes loaded sections outside the cache window farthest first`() {
        val plan = NovelBookPrefetchPlanner.plan(
            spine = spine(10),
            currentSectionIndex = 5,
            loadedSections = setOf(0, 1, 9),
        )

        plan.pruneSections shouldBe listOf(0, 1)
        plan.prefetchQueue shouldBe listOf(5, 4, 6, 7, 8, 3)
    }

    @Test
    fun `plan clamps the window at the start of the book`() {
        val plan = NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 0)

        plan.residentSections shouldBe listOf(0, 1)
        plan.prefetchQueue shouldBe listOf(0, 1, 2, 3, 4)
    }

    @Test
    fun `plan clamps the window at the end of the book`() {
        val plan = NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 9)

        plan.residentSections shouldBe listOf(8, 9)
        plan.prefetchQueue shouldBe listOf(9, 8, 7)
    }

    @Test
    fun `plan clamps an out of range current section`() {
        val plan = NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 99)

        plan shouldBe NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 9)
    }

    @Test
    fun `resolveConfig leaves the window untouched when idle`() {
        NovelBookPrefetchPlanner.resolveConfig() shouldBe NovelBookWindowConfig.DEFAULT
    }

    @Test
    fun `resolveConfig deepens look ahead for fast forward scrolling`() {
        val config = NovelBookPrefetchPlanner.resolveConfig(
            direction = NovelBookScrollDirection.Forward,
            isFastScrolling = true,
        )

        config.prefetchAhead shouldBe 5
        config.prefetchBehind shouldBe 1
        config.maxConcurrentPrefetch shouldBe 2
    }

    @Test
    fun `resolveConfig favours sections behind when scrolling backward`() {
        val config = NovelBookPrefetchPlanner.resolveConfig(
            direction = NovelBookScrollDirection.Backward,
        )

        config.prefetchAhead shouldBe 2
        config.prefetchBehind shouldBe 2
    }

    @Test
    fun `resolveConfig shrinks the window on a constrained network`() {
        val config = NovelBookPrefetchPlanner.resolveConfig(
            direction = NovelBookScrollDirection.Forward,
            isFastScrolling = true,
            isConstrainedNetwork = true,
        )

        config.prefetchAhead shouldBe 1
        config.prefetchBehind shouldBe 0
        config.maxConcurrentPrefetch shouldBe 1
    }

    @Test
    fun `nextPrefetchBatch respects the free concurrency slots`() {
        val plan = NovelBookPrefetchPlanner.plan(spine(10), currentSectionIndex = 5)

        NovelBookPrefetchPlanner.nextPrefetchBatch(plan, inFlightCount = 0) shouldBe listOf(5, 4)
        NovelBookPrefetchPlanner.nextPrefetchBatch(plan, inFlightCount = 1) shouldBe listOf(5)
        NovelBookPrefetchPlanner.nextPrefetchBatch(plan, inFlightCount = 2) shouldBe emptyList<Int>()
    }

    private fun spine(sectionCount: Int): NovelBookSpine {
        return testSpineOf((0 until sectionCount).map(::chapter))
    }

    private fun chapter(index: Int): NovelChapter {
        return NovelChapter.create().copy(id = index + 1L, name = "Chapter ${index + 1}")
    }
}
