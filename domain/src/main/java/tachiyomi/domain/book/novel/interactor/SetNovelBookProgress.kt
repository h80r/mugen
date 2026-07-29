package tachiyomi.domain.book.novel.interactor

import tachiyomi.domain.book.novel.repository.NovelBookStateRepository

/** Stores the reading position as a global character offset over the whole book. */
class SetNovelBookProgress(
    private val repository: NovelBookStateRepository,
) {
    suspend fun await(
        novelId: Long,
        charOffset: Long,
        lastChapterId: Long?,
        now: Long = System.currentTimeMillis(),
    ) {
        repository.setBookProgress(
            novelId = novelId,
            charOffset = charOffset.coerceAtLeast(0L),
            lastChapterId = lastChapterId,
            updatedAt = now,
        )
    }
}
