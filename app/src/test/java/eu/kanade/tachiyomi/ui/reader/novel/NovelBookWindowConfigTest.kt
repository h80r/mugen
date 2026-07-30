package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The resident window is sized in characters, not in sections: slicing a book into smaller blocks
 * must not shrink the amount of text that stays rendered around the reader.
 */
class NovelBookWindowConfigTest {

    @Test
    fun `smaller blocks get a wider window`() {
        val wide = NovelBookWindowConfig.forBlockChars(40_000)
        val narrow = NovelBookWindowConfig.forBlockChars(200_000)

        wide.residentRadius shouldBe 3
        narrow.residentRadius shouldBe NovelBookWindowConfig.DEFAULT_RESIDENT_RADIUS
        wide.residentRadius shouldBeGreaterThanOrEqual narrow.residentRadius
    }

    @Test
    fun `the resident window keeps roughly the same amount of text for any block size`() {
        listOf(20_000, 40_000, 100_000).forEach { blockChars ->
            val config = NovelBookWindowConfig.forBlockChars(blockChars)
            val residentChars = (config.residentRadius * 2 + 1) * blockChars
            residentChars shouldBeGreaterThanOrEqual NovelBookWindowConfig.TARGET_RESIDENT_CHARS / 2
        }
    }

    @Test
    fun `prefetching stays at least as wide as the resident window`() {
        val config = NovelBookWindowConfig.forBlockChars(NovelBookBlockPlanner.DEFAULT_TARGET_CHARS)

        config.prefetchAhead shouldBeGreaterThanOrEqual config.residentRadius
        config.prefetchBehind shouldBeGreaterThanOrEqual config.residentRadius
    }

    @Test
    fun `an unknown block size falls back to the default window`() {
        NovelBookWindowConfig.forBlockChars(0) shouldBe NovelBookWindowConfig.DEFAULT
    }
}
