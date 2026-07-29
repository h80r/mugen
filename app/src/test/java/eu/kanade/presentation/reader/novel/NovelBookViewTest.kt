package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NovelBookViewTest {

    @Test
    fun `page mode presents the book as pages instead of forcing a scroll`() {
        val renderer = resolveNovelBookRenderer(
            pageReaderEnabled = true,
            richNativeRendererExperimentalEnabled = false,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = false,
        )

        renderer shouldBe NovelBookRenderer.WEBVIEW_PAGINATED
        renderer.flow shouldBe NovelBookFlow.PAGINATED
        renderer.usesWebView shouldBe true
    }

    @Test
    fun `the experimental native renderer can present the book`() {
        val renderer = resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = true,
        )

        renderer shouldBe NovelBookRenderer.RICH_NATIVE_SCROLLED
        renderer.flow shouldBe NovelBookFlow.SCROLLED
        renderer.usesWebView shouldBe false
    }

    @Test
    fun `the native book renderer is mounted and can be killed by the flag`() {
        NOVEL_BOOK_NATIVE_RENDERER_READY shouldBe true

        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = false,
        ) shouldBe NovelBookRenderer.RICH_NATIVE_SCROLLED

        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = false,
        ) shouldBe NovelBookRenderer.WEBVIEW_SCROLLED
    }

    @Test
    fun `bionic reading and unsupported content keep the webview renderer`() {
        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = true,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = true,
        ) shouldBe NovelBookRenderer.WEBVIEW_SCROLLED

        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = true,
            nativeBookRendererAvailable = true,
        ) shouldBe NovelBookRenderer.WEBVIEW_SCROLLED
    }

    @Test
    fun `book mode never starts in the legacy reader webview`() {
        // The book engine renders both flows in its own WebView, so the legacy reader WebView stays
        // off in book mode. Turning it on for the paged flow left the screen blank, because that
        // WebView is fed an empty document while in book mode.
        shouldStartInWebView(
            preferWebViewRenderer = false,
            richNativeRendererExperimentalEnabled = false,
            pageReaderEnabled = true,
            contentBlocksCount = 0,
            richContentUnsupportedFeaturesDetected = false,
            bookModeEnabled = true,
        ) shouldBe false

        resolveNovelBookRenderer(
            pageReaderEnabled = false,
            richNativeRendererExperimentalEnabled = true,
            bionicReadingEnabled = false,
            richContentUnsupportedFeaturesDetected = false,
            nativeBookRendererAvailable = true,
        ).usesWebView shouldBe false
    }

    @Test
    fun `flow switching toggles the paginated layout class`() {
        val paginated = buildBookFlowJavascript(paginated = true)
        paginated shouldContain "classList.add('$BOOK_PAGINATED_CLASS')"
        paginated shouldContain "const paginated = true;"

        val scrolled = buildBookFlowJavascript(paginated = false)
        scrolled shouldContain "classList.remove('$BOOK_PAGINATED_CLASS')"
        scrolled shouldContain "const paginated = false;"
    }

    @Test
    fun `page turns move along the axis of the active flow`() {
        val forward = buildBookPageTurnJavascript(delta = 1)
        forward shouldContain "const step = 1;"
        forward shouldContain "scrollLeft"
        forward shouldContain "scrollTop"

        buildBookPageTurnJavascript(delta = -1) shouldContain "const step = -1;"
    }

    @Test
    fun `the paginated flow breaks the book into pages at chapter boundaries`() {
        val css = buildBookSectionsCss()

        css shouldContain "html.$BOOK_PAGINATED_CLASS body"
        css shouldContain "column-width: 100vw !important;"
        css shouldContain "break-after: column !important;"
    }
}
