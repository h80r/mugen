package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookUiCommand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookNativeScrollTest {

    private fun parse(html: String): List<String> = listOf(html)

    @Test
    fun `appended sections are kept in spine order`() {
        val sections = applyNovelBookCommandsToNativeSections(
            sections = emptyList(),
            commands = listOf(
                NovelBookUiCommand.Append(id = 1L, sectionIndex = 2, html = "third"),
                NovelBookUiCommand.Append(id = 2L, sectionIndex = 0, html = "first"),
                NovelBookUiCommand.Append(id = 3L, sectionIndex = 1, html = "second"),
            ),
            parseSection = ::parse,
        )

        sections.map { it.sectionIndex } shouldBe listOf(0, 1, 2)
        sections.map { it.blocks.single() } shouldBe listOf("first", "second", "third")
    }

    @Test
    fun `a section is parsed once and pruning releases it`() {
        var parseCount = 0
        val parseCounting: (String) -> List<String> = { html ->
            parseCount += 1
            listOf(html)
        }

        val afterAppend = applyNovelBookCommandsToNativeSections(
            sections = emptyList(),
            commands = listOf(NovelBookUiCommand.Append(id = 1L, sectionIndex = 4, html = "body")),
            parseSection = parseCounting,
        )
        val afterReappend = applyNovelBookCommandsToNativeSections(
            sections = afterAppend,
            commands = listOf(NovelBookUiCommand.Append(id = 2L, sectionIndex = 4, html = "body")),
            parseSection = parseCounting,
        )
        parseCount shouldBe 1
        afterReappend.size shouldBe 1

        val afterPrune = applyNovelBookCommandsToNativeSections(
            sections = afterReappend,
            commands = listOf(NovelBookUiCommand.Prune(id = 3L, sectionIndex = 4)),
            parseSection = parseCounting,
        )
        afterPrune shouldBe emptyList()
    }

    @Test
    fun `scroll commands do not change the resident sections`() {
        val sections = listOf(NovelBookNativeSection(sectionIndex = 0, blocks = listOf("body")))

        applyNovelBookCommandsToNativeSections(
            sections = sections,
            commands = listOf(
                NovelBookUiCommand.ScrollTo(id = 1L, sectionIndex = 0, sectionFraction = 0.5f),
            ),
            parseSection = ::parse,
        ) shouldBe sections
    }

    @Test
    fun `only the newest scroll request is applied`() {
        latestNovelBookScrollCommand(
            commands = listOf(
                NovelBookUiCommand.ScrollTo(id = 1L, sectionIndex = 0, sectionFraction = 0.1f),
                NovelBookUiCommand.Append(id = 2L, sectionIndex = 1, html = "body"),
                NovelBookUiCommand.ScrollTo(id = 3L, sectionIndex = 7, sectionFraction = 0.4f),
            ),
        ) shouldBe NovelBookUiCommand.ScrollTo(id = 3L, sectionIndex = 7, sectionFraction = 0.4f)

        latestNovelBookScrollCommand(commands = emptyList()) shouldBe null
    }

    @Test
    fun `the reading position follows the section under the viewport top`() {
        val location = resolveNovelBookNativeRelocate(
            items = listOf(
                NovelBookNativeViewportItem(sectionIndex = 3, offsetPx = -600, heightPx = 800),
                NovelBookNativeViewportItem(sectionIndex = 4, offsetPx = 200, heightPx = 800),
            ),
        )

        location shouldBe NovelBookViewLocation(sectionIndex = 3, sectionFraction = 0.75f)
    }

    @Test
    fun `an unmeasured or empty list never reports a bogus position`() {
        resolveNovelBookNativeRelocate(items = emptyList()) shouldBe null

        resolveNovelBookNativeRelocate(
            items = listOf(NovelBookNativeViewportItem(sectionIndex = 2, offsetPx = 0, heightPx = 0)),
        ) shouldBe NovelBookViewLocation(sectionIndex = 2, sectionFraction = 0f)
    }
}
