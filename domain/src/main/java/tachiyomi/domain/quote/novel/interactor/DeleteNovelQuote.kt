package tachiyomi.domain.quote.novel.interactor

import tachiyomi.domain.quote.novel.repository.NovelQuoteRepository

class DeleteNovelQuote(
    private val repository: NovelQuoteRepository,
) {
    suspend fun await(id: Long) {
        repository.delete(id)
    }
}
