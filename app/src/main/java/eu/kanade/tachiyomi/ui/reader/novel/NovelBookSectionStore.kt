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
    private val diskDelete: (Long) -> Unit = {},
    /**
     * Combined long term storage hooks.
     *
     * The split html/baseUrl hooks above exist for the chapter disk cache, which stores both under
     * separate keys. A book section cache stores one payload per section, so going through the split
     * hooks would need a read-modify-write for every write. When [diskWriteSection] is set it
     * replaces them, and [diskReadSection] is consulted before them.
     */
    private val diskReadSection: (Long) -> NovelBookPreparedSection? = { null },
    private val diskWriteSection: ((Long, NovelBookPreparedSection) -> Unit)? = null,
) {
    private val maxEntries = maxResidentEntries.coerceAtLeast(1)

    /**
     * Guards the resident map.
     *
     * Sections are prepared concurrently (the loader allows parallel loads and the reader prepares
     * on the IO dispatcher while the planner reads the store), and an access ordered LinkedHashMap
     * mutates on plain reads, so unsynchronized access could corrupt it.
     */
    private val lock = Any()

    private val resident = object : LinkedHashMap<Long, NovelBookPreparedSection>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, NovelBookPreparedSection>,
        ): Boolean {
            return size > maxEntries
        }
    }

    /** Chapter ids currently held in memory, oldest use first. */
    val residentChapterIds: Set<Long> get() = synchronized(lock) { resident.keys.toSet() }

    val residentCount: Int get() = synchronized(lock) { resident.size }

    fun put(
        chapterId: Long,
        html: String,
        baseUrl: String? = null,
        persist: Boolean = true,
    ) {
        if (html.isBlank()) return
        synchronized(lock) {
            resident[chapterId] = NovelBookPreparedSection(html = html, baseUrl = baseUrl)
        }
        if (persist) {
            val writeSection = diskWriteSection
            if (writeSection != null) {
                runCatching {
                    writeSection(chapterId, NovelBookPreparedSection(html = html, baseUrl = baseUrl))
                }
            } else {
                runCatching { diskWrite(chapterId, html) }
                runCatching { diskBaseUrlWrite(chapterId, baseUrl) }
            }
        }
    }

    /** Returns the section HTML from memory, falling back to disk and promoting it back into memory. */
    fun get(chapterId: Long): String? = getPrepared(chapterId)?.html

    fun getPrepared(chapterId: Long): NovelBookPreparedSection? {
        synchronized(lock) { resident[chapterId] }?.let { return it }
        val prepared = readFromDisk(chapterId) ?: return null
        synchronized(lock) { resident[chapterId] = prepared }
        return prepared
    }

    fun isResident(chapterId: Long): Boolean = synchronized(lock) { resident.containsKey(chapterId) }

    /** True when the section can be shown without going back to the source. */
    fun isPrepared(chapterId: Long): Boolean {
        if (isResident(chapterId)) return true
        return readFromDisk(chapterId) != null
    }

    fun release(chapterId: Long) {
        synchronized(lock) { resident.remove(chapterId) }
    }

    /**
     * Forgets a section completely, in memory and in long term storage.
     *
     * [release] deliberately only frees memory, so an offline prepared book survives window pruning.
     * A forced reload needs this stronger variant: without it the refetched HTML would be shadowed
     * by the stale copy still sitting on disk.
     */
    fun invalidate(chapterId: Long) {
        synchronized(lock) { resident.remove(chapterId) }
        runCatching { diskDelete(chapterId) }
    }

    fun clear() {
        synchronized(lock) { resident.clear() }
    }

    /** Long term storage lookup: the combined hook wins, the split chapter-cache hooks follow. */
    private fun readFromDisk(chapterId: Long): NovelBookPreparedSection? {
        runCatching { diskReadSection(chapterId) }
            .getOrNull()
            ?.takeIf { it.html.isNotBlank() }
            ?.let { return it }
        val html = runCatching { diskRead(chapterId) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return NovelBookPreparedSection(
            html = html,
            baseUrl = runCatching { diskBaseUrlRead(chapterId) }.getOrNull(),
        )
    }

    companion object {
        const val DEFAULT_MAX_RESIDENT_ENTRIES = 6
    }
}

internal data class NovelBookPreparedSection(
    val html: String,
    val baseUrl: String? = null,
)
