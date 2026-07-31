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
}
