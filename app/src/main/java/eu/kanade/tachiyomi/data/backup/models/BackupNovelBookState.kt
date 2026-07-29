package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Compiled-book state of a novel inside a backup.
 *
 * The book artifact (body/index/meta files) is derived data and is deliberately left out of
 * backups; on restore the state is written back so the title still knows it was read as a book, at
 * which character offset the reader stopped, and which chapter set the book was compiled from. The
 * stored [chapterSetHash] lets the title screen report the book as outdated until it is rebuilt.
 */
@Serializable
data class BackupNovelBookState(
    @ProtoNumber(1) var enabled: Boolean = false,
    @ProtoNumber(2) var bookVersion: Long = 1,
    @ProtoNumber(3) var sourceId: Long = 0,
    @ProtoNumber(4) var chapterSetHash: String = "",
    @ProtoNumber(5) var totalChars: Long = 0,
    @ProtoNumber(6) var chapterCount: Int = 0,
    @ProtoNumber(7) var charOffset: Long = 0,
    @ProtoNumber(8) var lastChapterUrl: String? = null,
    @ProtoNumber(9) var complete: Boolean = false,
    @ProtoNumber(10) var builtAt: Long = 0,
    @ProtoNumber(11) var updatedAt: Long = 0,
)
