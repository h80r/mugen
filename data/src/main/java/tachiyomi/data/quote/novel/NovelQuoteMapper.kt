package tachiyomi.data.quote.novel

import tachiyomi.domain.quote.novel.model.NovelQuoteWithRelations
import java.util.Date

object NovelQuoteMapper {
    fun mapNovelQuoteWithRelations(
        id: Long,
        chapterId: Long,
        text: String,
        savedAt: Date,
        novelTitle: String,
        chapterName: String,
        chapterNumber: Double,
    ): NovelQuoteWithRelations = NovelQuoteWithRelations(
        id = id,
        chapterId = chapterId,
        text = text,
        savedAt = savedAt,
        novelTitle = novelTitle,
        chapterName = chapterName,
        chapterNumber = chapterNumber,
    )
}
