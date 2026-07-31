package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookScrollSurfaceReportTest {

    @Test
    fun `parses a plain scroll report`() {
        val report = parseBookScrollReport("""{"consumed": 120, "forward": true}""")

        report?.consumedPx shouldBe 120
        report?.canScrollForward shouldBe true
    }

    @Test
    fun `parses a report that reached the end`() {
        val report = parseBookScrollReport("""{"consumed": 0, "forward": false}""")

        report?.consumedPx shouldBe 0
        report?.canScrollForward shouldBe false
    }

    @Test
    fun `parses the double quoted and escaped string evaluateJavascript returns`() {
        // evaluateJavascript hands JSON back as a quoted and escaped JSON string.
        val report = parseBookScrollReport("\"{\\\"consumed\\\": 45, \\\"forward\\\": true}\"")

        report?.consumedPx shouldBe 45
        report?.canScrollForward shouldBe true
    }

    @Test
    fun `null and blank results mean the engine is not reachable`() {
        parseBookScrollReport(null) shouldBe null
        parseBookScrollReport("null") shouldBe null
        parseBookScrollReport("") shouldBe null
    }

    @Test
    fun `a negative consumed amount reports scrolling backwards`() {
        val report = parseBookScrollReport("""{"consumed": -30, "forward": true}""")

        report?.consumedPx shouldBe -30
    }
}
