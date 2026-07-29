package eu.kanade.tachiyomi.ui.reader.novel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of preparing a single book-mode section (one novel chapter).
 */
internal sealed interface NovelBookSectionResult {
    val chapterId: Long

    data class Ready(
        override val chapterId: Long,
        val html: String,
        val baseUrl: String? = null,
    ) : NovelBookSectionResult

    data class Failed(override val chapterId: Long, val message: String?) : NovelBookSectionResult
}

/**
 * Loads ready-to-insert section HTML for book mode.
 *
 * The loader is intentionally free of Android and repository dependencies: the caller supplies a
 * [fetchSectionHtml] lambda that resolves (download/network/cache) and normalizes a chapter into
 * reader-ready HTML. Results are stored in [store], which keeps a small resident set in memory and
 * the rest on disk.
 *
 * Concurrent requests for the same chapter share a single load, and the number of parallel loads is
 * capped so that prefetching never starves the section the reader is actually waiting for.
 */
internal class NovelBookSectionLoader(
    private val store: NovelBookSectionStore,
    private val maxConcurrentLoads: Int = DEFAULT_MAX_CONCURRENT_LOADS,
    private val fetchSectionBaseUrl: (Long) -> String? = { null },
    private val fetchSectionHtml: suspend (Long) -> String,
) {
    private val semaphore = Semaphore(maxConcurrentLoads.coerceAtLeast(1))
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<Long, CompletableDeferred<NovelBookSectionResult>>()
    private val failed = mutableSetOf<Long>()

    val inFlightChapterIds: Set<Long>
        get() = inFlight.keys.toSet()

    val failedChapterIds: Set<Long>
        get() = failed.toSet()

    fun isPrepared(chapterId: Long): Boolean = store.isPrepared(chapterId)

    fun preparedHtml(chapterId: Long): String? = store.get(chapterId)

    fun release(chapterId: Long) {
        store.release(chapterId)
    }

    fun clear() {
        store.clear()
        failed.clear()
    }

    /**
     * Ensures the section for [chapterId] is prepared and returns its HTML.
     *
     * Already prepared sections resolve immediately from memory or disk unless [forceReload] is set.
     */
    suspend fun prepare(chapterId: Long, forceReload: Boolean = false): NovelBookSectionResult {
        if (!forceReload) {
            val cached = store.getPrepared(chapterId)
            if (cached != null) {
                mutex.withLock { failed.remove(chapterId) }
                return NovelBookSectionResult.Ready(
                    chapterId = chapterId,
                    html = cached.html,
                    baseUrl = cached.baseUrl,
                )
            }
        }
        val pending = CompletableDeferred<NovelBookSectionResult>()
        val running = mutex.withLock {
            val current = inFlight[chapterId]
            if (current == null) {
                inFlight[chapterId] = pending
                failed.remove(chapterId)
            }
            current
        }
        if (running != null) return running.await()
        var result: NovelBookSectionResult? = null
        try {
            result = try {
                semaphore.withPermit {
                    val html = fetchSectionHtml(chapterId)
                    if (html.isBlank()) {
                        NovelBookSectionResult.Failed(chapterId, EMPTY_SECTION_MESSAGE)
                    } else {
                        val baseUrl = fetchSectionBaseUrl(chapterId)
                        store.put(chapterId, html, baseUrl = baseUrl)
                        NovelBookSectionResult.Ready(chapterId, html, baseUrl)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NovelBookSectionResult.Failed(chapterId, e.message)
            }
            return result
        } finally {
            val settled = result
            withContext(NonCancellable) {
                mutex.withLock {
                    inFlight.remove(chapterId)
                    when {
                        settled == null -> Unit
                        settled is NovelBookSectionResult.Failed -> failed.add(chapterId)
                        else -> failed.remove(chapterId)
                    }
                }
            }
            if (settled != null) {
                pending.complete(settled)
            } else {
                pending.cancel()
            }
        }
    }

    suspend fun retry(chapterId: Long): NovelBookSectionResult = prepare(chapterId, forceReload = true)

    companion object {
        const val DEFAULT_MAX_CONCURRENT_LOADS = 2
        const val EMPTY_SECTION_MESSAGE = "Chapter content is empty"
    }
}
