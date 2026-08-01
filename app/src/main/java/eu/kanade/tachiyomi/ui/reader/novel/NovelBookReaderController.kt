package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.book.novel.interactor.SetNovelBookProgress
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Host the book controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 *
 * The screen model keeps the per-chapter state and the shared progress/history pipeline; the book
 * controller only talks to it through this narrow surface so the two stay decoupled.
 */
internal interface NovelBookReaderHost {
    val bookScope: CoroutineScope

    fun bookCurrentNovel(): tachiyomi.domain.entries.novel.model.Novel?
    fun bookCurrentChapter(): NovelChapter?
    fun bookChapterOrderList(): List<NovelChapter>
    fun bookFullChapterOrderList(): List<NovelChapter>
    fun bookMarkChapterReadInMemory(chapterId: Long)
    fun bookTtsChapterRepository(): NovelTtsChapterRepository

    fun bookUpdateSuccessState(
        transform: (NovelReaderScreenModel.State.Success) -> NovelReaderScreenModel.State.Success,
    )
    fun bookAdoptBookModeChapter(chapterId: Long)
    fun bookEnqueueProgressPersistence(update: PendingProgressPersistence)
    fun bookApplyBookSectionTranslation(chapterId: Long, bodyHtml: String): String
    fun bookGeminiTranslationVisible(): Boolean
    fun bookGoogleTranslationVisible(): Boolean
}

/**
 * Book-mode subsystem of the novel reader.
 *
 * Owns the compiled-book artifact source, the book runtime, the section command queue and the
 * book-specific progress/read-marking state. [NovelReaderScreenModel] delegates every
 * `onBookMode*` / `bookEngine*` call here, which keeps the screen model focused on the
 * chapter-by-chapter reader.
 */
internal class NovelBookReaderController(
    private val host: NovelBookReaderHost,
    private val novelReaderPreferences: NovelReaderPreferences = Injekt.get(),
    private val getNovelBookState: tachiyomi.domain.book.novel.interactor.GetNovelBookState = Injekt.get(),
    private val setNovelBookProgress: SetNovelBookProgress = Injekt.get(),
) {

    private val bookModeRuntime = NovelBookModeRuntime(
        loadRawSection = { sectionChapterId ->
            val snapshot = host.bookTtsChapterRepository().loadChapterSnapshot(sectionChapterId)
            NovelBookRawSection(
                chapterId = sectionChapterId,
                chapterName = snapshot.chapter.name,
                rawHtml = snapshot.rawHtml,
                chapterWebUrl = snapshot.chapterWebUrl,
            )
        },
        normalizeHtml = { sectionChapterId, rawHtml, chapterName ->
            withContext(Dispatchers.Default) {
                val withHeading = prependChapterHeadingIfMissing(
                    rawHtml = rawHtml.normalizeStructuredChapterPayload(),
                    chapterName = chapterName,
                )
                val sanitized = sanitizeChapterHtmlForReader(withHeading)
                val bodyHtml = if (sanitized.isBlank()) withHeading else sanitized
                host.bookApplyBookSectionTranslation(chapterId = sectionChapterId, bodyHtml = bodyHtml)
            }
        },
        showChapterHeadings = { novelReaderPreferences.bookModeShowChapterHeadings().get() },
        prepareAhead = { novelReaderPreferences.bookModePrepareAhead().get() },
        sectionCacheScope = { bookSectionCacheScope() },
        readCachedSection = { key -> NovelBookSectionDiskCacheStore.read(key) },
        writeCachedSection = { key, section -> NovelBookSectionDiskCacheStore.write(key, section) },
        deleteCachedSection = { key -> NovelBookSectionDiskCacheStore.remove(key) },
    )

    /**
     * Scope prepared book sections are cached under.
     *
     * Section HTML is normalized, heading-wrapped and possibly translated, so it may only be reused
     * for the same novel and the same visible translation. Mixing those would show a translated
     * section after the translation was hidden (or the other way round), which is why the translation
     * state is part of the scope instead of being invalidated on every toggle. Null before a novel is
     * known, which keeps sections in memory only.
     */
    private fun bookSectionCacheScope(): String? {
        val novelId = host.bookCurrentNovel()?.id ?: return null
        val translation = when {
            host.bookGeminiTranslationVisible() -> "gemini"
            host.bookGoogleTranslationVisible() -> "google"
            else -> "raw"
        }
        val bookVersion = bookStateVersion?.takeIf { it > 0 }?.let { "v$it-" } ?: ""
        return "novel$novelId-$bookVersion$translation"
    }

    /**
     * Version of the compiled book whose sections are being cached.
     *
     * A rebuilt artifact can hold different text for the same chapter ids, so the prepared-section
     * cache has to be scoped to the book version; without it a rebuild would keep serving sections
     * compiled from the old chapters.
     */
    private var bookStateVersion: Long? = null

    /** Opened compiled-book artifact, non-null while this novel is read as one continuous book. */
    var artifactSource: NovelBookArtifactSource? = null
        private set

    /** Pre-compiled native blocks of a book section, or null when this book has none. */
    fun nativeBookBlocksForSection(sectionIndex: Int): List<NovelRichContentBlock>? =
        artifactSource
            ?.nativeBlocksFor(sectionIndex)
            ?.toRichContentBlocks()
            ?.takeIf { it.isNotEmpty() }

    /** Pending book-mode DOM work, consumed and acknowledged by the reader UI. */
    private val bookModeCommandQueue = NovelBookUiCommandQueue()

    internal val bookModeCommands: kotlinx.coroutines.flow.StateFlow<List<NovelBookUiCommand>> =
        bookModeCommandQueue.commands

    internal val bookEngineSpine: NovelBookSpine
        get() = bookModeRuntime.engineSpine

    internal val bookEngineLocation: NovelBookLocation
        get() = bookModeRuntime.location

    /**
     * Explicit renderer moves, the only way the core changes the reading position.
     *
     * The current position is never pushed back into the renderer: it flows upwards only. Resume,
     * the seek bar, the chapter picker, TTS and search publish a request here, the renderer applies
     * it once and acknowledges it through [onBookSeekApplied].
     */
    private val bookSeekRequestState = MutableStateFlow<BookSeekRequest?>(null)

    internal val bookSeekRequests: StateFlow<BookSeekRequest?> = bookSeekRequestState.asStateFlow()

    private var lastBookSeekRequestId = 0L

    /** Publishes a move for the renderer and keeps the runtime position in step with it. */
    private fun requestBookSeek(location: NovelBookLocation, reason: BookSeekReason) {
        val artifact = artifactSource
        val locator = artifact?.locatorOf(artifact.charOffsetOf(location)) ?: BookLocator.UNKNOWN
        lastBookSeekRequestId += 1
        bookSeekRequestState.value = BookSeekRequest(
            id = lastBookSeekRequestId,
            locator = locator,
            location = location,
            reason = reason,
        )
        logBookModeTrace {
            "seek request id=$lastBookSeekRequestId reason=$reason " +
                "section=${location.sectionIndex} offset=${location.charOffset}"
        }
    }

    /**
     * Acknowledgement from the renderer that a seek landed.
     *
     * The "restoring position" cover is dropped here, on the fact of the resume being applied,
     * instead of by a timer that could uncover the reader too early or too late.
     */
    fun onBookSeekApplied(seekRequestId: Long) {
        val request = bookSeekRequestState.value ?: return
        if (request.id != seekRequestId) return
        if (request.reason == BookSeekReason.Resume && bookModeRestoringPosition) {
            bookModeRestoringPosition = false
            refreshBookModeState()
        }
    }

    /** Snapshot of the book-mode UI state, merged into the reader state by the screen model. */
    fun bookModeUiState(): NovelReaderScreenModel.State.ReaderBookModeState =
        bookModeRuntime.uiState()

    internal suspend fun loadBookEngineDocument(section: NovelBookSection): NovelBookDocument {
        val document = bookModeRuntime.loadEngineDocument(section)
        scheduleBookEnginePrefetch(section.index)
        // Compiled-book sections come straight out of the artifact body, bypassing the section
        // pipeline where applyBookSectionTranslation lives, so translations were computed, cached
        // and then never shown. Sections that cover exactly one chapter can be translated safely;
        // sections straddling a chapter boundary are left untranslated rather than misaligning the
        // per-chapter block indices.
        val translated = if (artifactSource != null) {
            val chapters = artifactSource?.chaptersOfSection(section.index).orEmpty()
            if (chapters.size == 1) {
                host.bookApplyBookSectionTranslation(chapterId = chapters.first(), bodyHtml = document.html)
            } else {
                document.html
            }
        } else {
            document.html
        }
        return if (translated == document.html) {
            document
        } else {
            document.copy(html = translated)
        }
    }

    private var bookEnginePrefetchJob: kotlinx.coroutines.Job? = null

    private var lastBookEnginePrefetchSectionIndex = -1

    private var bookModeSyncJob: kotlinx.coroutines.Job? = null

    /** Set while a sync round runs, to request exactly one more round with the latest position. */
    private var bookModeSyncPending = false

    private var bookModeProgressJob: kotlinx.coroutines.Job? = null

    private var lastPersistedBookModeSectionIndex = -1

    /** Whole-book offset this session started from; earlier chapters are not this session's doing. */
    private var bookModeSessionStartCharOffset = 0

    /** Chapter under the reading caret, tracked to notice chapter changes inside one block. */
    private var lastBookModeChapterId: Long? = null

    /** Mirrors [NovelReaderScreenModel.State.ReaderBookModeState.isRestoringPosition] for the UI snapshot. */
    private var bookModeRestoringPosition = false

    /** Chapters this book-mode session already reported as read, to avoid duplicate write-through. */
    private val bookModeMarkedReadChapterIds = mutableSetOf<Long>()

    private var lastBookModeFailureLogAtMs = 0L

    /** Idle delay before a book-mode position is written through the progress pipeline. */
    private val bookModeProgressDebounceMs = 1_500L

    /** Minimum gap between book-mode failure logs, so a failing source cannot flood logcat. */
    private val bookModeFailureLogThrottleMs = 5_000L

    /**
     * Gate for book-mode trace logging.
     *
     * The trace used to be pinned on, so every append/prune round was written to logcat even in
     * release builds. It is now driven by a hidden debug preference and read on each call, which
     * makes it possible to turn tracing on for one reproduction without rebuilding the app.
     */
    private val bookModeTraceLoggingEnabled: Boolean
        get() = novelReaderPreferences.bookModeTraceLogging().get()

    /** True when the reader should present the whole novel as one continuous document. */
    fun isBookModeEnabled(): Boolean = artifactSource != null

    /** True while the book runtime is active (a spine is loaded and being read). */
    fun isBookRuntimeActive(): Boolean = bookModeRuntime.isActive

    /**
     * Starts book mode for the currently loaded chapter. Suspending: called from the chapter load
     * path, which already runs on the screen model scope.
     */
    suspend fun startForChapter(chapter: NovelChapter) {
        maybeStartBookMode(chapter)
    }

    /**
     * Re-renders a book section whose translation changed.
     *
     * Sections are cached once they are rendered, so a translation that finishes after the section
     * was mounted would otherwise only appear after leaving and re-entering the book.
     */
    fun refreshSectionAfterTranslation(chapterId: Long) {
        if (!bookModeRuntime.isActive) return
        // A chapter can cover several sections of a compiled book, so every section holding this
        // chapter's text has to be rebuilt, not just the first one.
        val sectionKeys = bookModeRuntime.engineSpine.sections
            .filter { it.covers(chapterId) }
            .map { it.loaderKey }
        if (sectionKeys.isEmpty()) return
        host.bookScope.launch {
            sectionKeys.forEach { sectionKey ->
                runCatching { bookModeRuntime.retrySection(sectionKey) }
            }
            onBookModeDocumentReady()
        }
    }

    /** Watches the reading-mode preference so book mode can be switched on without a reload. */
    private var readingModeJob: kotlinx.coroutines.Job? = null

    fun observeReadingModeChanges(loadedChapter: NovelChapter) {
        readingModeJob?.cancel()
        readingModeJob = host.bookScope.launch {
            novelReaderPreferences.readingMode().changes()
                .distinctUntilChanged()
                .collect {
                    // The global book reading mode is gone: only the per-title artifact decides.
                    if (isBookModeEnabled() == bookModeRuntime.isActive) return@collect
                    if (isBookModeEnabled()) {
                        maybeStartBookMode(host.bookCurrentChapter() ?: loadedChapter)
                    } else {
                        bookEnginePrefetchJob?.cancel()
                        lastBookEnginePrefetchSectionIndex = -1
                        bookModeRuntime.stop()
                        bookModeCommandQueue.clear()
                    }
                    refreshBookModeState()
                }
        }
    }

    /**
     * Rebuilds the book spine when the full chapter list arrives after the reader was already
     * opened with a narrower window. The spine is rebuilt only when its section count no longer
     * matches the full list.
     */
    fun rebuildSpineIfNeeded(chapter: NovelChapter, fullList: List<NovelChapter>) {
        if (!bookModeRuntime.isActive || fullList.isEmpty()) return
        if (bookModeRuntime.uiState().sectionCount == fullList.size) return
        host.bookScope.launch {
            maybeStartBookMode(chapter)
            refreshBookModeState()
        }
    }

    /**
     * Builds the native block stream of an older book in the background.
     *
     * The book stays fully readable while this runs: sections keep being parsed on the fly until the
     * stream is ready. Only then is the artifact re-opened, so the sections rendered after that point
     * come from pre-compiled blocks. Nothing is re-downloaded and no offset changes, which is why
     * swapping the source mid-read is safe.
     */
    private fun scheduleNativeBookStreamUpgrade() {
        val novel = host.bookCurrentNovel() ?: return
        val source = artifactSource ?: return
        if (source.hasNativeBlocks) return
        host.bookScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val migrated = runCatching {
                eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder().ensureNativeStream(novel)
            }.getOrDefault(false)
            if (!migrated || host.bookCurrentNovel()?.id != novel.id) return@launch
            val upgraded = runCatching {
                NovelBookArtifactSource.open(
                    directory = eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact.directoryFor(
                        root = eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder
                            .defaultRootDirectory(),
                        sourceId = novel.source,
                        novelId = novel.id,
                    ),
                )
            }.getOrNull() ?: return@launch
            if (host.bookCurrentNovel()?.id == novel.id) {
                artifactSource = upgraded
                logcat(LogPriority.INFO) { "Native book stream ready: novel=${novel.id}" }
            }
        }
    }

    /** Starts book mode for the currently loaded chapter, or keeps the runtime inert otherwise. */
    private suspend fun maybeStartBookMode(chapter: NovelChapter) {
        // Phase-0 baseline metric: wall-clock cost of opening a book, from the artifact lookup to
        // the queued resume scroll. Compared against the recorded baseline after each phase.
        val startedAtMs = System.currentTimeMillis()
        val novel = host.bookCurrentNovel()
        val bookState = novel?.let { getNovelBookState.await(it.id) }
        bookStateVersion = bookState?.bookVersion
        artifactSource = if (bookState?.enabled == true) {
            novel?.let {
                NovelBookArtifactSource.open(
                    directory = eu.kanade.tachiyomi.data.book.novel.NovelBookArtifact.directoryFor(
                        root = eu.kanade.tachiyomi.data.book.novel.NovelBookBuilder
                            .defaultRootDirectory(),
                        sourceId = novel.source,
                        novelId = novel.id,
                    ),
                )
            }
        } else {
            null
        }
        scheduleNativeBookStreamUpgrade()
        if (!isBookModeEnabled()) {
            if (bookModeRuntime.isActive) {
                bookEnginePrefetchJob?.cancel()
                lastBookEnginePrefetchSectionIndex = -1
                bookModeRuntime.stop()
            }
            return
        }
        val chapters = host.bookFullChapterOrderList().ifEmpty { host.bookChapterOrderList() }
        if (chapters.isEmpty()) return
        // Book mode only ever runs over a compiled artifact: the entry point is hidden until the
        // book is built, and the early return above already left book mode when there is none.
        val artifact = artifactSource ?: return
        // Opening the chapter the stored position belongs to means "continue reading", so the
        // exact offset is restored. Any other chapter comes from a table-of-contents tap and
        // must land on that chapter's first character instead.
        // Stored position as a locator: the chapter plus the offset inside it survives a rebuild of
        // the artifact, while the whole-book offset alone would silently shift by every character
        // added or removed before it.
        val storedLocator = bookState?.let { state ->
            state.lastChapterId?.let { lastChapterId ->
                BookLocator(
                    chapterId = lastChapterId,
                    blockIndex = state.blockIndex,
                    charOffset = state.chapterCharOffset,
                )
            }
        }
        val storedLocation = storedLocator
            ?.let { artifact.locationOf(it) }
            ?: artifact.locationOf((bookState?.charOffset ?: 0L).toInt())
        val resumeLocation = if (bookState?.lastChapterId == chapter.id) {
            storedLocation
        } else {
            artifact.locationOfChapter(chapter.id) ?: storedLocation
        }
        bookModeRuntime.startWithSpine(
            spine = artifact.spine,
            resumeLocation = resumeLocation,
            windowConfig = NovelBookWindowConfig.forBlockChars(
                eu.kanade.tachiyomi.data.book.novel.NovelBookBlockPlanner.DEFAULT_TARGET_CHARS,
            ),
            fetchSectionHtml = { sectionKey ->
                artifact.documentFor(sectionKey.toInt())?.html.orEmpty()
            },
        )
        bookEnginePrefetchJob?.cancel()
        lastBookEnginePrefetchSectionIndex = -1
        bookModeCommandQueue.clear()
        val resumeState = bookModeRuntime.uiState()
        val resumedLocation = bookModeRuntime.location
        bookModeRestoringPosition = resumedLocation.sectionIndex > 0 || resumedLocation.charOffset > 0
        // The renderer is told to move exactly once, and the cover above it stays until that move
        // is acknowledged. No timer decides when the resume is "probably" done anymore.
        requestBookSeek(resumedLocation, BookSeekReason.Resume)
        bookModeSessionStartCharOffset = artifactSource
            ?.let { it.chapterStartAt(it.charOffsetOf(resumedLocation)) }
            ?: 0
        lastPersistedBookModeSectionIndex = resumedLocation.sectionIndex
        lastBookModeChapterId = bookModeChapterAtReadingPosition()?.id
        bookModeMarkedReadChapterIds.clear()
        // The native renderer has no document-ready hook to place the reader, so the resume position
        // is queued as a regular scroll command. Both renderers therefore open the book where the
        // reader left off instead of at the top of the resident window.
        bookModeCommandQueue.enqueueScrollTo(
            sectionIndex = resumeState.currentSectionIndex,
            sectionFraction = resumeState.currentSectionFraction,
        )
        logcat(LogPriority.INFO) {
            "Book mode started: sections=${resumeState.sectionCount}, " +
                "resumeSection=${resumeState.currentSectionIndex}, " +
                "resumeFraction=${resumeState.currentSectionFraction}, chapterId=${chapter.id}"
        }
        logBookModeMetric(
            "open novelId=${novel?.id} sections=${resumeState.sectionCount} " +
                "source=artifact " +
                "v2=${novelReaderPreferences.novelBookModeV2().get()} " +
                "durationMs=${System.currentTimeMillis() - startedAtMs}",
        )
    }

    /**
     * Rebuilds the whole book document after the reader (re)loaded it.
     *
     * The reader document is loaded asynchronously, so every piece of DOM work that book mode had
     * already executed was thrown away when the fresh document committed. The reader calls this once
     * per loaded document.
     */
    fun onBookModeDocumentReady() {
        if (!bookModeRuntime.isActive) return
        bookModeCommandQueue.clear()
        bookModeRuntime.forgetRenderedSections()
        val current = bookModeRuntime.uiState()
        // The legacy WebView used to be re-seeded with one placeholder per section here; the book
        // engine and the native list rebuild their resident window from the sync round instead, so
        // only the reading position is re-applied.
        bookModeCommandQueue.enqueueScrollTo(
            sectionIndex = current.currentSectionIndex,
            sectionFraction = current.currentSectionFraction,
        )
        logcat(LogPriority.INFO) {
            "Book mode document ready: section=${current.currentSectionIndex}, " +
                "fraction=${current.currentSectionFraction}"
        }
        scheduleBookModeSync()
    }

    /**
     * Runs book-mode rounds strictly one at a time: a round queues the sections to append/prune for
     * the current position and prepares the sections around it.
     */
    private fun scheduleBookModeSync() {
        if (!bookModeRuntime.isActive) return
        if (bookModeSyncJob?.isActive == true) {
            bookModeSyncPending = true
            return
        }
        bookModeSyncJob = host.bookScope.launch {
            do {
                val replan = runBookModeSyncRound()
            } while (replan && bookModeRuntime.isActive)
            refreshBookModeState()
        }
    }

    /** One planning round. Returns true when a section finished preparing during the round. */
    private suspend fun runBookModeSyncRound(): Boolean {
        bookModeSyncPending = false
        runCatching {
            bookModeRuntime.sync(
                renderSection = { section, html ->
                    bookModeCommandQueue.enqueueAppend(sectionIndex = section.index, html = html)
                    logBookModeTrace { "append section=${section.index} chars=${html.length}" }
                },
                releaseSection = { section ->
                    bookModeCommandQueue.enqueuePrune(sectionIndex = section.index)
                    logBookModeTrace { "prune section=${section.index}" }
                },
                prepareSection = { section ->
                    val prepared = withContext(Dispatchers.IO) {
                        runCatching { bookModeRuntime.prepareSection(section.loaderKey) }
                            .onFailure { error ->
                                logBookModeFailure("prepare section ${section.index}", error)
                            }
                            .isSuccess
                    }
                    if (prepared) {
                        bookModeSyncPending = true
                    }
                },
            )
        }.onFailure { error ->
            logBookModeFailure("sync sections", error)
        }
        refreshBookModeState()
        return bookModeSyncPending
    }

    /** Throttled book-mode logging: repeated failures collapse into one entry per few seconds. */
    private fun logBookModeFailure(what: String, error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastBookModeFailureLogAtMs < bookModeFailureLogThrottleMs) return
        lastBookModeFailureLogAtMs = now
        logcat(LogPriority.WARN, error) { "Book mode failed to $what" }
    }

    /**
     * Debug-only book-mode trace, emitted under the structured [BOOK_MODE_LOG_TAG] tag so a whole
     * reading session can be filtered with `adb logcat -s NovelBook`.
     */
    private inline fun logBookModeTrace(message: () -> String) {
        if (!bookModeTraceLoggingEnabled) return
        logcat.logcat(priority = LogPriority.DEBUG, tag = BOOK_MODE_LOG_TAG) { message() }
    }

    /**
     * Book-mode metric line: always emitted (they are rare and one line each) under the same tag,
     * so the phase-0 baseline numbers can be collected from a release build.
     */
    private fun logBookModeMetric(message: String) {
        logcat.logcat(priority = LogPriority.INFO, tag = BOOK_MODE_LOG_TAG) { "metric $message" }
    }

    private fun refreshBookModeState() {
        host.bookUpdateSuccessState { successState ->
            // The prepare-book action is owned by the controller, so its progress is merged into the
            // runtime snapshot instead of adding a second state holder for the UI to read.
            val chapterCount = bookPrepareTotalCount.takeIf { it > 0 }
                ?: host.bookFullChapterOrderList().ifEmpty { host.bookChapterOrderList() }.size
            successState.copy(
                bookMode = bookModeRuntime.uiState().copy(
                    isPreparingWholeBook = bookPrepareRunning,
                    preparedChapterCount = bookPreparedChapterCount,
                    totalChapterCount = chapterCount,
                    isRestoringPosition = bookModeRestoringPosition,
                ),
            )
        }
    }

    /** Reports the reader position inside the book after a scroll update from the WebView. */
    fun onBookModeScroll(sectionIndex: Int, sectionFraction: Float) {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.moveTo(sectionIndex = sectionIndex, sectionFraction = sectionFraction)
        scheduleBookModeSync()
        refreshBookModeState()
        onBookModePositionChanged(sectionFraction)
    }

    /** Receives the dedicated renderer's exact section/text location. */
    fun onBookEngineLocationChanged(location: NovelBookLocation) {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.moveTo(location)
        refreshBookModeState()
        val currentLocation = bookModeRuntime.location
        scheduleBookEnginePrefetch(currentLocation.sectionIndex)
        onBookModePositionChanged(bookModeRuntime.uiState().currentSectionFraction)
    }

    /**
     * Turns a new reading position into read marking and persistence.
     *
     * Both renderers funnel through here so the rules cannot drift apart.
     */
    private fun onBookModePositionChanged(sectionFraction: Float) {
        val location = bookModeRuntime.location
        val chapterId = bookModeChapterAtReadingPosition()?.id
        val chapterChanged = chapterId != null && chapterId != lastBookModeChapterId
        if (chapterChanged) {
            lastBookModeChapterId = chapterId
            host.bookAdoptBookModeChapter(chapterId)
            markBookModeCrossedChaptersRead()
        } else if (location.sectionIndex != lastPersistedBookModeSectionIndex) {
            markBookModeCrossedChaptersRead()
        }
        if (chapterChanged) {
            // Crossing into another chapter is a checkpoint worth surviving a kill, so the position
            // is written immediately instead of waiting out the debounce window.
            flushBookModeProgress()
            lastPersistedBookModeSectionIndex = location.sectionIndex
            return
        }
        scheduleBookModeProgressPersistence(
            sectionIndex = location.sectionIndex,
            sectionFraction = sectionFraction,
        )
    }

    /**
     * Writes the current position through immediately, cancelling the pending debounce.
     *
     * Called from the lifecycle's ON_STOP, when TTS takes over, when the reader is left and when the
     * reader crosses into another chapter. The 1.5s debounce is fine while reading, but any of these
     * moments can be the last one before the process goes away.
     */
    fun flushBookModeProgress() {
        if (!bookModeRuntime.isActive) return
        bookModeProgressJob?.cancel()
        persistBookModeProgress(bookModeRuntime.uiState().currentSectionFraction)
    }

    fun seekBookModeToProgress(fraction: Float) {
        if (!bookModeRuntime.isActive) return
        val artifact = artifactSource
        val location = if (artifact != null) {
            val charOffset = (fraction.coerceIn(0f, 1f) * artifact.totalChars).toInt()
            artifact.locationOf(charOffset)
        } else {
            val totalSections = bookModeRuntime.uiState().sectionCount
            val sectionIndex = (fraction.coerceIn(0f, 1f) * (totalSections - 1).coerceAtLeast(0)).toInt()
            NovelBookLocation(sectionIndex = sectionIndex, charOffset = 0)
        }
        // A seek is an explicit move: the runtime follows the request, and the renderer is asked to
        // go there once. It is not a reported position, so it never loops back.
        bookModeRuntime.moveTo(location)
        requestBookSeek(location, BookSeekReason.Seekbar)
        refreshBookModeState()
        scheduleBookEnginePrefetch(location.sectionIndex)
        onBookModePositionChanged(bookModeRuntime.uiState().currentSectionFraction)
    }

    private fun scheduleBookEnginePrefetch(sectionIndex: Int) {
        if (!bookModeRuntime.isActive || sectionIndex == lastBookEnginePrefetchSectionIndex) return
        lastBookEnginePrefetchSectionIndex = sectionIndex
        bookEnginePrefetchJob?.cancel()
        bookEnginePrefetchJob = host.bookScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { bookModeRuntime.prefetchAround(sectionIndex) }
            }
            result.onFailure { error ->
                logBookModeFailure("prefetch around section $sectionIndex", error)
            }
            refreshBookModeState()
        }
    }

    /** Chapter that currently sits under the reading position over a book artifact. */
    fun bookModeChapterAtReadingPosition(): NovelChapter? = artifactSource
        ?.takeIf { bookModeRuntime.isActive }
        ?.let { artifact ->
            val chapterId = artifact.chapterAt(artifact.charOffsetOf(bookModeRuntime.location))?.chapterId
            chapterId?.let { id ->
                host.bookChapterOrderList().firstOrNull { it.id == id }
                    ?: host.bookFullChapterOrderList().firstOrNull { it.id == id }
            }
        }

    private fun markBookModeCrossedChaptersRead() {
        if (!bookModeRuntime.isActive) return
        val alreadyRead = buildSet {
            addAll(bookModeMarkedReadChapterIds)
            host.bookChapterOrderList().forEach { if (it.read) add(it.id) }
        }
        // A section is a fixed-size block, not a chapter, so "the section was read" cannot mark a
        // chapter read. Crossed chapters are derived from the whole-book character offset instead,
        // which is the exact same signal the position is persisted from.
        val artifact = artifactSource
        val crossedChapterIds = if (artifact != null) {
            val charOffset = artifact.charOffsetOf(bookModeRuntime.location)
            val crossed = artifact
                .chaptersFullyReadBetween(
                    fromCharOffset = bookModeSessionStartCharOffset,
                    toCharOffset = charOffset,
                )
                .filterNot { it in alreadyRead }
                .toMutableList()
            // The chapter under the caret is still being read, but it counts as read once the
            // reader is past the same threshold the per-chapter reader uses (90%). Without this,
            // a chapter the reader stopped in the middle of stayed unread until 99% of the WHOLE
            // book, and "novel completed" / the series interstitial never fired.
            artifact.chapterAt(charOffset)?.let { current ->
                val progressInside = if (current.charLength > 0) {
                    ((charOffset - current.charStart).toFloat() / current.charLength.toFloat())
                } else {
                    0f
                }
                if (progressInside >= BOOK_MODE_READ_THRESHOLD &&
                    current.chapterId !in alreadyRead
                ) {
                    crossed += current.chapterId
                }
            }
            crossed
        } else {
            emptyList()
        }
        if (crossedChapterIds.isEmpty()) return
        crossedChapterIds.forEach { chapterId ->
            val chapter = host.bookChapterOrderList().firstOrNull { it.id == chapterId }
                ?: host.bookFullChapterOrderList().firstOrNull { it.id == chapterId }
                ?: return@forEach
            bookModeMarkedReadChapterIds += chapterId
            val becameRead = !chapter.read
            host.bookMarkChapterReadInMemory(chapterId)
            host.bookEnqueueProgressPersistence(
                PendingProgressPersistence(
                    chapterId = chapter.id,
                    novelId = chapter.novelId,
                    chapterNumber = chapter.chapterNumber.toInt(),
                    read = true,
                    // The book position never leaks into the chapter row: `lastPageRead` belongs to
                    // the chapter-by-chapter reader, and book mode resumes from its own locator.
                    lastPageRead = chapter.lastPageRead,
                    emitReadEvent = becameRead,
                    emitNovelCompleted = becameRead &&
                        host.bookChapterOrderList().all { it.read },
                    sessionReadDurationMs = 0L,
                ),
            )
        }
    }

    /**
     * Writes the position through the progress pipeline at most once per idle window, and right away
     * when the reader crosses into another section.
     */
    private fun scheduleBookModeProgressPersistence(sectionIndex: Int, sectionFraction: Float) {
        bookModeProgressJob?.cancel()
        if (sectionIndex != lastPersistedBookModeSectionIndex) {
            lastPersistedBookModeSectionIndex = sectionIndex
            persistBookModeProgress(sectionFraction)
            return
        }
        bookModeProgressJob = host.bookScope.launch {
            kotlinx.coroutines.delay(bookModeProgressDebounceMs)
            persistBookModeProgress(sectionFraction)
        }
    }

    /**
     * Persists the reading position as a book locator so the reader can resume anywhere in the
     * novel.
     *
     * There is exactly one writer of the book position now: the artifact state row. The chapter row
     * is deliberately not touched here, because a percentage of a whole book cannot be expressed as
     * a page index of a single chapter, and that round trip is what made progress jump. The "read"
     * flag stays with [markBookModeCrossedChaptersRead] and its per-chapter threshold.
     */
    private fun persistBookModeProgress(@Suppress("UNUSED_PARAMETER") sectionFraction: Float) {
        persistBookArtifactProgress()
    }

    /**
     * Stores the whole-book position of a compiled book as a global character offset.
     *
     * Blocks are not chapters, so the per-chapter progress row cannot describe where the reader is
     * inside the book; the artifact state row keeps the exact offset used to resume.
     */
    private fun persistBookArtifactProgress() {
        val artifact = artifactSource ?: return
        val novelId = host.bookCurrentNovel()?.id ?: return
        val charOffset = artifact.charOffsetOf(bookModeRuntime.location)
        val locator = artifact.locatorOf(charOffset)
        host.bookScope.launch {
            setNovelBookProgress.await(
                novelId = novelId,
                // Whole-book offset stays stored for the progress bar and the library; the locator
                // is what the reader actually resumes from.
                charOffset = charOffset.toLong(),
                lastChapterId = locator.chapterId.takeIf { it != BookLocator.NO_CHAPTER_ID },
                blockIndex = locator.blockIndex,
                chapterCharOffset = locator.charOffset,
            )
        }
    }

    /** Jumps to a chapter without leaving the continuous document. */
    fun onBookModeChapterSelected(chapterId: Long): Boolean {
        if (!bookModeRuntime.moveToChapter(chapterId)) return false
        requestBookSeek(bookModeRuntime.location, BookSeekReason.TableOfContents)
        refreshBookModeState()
        return true
    }

    fun onBookModeCommandsExecuted(commandIds: List<Long>) {
        bookModeCommandQueue.ack(commandIds)
    }

    /**
     * Retries a section that failed to load, e.g. after a network drop.
     */
    fun onBookModeRetrySection(sectionIndex: Int) {
        if (!bookModeRuntime.isActive) return
        val section = bookModeRuntime.engineSpine.sectionAt(sectionIndex) ?: return
        host.bookScope.launch {
            val retried = withContext(Dispatchers.IO) {
                runCatching { bookModeRuntime.retrySection(section.loaderKey) }
                    .onFailure { error -> logBookModeFailure("retry section $sectionIndex", error) }
                    .isSuccess
            }
            if (retried) {
                scheduleBookModeSync()
            }
            refreshBookModeState()
        }
    }

    private var bookPrepareJob: Job? = null

    private var bookPrepareRunning: Boolean = false

    private var bookPreparedChapterCount: Int = 0

    private var bookPrepareTotalCount: Int = 0

    /**
     * Prepares every chapter of the book, so the whole novel can be read offline without waiting for
     * the sliding window. Sections are prepared one by one to stay friendly to source rate limits.
     */
    fun prepareWholeBook() {
        if (!bookModeRuntime.isActive || bookPrepareJob?.isActive == true) return
        // Sections, not chapters: the unit that gets prepared and cached is a spine section, and
        // walking chapters prepared the wrong things (and the wrong count) over a compiled book.
        val sections = bookModeRuntime.engineSpine.sections
        if (sections.isEmpty()) return
        bookPrepareTotalCount = sections.size
        bookPreparedChapterCount = 0
        bookPrepareRunning = true
        refreshBookModeState()
        bookPrepareJob = host.bookScope.launch(Dispatchers.IO) {
            try {
                sections.forEach { section ->
                    runCatching { bookModeRuntime.prepareSection(section.loaderKey) }
                        .onFailure { error ->
                            logBookModeFailure("prepare section ${section.index}", error)
                        }
                    bookPreparedChapterCount += 1
                    refreshBookModeState()
                }
            } finally {
                bookPrepareRunning = false
                refreshBookModeState()
            }
        }
    }

    /**
     * Flushes the debounced book-mode position and tears the runtime down. Called from the screen
     * model's disposal: leaving the reader mid-section would otherwise lose up to one debounce
     * window of reading progress, and chapters the reader scrolled past would never be marked read.
     */
    fun flushAndStop() {
        readingModeJob?.cancel()
        readingModeJob = null
        if (bookModeRuntime.isActive) {
            markBookModeCrossedChaptersRead()
            persistBookModeProgress(bookModeRuntime.uiState().currentSectionFraction)
        }
        bookModeSyncJob?.cancel()
        bookModeProgressJob?.cancel()
        bookEnginePrefetchJob?.cancel()
        bookPrepareJob?.cancel()
        bookModeSyncPending = false
        lastPersistedBookModeSectionIndex = -1
        bookSeekRequestState.value = null
        bookModeRestoringPosition = false
        bookModeSessionStartCharOffset = 0
        lastBookModeChapterId = null
        bookModeMarkedReadChapterIds.clear()
        bookModeRuntime.stop()
        bookModeCommandQueue.clear()
    }

    companion object {
        /**
         * Structured logcat tag for every book-mode trace and metric line.
         *
         * One tag for the whole subsystem means a session can be captured with
         * `adb logcat -s NovelBook` instead of grepping the "Book mode: " prefix out of the
         * app-wide log.
         */
        const val BOOK_MODE_LOG_TAG = "NovelBook"

        /**
         * Share of a chapter that has to be passed before book mode marks it read.
         *
         * It mirrors the per-chapter reader's threshold: without it a chapter the reader stopped in
         * the middle of stayed unread until almost the whole book was finished.
         */
        private const val BOOK_MODE_READ_THRESHOLD = 0.9f
    }
}
