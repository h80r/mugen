package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReaderQuoteBlockMatchTest {

    @Test
    fun `exact substring match returns the containing block`() {
        val blocks = listOf(
            0 to "The first paragraph of the chapter.",
            1 to "A second paragraph with the quoted sentence in it.",
            2 to "A closing paragraph.",
        )

        findQuoteTextBlockMatch(blocks, "the quoted sentence") shouldBe NovelReaderQuoteBlockMatch(1)
    }

    @Test
    fun `whitespace-normalized match still finds the block`() {
        val blocks = listOf(
            0 to "A   paragraph  with\nirregular   whitespace in the source text.",
        )

        findQuoteTextBlockMatch(blocks, "with irregular whitespace") shouldBe NovelReaderQuoteBlockMatch(0)
    }

    @Test
    fun `no match returns null`() {
        val blocks = listOf(
            0 to "The first paragraph.",
            1 to "The second paragraph.",
        )

        findQuoteTextBlockMatch(blocks, "text that was never in the chapter") shouldBe null
    }

    @Test
    fun `blank quote text returns null without searching`() {
        val blocks = listOf(0 to "Any paragraph text.")

        findQuoteTextBlockMatch(blocks, "   ") shouldBe null
    }

    @Test
    fun `uses the caller-supplied block index, not list position`() {
        val blocks = listOf(
            5 to "Skipped image block has no text here.",
            9 to "The paragraph that actually contains the quote.",
        )

        findQuoteTextBlockMatch(blocks, "actually contains the quote") shouldBe NovelReaderQuoteBlockMatch(9)
    }
}
