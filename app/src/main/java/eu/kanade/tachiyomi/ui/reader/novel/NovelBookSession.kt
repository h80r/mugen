package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Runtime state of one book-mode reading session.
 *
 * The session owns the spine, the current global location and the set of sections that are currently
 * rendered in the reader document. It turns those into render/release/prepare work by delegating to
 * [NovelBookRenderCoordinator], and it never touches the WebView itself: the caller supplies the
 * callbacks that append, prune and prefetch sections. That keeps the whole flow unit-testable.
 */
internal class NovelBookSession(
    private val loader: NovelBookSectionLoader,
    private val config: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
) {

    var spine: NovelBookSpine = NovelBookSpine.EMPTY
        private set

    var location: NovelBookLocation = NovelBookLocation.START
        private set

    private val renderedSections = linkedSetOf<Int>()

    private val observedTextLengths = mutableMapOf<Long, Int>()

    /**
     * Real text lengths observed while rendering, keyed by chapter id.
     *
     * They are deliberately NOT applied to the live spine: section weights must stay stable for the
     * whole session, otherwise whole-book progress rescales every time a section renders. They are
     * meant to be persisted and fed back into [NovelBookSpine.fromChapters] on the next session so
     * the estimates converge over time.
     */
    val measuredTextLengths: Map<Long, Int> get() = observedTextLengths.toMap()

    val renderedSectionIndices: List<Int>
        get() = renderedSections.sorted()

    val isEmpty: Boolean
        get() = spine.isEmpty

    /** Starts (or restarts) the session on [spine], resuming at [location]. */
    fun reset(spine: NovelBookSpine, location: NovelBookLocation = NovelBookLocation.START) {
        this.spine = spine
        this.location = spine.clampLocation(location)
        renderedSections.clear()
        observedTextLengths.clear()
    }

    /**
     * Forgets which sections are currently in the reader document, keeping the spine, the reading
     * position and the observed text lengths.
     *
     * The reader document can be destroyed and rebuilt underneath the session (the WebView reloads
     * its document, e.g. when the reader is opened or the document is replaced). Everything the
     * session had appended is gone at that point, but the session still believed those sections were
     * rendered, so the next planning round appended nothing: the reader stayed on a document holding
     * only whatever arrived after the reload. Calling this makes the next [sync] re-render the whole
     * resident window from the already prepared HTML.
     */
    fun forgetRenderedSections() {
        renderedSections.clear()
    }

    /** Replaces the estimated text length of a section with its measured length (size domain). */
    fun measureSection(chapterId: Long, charCount: Int) {
        spine = spine.withMeasuredSection(chapterId, charCount)
        location = spine.clampLocation(location)
    }

    /**
     * Records the pixel height the renderer gave a section. Kept out of the size domain on purpose:
     * heights depend on font size, margins and theme, so writing them into the section weight made
     * whole-book progress rescale on every re-measure.
     */
    fun measureLayoutHeight(chapterId: Long, heightPx: Int) {
        spine = spine.withMeasuredLayoutHeight(chapterId, heightPx)
    }

    /** Moves the reading position, e.g. after a scroll metrics update. */
    fun moveTo(location: NovelBookLocation) {
        this.location = spine.clampLocation(location)
    }

    /** Moves the reading position to a chapter, e.g. after a chapter list tap. */
    fun moveToChapter(chapterId: Long, fraction: Float = 0f): Boolean {
        val target = spine.locationForChapterFraction(chapterId, fraction) ?: return false
        location = spine.clampLocation(target)
        return true
    }

    fun sectionAt(sectionIndex: Int): NovelBookSection? = spine.sectionAt(sectionIndex)

    fun currentSection(): NovelBookSection? = spine.sectionAt(location.sectionIndex)

    fun plan(): NovelBookRenderPlan = NovelBookRenderCoordinator.resolve(
        spine = spine,
        currentSectionIndex = location.sectionIndex,
        renderedSections = renderedSections.toSet(),
        preparedSections = preparedSectionIndices(),
        inFlightSections = inFlightSectionIndices(),
        config = config,
    )

    /**
     * Applies one planning round: appends newly prepared sections, prunes sections that left the
     * resident window and asks for the next sections to be prepared.
     *
     * [prepareSection] is expected to start background work and return quickly. Pass
     * [prepareSectionInline] when the caller wants the load to happen inline instead, e.g. in tests
     * or for a blocking "prepare book" action. It has no default value on purpose: a suspending
     * default argument that calls the loader crashes the Kotlin IR backend.
     */
    suspend fun sync(
        renderSection: suspend (NovelBookSection, String) -> Unit,
        releaseSection: suspend (NovelBookSection) -> Unit,
        prepareSection: suspend (NovelBookSection) -> Unit,
    ): NovelBookRenderPlan {
        val plan = plan()
        plan.render.forEach { command ->
            val section = spine.sectionAt(command.sectionIndex) ?: return@forEach
            val html = loader.preparedHtml(section.chapterId) ?: return@forEach
            renderSection(section, html)
            renderedSections += section.index
            // Remember the section's real text length without rescaling the live size domain: the
            // weights the session started with stay fixed, and these values seed the next session.
            observedTextLengths[section.chapterId] = novelBookSectionTextLength(html)
        }
        plan.release.forEach { command ->
            val section = spine.sectionAt(command.sectionIndex) ?: return@forEach
            releaseSection(section)
            renderedSections -= section.index
            loader.release(section.chapterId)
        }
        plan.prepare.forEach { command ->
            val section = spine.sectionAt(command.sectionIndex) ?: return@forEach
            prepareSection(section)
        }
        return plan
    }

    /** Loads a section inline; usable as the `prepareSection` argument of [sync]. */
    suspend fun prepareSectionInline(section: NovelBookSection) {
        loader.prepare(section.chapterId)
    }

    /** Chapter ids that the current position turned into "read" chapters. */
    fun chaptersToMarkRead(alreadyReadChapterIds: Set<Long> = emptySet()): List<Long> =
        NovelBookReadMarkingPolicy.sectionsToMarkRead(
            spine = spine,
            location = location,
            alreadyReadChapterIds = alreadyReadChapterIds,
        )

    /** The value to persist in the current chapter's `lastPageRead` column. */
    fun encodedProgress(): Long = NovelBookReadMarkingPolicy.encodeLocation(spine, location)

    fun uiState(showChapterHeadings: Boolean = true): NovelReaderScreenModel.State.ReaderBookModeState =
        NovelReaderScreenModel.State.ReaderBookModeState(
            isEnabled = true,
            sectionCount = spine.sections.size,
            currentSectionIndex = location.sectionIndex,
            currentSectionFraction = spine.sectionProgressOf(location),
            bookProgressFraction = spine.progressOf(location),
            renderedSectionIndices = renderedSectionIndices,
            preparingSectionIndices = inFlightSectionIndices().sorted(),
            failedSectionIndices = failedSectionIndices().sorted(),
            showChapterHeadings = showChapterHeadings,
        )

    private fun preparedSectionIndices(): Set<Int> = spine.sections
        .filter { loader.isPrepared(it.chapterId) }
        .map { it.index }
        .toSet()

    private fun inFlightSectionIndices(): Set<Int> = sectionIndicesOf(loader.inFlightChapterIds)

    private fun failedSectionIndices(): Set<Int> = sectionIndicesOf(loader.failedChapterIds)

    private fun sectionIndicesOf(chapterIds: Set<Long>): Set<Int> = chapterIds
        .mapNotNull { chapterId -> spine.indexOf(chapterId).takeIf { it >= 0 } }
        .toSet()
}
