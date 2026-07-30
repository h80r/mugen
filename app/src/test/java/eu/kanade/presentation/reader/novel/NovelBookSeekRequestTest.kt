package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookSeekRequestTest {

    @Test
    fun `an echo of the engine location never reopens the document`() {
        val engineLocation = NovelBookLocation(sectionIndex = 3, charOffset = 12_000)
        isExternalBookSeekRequest(
            requested = engineLocation,
            engineLocation = engineLocation,
            lastEmitted = engineLocation,
            millisSinceEmit = 0L,
        ) shouldBe false
    }

    @Test
    fun `a rounded offset reported right after the engine moved is still an echo`() {
        // The screen model re-derives the offset from a fraction of the measured section length,
        // so the value that travels back down is close but never equal.
        isExternalBookSeekRequest(
            requested = NovelBookLocation(sectionIndex = 3, charOffset = 12_140),
            engineLocation = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            lastEmitted = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            millisSinceEmit = 40L,
        ) shouldBe false
    }

    @Test
    fun `a far jump inside the same section long after the last report is a seek`() {
        isExternalBookSeekRequest(
            requested = NovelBookLocation(sectionIndex = 3, charOffset = 30_000),
            engineLocation = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            lastEmitted = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            millisSinceEmit = 9_000L,
        ) shouldBe true
    }

    @Test
    fun `another section is always a seek`() {
        isExternalBookSeekRequest(
            requested = NovelBookLocation(sectionIndex = 7, charOffset = 0),
            engineLocation = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            lastEmitted = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            millisSinceEmit = 10L,
        ) shouldBe true
    }

    @Test
    fun `a fresh reader with no reported location honours the restored position`() {
        isExternalBookSeekRequest(
            requested = NovelBookLocation(sectionIndex = 3, charOffset = 12_000),
            engineLocation = NovelBookLocation(sectionIndex = 0, charOffset = 0),
            lastEmitted = null,
            millisSinceEmit = Long.MAX_VALUE,
        ) shouldBe true
    }
}
