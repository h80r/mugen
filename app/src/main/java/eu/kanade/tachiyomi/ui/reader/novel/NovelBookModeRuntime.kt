package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tachiyomi.domain.items.novelchapter.model.NovelChapter

/**
 * Single entry point the novel reader screen model uses to drive book mode.
 *
 * It owns the spine, the section store/loader and the reading session, so the screen model only has
 * to start/stop it, report scroll metrics and forward render/prune work to the WebView. Everything
 * here is Android free: the chapter payload and HTML normalization come in as lambdas, which keeps
 * the chapter-by-chapter reader untouched and this whole flow unit testable.
 */
internal class NovelBookModeRuntime(
    private val loadRawSection: suspend (Long) -> NovelBookRawSection,
    private val normalizeHtml: suspend (String, String) -> String,
    private val showChapterHeadings: () -> Boolean = { true },
    private val prepareAhead: () -> Int = { NovelBookWindowConfig.DEFAULT_PREFETCH_AHEAD },
) {

    private var spine: NovelBookSpine = NovelBookSpine.EMPTY

    private val store = NovelBookSectionStore()

    private val resolver = NovelBookSectionHtmlResolver(
        currentSpine = { spine },
        loadRawSection = loadRawSection,
        normalizeHtml = normalizeHtml,
        showChapterHeadings = showChapterHeadings,
    )

    private var loader = NovelBookSectionLoader(
        store = store,
        fetchSectionBaseUrl = resolver::resolvedBaseUrl,
        fetchSectionHtml = { chapterId -> resolver.resolve(chapterId) },
    )

    private var session: NovelBookSession? = null

    /**
     * Window configuration of the running session.
     *
     * Prefetching used to rebuild the default configuration instead of reusing the one the session
     * was started with, so a book started with a wider window kept prefetching (and pruning) with
     * the narrow default.
     */
    private var activeConfig: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT

    val isActive: Boolean get() = session != null

    val location: NovelBookLocation get() = session?.location ?: NovelBookLocation.START

    val engineSpine: NovelBookSpine get() = spine

    val currentChapterId: Long? get() = session?.currentSection()?.chapterId

    /**
     * Real section text lengths observed while reading. They never change the current session's size
     * domain; they are meant to be persisted and passed back as `measuredCharCounts` to [start] so the
     * spine's estimates converge across sessions.
     */
    val measuredTextLengths: Map<Long, Int> get() = session?.measuredTextLengths ?: emptyMap()

    /**
     * Builds the spine for [chapters] and resumes where the reader left off, preferring a stored
     * book location and falling back to the legacy per-chapter position.
     */
    fun start(
        chapters: List<NovelChapter>,
        resumeProgress: Long = 0L,
        resumeChapterId: Long? = null,
        resumeChapterFraction: Float = 0f,
        measuredCharCounts: Map<Long, Int> = emptyMap(),
    ): NovelBookLocation {
        spine = NovelBookSpine.fromChapters(
            chapters = chapters,
            measuredCharCounts = measuredCharCounts,
        )
        val resumeLocation = NovelBookReadMarkingPolicy.resolveResumeLocation(
            spine = spine,
            progressValue = resumeProgress,
            fallbackChapterId = resumeChapterId,
            fallbackChapterFraction = resumeChapterFraction,
        )
        activeConfig = NovelBookWindowConfig.DEFAULT.copy(
            prefetchAhead = prepareAhead().coerceAtLeast(NovelBookWindowConfig.DEFAULT.prefetchAhead),
        )
        session = NovelBookSession(
            loader = loader,
            config = activeConfig,
        ).also { it.reset(spine, resumeLocation) }
        return resumeLocation
    }

    /** Stops book mode and frees every resident section. */
    fun stop() {
        session = null
        spine = NovelBookSpine.EMPTY
        loader.clear()
        loader = NovelBookSectionLoader(
            store = store,
            fetchSectionBaseUrl = resolver::resolvedBaseUrl,
            fetchSectionHtml = { chapterId -> resolver.resolve(chapterId) },
        )
    }

    /**
     * Drops the rendered-section bookkeeping so the next [sync] re-renders the resident window.
     *
     * Used when the reader document was rebuilt underneath the runtime: the prepared HTML is still
     * cached, only the document lost its content.
     */
    fun forgetRenderedSections() {
        session?.forgetRenderedSections()
    }

    fun moveTo(sectionIndex: Int, sectionFraction: Float) {
        val activeSession = session ?: return
        val section = activeSession.sectionAt(sectionIndex) ?: return
        activeSession.moveTo(
            NovelBookLocation(
                sectionIndex = sectionIndex,
                charOffset = (section.charCount * sectionFraction).toInt(),
            ),
        )
        spine = activeSession.spine
    }

    /** Moves to an exact renderer-owned text position without converting through a pixel fraction. */
    fun moveTo(location: NovelBookLocation) {
        val activeSession = session ?: return
        activeSession.moveTo(location)
        spine = activeSession.spine
    }

    fun moveToChapter(chapterId: Long, fraction: Float = 0f): Boolean {
        val activeSession = session ?: return false
        val moved = activeSession.moveToChapter(chapterId, fraction)
        spine = activeSession.spine
        return moved
    }

    fun measureSection(chapterId: Long, charCount: Int) {
        val activeSession = session ?: return
        activeSession.measureSection(chapterId, charCount)
        spine = activeSession.spine
    }

    /**
     * Records the pixel height the renderer reported for a section. Heights are used for anchoring
     * and placeholders only; the book's progress domain stays on section text weights.
     */
    fun measureSectionLayoutHeight(chapterId: Long, heightPx: Int) {
        val activeSession = session ?: return
        activeSession.measureLayoutHeight(chapterId, heightPx)
        spine = activeSession.spine
    }

    /** Applies one render/prune/prefetch round for the current position. */
    suspend fun sync(
        renderSection: suspend (NovelBookSection, String) -> Unit,
        releaseSection: suspend (NovelBookSection) -> Unit,
        prepareSection: suspend (NovelBookSection) -> Unit,
    ): NovelBookRenderPlan {
        val activeSession = session ?: return NovelBookRenderPlan.EMPTY
        val plan = activeSession.sync(
            renderSection = renderSection,
            releaseSection = releaseSection,
            prepareSection = prepareSection,
        )
        spine = activeSession.spine
        return plan
    }

    /** Prepares a chapter's section HTML, e.g. for the "prepare book" action. */
    suspend fun prepareChapter(chapterId: Long): NovelBookSectionResult =
        loader.prepare(chapterId).also { measurePreparedSection(chapterId, it) }

    /**
     * Replaces a section estimate with its real text length as soon as the section HTML exists.
     *
     * Section weights used to stay on the default estimate until the renderer displayed a section, so
     * the book percentage was only exact around the few chapters that had been shown. Every prepared
     * chapter now contributes its true weight, which makes whole-book progress exact once the novel is
     * fully downloaded and prepared.
     */
    private fun measurePreparedSection(chapterId: Long, result: NovelBookSectionResult) {
        val ready = result as? NovelBookSectionResult.Ready ?: return
        val activeSession = session ?: return
        val charCount = novelBookSectionTextLength(ready.html)
        if (charCount <= 0) return
        activeSession.measureSection(chapterId, charCount)
        spine = activeSession.spine
    }

    suspend fun loadEngineDocument(section: NovelBookSection): NovelBookDocument {
        return when (val result = prepareChapter(section.chapterId)) {
            is NovelBookSectionResult.Ready -> NovelBookDocument(
                sectionIndex = section.index,
                chapterId = section.chapterId,
                html = result.html,
                baseUrl = result.baseUrl,
            )
            is NovelBookSectionResult.Failed -> error(
                result.message ?: "Failed to load book section ${section.chapterId}",
            )
        }
    }

    /** Prepares the dedicated reader's neighboring spine sections without adding them to one DOM. */
    suspend fun prefetchAround(sectionIndex: Int): List<NovelBookSectionResult> = coroutineScope {
        if (session == null || spine.isEmpty) return@coroutineScope emptyList()
        val config = activeConfig
        val loadedSections = spine.sections
            .filter { loader.isPrepared(it.chapterId) }
            .mapTo(mutableSetOf()) { it.index }
        val inFlightSections = loader.inFlightChapterIds
            .mapNotNullTo(mutableSetOf()) { chapterId ->
                spine.indexOf(chapterId).takeIf { it >= 0 }
            }
        val plan = NovelBookPrefetchPlanner.plan(
            spine = spine,
            currentSectionIndex = sectionIndex,
            loadedSections = loadedSections,
            inFlightSections = inFlightSections,
            config = config,
        )
        val prepared = plan.prefetchQueue
            .mapNotNull(spine::sectionAt)
            .map { section ->
                async { section.chapterId to loader.prepare(section.chapterId) }
            }
            .awaitAll()
        // Measuring mutates the session, so it runs after the parallel loads have joined.
        prepared.forEach { (chapterId, result) -> measurePreparedSection(chapterId, result) }
        prepared.map { it.second }
    }

    suspend fun retryChapter(chapterId: Long): NovelBookSectionResult = loader.retry(chapterId)

    /** Chapter behind a spine position, so the reader can act on a section it only knows by index. */
    fun chapterIdOfSection(sectionIndex: Int): Long? = spine.sectionAt(sectionIndex)?.chapterId

    fun chaptersToMarkRead(alreadyReadChapterIds: Set<Long> = emptySet()): List<Long> =
        session?.chaptersToMarkRead(alreadyReadChapterIds) ?: emptyList()

    fun encodedProgress(): Long? = session?.encodedProgress()

    fun uiState(): NovelReaderScreenModel.State.ReaderBookModeState =
        session?.uiState(showChapterHeadings = showChapterHeadings())
            ?: NovelReaderScreenModel.State.ReaderBookModeState()

    /**
     * Starts book mode from a pre-built [spine] (e.g. from a compiled book artifact) and a custom
     * section fetcher. The fetcher receives the synthetic section key stored in
     * [NovelBookSection.chapterId] and must return the HTML for that section.
     */
    fun startWithSpine(
        spine: NovelBookSpine,
        resumeLocation: NovelBookLocation,
        /**
         * Window of the compiled book. Blocks are much smaller than chapters, so the caller sizes
         * the window from the block length instead of leaving it at the per-chapter default.
         */
        windowConfig: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
        fetchSectionHtml: suspend (Long) -> String,
    ): NovelBookLocation {
        this.spine = spine
        loader = NovelBookSectionLoader(
            store = store,
            fetchSectionBaseUrl = { null },
            fetchSectionHtml = fetchSectionHtml,
        )
        activeConfig = windowConfig.copy(
            prefetchAhead = prepareAhead().coerceAtLeast(windowConfig.prefetchAhead),
        )
        session = NovelBookSession(
            loader = loader,
            config = activeConfig,
        ).also { it.reset(spine, resumeLocation) }
        return resumeLocation
    }
}
