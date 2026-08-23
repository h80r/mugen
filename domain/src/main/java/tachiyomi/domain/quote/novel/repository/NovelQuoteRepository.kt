package tachiyomi.domain.quote.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import java.util.Date

interface NovelQuoteRepository {

    fun getAll(): Flow<List<NovelQuoteWithRelations>>

    suspend fun insert(chapterId: Long, text: String, savedAt: Date)

    suspend fun delete(id: Long)
}
