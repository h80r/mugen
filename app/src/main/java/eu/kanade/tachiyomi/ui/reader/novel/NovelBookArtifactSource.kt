package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterEntry
import eu.kanade.tachiyomi.data.book.novel.NovelBookIndex
import eu.kanade.tachiyomi.data.book.novel.NovelBookMeta
import java.io.File

/**
 * Reader-side view over a compiled book artifact.
 *
 * The book is read as fixed-size blocks of the continuous body instead of one document per chapter,
 * which is what removes the half-empty page at every chapter boundary: a block simply continues with
 * the next chapter heading right after the previous paragraph.
 *
 * Block lengths come from the artifact index, so every section is measured from the start and
 * whole-book progress is exact instead of being rescaled while chapters are measured.
 */
class NovelBookArtifactSource(
    val directory: File,
    val index: NovelBookIndex,
    val meta: NovelBookMeta,
    val blocks: List<NovelBookBlock>,
) {

    /** Spine of blocks. Section indices are block indices, not chapter indices. */
    val spine: NovelBookSpine = NovelBookSpine(
        blocks.map { block ->
            NovelBookSection(
                chapterId = sectionKeyOf(block.index),
                index = block.index,
                name = NovelBookBlockPlanner.chapterById(index, block.firstChapterId)?.title.orEmpty(),
                charCount = block.charLength.coerceAtLeast(1),
                isMeasured = true,
            )
        },
    )

    val totalChars: Int get() = meta.totalChars

    /** Reads the body slice of a block as the renderer document for that section. */
    fun documentFor(sectionIndex: Int): NovelBookDocument? {
        val block = blocks.getOrNull(sectionIndex) ?: return null
        val html = NovelBookArtifact.readRange(
            directory = directory,
            byteStart = block.byteStart,
            byteLength = block.byteLength,
        )
        if (html.isEmpty()) return null
        return NovelBookDocument(
            sectionIndex = block.index,
            chapterId = block.firstChapterId,
            html = html,
        )
    }

    /** Whole-book character offset of a block location, used as the persisted reading position. */
    fun charOffsetOf(location: NovelBookLocation): Int {
        val block = blocks.getOrNull(location.sectionIndex) ?: return 0
        val maxOffset = (block.charLength - 1).coerceAtLeast(0)
        return block.charStart + location.charOffset.coerceIn(0, maxOffset)
    }

    /** Block location of a whole-book character offset. */
    fun locationOf(charOffset: Int): NovelBookLocation {
        val block = NovelBookBlockPlanner.blockAt(blocks, charOffset) ?: return NovelBookLocation.START
        return NovelBookLocation(
            sectionIndex = block.index,
            charOffset = (charOffset - block.charStart).coerceAtLeast(0),
        )
    }

    /** Opens the book exactly at the first character of a chapter, for table-of-contents taps. */
    fun locationOfChapter(chapterId: Long): NovelBookLocation? {
        val chapter = NovelBookBlockPlanner.chapterById(index, chapterId) ?: return null
        return locationOf(chapter.charStart)
    }

    /** Chapter that owns a whole-book character offset, for the reader title and read marking. */
    fun chapterAt(charOffset: Int): NovelBookChapterEntry? =
        NovelBookBlockPlanner.chapterAt(index, charOffset)

    /**
     * Chapters whose text ends before [charOffset], i.e. the ones the reader scrolled completely
     * past. In book mode nothing is ever "closed", so this offset comparison replaces the
     * per-chapter read threshold; the chapter under the caret is intentionally excluded because it
     * is still being read.
     */
    fun chaptersFullyReadBefore(charOffset: Int): List<Long> = index.chapters
        .asSequence()
        .filter { it.charStart + it.charLength <= charOffset }
        .map { it.chapterId }
        .toList()

    fun progressOf(charOffset: Int): Float = NovelBookBlockPlanner.progressOf(meta, charOffset)

    companion object {
        /** Synthetic section key: block indices must not collide with real chapter ids. */
        fun sectionKeyOf(blockIndex: Int): Long = -(blockIndex.toLong() + 1L)

        /**
         * Opens the artifact of a novel, or returns null when no book was compiled for it yet or the
         * artifact belongs to a different source than the one the novel currently uses.
         */
        fun open(
            directory: File,
            targetChars: Int = NovelBookBlockPlanner.DEFAULT_TARGET_CHARS,
        ): NovelBookArtifactSource? {
            if (!NovelBookArtifact.exists(directory)) return null
            val index = NovelBookArtifact.readIndex(directory) ?: return null
            val meta = NovelBookArtifact.readMeta(directory) ?: return null
            if (index.chapters.isEmpty()) return null
            val blocks = NovelBookBlockPlanner.plan(index, targetChars)
            if (blocks.isEmpty()) return null
            return NovelBookArtifactSource(
                directory = directory,
                index = index,
                meta = meta,
                blocks = blocks,
            )
        }
    }
}
