package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * One piece of book-mode work the reader UI has to run against the mounted book renderer.
 *
 * The screen model never touches the renderer itself; it queues commands and the reader applies
 * them (JavaScript for the book engine, resident sections for the native list), then acknowledges
 * them by id.
 */
sealed interface NovelBookUiCommand {
    val id: Long
    val sectionIndex: Int

    /** Append a prepared section to the end (or start) of the document. */
    data class Append(
        override val id: Long,
        override val sectionIndex: Int,
        val html: String,
        val keepScrollAnchored: Boolean = true,
    ) : NovelBookUiCommand

    /**
     * Swap the content of a resident section without touching the reading position.
     *
     * A finished translation only changes the text of the chapters it covers, so the document is not
     * rebuilt for it: the affected sections are replaced in place.
     */
    data class Replace(
        override val id: Long,
        override val sectionIndex: Int,
        val html: String,
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

/**
 * Ordered queue of pending [NovelBookUiCommand]s.
 *
 * Commands stay pending until the reader acknowledges them, so a recomposition or a short lived
 * renderer detach cannot silently drop work. Only the newest scroll request is kept: older ones
 * are always obsolete.
 */
internal class NovelBookUiCommandQueue {

    private val nextId = AtomicLong(1L)

    private val pending = MutableStateFlow<List<NovelBookUiCommand>>(emptyList())

    val commands: StateFlow<List<NovelBookUiCommand>> = pending.asStateFlow()

    val pendingCount: Int get() = pending.value.size

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

    fun enqueueReplace(sectionIndex: Int, html: String): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            current.filterNot { it is NovelBookUiCommand.Replace && it.sectionIndex == sectionIndex } +
                NovelBookUiCommand.Replace(id = id, sectionIndex = sectionIndex, html = html)
        }
        return id
    }

    fun enqueuePrune(sectionIndex: Int): Long {
        val id = nextId.getAndIncrement()
        pending.update { current ->
            current.filterNot {
                it.sectionIndex == sectionIndex &&
                    (it is NovelBookUiCommand.Append || it is NovelBookUiCommand.Replace)
            } + NovelBookUiCommand.Prune(id = id, sectionIndex = sectionIndex)
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
