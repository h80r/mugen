package eu.kanade.tachiyomi.ui.reader.novel

internal enum class NovelBookEngineFlow {
    SCROLLED,
    PAGINATED,
}

data class NovelBookDocument(
    val sectionIndex: Int,
    val chapterId: Long,
    val html: String,
    val baseUrl: String? = null,
)

internal sealed interface NovelBookPageTurnResult {
    /**
     * A renderer-owned position. [sectionIndex] is -1 for documents that hold a single section; the
     * stitched scrolled document reports the section the viewport actually starts in.
     */
    data class Moved(
        val charOffset: Int,
        val sectionIndex: Int = -1,
    ) : NovelBookPageTurnResult

    data object StartOfDocument : NovelBookPageTurnResult

    data object EndOfDocument : NovelBookPageTurnResult
}

internal interface NovelBookEngineRenderer {
    /**
     * Replaces the whole renderer document with [document].
     *
     * [restoreFraction] is used instead of the location char offset when the section length that
     * offset was built from is only an estimate: the renderer knows the real text length once the
     * document loaded, so restoring by fraction lands on the position the reader actually left.
     */
    suspend fun open(
        document: NovelBookDocument,
        location: NovelBookLocation,
        flow: NovelBookEngineFlow,
        restoreFraction: Float? = null,
    )

    suspend fun next(transitionStyleName: String = "SLIDE"): NovelBookPageTurnResult

    suspend fun previous(transitionStyleName: String = "SLIDE"): NovelBookPageTurnResult

    suspend fun relocate(): NovelBookPageTurnResult

    /** Appends a section after the last resident one, keeping the current scroll position. */
    suspend fun appendSection(document: NovelBookDocument): Boolean = false

    /** Inserts a section before the first resident one and compensates the scroll offset. */
    suspend fun prependSection(document: NovelBookDocument): Boolean = false

    /** Drops a resident section from the document, compensating the scroll offset when needed. */
    suspend fun removeSection(sectionIndex: Int): Boolean = false
}

/**
 * Owns the document lifecycle of book mode independently from the chapter reader document.
 *
 * Each spine section is loaded as its own renderer document. The renderer receives a stable book
 * location and chooses the concrete scrolled or paginated layout after that document is loaded.
 */
internal class NovelBookEngine(
    private val loadDocument: suspend (NovelBookSection) -> NovelBookDocument,
    private val renderer: NovelBookEngineRenderer,
    private val onLocationChanged: (NovelBookLocation) -> Unit = {},
    private val onSectionMeasured: (Long, Int) -> Unit = { _, _ -> },
) {

    private var spine: NovelBookSpine = NovelBookSpine.EMPTY

    private var flow: NovelBookEngineFlow = NovelBookEngineFlow.SCROLLED

    /**
     * Range of spine sections that currently live in the renderer document.
     *
     * The scrolled flow stitches neighbouring chapters into one document, so crossing a chapter
     * boundary is a plain scroll: no document swap, and therefore no jump.
     */
    private var residentFirst = -1

    private var residentLast = -1

    private var stitchInFlight = false

    var location: NovelBookLocation = NovelBookLocation.START
        private set

    suspend fun open(
        spine: NovelBookSpine,
        location: NovelBookLocation,
        flow: NovelBookEngineFlow,
    ) {
        this.spine = spine
        this.location = clampLocationForOpen(spine, location)
        this.flow = flow
        openCurrentSection()
    }

    /**
     * Stitches the adjacent chapter into the scrolled document instead of replacing it.
     *
     * Returns true when a section was added, so the caller can keep asking until there is enough
     * content ahead of (or behind) the viewport for a seamless crossing.
     */
    suspend fun stitch(forward: Boolean): Boolean {
        if (flow != NovelBookEngineFlow.SCROLLED || stitchInFlight) return false
        val targetIndex = if (forward) residentLast + 1 else residentFirst - 1
        if (targetIndex < 0) return false
        val section = spine.sectionAt(targetIndex) ?: return false
        stitchInFlight = true
        return try {
            val document = loadDocument(section)
            val added = if (forward) {
                renderer.appendSection(document)
            } else {
                renderer.prependSection(document)
            }
            if (added) {
                if (forward) residentLast = targetIndex else residentFirst = targetIndex
                pruneResidentWindow(keepForward = forward)
            }
            added
        } finally {
            stitchInFlight = false
        }
    }

    suspend fun next(transitionStyleName: String = "SLIDE") {
        when (val result = renderer.next(transitionStyleName)) {
            is NovelBookPageTurnResult.Moved -> {
                updateLocation(locationOf(result))
            }
            NovelBookPageTurnResult.EndOfDocument -> {
                // The scrolled document grows instead of being replaced, so a document swap is only
                // the fallback for the paginated flow and for the very end of the book.
                if (stitch(forward = true)) {
                    val stitched = renderer.next(transitionStyleName)
                    if (stitched is NovelBookPageTurnResult.Moved) updateLocation(locationOf(stitched))
                    return
                }
                val nextSection = spine.sectionAt(location.sectionIndex + 1) ?: return
                updateLocation(NovelBookLocation(sectionIndex = nextSection.index, charOffset = 0))
                openCurrentSection()
            }
            NovelBookPageTurnResult.StartOfDocument -> Unit
        }
    }

    suspend fun previous(transitionStyleName: String = "SLIDE") {
        when (val result = renderer.previous(transitionStyleName)) {
            is NovelBookPageTurnResult.Moved -> {
                updateLocation(locationOf(result))
            }
            NovelBookPageTurnResult.StartOfDocument -> {
                if (stitch(forward = false)) {
                    val stitched = renderer.previous(transitionStyleName)
                    if (stitched is NovelBookPageTurnResult.Moved) updateLocation(locationOf(stitched))
                    return
                }
                val previousSection = spine.sectionAt(location.sectionIndex - 1) ?: return
                updateLocation(
                    NovelBookLocation(
                        sectionIndex = previousSection.index,
                        charOffset = if (previousSection.isMeasured) {
                            (previousSection.charCount - 1).coerceAtLeast(0)
                        } else {
                            Int.MAX_VALUE
                        },
                    ),
                )
                openCurrentSection()
            }
            NovelBookPageTurnResult.EndOfDocument -> Unit
        }
    }

    suspend fun setFlow(flow: NovelBookEngineFlow) {
        if (this.flow == flow) return
        this.flow = flow
        openCurrentSection()
    }

    suspend fun reload() {
        openCurrentSection()
    }

    suspend fun flushLocation() {
        val result = renderer.relocate()
        if (result is NovelBookPageTurnResult.Moved) {
            updateLocation(locationOf(result))
        }
    }

    fun onRendererRelocated(charOffset: Int, sectionIndex: Int = -1) {
        updateLocation(
            locationOf(
                NovelBookPageTurnResult.Moved(charOffset = charOffset, sectionIndex = sectionIndex),
            ),
        )
    }

    /**
     * Records the real text length of a section the renderer holds.
     *
     * Any resident section may report, not only the one the viewport is in: a stitched document
     * measures every chapter it swallowed, which keeps whole-book progress on real lengths instead
     * of leaving neighbouring chapters on their estimated weight.
     */
    fun onRendererMeasured(
        sectionIndex: Int,
        chapterId: Long,
        charCount: Int,
    ) {
        if (charCount <= 0) return
        val section = spine.sectionAt(sectionIndex) ?: return
        if (section.chapterId != chapterId) return
        spine = spine.withMeasuredSection(chapterId, charCount)
        onSectionMeasured(chapterId, charCount)
        updateLocation(location)
    }

    private fun updateLocation(newLocation: NovelBookLocation) {
        val clampedLocation = spine.clampLocation(newLocation)
        if (location == clampedLocation) return
        location = clampedLocation
        onLocationChanged(clampedLocation)
    }

    /**
     * Keeps the stitched document bounded: sections that scrolled far out of reach are dropped, so a
     * long reading session cannot grow the DOM until scrolling stutters. Five resident sections keep
     * both directions seamless while staying cheap.
     */
    private suspend fun pruneResidentWindow(keepForward: Boolean) {
        while (residentLast - residentFirst + 1 > 5) {
            val victim = if (keepForward) residentFirst else residentLast
            if (victim == location.sectionIndex) break
            if (!renderer.removeSection(victim)) break
            if (keepForward) residentFirst += 1 else residentLast -= 1
        }
    }

    private fun locationOf(result: NovelBookPageTurnResult.Moved): NovelBookLocation =
        if (result.sectionIndex >= 0) {
            NovelBookLocation(sectionIndex = result.sectionIndex, charOffset = result.charOffset)
        } else {
            location.copy(charOffset = result.charOffset)
        }

    private suspend fun openCurrentSection() {
        val section = spine.sectionAt(location.sectionIndex) ?: return
        // An unmeasured section only has an estimated length, so its char offset is an estimate too.
        // The renderer knows the real length once the document loaded, so the position is restored
        // from the fraction instead: that is what makes reopening the book land on the same line.
        val restoreFraction = if (section.isMeasured || section.charCount <= 0) {
            null
        } else {
            (location.charOffset.toFloat() / section.charCount.toFloat()).coerceIn(0f, 1f)
        }
        renderer.open(
            document = loadDocument(section),
            location = location,
            flow = flow,
            restoreFraction = restoreFraction,
        )
        residentFirst = section.index
        residentLast = section.index
    }

    private fun clampLocationForOpen(
        spine: NovelBookSpine,
        location: NovelBookLocation,
    ): NovelBookLocation {
        if (spine.isEmpty) return NovelBookLocation.START
        val sectionIndex = location.sectionIndex.coerceIn(0, spine.sections.lastIndex)
        val section = spine.sectionAt(sectionIndex) ?: return NovelBookLocation.START
        return if (section.isMeasured) {
            spine.clampLocation(location.copy(sectionIndex = sectionIndex))
        } else {
            NovelBookLocation(
                sectionIndex = sectionIndex,
                charOffset = location.charOffset.coerceAtLeast(0),
            )
        }
    }
}
