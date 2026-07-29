package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.novelchapter.model.NovelChapter

class NovelBookEngineTest {

    @Test
    fun `opening a book loads the requested section as its own renderer document`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )

        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 50),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        renderer.openedDocument shouldBe NovelBookDocument(
            sectionIndex = 1,
            chapterId = 2L,
            html = "<p>Chapter 2 text</p>",
        )
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 50)
        renderer.openedFlow shouldBe NovelBookEngineFlow.PAGINATED
    }

    @Test
    fun `next at the end of a renderer document opens the next spine section`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 100),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            nextResult = NovelBookPageTurnResult.EndOfDocument
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation.START,
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.next()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(1L, 2L)
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 0)
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 0)
    }

    @Test
    fun `previous at the start of a renderer document opens the end of the previous spine section`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            previousResult = NovelBookPageTurnResult.StartOfDocument
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 0),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.previous()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 1L)
        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 99)
        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 99)
    }

    @Test
    fun `switching flow reopens the same section at the exact book location`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            nextResult = NovelBookPageTurnResult.Moved(charOffset = 73)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.PAGINATED,
        )
        engine.next()

        engine.setFlow(NovelBookEngineFlow.SCROLLED)

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 2L)
        renderer.openedLocations shouldBe listOf(
            NovelBookLocation(sectionIndex = 1, charOffset = 25),
            NovelBookLocation(sectionIndex = 1, charOffset = 73),
        )
        renderer.openedFlow shouldBe NovelBookEngineFlow.SCROLLED
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 73)
    }

    @Test
    fun `reloading reopens the same section at the exact book location`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 200),
        )
        val renderer = RecordingNovelBookEngineRenderer().apply {
            relocateResult = NovelBookPageTurnResult.Moved(charOffset = 83)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.PAGINATED,
        )
        engine.flushLocation()

        engine.reload()

        renderer.openedDocuments.map { it.chapterId } shouldBe listOf(2L, 2L)
        renderer.openedLocations shouldBe listOf(
            NovelBookLocation(sectionIndex = 1, charOffset = 25),
            NovelBookLocation(sectionIndex = 1, charOffset = 83),
        )
        renderer.openedFlow shouldBe NovelBookEngineFlow.PAGINATED
        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 83)
    }

    @Test
    fun `renderer relocation updates and publishes the exact book location`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
                NovelChapter.create().copy(id = 2L, name = "Chapter 2"),
            ),
            measuredCharCounts = mapOf(1L to 100, 2L to 200),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = RecordingNovelBookEngineRenderer(),
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 1, charOffset = 25),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.onRendererRelocated(charOffset = 88)

        engine.location shouldBe NovelBookLocation(sectionIndex = 1, charOffset = 88)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 1, charOffset = 88))
    }

    @Test
    fun `flushing queries the renderer and publishes its exact book location`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
            ),
            measuredCharCounts = mapOf(1L to 100),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val renderer = RecordingNovelBookEngineRenderer().apply {
            relocateResult = NovelBookPageTurnResult.Moved(charOffset = 77)
        }
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 10),
            flow = NovelBookEngineFlow.SCROLLED,
        )

        engine.flushLocation()

        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 77)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 0, charOffset = 77))
    }

    @Test
    fun `opening an unmeasured section preserves its stored renderer offset until the dom is measured`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
            ),
        )
        val renderer = RecordingNovelBookEngineRenderer()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = renderer,
        )

        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 8_000),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        renderer.openedLocation shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 8_000)
        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 8_000)
    }

    @Test
    fun `renderer dom measurement replaces the estimate before relocation is clamped`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
            ),
        )
        val measuredSections = mutableListOf<Pair<Long, Int>>()
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = RecordingNovelBookEngineRenderer(),
            onLocationChanged = relocatedLocations::add,
            onSectionMeasured = { chapterId, charCount ->
                measuredSections += chapterId to charCount
            },
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 8_000),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.onRendererMeasured(sectionIndex = 0, chapterId = 1L, charCount = 10_000)
        engine.onRendererRelocated(charOffset = 9_000)

        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 9_000)
        measuredSections shouldBe listOf(1L to 10_000)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 0, charOffset = 9_000))
    }

    @Test
    fun `renderer dom measurement clamps an out of range stored offset`() = runTest {
        val spine = NovelBookSpine.fromChapters(
            chapters = listOf(
                NovelChapter.create().copy(id = 1L, name = "Chapter 1"),
            ),
        )
        val relocatedLocations = mutableListOf<NovelBookLocation>()
        val engine = NovelBookEngine(
            loadDocument = { section ->
                NovelBookDocument(
                    sectionIndex = section.index,
                    chapterId = section.chapterId,
                    html = "<p>${section.name} text</p>",
                )
            },
            renderer = RecordingNovelBookEngineRenderer(),
            onLocationChanged = relocatedLocations::add,
        )
        engine.open(
            spine = spine,
            location = NovelBookLocation(sectionIndex = 0, charOffset = 8_000),
            flow = NovelBookEngineFlow.PAGINATED,
        )

        engine.onRendererMeasured(sectionIndex = 0, chapterId = 1L, charCount = 2_000)

        engine.location shouldBe NovelBookLocation(sectionIndex = 0, charOffset = 1_999)
        relocatedLocations shouldBe listOf(NovelBookLocation(sectionIndex = 0, charOffset = 1_999))
    }

    private class RecordingNovelBookEngineRenderer : NovelBookEngineRenderer {
        val openedDocuments = mutableListOf<NovelBookDocument>()
        val openedLocations = mutableListOf<NovelBookLocation>()
        var openedDocument: NovelBookDocument? = null
        var openedLocation: NovelBookLocation? = null
        var openedFlow: NovelBookEngineFlow? = null
        var nextResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)
        var previousResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)
        var relocateResult: NovelBookPageTurnResult = NovelBookPageTurnResult.Moved(charOffset = 0)

        override suspend fun open(
            document: NovelBookDocument,
            location: NovelBookLocation,
            flow: NovelBookEngineFlow,
        ) {
            openedDocuments += document
            openedLocations += location
            openedDocument = document
            openedLocation = location
            openedFlow = flow
        }

        override suspend fun next(): NovelBookPageTurnResult = nextResult

        override suspend fun previous(): NovelBookPageTurnResult = previousResult

        override suspend fun relocate(): NovelBookPageTurnResult = relocateResult
    }
}
