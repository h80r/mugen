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

    val renderedSectionIndices: List<Int>
        get() = renderedSections.sorted()

    val isEmpty: Boolean
        get() = spine.isEmpty

    /** Starts (or restarts) the session on [spine], resuming at [location]. */
    fun reset(spine: NovelBookSpine, location: NovelBookLocation = NovelBookLocation.START) {
        this.spine = spine
        this.location = spine.clampLocation(location)
        renderedSections.clear()
    }

    /**
     * Forgets which sections are currently in the reader document, keeping the spine and the reading
     * position.
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
            val html = loader.preparedHtml(section.loaderKey) ?: return@forEach
            renderSection(section, html)
            renderedSections += section.index
        }
        plan.release.forEach { command ->
            val section = spine.sectionAt(command.sectionIndex) ?: return@forEach
            releaseSection(section)
            renderedSections -= section.index
            loader.release(section.loaderKey)
        }
        plan.prepare.forEach { command ->
            val section = spine.sectionAt(command.sectionIndex) ?: return@forEach
            prepareSection(section)
        }
        return plan
    }

    /** Loads a section inline; usable as the `prepareSection` argument of [sync]. */
    suspend fun prepareSectionInline(section: NovelBookSection) {
        loader.prepare(section.loaderKey)
    }

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
        .filter { loader.isPrepared(it.loaderKey) }
        .map { it.index }
        .toSet()

    private fun inFlightSectionIndices(): Set<Int> = sectionIndicesOf(loader.inFlightSectionKeys)

    private fun failedSectionIndices(): Set<Int> = sectionIndicesOf(loader.failedSectionKeys)

    private fun sectionIndicesOf(sectionKeys: Set<Long>): Set<Int> = sectionKeys
        .map { it.toInt() }
        .filter { spine.sectionAt(it) != null }
        .toSet()
}
