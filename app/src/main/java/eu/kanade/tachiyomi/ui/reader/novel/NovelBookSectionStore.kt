package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Holds reader-ready section HTML for the continuous ("book mode") reader.
 *
 * Memory keeps only the sections around the reader (LRU), while [diskWrite] / [diskRead] delegate
 * long term storage to the existing chapter disk cache. Nothing here touches Android or IO directly,
 * so it stays unit testable and cannot affect the chapter-by-chapter reader.
 */
internal class NovelBookSectionStore(
    private val maxResidentEntries: Int = DEFAULT_MAX_RESIDENT_ENTRIES,
    private val diskRead: (Long) -> String? = { null },
    private val diskWrite: (Long, String) -> Unit = { _, _ -> },
    private val diskBaseUrlRead: (Long) -> String? = { null },
    private val diskBaseUrlWrite: (Long, String?) -> Unit = { _, _ -> },
) {
    private val maxEntries = maxResidentEntries.coerceAtLeast(1)

    private val resident = object : LinkedHashMap<Long, NovelBookPreparedSection>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, NovelBookPreparedSection>,
        ): Boolean {
            return size > maxEntries
        }
    }

    /** Chapter ids currently held in memory, oldest use first. */
    val residentChapterIds: Set<Long> get() = resident.keys.toSet()

    val residentCount: Int get() = resident.size

    fun put(
        chapterId: Long,
        html: String,
        baseUrl: String? = null,
        persist: Boolean = true,
    ) {
        if (html.isBlank()) return
        resident[chapterId] = NovelBookPreparedSection(html = html, baseUrl = baseUrl)
        if (persist) {
            runCatching { diskWrite(chapterId, html) }
            runCatching { diskBaseUrlWrite(chapterId, baseUrl) }
        }
    }

    /** Returns the section HTML from memory, falling back to disk and promoting it back into memory. */
    fun get(chapterId: Long): String? = getPrepared(chapterId)?.html

    fun getPrepared(chapterId: Long): NovelBookPreparedSection? {
        resident[chapterId]?.let { return it }
        val fromDisk = runCatching { diskRead(chapterId) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val prepared = NovelBookPreparedSection(
            html = fromDisk,
            baseUrl = runCatching { diskBaseUrlRead(chapterId) }.getOrNull(),
        )
        resident[chapterId] = prepared
        return prepared
    }

    fun isResident(chapterId: Long): Boolean = resident.containsKey(chapterId)

    /** True when the section can be shown without going back to the source. */
    fun isPrepared(chapterId: Long): Boolean {
        if (isResident(chapterId)) return true
        return runCatching { diskRead(chapterId) }.getOrNull()?.isNotBlank() == true
    }

    fun release(chapterId: Long) {
        resident.remove(chapterId)
    }

    fun clear() {
        resident.clear()
    }

    companion object {
        const val DEFAULT_MAX_RESIDENT_ENTRIES = 6
    }
}

internal data class NovelBookPreparedSection(
    val html: String,
    val baseUrl: String? = null,
)
