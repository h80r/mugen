package tachiyomi.domain.quote.novel.interactor

import tachiyomi.domain.quote.novel.repository.NovelQuoteRepository
import java.util.Date

class InsertNovelQuote(
    private val repository: NovelQuoteRepository,
) {
    suspend fun await(chapterId: Long, text: String, savedAt: Date = Date()) {
        repository.insert(chapterId, text, savedAt)
    }
}
