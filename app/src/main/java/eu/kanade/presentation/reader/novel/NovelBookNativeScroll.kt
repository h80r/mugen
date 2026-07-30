package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.NovelBookUiCommand
import eu.kanade.tachiyomi.ui.reader.novel.NovelRichContentBlock
import eu.kanade.tachiyomi.ui.reader.novel.parseNovelRichContent

/**
 * Native-renderer side of book mode.
 *
 * The book session emits the same [NovelBookUiCommand] stream for every renderer: append a section,
 * prune a section, scroll to a position. The WebView adapter applies those commands with JavaScript;
 * the native renderer applies them to the list of resident sections below. Nothing about the book
 * model, the spine or progress tracking is renderer specific, so both renderers stay in sync by
 * construction.
 *
 * The section payload is generic so this layer can be unit tested without a parser or Compose.
 */
internal data class NovelBookNativeSection<T>(
    val sectionIndex: Int,
    val blocks: List<T>,
)

/**
 * Applies [commands] to the currently resident [sections].
 *
 * Appends are parsed once and inserted in spine order, so the list always reads top to bottom even
 * when the reader scrolls backwards and earlier sections arrive last. Prunes drop the section again,
 * which is what keeps a long book from holding every chapter in memory. Scroll commands are position
 * changes, not content changes, and are handled by the caller.
 */
internal fun <T> applyNovelBookCommandsToNativeSections(
    sections: List<NovelBookNativeSection<T>>,
    commands: List<NovelBookUiCommand>,
    parseSection: (String) -> List<T>,
    /**
     * Blocks the compiled book already holds for a section, or null when the book has none.
     *
     * When the artifact was built with the native stream, appending a section costs a byte range
     * read plus a JSON decode instead of a full HTML parse of a 200k character window, which is what
     * removes the stall when the reader scrolls into a new part of the book. [parseSection] stays as
     * the fallback so books compiled by older versions keep working unchanged.
     */
    precompiledSection: (Int) -> List<T>? = { null },
): List<NovelBookNativeSection<T>> {
    if (commands.isEmpty()) return sections
    val bySectionIndex = sections.associateByTo(LinkedHashMap()) { it.sectionIndex }
    commands.forEach { command ->
        when (command) {
            is NovelBookUiCommand.Append -> {
                if (!bySectionIndex.containsKey(command.sectionIndex)) {
                    val blocks = precompiledSection(command.sectionIndex)
                        ?.takeIf { it.isNotEmpty() }
                        ?: parseSection(command.html)
                    bySectionIndex[command.sectionIndex] = NovelBookNativeSection(
                        sectionIndex = command.sectionIndex,
                        blocks = blocks,
                    )
                }
            }
            is NovelBookUiCommand.Prune -> bySectionIndex.remove(command.sectionIndex)
            is NovelBookUiCommand.ScrollTo -> Unit
            // Seeding is a DOM-only concern: the native list derives its order from the resident
            // sections it already holds, so there is nothing to pre-create here.
            is NovelBookUiCommand.Seed -> Unit
        }
    }
    return bySectionIndex.values.sortedBy { it.sectionIndex }
}

/** Returns the last scroll command in [commands], which is the only one still worth applying. */
internal fun latestNovelBookScrollCommand(
    commands: List<NovelBookUiCommand>,
): NovelBookUiCommand.ScrollTo? = commands.filterIsInstance<NovelBookUiCommand.ScrollTo>().lastOrNull()

/** The resident book sections as the native renderer holds them. */
internal typealias NovelBookNativeSections = List<NovelBookNativeSection<NovelRichContentBlock>>

/**
 * One row of the native book list.
 *
 * A section that failed to load still occupies its place in the book, so the reader sees an inline
 * error with a retry action instead of the book silently skipping a chapter.
 */
internal sealed interface NovelBookNativeEntry {
    val sectionIndex: Int

    data class Section(
        val section: NovelBookNativeSection<NovelRichContentBlock>,
    ) : NovelBookNativeEntry {
        override val sectionIndex: Int get() = section.sectionIndex
    }

    data class Failed(override val sectionIndex: Int) : NovelBookNativeEntry
}

/** Merges resident sections and failed sections into one list in spine order. */
internal fun buildNovelBookNativeEntries(
    sections: NovelBookNativeSections,
    failedSectionIndices: List<Int>,
): List<NovelBookNativeEntry> {
    if (failedSectionIndices.isEmpty()) {
        return sections.map { NovelBookNativeEntry.Section(it) }
    }
    val residentIndices = sections.mapTo(mutableSetOf()) { it.sectionIndex }
    val failures = failedSectionIndices
        .distinct()
        .filterNot { it in residentIndices }
        .map { NovelBookNativeEntry.Failed(it) }
    return (sections.map { NovelBookNativeEntry.Section(it) } + failures).sortedBy { it.sectionIndex }
}

/**
 * Parses one book section for the native renderer.
 *
 * The section HTML is exactly what the WebView renderer appends to its document, so both renderers
 * show the same content and the same chapter headings.
 */
internal fun parseNovelBookNativeSection(html: String): List<NovelRichContentBlock> =
    parseNovelRichContent(html).blocks

/** A section as the native list currently lays it out, in viewport coordinates. */
internal data class NovelBookNativeViewportItem(
    val sectionIndex: Int,
    /** Offset of the section top relative to the viewport top; negative once scrolled past. */
    val offsetPx: Int,
    val heightPx: Int,
)

/**
 * Derives the reading position from the native list layout, mirroring what the WebView relocate
 * bridge reports: the section under the top of the viewport, plus how far into it the reader is.
 */
internal fun resolveNovelBookNativeRelocate(
    items: List<NovelBookNativeViewportItem>,
): NovelBookViewLocation? {
    if (items.isEmpty()) return null
    val current = items.firstOrNull { it.offsetPx <= 0 && it.offsetPx + it.heightPx > 0 }
        ?: items.first()
    val height = current.heightPx
    val fraction = if (height <= 0) {
        0f
    } else {
        ((-current.offsetPx).toFloat() / height.toFloat()).coerceIn(0f, 1f)
    }
    return NovelBookViewLocation(sectionIndex = current.sectionIndex, sectionFraction = fraction)
}
