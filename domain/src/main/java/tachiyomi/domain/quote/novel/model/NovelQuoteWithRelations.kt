package tachiyomi.domain.quote.novel.model

import java.util.Date

data class NovelQuoteWithRelations(
    val id: Long,
    val chapterId: Long,
    val text: String,
    val savedAt: Date,
    val novelTitle: String,
    val chapterName: String,
    val chapterNumber: Double,
)
