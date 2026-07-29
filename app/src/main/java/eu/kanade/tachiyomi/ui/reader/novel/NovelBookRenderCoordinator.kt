package eu.kanade.tachiyomi.ui.reader.novel

import kotlin.math.abs

/** A single thing the book-mode reader has to do to catch up with the current reading position. */
sealed interface NovelBookRenderCommand {
    val sectionIndex: Int

    /** Insert an already prepared section into the live document. */
    data class Render(override val sectionIndex: Int, val chapterId: Long) : NovelBookRenderCommand

    /** Replace a rendered section with a height-preserving placeholder to keep the DOM small. */
    data class Release(override val sectionIndex: Int) : NovelBookRenderCommand

    /** Prepare reader-ready HTML for a section in the background. */
    data class Prepare(override val sectionIndex: Int, val chapterId: Long) : NovelBookRenderCommand
}

data class NovelBookRenderPlan(
    val render: List<NovelBookRenderCommand.Render>,
    val release: List<NovelBookRenderCommand.Release>,
    val prepare: List<NovelBookRenderCommand.Prepare>,
) {
    /** Render first so reading never blocks, then free memory, then fetch ahead. */
    val commands: List<NovelBookRenderCommand> get() = render + release + prepare

    val isIdle: Boolean get() = render.isEmpty() && release.isEmpty() && prepare.isEmpty()

    companion object {
        val EMPTY = NovelBookRenderPlan(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Turns the book windowing plan into concrete reader commands.
 *
 * Pure: it only compares the spine with what is currently rendered / prepared, so the WebView layer
 * stays a thin executor and all of the windowing behaviour is unit testable.
 */
object NovelBookRenderCoordinator {

    fun resolve(
        spine: NovelBookSpine,
        currentSectionIndex: Int,
        renderedSections: Set<Int> = emptySet(),
        preparedSections: Set<Int> = emptySet(),
        inFlightSections: Set<Int> = emptySet(),
        config: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
    ): NovelBookRenderPlan {
        if (spine.isEmpty) return NovelBookRenderPlan.EMPTY
        val center = currentSectionIndex.coerceIn(0, spine.sections.lastIndex)
        val plan = NovelBookPrefetchPlanner.plan(
            spine = spine,
            currentSectionIndex = center,
            loadedSections = preparedSections,
            inFlightSections = inFlightSections,
            config = config,
        )

        val render = plan.residentSections
            .filter { it in preparedSections && it !in renderedSections }
            .sortedBy { abs(it - center) }
            .mapNotNull { index ->
                spine.sectionAt(index)?.let { section ->
                    NovelBookRenderCommand.Render(sectionIndex = index, chapterId = section.chapterId)
                }
            }

        val release = renderedSections
            .filter { it !in plan.residentSections }
            .sortedByDescending { abs(it - center) }
            .map { NovelBookRenderCommand.Release(sectionIndex = it) }

        val prepare = NovelBookPrefetchPlanner
            .nextPrefetchBatch(plan = plan, inFlightCount = inFlightSections.size, config = config)
            .mapNotNull { index ->
                spine.sectionAt(index)?.let { section ->
                    NovelBookRenderCommand.Prepare(sectionIndex = index, chapterId = section.chapterId)
                }
            }

        return NovelBookRenderPlan(render = render, release = release, prepare = prepare)
    }

    /** Sections that should stay in the document for the given position. */
    fun residentSections(
        spine: NovelBookSpine,
        currentSectionIndex: Int,
        config: NovelBookWindowConfig = NovelBookWindowConfig.DEFAULT,
    ): List<Int> = spine.windowAround(currentSectionIndex, config.residentRadius)
}
