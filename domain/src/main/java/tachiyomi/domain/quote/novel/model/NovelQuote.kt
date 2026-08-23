package tachiyomi.domain.quote.novel.model

import java.util.Date

data class NovelQuote(
    val id: Long,
    val chapterId: Long,
    val text: String,
    val savedAt: Date,
)
