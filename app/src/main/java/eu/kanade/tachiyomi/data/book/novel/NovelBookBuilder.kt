package eu.kanade.tachiyomi.data.book.novel

import android.app.Application
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.book.novel.repository.NovelBookStateRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.coroutines.coroutineContext

/** Progress of a book build, reported per chapter so the UI can show a determinate bar. */
data class NovelBookBuildProgress(
    val phase: Phase,
    val done: Int,
    val total: Int,
) {
    enum class Phase { DOWNLOADING, MERGING }

    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Outcome of a build request. */
sealed interface NovelBookBuildOutcome {
    data class Built(val state: NovelBookState, val missingChapterIds: List<Long>) : NovelBookBuildOutcome
    data class MissingDownloads(val missingChapters: List<NovelChapter>) : NovelBookBuildOutcome
    data object NothingToBuild : NovelBookBuildOutcome
}

/**
 * Compiles the downloaded chapters of a novel into a single book artifact and records its state.
 *
 * The builder never fetches chapter text itself: chapters must already be downloaded, or
 * [downloadMissing] has to be set so the download manager can fill the gaps first. That keeps the
 * merge step deterministic and lets a build resume after a crash, because the artifact is written
 * chapter by chapter in source order and every offset is recorded in the index.
 */
class NovelBookBuilder(
    private val rootDirectory: File = defaultRootDirectory(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val repository: NovelBookStateRepository = Injekt.get(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Builds (or rebuilds) the book of [novel] from [chapters].
     *
     * @param chapters chapters in reading order; the caller decides the range to include.
     * @param downloadMissing when false, a build that would skip chapters returns
     *   [NovelBookBuildOutcome.MissingDownloads] instead so the caller can ask the user first.
     * @param enableAfterBuild marks the title as read-as-book once the artifact is ready.
     * @param loadHtml optional chapter text provider. Local books (.epub/.fb2) carry their own
     *   text, so when a provider is given the download checks are skipped entirely and the text is
     *   pulled straight from the file instead of the per-chapter download cache.
     */
    suspend fun build(
        novel: Novel,
        chapters: List<NovelChapter>,
        downloadMissing: Boolean = false,
        enableAfterBuild: Boolean = true,
        loadHtml: ((NovelBookSourceChapter) -> String?)? = null,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): NovelBookBuildOutcome {
        if (chapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val notDownloaded = if (loadHtml != null) {
            emptyList()
        } else {
            chapters.filterNot { downloadManager.isChapterDownloaded(novel, it.id) }
        }
        if (notDownloaded.isNotEmpty()) {
            if (!downloadMissing) {
                return NovelBookBuildOutcome.MissingDownloads(notDownloaded)
            }
            notDownloaded.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.DOWNLOADING,
                        done = index,
                        total = notDownloaded.size,
                    ),
                )
                runCatching { downloadManager.downloadChapter(novel, chapter) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) {
                            "Book build could not download chapter ${chapter.id} of novel ${novel.id}"
                        }
                    }
            }
        }

        val sourceChapters = chapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val directory = NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id)
        val writer = NovelBookArtifactWriter(directory)
        val request = NovelBookBuildRequest(
            sourceId = novel.source,
            novelId = novel.id,
            novelTitle = novel.title,
            chapterSetHash = NovelBookArtifact.chapterSetHash(sourceChapters),
            builtAt = now(),
        )

        val previous = repository.getBookState(novel.id)
        val result = writer.build(
            request = request,
            chapters = sourceChapters,
            loadHtml = { chapter ->
                loadHtml?.invoke(chapter)
                    ?: downloadManager.getDownloadedChapterText(novel, chapter.id)
            },
            onProgress = { done, total ->
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.MERGING,
                        done = done,
                        total = total,
                    ),
                )
            },
        )

        if (result.index.chapters.isEmpty()) {
            NovelBookArtifact.delete(directory)
            return NovelBookBuildOutcome.NothingToBuild
        }

        val timestamp = now()
        val state = NovelBookState(
            novelId = novel.id,
            enabled = enableAfterBuild || previous?.enabled == true,
            bookVersion = (previous?.bookVersion ?: 0L) + 1L,
            sourceId = novel.source,
            chapterSetHash = result.meta.chapterSetHash,
            totalChars = result.meta.totalChars.toLong(),
            chapterCount = result.meta.chapterCount,
            charOffset = 0L,
            lastChapterId = result.index.chapters.first().chapterId,
            complete = result.meta.complete,
            builtAt = result.meta.builtAt,
            updatedAt = timestamp,
        )
        repository.upsertBookState(state)

        logcat(LogPriority.INFO) {
            "Built novel book: novel=${novel.id}, chapters=${state.chapterCount}, " +
                "chars=${state.totalChars}, missing=${result.missingChapterIds.size}"
        }
        return NovelBookBuildOutcome.Built(state, result.missingChapterIds)
    }

    /**
     * Appends chapters that appeared after the last build, keeping every existing offset intact.
     *
     * A rebuild would invalidate the saved reading position, so new chapters are streamed to the end
     * of the body file and only the index, meta and chapter count change. The stored position and
     * the read-as-book flag are preserved.
     *
     * @param chapters the full chapter list in reading order; chapters already in the artifact are
     *   skipped, so only the tail is written.
     */
    suspend fun appendNewChapters(
        novel: Novel,
        chapters: List<NovelChapter>,
        downloadMissing: Boolean = false,
        onProgress: (NovelBookBuildProgress) -> Unit = {},
    ): NovelBookBuildOutcome {
        if (chapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val directory = NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id)
        val existingIndex = NovelBookArtifact.readIndex(directory)
        if (existingIndex == null || existingIndex.chapters.isEmpty()) {
            // Nothing to extend: fall back to a normal build so the button still does the obvious
            // thing when the artifact is missing.
            return build(
                novel = novel,
                chapters = chapters,
                downloadMissing = downloadMissing,
                enableAfterBuild = false,
                onProgress = onProgress,
            )
        }

        val known = existingIndex.chapters.map { entry -> entry.chapterId }.toSet()
        val newChapters = chapters.filterNot { chapter -> chapter.id in known }
        if (newChapters.isEmpty()) return NovelBookBuildOutcome.NothingToBuild

        val notDownloaded = newChapters.filterNot { downloadManager.isChapterDownloaded(novel, it.id) }
        if (notDownloaded.isNotEmpty()) {
            if (!downloadMissing) {
                return NovelBookBuildOutcome.MissingDownloads(notDownloaded)
            }
            notDownloaded.forEachIndexed { index, chapter ->
                coroutineContext.ensureActive()
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.DOWNLOADING,
                        done = index,
                        total = notDownloaded.size,
                    ),
                )
                runCatching { downloadManager.downloadChapter(novel, chapter) }
                    .onFailure { error ->
                        logcat(LogPriority.WARN, error) {
                            "Book append could not download chapter ${chapter.id} of novel ${novel.id}"
                        }
                    }
            }
        }

        val sourceChapters = chapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val newSourceChapters = newChapters.map { chapter ->
            NovelBookSourceChapter(id = chapter.id, name = chapter.name, url = chapter.url)
        }
        val previous = repository.getBookState(novel.id)
        val writer = NovelBookArtifactWriter(directory)
        val request = NovelBookBuildRequest(
            sourceId = novel.source,
            novelId = novel.id,
            novelTitle = novel.title,
            chapterSetHash = NovelBookArtifact.chapterSetHash(sourceChapters),
            builtAt = now(),
        )
        val result = writer.append(
            request = request,
            existing = existingIndex,
            newChapters = newSourceChapters,
            loadHtml = { chapter -> downloadManager.getDownloadedChapterText(novel, chapter.id) },
            bookVersion = ((previous?.bookVersion ?: 1L) + 1L).toInt(),
            onProgress = { done, total ->
                onProgress(
                    NovelBookBuildProgress(
                        phase = NovelBookBuildProgress.Phase.MERGING,
                        done = done,
                        total = total,
                    ),
                )
            },
        )

        val timestamp = now()
        val state = NovelBookState(
            novelId = novel.id,
            enabled = previous?.enabled == true,
            bookVersion = (previous?.bookVersion ?: 1L) + 1L,
            sourceId = novel.source,
            chapterSetHash = result.meta.chapterSetHash,
            totalChars = result.meta.totalChars.toLong(),
            chapterCount = result.meta.chapterCount,
            charOffset = previous?.charOffset ?: 0L,
            lastChapterId = previous?.lastChapterId ?: result.index.chapters.first().chapterId,
            complete = result.meta.complete,
            builtAt = result.meta.builtAt,
            updatedAt = timestamp,
        )
        repository.upsertBookState(state)

        logcat(LogPriority.INFO) {
            "Appended novel book chapters: novel=${novel.id}, added=${newChapters.size}, " +
                "chapters=${state.chapterCount}, chars=${state.totalChars}"
        }
        return NovelBookBuildOutcome.Built(state, result.missingChapterIds)
    }

    /** Drops the artifact and the stored state, returning the title to per-chapter reading. */
    suspend fun delete(novel: Novel) {
        NovelBookArtifact.delete(NovelBookArtifact.directoryFor(rootDirectory, novel.source, novel.id))
        repository.deleteBookState(novel.id)
    }

    /** Deletes the per-chapter downloads the book was built from, keeping the artifact. */
    fun deleteSourceChapters(novel: Novel, chapterIds: Collection<Long>) {
        if (chapterIds.isEmpty()) return
        downloadManager.deleteChapters(novel, chapterIds)
    }

    companion object {
        const val ROOT_DIRECTORY_NAME = "novel_books"

        /** App-private location of the artifacts: they need real file offsets, so no SAF here. */
        fun defaultRootDirectory(): File {
            val filesDir = runCatching { Injekt.get<Application>().filesDir }.getOrNull()
            return File(filesDir, ROOT_DIRECTORY_NAME)
        }
    }
}
