package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Pure policy helpers that map a book-mode reading position onto the per-chapter data model.
 *
 * Book mode reads a novel as one continuous document, but progress, history and trackers stay keyed
 * by chapter id. These helpers translate between the two worlds:
 * - which chapters became "read" once the reader crossed a section boundary,
 * - how the global location is encoded into the existing `lastPageRead` column,
 * - how a legacy per-chapter position is resumed as a book location.
 */
internal object NovelBookReadMarkingPolicy {

    /** A section counts as read once this much of it has been passed. */
    const val DEFAULT_READ_THRESHOLD = 0.9f

    /**
     * Chapter ids that should be marked read for the given [location].
     *
     * Every section before the current one is fully passed, and the current section counts once the
     * reader is past [readThreshold]. Chapters in [alreadyReadChapterIds] are skipped.
     */
    fun sectionsToMarkRead(
        spine: NovelBookSpine,
        location: NovelBookLocation,
        alreadyReadChapterIds: Set<Long> = emptySet(),
        readThreshold: Float = DEFAULT_READ_THRESHOLD,
    ): List<Long> {
        if (spine.isEmpty) return emptyList()
        val clamped = spine.clampLocation(location)
        val threshold = readThreshold.coerceIn(0f, 1f)
        val currentProgress = spine.sectionProgressOf(clamped)
        val marked = mutableListOf<Long>()
        spine.sections.forEach { section ->
            if (section.chapterId in alreadyReadChapterIds) return@forEach
            val isPassed = section.index < clamped.sectionIndex
            val isCurrentComplete = section.index == clamped.sectionIndex && currentProgress >= threshold
            if (isPassed || isCurrentComplete) marked += section.chapterId
        }
        return marked
    }

    /** Encodes [location] into the value stored in the chapter `lastPageRead` column. */
    fun encodeLocation(spine: NovelBookSpine, location: NovelBookLocation): Long {
        val clamped = spine.clampLocation(location)
        return encodeBookLocationProgress(
            sectionIndex = clamped.sectionIndex,
            sectionFraction = spine.sectionProgressOf(clamped),
        )
    }

    /** Decodes a stored book-mode progress value, or returns null when it is not a book location. */
    fun decodeLocation(spine: NovelBookSpine, progressValue: Long): NovelBookLocation? {
        val decoded = decodeBookLocationProgress(progressValue) ?: return null
        val section = spine.sectionAt(decoded.sectionIndex) ?: return null
        val charOffset = (section.charCount * decoded.sectionFraction).toInt()
        return spine.clampLocation(NovelBookLocation(decoded.sectionIndex, charOffset))
    }

    /**
     * Resolves the position to open the book at.
     *
     * Prefers a stored book location, then falls back to a legacy per-chapter position so switching
     * an in-progress novel to book mode resumes where the classic reader stopped.
     */
    fun resolveResumeLocation(
        spine: NovelBookSpine,
        progressValue: Long,
        fallbackChapterId: Long?,
        fallbackChapterFraction: Float = 0f,
    ): NovelBookLocation {
        decodeLocation(spine, progressValue)?.let { return it }
        if (fallbackChapterId != null) {
            spine.locationForChapterFraction(fallbackChapterId, fallbackChapterFraction)?.let { return it }
            spine.locationFor(fallbackChapterId)?.let { return it }
        }
        return NovelBookLocation.START
    }
}
