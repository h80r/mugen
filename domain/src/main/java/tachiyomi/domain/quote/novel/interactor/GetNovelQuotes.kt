package tachiyomi.domain.quote.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import tachiyomi.domain.quote.novel.repository.NovelQuoteRepository

class GetNovelQuotes(
    private val repository: NovelQuoteRepository,
) {
    fun subscribe(): Flow<List<NovelQuoteWithRelations>> {
        return repository.getAll()
    }
}
