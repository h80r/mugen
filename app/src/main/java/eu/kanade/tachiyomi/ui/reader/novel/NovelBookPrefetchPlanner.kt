package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Tuning for the resident/prefetch windows of the continuous ("book mode") reader.
 *
 * - [residentRadius]: sections kept rendered around the current one. Everything else is pruned so
 *   the DOM stays small no matter how long the book is.
 * - [prefetchAhead] / [prefetchBehind]: sections whose reader-ready HTML is prepared in the
 *   background so crossing a chapter boundary never waits on network or sanitizing.
 * - [maxConcurrentPrefetch]: how many prefetch jobs may run at once, to stay friendly to sources.
 */
data class NovelBookWindowConfig(
    val residentRadius: Int = DEFAULT_RESIDENT_RADIUS,
    val prefetchAhead: Int = DEFAULT_PREFETCH_AHEAD,
    val prefetchBehind: Int = DEFAULT_PREFETCH_BEHIND,
    val maxConcurrentPrefetch: Int = DEFAULT_MAX_CONCURRENT_PREFETCH,
) {
    companion object {
        const val DEFAULT_RESIDENT_RADIUS = 1
        const val DEFAULT_PREFETCH_AHEAD = 3
        const val DEFAULT_PREFETCH_BEHIND = 1
        const val DEFAULT_MAX_CONCURRENT_PREFETCH = 2

        val DEFAULT = NovelBookWindowConfig()
    }
}

/**
 * What the reader should render, prepare and release for the current position.
 *
 * [prefetchQueue] is already ordered by priority: the section the reader is about to enter first,
 * then further ahead, then the sections behind.
 */
data class NovelBookWindowPlan(
    val residentSections: List<Int>,
    val prefetchQueue: List<Int>,
    val pruneSections: List<Int>,
) {
    val isIdle: Boolean get() = prefetchQueue.isEmpty() && pruneSections.isEmpty()

    companion object {
        val EMPTY = NovelBookWindowPlan(
            residentSections = emptyList(),
            prefetchQueue = emptyList(),
            pruneSections = emptyList(),
        )
    }
}

/** Direction the reader is currently moving through the book. */
enum class NovelBookScrollDirection {
    Forward,
    Backward,
    Idle,
}

/**
 * Pure planner for book mode windowing: given the spine, the current section and what is already
 * loaded, decides what to keep resident, what to prefetch next and what to release.
 *
 * No Android, no IO, no reader state, so it is fully unit testable and cannot affect the existing
 * chapter-by-chapter reader.
 */
object NovelBookPrefetchPlanner {

    fun plan(
        spine: NovelBookSpine,
        currentSectionIndex: Int,
        loadedSections: Set<Int> = emptySet(),
        inFlightSections: Set<Int> = emptySet(),
        config: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
    ): NovelBookWindowPlan {
        if (spine.isEmpty) return NovelBookWindowPlan.EMPTY
        val center = currentSectionIndex.coerceIn(0, spine.sections.lastIndex)
        val resident = spine.windowAround(center, config.residentRadius)

        val cacheStart = (center - config.residentRadius - config.prefetchBehind.coerceAtLeast(0))
            .coerceAtLeast(0)
        val cacheEnd = (center + config.residentRadius + config.prefetchAhead.coerceAtLeast(0))
            .coerceAtMost(spine.sections.lastIndex)
        val cacheRange = cacheStart..cacheEnd

        val queue = prefetchOrder(
            center = center,
            cacheRange = cacheRange,
            resident = resident,
        ).filter { it !in loadedSections && it !in inFlightSections }

        val prune = loadedSections
            .filter { it !in cacheRange && it in spine.sections.indices }
            .sortedBy { kotlin.math.abs(it - center) }
            .reversed()

        return NovelBookWindowPlan(
            residentSections = resident,
            prefetchQueue = queue,
            pruneSections = prune,
        )
    }

    /**
     * Adapts the window to reading conditions: fast forward scrolling deepens the look-ahead,
     * metered or slow connections shrink it so we never burn data or hammer a source.
     */
    fun resolveConfig(
        base: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
        direction: NovelBookScrollDirection = NovelBookScrollDirection.Idle,
        isFastScrolling: Boolean = false,
        isConstrainedNetwork: Boolean = false,
    ): NovelBookWindowConfig {
        var ahead = base.prefetchAhead
        var behind = base.prefetchBehind
        var concurrency = base.maxConcurrentPrefetch

        when (direction) {
            NovelBookScrollDirection.Forward -> {
                if (isFastScrolling) ahead += 2
                behind = behind.coerceAtMost(1)
            }
            NovelBookScrollDirection.Backward -> {
                behind += 1
                ahead = ahead.coerceAtMost(2)
            }
            NovelBookScrollDirection.Idle -> Unit
        }

        if (isConstrainedNetwork) {
            ahead = ahead.coerceAtMost(1)
            behind = 0
            concurrency = 1
        }

        return base.copy(
            prefetchAhead = ahead.coerceAtLeast(0),
            prefetchBehind = behind.coerceAtLeast(0),
            maxConcurrentPrefetch = concurrency.coerceAtLeast(1),
        )
    }

    /** Sections to hand to the prefetcher next, respecting [NovelBookWindowConfig.maxConcurrentPrefetch]. */
    fun nextPrefetchBatch(
        plan: NovelBookWindowPlan,
        inFlightCount: Int,
        config: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
    ): List<Int> {
        val slots = (config.maxConcurrentPrefetch - inFlightCount.coerceAtLeast(0)).coerceAtLeast(0)
        if (slots == 0) return emptyList()
        return plan.prefetchQueue.take(slots)
    }

    private fun prefetchOrder(
        center: Int,
        cacheRange: IntRange,
        resident: List<Int>,
    ): List<Int> {
        val ordered = LinkedHashSet<Int>()
        // Resident sections first: they are needed to render right now.
        resident.sortedBy { kotlin.math.abs(it - center) }.forEach(ordered::add)
        // Then forward look-ahead, then the sections behind for backwards scrolling.
        var forward = center + 1
        while (forward in cacheRange) {
            ordered.add(forward)
            forward++
        }
        var backward = center - 1
        while (backward in cacheRange) {
            ordered.add(backward)
            backward--
        }
        return ordered.filter { it in cacheRange }
    }
}
