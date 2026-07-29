package eu.kanade.tachiyomi.ui.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookLocationProgressCodecTest {

    @Test
    fun `book location round trips`() {
        val encoded = encodeBookLocationProgress(sectionIndex = 12, sectionFraction = 0.25f)
        val decoded = decodeBookLocationProgress(encoded)

        decoded?.sectionIndex shouldBe 12
        decoded?.sectionPermille shouldBe 250
        decoded?.sectionFraction shouldBe 0.25f
    }

    @Test
    fun `fraction is clamped inside the section`() {
        decodeBookLocationProgress(encodeBookLocationProgress(0, -1f))?.sectionPermille shouldBe 0
        decodeBookLocationProgress(encodeBookLocationProgress(0, 2f))?.sectionPermille shouldBe 999
        decodeBookLocationProgress(encodeBookLocationProgress(-5, 0.5f))?.sectionIndex shouldBe 0
    }

    @Test
    fun `book locations do not collide with other encodings`() {
        val encoded = encodeBookLocationProgress(sectionIndex = 40, sectionFraction = 0.9f)

        decodeWebScrollProgressPercent(encoded) shouldBe null
        decodeNativeScrollProgress(encoded) shouldBe null
        decodePageReaderProgress(encoded) shouldBe null
    }

    @Test
    fun `other encodings are not read as book locations`() {
        decodeBookLocationProgress(encodeWebScrollProgressPercent(42)) shouldBe null
        decodeBookLocationProgress(encodeNativeScrollProgress(3, 120)) shouldBe null
        decodeBookLocationProgress(encodeNativeScrollProgress(3, 120, totalItems = 40)) shouldBe null
        decodeBookLocationProgress(encodePageReaderProgress(2, 10)) shouldBe null
        decodeBookLocationProgress(0L) shouldBe null
        decodeBookLocationProgress(7L) shouldBe null
    }

    @Test
    fun `first and last sections stay inside the reserved range`() {
        decodeBookLocationProgress(encodeBookLocationProgress(0, 0f))?.sectionIndex shouldBe 0
        val last = encodeBookLocationProgress(sectionIndex = 899_999, sectionFraction = 0.999f)
        decodeBookLocationProgress(last)?.sectionIndex shouldBe 899_999
        decodeBookLocationProgress(encodeBookLocationProgress(5_000_000, 0.5f))?.sectionIndex shouldBe 899_999
    }
}
