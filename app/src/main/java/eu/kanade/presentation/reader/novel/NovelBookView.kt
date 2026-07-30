package eu.kanade.presentation.reader.novel

import android.webkit.WebView

/**
 * Renderer-independent view layer for book mode.
 *
 * Book mode used to be hard-wired to the WebView because the reader talked to it with raw JavaScript.
 * The book itself (spine, sections, progress) knows nothing about pixels or WebViews, so the renderer
 * is reduced to this small surface: place the reader at a location, step a page, and switch flow. Any
 * renderer that can do those three things can present the book.
 */
internal enum class NovelBookFlow {
    /** One continuous scroll. */
    SCROLLED,

    /** Discrete pages, laid out as one column per viewport (the model foliate/readest use). */
    PAGINATED,
}

/** Renderer that presents the book, plus the flow it implies. */
internal enum class NovelBookRenderer(
    val flow: NovelBookFlow,
    val usesWebView: Boolean,
) {
    WEBVIEW_SCROLLED(flow = NovelBookFlow.SCROLLED, usesWebView = true),
    WEBVIEW_PAGINATED(flow = NovelBookFlow.PAGINATED, usesWebView = true),
    RICH_NATIVE_SCROLLED(flow = NovelBookFlow.SCROLLED, usesWebView = false),
}

/**
 * Whether the native book renderer is mounted in the reader.
 *
 * The native list consumes the same book command stream as the WebView document, so book mode works
 * on both renderers. The flag stays as an explicit kill switch: if the native list ever regresses,
 * book mode falls back to the WebView renderer without touching the policy or the reader.
 */
internal const val NOVEL_BOOK_NATIVE_RENDERER_READY = true

/**
 * Picks the renderer for book mode from the reader settings.
 *
 * Book mode is no longer WebView-only: "pages" maps to the paginated book flow, and the experimental
 * native renderer presents the same book as a native scroll. Bionic reading and content the native
 * renderer cannot express still fall back to the WebView, exactly like in chapter-by-chapter mode.
 */
internal fun resolveNovelBookRenderer(
    pageReaderEnabled: Boolean,
    richNativeRendererExperimentalEnabled: Boolean,
    bionicReadingEnabled: Boolean,
    richContentUnsupportedFeaturesDetected: Boolean,
    /**
     * True when the user configured custom CSS or JavaScript for the reader.
     *
     * Those hooks are applied to the WebView document and have no meaning for Compose text,
     * so silently dropping them in the native renderer would look like the setting stopped
     * working. Honouring the setting by staying on the WebView is the honest behaviour.
     */
    customStylesPresent: Boolean = false,
    nativeBookRendererAvailable: Boolean = NOVEL_BOOK_NATIVE_RENDERER_READY,
): NovelBookRenderer {
    if (pageReaderEnabled) return NovelBookRenderer.WEBVIEW_PAGINATED
    val canUseRichNative = nativeBookRendererAvailable &&
        richNativeRendererExperimentalEnabled &&
        !bionicReadingEnabled &&
        !customStylesPresent &&
        !richContentUnsupportedFeaturesDetected
    return if (canUseRichNative) {
        NovelBookRenderer.RICH_NATIVE_SCROLLED
    } else {
        NovelBookRenderer.WEBVIEW_SCROLLED
    }
}

/** A position inside the book, expressed without any renderer units. */
internal data class NovelBookViewLocation(
    val sectionIndex: Int,
    val sectionFraction: Float,
)

/** What every book renderer must be able to do. */
internal interface NovelBookView {
    /** Places the reader at [location]. */
    fun goTo(location: NovelBookViewLocation)

    /** Steps forward: one page in paginated flow, roughly one viewport in scrolled flow. */
    fun next(transitionStyleName: String = "SLIDE")

    /** Steps backward. */
    fun previous(transitionStyleName: String = "SLIDE")

    /** Switches between scrolled and paginated flow, keeping the reading position. */
    fun setFlow(flow: NovelBookFlow)

    /** Asks the renderer to report its current position through the relocate channel. */
    fun requestRelocate()
}

/** [NovelBookView] backed by the continuous WebView document. */
internal class WebViewNovelBookView(private val view: WebView) : NovelBookView {

    override fun goTo(location: NovelBookViewLocation) {
        evaluate(
            buildScrollToBookSectionJavascript(
                sectionIndex = location.sectionIndex,
                sectionFraction = location.sectionFraction,
            ),
        )
    }

    override fun next(transitionStyleName: String) =
        evaluate(buildBookPageTurnJavascript(delta = 1, transitionStyleName = transitionStyleName))

    override fun previous(transitionStyleName: String) =
        evaluate(buildBookPageTurnJavascript(delta = -1, transitionStyleName = transitionStyleName))

    override fun setFlow(flow: NovelBookFlow) =
        evaluate(buildBookFlowJavascript(paginated = flow == NovelBookFlow.PAGINATED))

    override fun requestRelocate() {
        view.post {
            if (!view.settings.javaScriptEnabled) return@post
            view.requestBookRelocate()
        }
    }

    private fun evaluate(script: String) {
        view.post {
            if (!view.settings.javaScriptEnabled) return@post
            view.evaluateJavascript(script, null)
        }
    }
}
