package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookMetricsTest {

    private val payload = """
        {"scrollTop":1500,"viewportHeight":800,"contentHeight":6000,"sections":[
        {"index":0,"chapterId":"10","top":0,"height":1000,"pruned":true},
        {"index":1,"chapterId":"11","top":1000,"height":2000,"pruned":false},
        {"index":2,"chapterId":"12","top":3000,"height":3000,"pruned":false}]}
    """.trimIndent()

    @Test
    fun `a metrics payload is parsed into sections`() {
        val metrics = parseNovelBookDocumentMetrics(payload)!!

        metrics.scrollTopPx shouldBe 1500
        metrics.viewportHeightPx shouldBe 800
        metrics.contentHeightPx shouldBe 6000
        metrics.sections.map { it.chapterId } shouldBe listOf(10L, 11L, 12L)
        metrics.sections.first().isPruned shouldBe true
    }

    @Test
    fun `a json encoded string result is unwrapped first`() {
        val quoted = "\"" + payload.replace("\"", "\\\"").replace("\n", "") + "\""

        val metrics = parseNovelBookDocumentMetrics(quoted)!!

        metrics.sections.size shouldBe 3
    }

    @Test
    fun `missing or malformed results are reported as null`() {
        parseNovelBookDocumentMetrics(null) shouldBe null
        parseNovelBookDocumentMetrics("") shouldBe null
        parseNovelBookDocumentMetrics("null") shouldBe null
        parseNovelBookDocumentMetrics("undefined") shouldBe null
        parseNovelBookDocumentMetrics("{not json") shouldBe null
    }

    @Test
    fun `the current section is the one under the scroll position`() {
        val metrics = parseNovelBookDocumentMetrics(payload)!!

        val current = metrics.currentSection()!!
        current.index shouldBe 1
        metrics.fractionInside(current) shouldBe 0.25f
    }

    @Test
    fun `positions outside every section fall back to the closest one`() {
        val metrics = parseNovelBookDocumentMetrics(payload)!!.copy(scrollTopPx = 99_000)

        metrics.currentSection()!!.index shouldBe 2
        metrics.fractionInside(metrics.currentSection()!!) shouldBe 1f
    }

    @Test
    fun `only laid out sections are reported as measured`() {
        val metrics = parseNovelBookDocumentMetrics(payload)!!

        metrics.measuredSections().map { it.index } shouldBe listOf(1, 2)
    }

    @Test
    fun `an empty document has no current section and no measurements`() {
        val metrics = parseNovelBookDocumentMetrics(
            """{"scrollTop":0,"viewportHeight":0,"contentHeight":0,"sections":[]}""",
        )!!

        metrics.isEmpty shouldBe true
        metrics.currentSection() shouldBe null
        metrics.measuredSections() shouldBe emptyList()
    }
}
