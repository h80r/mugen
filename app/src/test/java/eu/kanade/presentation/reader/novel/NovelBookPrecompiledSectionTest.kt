package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookUiCommand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookPrecompiledSectionTest {

    @Test
    fun `precompiled blocks are preferred over parsing the section html`() {
        var parseCount = 0
        val sections = applyNovelBookCommandsToNativeSections(
            sections = emptyList(),
            commands = listOf(NovelBookUiCommand.Append(id = 1L, sectionIndex = 0, html = "<p>html</p>")),
            parseSection = { html ->
                parseCount += 1
                listOf(html)
            },
            precompiledSection = { listOf("precompiled") },
        )

        parseCount shouldBe 0
        sections.single().blocks shouldBe listOf("precompiled")
    }

    @Test
    fun `books without a native stream still fall back to parsing`() {
        var parseCount = 0
        val sections = applyNovelBookCommandsToNativeSections(
            sections = emptyList(),
            commands = listOf(NovelBookUiCommand.Append(id = 1L, sectionIndex = 0, html = "<p>html</p>")),
            parseSection = { html ->
                parseCount += 1
                listOf(html)
            },
            precompiledSection = { null },
        )

        parseCount shouldBe 1
        sections.single().blocks shouldBe listOf("<p>html</p>")
    }

    @Test
    fun `an empty precompiled result never blanks a section`() {
        val sections = applyNovelBookCommandsToNativeSections(
            sections = emptyList(),
            commands = listOf(NovelBookUiCommand.Append(id = 1L, sectionIndex = 0, html = "<p>html</p>")),
            parseSection = { html -> listOf(html) },
            precompiledSection = { emptyList() },
        )

        sections.single().blocks shouldBe listOf("<p>html</p>")
    }
}
