package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookUiCommandQueueTest {

    @Test
    fun `commands stay pending until they are acknowledged`() {
        val queue = NovelBookUiCommandQueue()

        val appendId = queue.enqueueAppend(sectionIndex = 0, html = "<section></section>")
        val pruneId = queue.enqueuePrune(sectionIndex = 4)

        queue.pendingCount shouldBe 2
        queue.ack(listOf(appendId))
        queue.pendingCount shouldBe 1
        queue.commands.value.single().id shouldBe pruneId
        queue.ack(listOf(pruneId))
        queue.pendingCount shouldBe 0
    }

    @Test
    fun `commands are executed in the order they were queued`() {
        val queue = NovelBookUiCommandQueue()

        queue.enqueueAppend(sectionIndex = 1, html = "one")
        queue.enqueueAppend(sectionIndex = 2, html = "two")
        queue.enqueuePrune(sectionIndex = 9)

        queue.commands.value.map { it.sectionIndex } shouldBe listOf(1, 2, 9)
    }

    @Test
    fun `re-appending a section replaces the stale markup`() {
        val queue = NovelBookUiCommandQueue()

        queue.enqueueAppend(sectionIndex = 3, html = "old")
        queue.enqueueAppend(sectionIndex = 3, html = "new")

        val append = queue.commands.value.single() as NovelBookUiCommand.Append
        append.html shouldBe "new"
    }

    @Test
    fun `pruning a section drops its pending append`() {
        val queue = NovelBookUiCommandQueue()

        queue.enqueueAppend(sectionIndex = 5, html = "markup")
        queue.enqueuePrune(sectionIndex = 5)

        queue.commands.value.single().shouldBePrune(sectionIndex = 5)
    }

    @Test
    fun `only the newest scroll request survives and is clamped`() {
        val queue = NovelBookUiCommandQueue()

        queue.enqueueScrollTo(sectionIndex = 1, sectionFraction = 0.2f)
        queue.enqueueScrollTo(sectionIndex = 7, sectionFraction = 1.5f)

        val scroll = queue.commands.value.single() as NovelBookUiCommand.ScrollTo
        scroll.sectionIndex shouldBe 7
        scroll.sectionFraction shouldBe 1f
    }

    @Test
    fun `clear drops everything and acknowledging unknown ids is harmless`() {
        val queue = NovelBookUiCommandQueue()

        queue.enqueueAppend(sectionIndex = 0, html = "markup")
        queue.ack(emptyList())
        queue.ack(listOf(999L))
        queue.pendingCount shouldBe 1

        queue.clear()
        queue.pendingCount shouldBe 0
    }

    private fun NovelBookUiCommand.shouldBePrune(sectionIndex: Int) {
        (this is NovelBookUiCommand.Prune) shouldBe true
        this.sectionIndex shouldBe sectionIndex
    }
}
