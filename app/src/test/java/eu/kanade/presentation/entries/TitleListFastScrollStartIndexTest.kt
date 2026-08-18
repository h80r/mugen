package eu.kanade.presentation.entries

import androidx.compose.ui.unit.IntOffset
import eu.kanade.presentation.entries.anime.resolveAnimeAuroraFastScrollBlockStartIndex
import eu.kanade.presentation.entries.manga.resolveMangaAuroraFastScrollBlockStartIndex
import eu.kanade.presentation.entries.novel.resolveNovelAuroraFastScrollBlockStartIndex
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleListFastScrollStartIndexTest {

    @Test
    fun `novel aurora block start index matches phone and two pane layouts`() {
        resolveNovelAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = false,
            chapterPageEnabled = false,
            showScanlatorSelector = false,
        ) shouldBe 3

        resolveNovelAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = false,
            chapterPageEnabled = true,
            showScanlatorSelector = true,
        ) shouldBe 5

        resolveNovelAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = true,
            chapterPageEnabled = true,
            showScanlatorSelector = false,
        ) shouldBe 2
    }

    @Test
    fun `manga block start index aligns to first chapter card`() {
        resolveMangaAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = false,
            showScanlatorSelector = false,
        ) shouldBe 3

        resolveMangaAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = true,
            showScanlatorSelector = true,
        ) shouldBe 2
    }

    @Test
    fun `anime block start index aligns to first season or episode card`() {
        resolveAnimeAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = false,
        ) shouldBe 3

        resolveAnimeAuroraFastScrollBlockStartIndex(
            useTwoPaneLayout = true,
        ) shouldBe 1
    }

    @Test
    fun `fast scroll spec can align to the first visible list block offset`() {
        resolveTitleListFastScrollSpec(
            baseTopPaddingPx = 24,
            firstVisibleItemIndex = 2,
            blockStartIndex = 3,
            blockStartOffsetPx = IntOffset(x = 0, y = 288).y,
        ) shouldBe TitleListFastScrollSpec(
            thumbAllowed = true,
            topPaddingPx = 288,
        )
    }
}
