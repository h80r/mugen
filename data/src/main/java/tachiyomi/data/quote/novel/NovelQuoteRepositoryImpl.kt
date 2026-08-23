package tachiyomi.data.quote.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import tachiyomi.domain.quote.novel.repository.NovelQuoteRepository
import java.util.Date

class NovelQuoteRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelQuoteRepository {

    override fun getAll(): Flow<List<NovelQuoteWithRelations>> {
        return handler.subscribeToList { db ->
            db.novel_quotesQueries.getAll(NovelQuoteMapper::mapNovelQuoteWithRelations)
        }
    }

    override suspend fun insert(chapterId: Long, text: String, savedAt: Date) {
        handler.await { db -> db.novel_quotesQueries.insert(chapterId, text, savedAt) }
    }

    override suspend fun delete(id: Long) {
        handler.await { db -> db.novel_quotesQueries.delete(id) }
    }
}
