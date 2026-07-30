package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookUiCommand
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Scroll commands must be applied exactly once. Re-applying them for every command batch made the
 * book snap back to the last requested position on every append or prune, i.e. it could not be
 * scrolled at all.
 */
class NovelBookNativeScrollTargetTest {

    private fun entries(vararg sectionIndices: Int): List<NovelBookNativeEntry> =
        sectionIndices.map { index ->
            NovelBookNativeEntry.Section(
                NovelBookNativeSection<NovelRichContentBlock>(sectionIndex = index, blocks = emptyList()),
            )
        }

    @Test
    fun `a scroll command resolves to its list item and position inside the section`() {
        val target = resolveNovelBookNativeScrollTarget(
            entries = entries(3, 4, 5),
            commands = listOf(
                NovelBookUiCommand.Append(id = 7L, sectionIndex = 5, html = "body"),
                NovelBookUiCommand.ScrollTo(id = 8L, sectionIndex = 4, sectionFraction = 0.5f),
            ),
            lastAppliedCommandId = 0L,
        )

        target?.commandId shouldBe 8L
        target?.itemIndex shouldBe 1
        target?.fraction shouldBe 0.5f
    }

    @Test
    fun `an already applied scroll command is never replayed`() {
        val commands = listOf(NovelBookUiCommand.ScrollTo(id = 8L, sectionIndex = 4, sectionFraction = 0.5f))

        resolveNovelBookNativeScrollTarget(
            entries = entries(3, 4, 5),
            commands = commands,
            lastAppliedCommandId = 8L,
        ) shouldBe null
    }

    @Test
    fun `window syncs without a scroll command leave the position alone`() {
        resolveNovelBookNativeScrollTarget(
            entries = entries(3, 4, 5),
            commands = listOf(
                NovelBookUiCommand.Append(id = 9L, sectionIndex = 6, html = "body"),
                NovelBookUiCommand.Prune(id = 10L, sectionIndex = 3),
            ),
            lastAppliedCommandId = 8L,
        ) shouldBe null
    }

    @Test
    fun `a scroll to a section that is not resident yet is skipped`() {
        resolveNovelBookNativeScrollTarget(
            entries = entries(3, 4),
            commands = listOf(NovelBookUiCommand.ScrollTo(id = 11L, sectionIndex = 40, sectionFraction = 0f)),
            lastAppliedCommandId = 0L,
        ) shouldBe null
    }
}
