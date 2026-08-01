package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    fun bookUpdateReadingProgress(currentIndex: Int, totalItems: Int, persistedProgress: Long?)
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
    private var bookModeResumeGuardJob: kotlinx.coroutines.Job? = null

    private var lastPersistedBookModeSectionIndex = -1

    /** Position this book session resumed at, kept until the renderer actually reports it. */
    private var bookModeResumeLocation: NovelBookLocation? = null

    /** Hard expiry for the resume guard, so a never-arriving resume cannot freeze persistence. */
    private var bookModeResumeGuardUntilMs = 0L

    /** Whole-book offset this session started from; earlier chapters are not this session's doing. */
    private var bookModeSessionStartCharOffset = 0

    /** Chapter under the reading caret, tracked to notice chapter changes inside one block. */
    private var lastBookModeChapterId: Long? = null

    /** Mirrors [NovelReaderScreenModel.State.ReaderBookModeState.isRestoringPosition] for the UI snapshot. */
    private var bookModeRestoringPosition = false

    /** Slack when comparing a reported position with the resume position. */
    private val bookModeResumeToleranceChars = 400

    /** Upper bound for the resume guard window. */
    private val bookModeResumeGuardMillis = 1_200L

    /** Chapters this book-mode session already reported as read, to avoid duplicate write-through. */
    private val bookModeMarkedReadChapterIds = mutableSetOf<Long>()

    private var lastBookModeFailureLogAtMs = 0L

    /** Idle delay before a book-mode position is written through the progress pipeline. */
    private val bookModeProgressDebounceMs = 1_500L

    /** Minimum gap between book-mode failure logs, so a failing source cannot flood logcat. */
    private val bookModeFailureLogThrottleMs = 5_000L

    /** Gate for book-mode trace logging. */
    private val bookModeTraceLoggingEnabled = true

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
        host.bookScope.launch {
            runCatching { bookModeRuntime.retryChapter(chapterId) }
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
        val artifact = artifactSource
        if (artifact != null) {
            // Opening the chapter the stored position belongs to means "continue reading", so the
            // exact offset is restored. Any other chapter comes from a table-of-contents tap and
            // must land on that chapter's first character instead.
            val storedLocation = artifact.locationOf((bookState?.charOffset ?: 0L).toInt())
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
                    artifact.documentFor(-(sectionKey.toInt() + 1))?.html.orEmpty()
                },
            )
        } else {
            bookModeRuntime.start(
                chapters = chapters,
                resumeProgress = chapter.lastPageRead,
                resumeChapterId = chapter.id,
            )
        }
        bookEnginePrefetchJob?.cancel()
        lastBookEnginePrefetchSectionIndex = -1
        bookModeCommandQueue.clear()
        val resumeState = bookModeRuntime.uiState()
        val resumedLocation = bookModeRuntime.location
        bookModeResumeLocation = resumedLocation
        bookModeResumeGuardUntilMs = System.currentTimeMillis() + bookModeResumeGuardMillis
        bookModeRestoringPosition = resumedLocation.sectionIndex > 0 || resumedLocation.charOffset > 0
        bookModeResumeGuardJob?.cancel()
        if (bookModeRestoringPosition) {
            bookModeResumeGuardJob = host.bookScope.launch {
                kotlinx.coroutines.delay(bookModeResumeGuardMillis)
                releaseBookModeResumeGuard()
            }
        }
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
                        runCatching { bookModeRuntime.prepareChapter(section.chapterId) }
                            .onFailure { error ->
                                logBookModeFailure("prepare section ${section.chapterId}", error)
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

    /** Debug-only book-mode trace. */
    private inline fun logBookModeTrace(message: () -> String) {
        if (!bookModeTraceLoggingEnabled) return
        logcat(LogPriority.DEBUG) { "Book mode: ${message()}" }
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
        if (isBookModeResumeEcho(location)) return
        val chapterId = bookModeChapterAtReadingPosition()?.id
        if (chapterId != null && chapterId != lastBookModeChapterId) {
            lastBookModeChapterId = chapterId
            host.bookAdoptBookModeChapter(chapterId)
            markBookModeCrossedChaptersRead()
        } else if (location.sectionIndex != lastPersistedBookModeSectionIndex) {
            markBookModeCrossedChaptersRead()
        }
        scheduleBookModeProgressPersistence(
            sectionIndex = location.sectionIndex,
            sectionFraction = sectionFraction,
        )
    }

    /** True while the renderer is still reporting positions from before the resume point. */
    private fun isBookModeResumeEcho(location: NovelBookLocation): Boolean {
        val pending = bookModeResumeLocation ?: return false
        if (System.currentTimeMillis() > bookModeResumeGuardUntilMs) {
            releaseBookModeResumeGuard()
            return false
        }
        val artifact = artifactSource
        val reached = if (artifact != null) {
            artifact.charOffsetOf(location) + bookModeResumeToleranceChars >=
                artifact.charOffsetOf(pending)
        } else {
            location.sectionIndex > pending.sectionIndex ||
                (
                    location.sectionIndex == pending.sectionIndex &&
                        location.charOffset + bookModeResumeToleranceChars >= pending.charOffset
                    )
        }
        if (!reached) return true
        releaseBookModeResumeGuard()
        return false
    }

    /** Drops the resume guard and uncovers the reader once the saved position is on screen. */
    private fun releaseBookModeResumeGuard() {
        bookModeResumeGuardJob?.cancel()
        bookModeResumeGuardJob = null
        bookModeResumeLocation = null
        if (bookModeRestoringPosition) {
            bookModeRestoringPosition = false
            refreshBookModeState()
        }
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
        onBookEngineLocationChanged(location)
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

    /** Replaces a section estimate with the dedicated renderer's stabilized DOM text length. */
    fun onBookEngineSectionMeasured(chapterId: Long, charCount: Int) {
        if (!bookModeRuntime.isActive || charCount <= 0) return
        bookModeRuntime.measureSection(chapterId = chapterId, charCount = charCount)
        refreshBookModeState()
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
        // Over an artifact the spine sections are fixed-size blocks with synthetic ids, so the
        // spine's read-marking policy cannot name real chapters. Crossed chapters are derived from
        // the whole-book character offset instead, which is the exact same signal the position is
        // persisted from.
        val artifact = artifactSource
        val activeChapter = bookModeChapterAtReadingPosition() ?: host.bookCurrentChapter()
        val activeChapterId = activeChapter?.id
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
                if (progressInside >= NovelBookReadMarkingPolicy.DEFAULT_READ_THRESHOLD &&
                    current.chapterId !in alreadyRead
                ) {
                    crossed += current.chapterId
                }
            }
            crossed
        } else {
            // The live spine's policy already includes the current section once it passes the
            // threshold, so the current chapter must not be filtered out below.
            bookModeRuntime.chaptersToMarkRead(alreadyRead)
        }
        if (crossedChapterIds.isEmpty()) return
        val encodedProgress = bookModeRuntime.encodedProgress()
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
                    // The chapter under the caret keeps its book location so a later resume lands
                    // on the same text; fully passed chapters keep their own stored progress.
                    lastPageRead = if (chapterId == activeChapterId) {
                        encodedProgress ?: chapter.lastPageRead
                    } else {
                        chapter.lastPageRead
                    },
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
     * Persists the reading position as a book location so the reader can resume anywhere in the
     * novel.
     *
     * The global percent is used for the position only. The "read" flag of the chapter under the
     * caret is NOT derived from the whole-book percentage (that would require 99% of the entire
     * book); [markBookModeCrossedChaptersRead] owns the read flag with the per-chapter threshold.
     */
    private fun persistBookModeProgress(sectionFraction: Float) {
        persistBookArtifactProgress()
        val encodedProgress = bookModeRuntime.encodedProgress() ?: return
        val artifact = artifactSource
        val globalPercent = if (artifact != null) {
            val charOffset = artifact.charOffsetOf(bookModeRuntime.location)
            (artifact.progressOf(charOffset) * 100f).toInt().coerceIn(0, 100)
        } else {
            (sectionFraction * 100f).toInt().coerceIn(0, 100)
        }
        host.bookUpdateReadingProgress(
            currentIndex = globalPercent,
            totalItems = 100,
            persistedProgress = encodedProgress,
        )
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
        val chapterId = artifact.chapterAt(charOffset)?.chapterId
        host.bookScope.launch {
            setNovelBookProgress.await(
                novelId = novelId,
                charOffset = charOffset.toLong(),
                lastChapterId = chapterId,
            )
        }
    }

    /** Records the pixel height the renderer measured for a section. */
    fun onBookModeSectionMeasured(chapterId: Long, heightPx: Int) {
        if (!bookModeRuntime.isActive) return
        bookModeRuntime.measureSectionLayoutHeight(chapterId = chapterId, heightPx = heightPx)
    }

    /** Jumps to a chapter without leaving the continuous document. */
    fun onBookModeChapterSelected(chapterId: Long): Boolean {
        if (!bookModeRuntime.moveToChapter(chapterId)) return false
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
        val chapterId = bookModeRuntime.chapterIdOfSection(sectionIndex) ?: return
        host.bookScope.launch {
            val retried = withContext(Dispatchers.IO) {
                runCatching { bookModeRuntime.retryChapter(chapterId) }
                    .onFailure { error -> logBookModeFailure("retry section $chapterId", error) }
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
        val chapters = host.bookFullChapterOrderList().ifEmpty { host.bookChapterOrderList() }
        if (chapters.isEmpty()) return
        bookPrepareTotalCount = chapters.size
        bookPreparedChapterCount = 0
        bookPrepareRunning = true
        refreshBookModeState()
        bookPrepareJob = host.bookScope.launch(Dispatchers.IO) {
            try {
                chapters.forEach { chapter ->
                    runCatching { bookModeRuntime.prepareChapter(chapter.id) }
                        .onFailure { error ->
                            logBookModeFailure("prepare chapter ${chapter.id}", error)
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
        bookModeResumeGuardJob?.cancel()
        bookEnginePrefetchJob?.cancel()
        bookPrepareJob?.cancel()
        bookModeSyncPending = false
        lastPersistedBookModeSectionIndex = -1
        bookModeResumeLocation = null
        bookModeResumeGuardUntilMs = 0L
        bookModeRestoringPosition = false
        bookModeSessionStartCharOffset = 0
        lastBookModeChapterId = null
        bookModeMarkedReadChapterIds.clear()
        bookModeRuntime.stop()
        bookModeCommandQueue.clear()
    }
}
