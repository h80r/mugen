package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * One piece of book-mode work the reader UI has to run against the live WebView document.
 *
 * The screen model never touches the WebView itself; it queues commands and the reader executes the
 * matching JavaScript, then acknowledges them by id.
 */
sealed interface NovelBookUiCommand {
    val id: Long
    val sectionIndex: Int

    /**
     * Seed the document with one placeholder per spine section.
     *
     * Book mode used to grow the document only from the sections it rendered, so resuming in the
     * middle of a novel left nothing above the resume point and scrolling back was impossible. The
     * skeleton makes the whole book addressable from the first frame.
     */
    data class Seed(
        override val id: Long,
        val sections: List<SeedSection>,
        override val sectionIndex: Int = -1,
    ) : NovelBookUiCommand

    /** Append a prepared section to the end (or start) of the document. */
    data class Append(
        override val id: Long,
        override val sectionIndex: Int,
        val html: String,
        val keepScrollAnchored: Boolean = true,
    ) : NovelBookUiCommand

    /** Replace a section with a fixed-height placeholder so the scroll position stays stable. */
    data class Prune(
        override val id: Long,
        override val sectionIndex: Int,
    ) : NovelBookUiCommand

    /** Jump to a position inside a section, e.g. when resuming or opening a chapter from the list. */
    data class ScrollTo(
        override val id: Long,
        override val sectionIndex: Int,
        val sectionFraction: Float,
    ) : NovelBookUiCommand
}

/** One placeholder of the book skeleton: where a section lives in the spine and which chapter it is. */
data class SeedSection(
    val sectionIndex: Int,
    val chapterId: Long,
)

/**
 * Ordered queue of pending [NovelBookUiCommand]s.
 *
 * Commands stay pending until the reader acknowledges them, so a recomposition or a short lived
 * WebView detach cannot silently drop DOM work. Only the newest scroll request is kept: older ones
 * are always obsolete.
 */
internal class NovelBookUiCommandQueue {

    private val nextId = AtomicLong(1L)

    private val pending = MutableStateFlow<List<NovelBookUiCommand>>(emptyList())

    val commands: StateFlow<List<NovelBookUiCommand>> = pending.asStateFlow()

    val pendingCount: Int get() = pending.value.size

    /**
     * Queues the book skeleton. Only the newest seed is kept and it always runs before the other
     * pending work, because appends and scroll requests target the placeholders it creates.
     */
    fun enqueueSeed(sections: List<SeedSection>): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            listOf(NovelBookUiCommand.Seed(id = id, sections = sections)) +
                current.filterNot { it is NovelBookUiCommand.Seed }
        }
        return id
    }

    fun enqueueAppend(sectionIndex: Int, html: String, keepScrollAnchored: Boolean = true): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            current.filterNot { it is NovelBookUiCommand.Append && it.sectionIndex == sectionIndex } +
                NovelBookUiCommand.Append(
                    id = id,
                    sectionIndex = sectionIndex,
                    html = html,
                    keepScrollAnchored = keepScrollAnchored,
                )
        }
        return id
    }

    fun enqueuePrune(sectionIndex: Int): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            current.filterNot { it.sectionIndex == sectionIndex && it is NovelBookUiCommand.Append } +
                NovelBookUiCommand.Prune(id = id, sectionIndex = sectionIndex)
        }
        return id
    }

    fun enqueueScrollTo(sectionIndex: Int, sectionFraction: Float): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            current.filterNot { it is NovelBookUiCommand.ScrollTo } +
                NovelBookUiCommand.ScrollTo(
                    id = id,
                    sectionIndex = sectionIndex,
                    sectionFraction = sectionFraction.coerceIn(0f, 1f),
                )
        }
        return id
    }

    fun ack(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val acknowledged = ids.toSet()
        pending.update { current -> current.filterNot { it.id in acknowledged } }
    }

    fun clear() {
        pending.value = emptyList()
    }
}
