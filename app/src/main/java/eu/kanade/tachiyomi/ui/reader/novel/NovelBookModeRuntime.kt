package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Spine kind of a session, kept in the cache key so artifacts never read a foreign cache entry. */
private const val SPINE_KIND_ARTIFACT = "artifact"

/**
 * Single entry point the novel reader screen model uses to drive book mode.
 *
 * It owns the spine, the section store/loader and the reading session, so the screen model only has
 * to start/stop it, report scroll metrics and forward render/prune work to the WebView. Everything
 * here is Android free: the chapter payload and HTML normalization come in as lambdas, which keeps
 * the chapter-by-chapter reader untouched and this whole flow unit testable.
 */
internal class NovelBookModeRuntime(
    private val sectionRepository: BookSectionRepository,
    private val showChapterHeadings: () -> Boolean = { true },
    private val prepareAhead: () -> Int = { NovelBookWindowConfig.DEFAULT_PREFETCH_AHEAD },
    /**
     * Scope the prepared sections are cached under, e.g. the novel plus the translation variant the
     * HTML was built with. Returning null keeps sections in memory only, which is what tests want.
     */
    private val sectionCacheScope: () -> String? = { null },
    private val readCachedSection: (String) -> NovelBookPreparedSection? = { null },
    private val writeCachedSection: (String, NovelBookPreparedSection) -> Unit = { _, _ -> },
    private val deleteCachedSection: (String) -> Unit = {},
    /**
     * Drops every cached section of this scope that was not built by the current transformation
     * chain. Called once per session, on the first cache miss.
     */
    private val pruneCachedSections: (String, String) -> Unit = { _, _ -> },
) {

    private var spine: NovelBookSpine = NovelBookSpine.EMPTY

    private val store = NovelBookSectionStore(
        diskReadSection = { sectionKey ->
            val cached = sectionCacheKey(sectionKey)?.let(readCachedSection)
            if (cached == null) pruneStaleCachedSections()
            cached
        },
        diskWriteSection = { sectionKey, section ->
            sectionCacheKey(sectionKey)?.let { key -> writeCachedSection(key, section) }
        },
        diskDelete = { sectionKey -> sectionCacheKey(sectionKey)?.let(deleteCachedSection) },
    )

    /**
     * Disk key of one section, or null while no scope is configured.
     *
     * The heading setting is part of the key on purpose: the same section can be prepared with or
     * without its chapter heading, so one shared key would serve the wrong markup.
     */
    private fun sectionCacheKey(sectionKey: Long): String? =
        sectionCacheKeyPrefix()?.let { prefix -> "${prefix}s$sectionKey" }

    /**
     * Key prefix every section of the current scope and transformation chain shares.
     *
     * The signature carries the pipeline version, the heading setting and the visible translation,
     * so entries built by a different chain can be recognized - and deleted - instead of piling up
     * on disk forever.
     */
    private fun sectionCacheKeyPrefix(): String? {
        val scope = sectionCacheScope()?.takeIf { it.isNotBlank() } ?: return null
        return "$scope-$SPINE_KIND_ARTIFACT-${sectionRepository.transformSignature()}-"
    }

    /**
     * Deletes stale entries of this scope once per session.
     *
     * A miss means the current chain has nothing cached here, which is exactly the moment the
     * entries of older chains are known to be dead weight.
     */
    private fun pruneStaleCachedSections() {
        if (staleSectionsPruned) return
        val scope = sectionCacheScope()?.takeIf { it.isNotBlank() } ?: return
        val keepPrefix = sectionCacheKeyPrefix() ?: return
        staleSectionsPruned = true
        runCatching { pruneCachedSections(scope, keepPrefix) }
    }

    private var staleSectionsPruned = false

    private val resolver = NovelBookSectionHtmlResolver(
        currentSpine = { spine },
        repository = sectionRepository,
    )

    private var loader = NovelBookSectionLoader(
        store = store,
        fetchSectionBaseUrl = resolver::resolvedBaseUrl,
        fetchSectionHtml = { sectionKey -> resolver.resolve(sectionKey) },
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

    /** Stops book mode and frees every resident section. */
    fun stop() {
        session = null
        spine = NovelBookSpine.EMPTY
        loader.clear()
        loader = NovelBookSectionLoader(
            store = store,
            fetchSectionBaseUrl = resolver::resolvedBaseUrl,
            fetchSectionHtml = { sectionKey -> resolver.resolve(sectionKey) },
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

    /** Prepares one section's HTML by spine index, e.g. for the "prepare book" action. */
    suspend fun prepareSection(sectionKey: Long): NovelBookSectionResult = loader.prepare(sectionKey)

    suspend fun loadEngineDocument(section: NovelBookSection): NovelBookDocument {
        return when (val result = prepareSection(section.loaderKey)) {
            is NovelBookSectionResult.Ready -> NovelBookDocument(
                sectionIndex = section.index,
                chapterId = section.chapterId,
                html = result.html,
                baseUrl = result.baseUrl,
            )
            is NovelBookSectionResult.Failed -> error(
                result.message ?: "Failed to load book section ${section.index}",
            )
        }
    }

    /** Prepares the dedicated reader's neighboring spine sections without adding them to one DOM. */
    suspend fun prefetchAround(sectionIndex: Int): List<NovelBookSectionResult> = coroutineScope {
        if (session == null || spine.isEmpty) return@coroutineScope emptyList()
        val config = activeConfig
        val loadedSections = spine.sections
            .filter { loader.isPrepared(it.loaderKey) }
            .mapTo(mutableSetOf()) { it.index }
        val inFlightSections = loader.inFlightSectionKeys
            .mapTo(mutableSetOf()) { sectionKey -> sectionKey.toInt() }
        val plan = NovelBookPrefetchPlanner.plan(
            spine = spine,
            currentSectionIndex = sectionIndex,
            loadedSections = loadedSections,
            inFlightSections = inFlightSections,
            config = config,
        )
        plan.prefetchQueue
            .mapNotNull(spine::sectionAt)
            .map { section -> async { loader.prepare(section.loaderKey) } }
            .awaitAll()
    }

    suspend fun retrySection(sectionKey: Long): NovelBookSectionResult = loader.retry(sectionKey)

    /** Chapter behind a spine position, so the reader can act on a section it only knows by index. */
    fun chapterIdOfSection(sectionIndex: Int): Long? = spine.sectionAt(sectionIndex)?.chapterId

    fun uiState(): NovelReaderScreenModel.State.ReaderBookModeState =
        session?.uiState(showChapterHeadings = showChapterHeadings())
            ?: NovelReaderScreenModel.State.ReaderBookModeState()

    /**
     * Starts book mode from a pre-built [spine] (e.g. from a compiled book artifact) and a custom
     * section fetcher. The fetcher receives [NovelBookSection.loaderKey], i.e. the spine index of
     * the section, and must return the HTML for that section.
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
