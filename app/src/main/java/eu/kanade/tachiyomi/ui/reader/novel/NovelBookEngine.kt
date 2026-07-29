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
    data class Moved(val charOffset: Int) : NovelBookPageTurnResult

    data object StartOfDocument : NovelBookPageTurnResult

    data object EndOfDocument : NovelBookPageTurnResult
}

internal interface NovelBookEngineRenderer {
    suspend fun open(
        document: NovelBookDocument,
        location: NovelBookLocation,
        flow: NovelBookEngineFlow,
    )

    suspend fun next(): NovelBookPageTurnResult

    suspend fun previous(): NovelBookPageTurnResult

    suspend fun relocate(): NovelBookPageTurnResult
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

    suspend fun next() {
        when (val result = renderer.next()) {
            is NovelBookPageTurnResult.Moved -> {
                updateLocation(location.copy(charOffset = result.charOffset))
            }
            NovelBookPageTurnResult.EndOfDocument -> {
                val nextSection = spine.sectionAt(location.sectionIndex + 1) ?: return
                updateLocation(NovelBookLocation(sectionIndex = nextSection.index, charOffset = 0))
                openCurrentSection()
            }
            NovelBookPageTurnResult.StartOfDocument -> Unit
        }
    }

    suspend fun previous() {
        when (val result = renderer.previous()) {
            is NovelBookPageTurnResult.Moved -> {
                updateLocation(location.copy(charOffset = result.charOffset))
            }
            NovelBookPageTurnResult.StartOfDocument -> {
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
            updateLocation(location.copy(charOffset = result.charOffset))
        }
    }

    fun onRendererRelocated(charOffset: Int) {
        updateLocation(location.copy(charOffset = charOffset))
    }

    fun onRendererMeasured(
        sectionIndex: Int,
        chapterId: Long,
        charCount: Int,
    ) {
        if (charCount <= 0) return
        val currentSection = spine.sectionAt(location.sectionIndex) ?: return
        if (currentSection.index != sectionIndex || currentSection.chapterId != chapterId) return
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

    private suspend fun openCurrentSection() {
        val section = spine.sectionAt(location.sectionIndex) ?: return
        renderer.open(
            document = loadDocument(section),
            location = location,
            flow = flow,
        )
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
