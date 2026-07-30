package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner
import eu.kanade.tachiyomi.data.book.novel.NovelBookChapterEntry
import eu.kanade.tachiyomi.data.book.novel.NovelBookIndex
import eu.kanade.tachiyomi.data.book.novel.NovelBookMeta
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
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

    /**
     * True when this book carries pre-compiled native blocks, so the native renderer can skip the
     * HTML parse at scroll time.
     */
    val hasNativeBlocks: Boolean = NovelBookArtifact.hasNativeStream(directory, meta) &&
        index.chapters.all { it.nativeByteLength > 0 }

    /**
     * Pre-compiled blocks of a section, or null when the book has no native stream yet and the
     * caller has to fall back to parsing [documentFor].
     *
     * A section is a window of the continuous book and can span several chapters, so the native
     * ranges of every chapter it touches are read and then clipped to the section's own character
     * range. That clipping uses the same offsets as the HTML path, which is why progress, the table
     * of contents and read marking behave identically in both renderers.
     */
    fun nativeBlocksFor(sectionIndex: Int): List<NovelBookNativeBlock>? {
        if (!hasNativeBlocks) return null
        val block = blocks.getOrNull(sectionIndex) ?: return null
        val sectionStart = block.charStart
        val sectionEnd = block.charStart + block.charLength
        val chapters = index.chapters.filter { chapter ->
            chapter.charStart < sectionEnd && chapter.charStart + chapter.charLength > sectionStart
        }
        if (chapters.isEmpty()) return null

        // Chapters are contiguous in the native stream, so overlapping chapters collapse into a
        // single sequential read instead of one read per chapter.
        val nativeStart = chapters.first().nativeByteStart
        val nativeLength = chapters.sumOf { it.nativeByteLength }
        val decoded = NovelBookArtifact.readNativeRange(
            directory = directory,
            byteStart = nativeStart,
            byteLength = nativeLength,
        )
        if (decoded.isEmpty()) return null
        return decoded.filter { it.charStart < sectionEnd && it.charStart + it.charLength > sectionStart }
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
