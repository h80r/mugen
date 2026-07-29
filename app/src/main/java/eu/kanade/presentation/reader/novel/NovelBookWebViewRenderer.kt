package eu.kanade.presentation.reader.novel

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImageResolver
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookDocument
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineFlow
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookEngineRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookLocation
import eu.kanade.tachiyomi.ui.reader.novel.NovelBookPageTurnResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Dedicated Android WebView adapter for the book engine.
 *
 * It never shares a document with the chapter reader: every spine section is loaded into this
 * WebView as an isolated document, waits for the document's image-stabilized ready event, and only
 * then restores its exact text offset. Renderer events are generation-scoped so a late callback from
 * a replaced document cannot overwrite the current book location.
 */
internal class NovelBookWebViewRenderer(
    private val webView: WebView,
    private val onRelocated: (Int) -> Unit = {},
    private val onDocumentMeasured: (Int, Long, Int) -> Unit = { _, _, _ -> },
    private val onScrollBoundary: (Boolean) -> Unit = {},
    private val readerCss: () -> String = { "" },
    private val resolveResource: (String) -> WebResourceResponse? = { null },
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
) : NovelBookEngineRenderer {

    private val generations = AtomicLong(0L)

    @Volatile
    private var activeGeneration = 0L

    @Volatile
    private var pendingReady: CompletableDeferred<NovelBookRendererReady>? = null

    @Volatile
    private var disposed = false

    private val nativeBridge = NativeBridge()

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = false
        webView.addJavascriptInterface(nativeBridge, NATIVE_BRIDGE_NAME)
        // Book-mode layout problems are invisible from Kotlin, so the document mirrors its own
        // measurements into logcat: adb logcat -s NovelBookWebView.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message() ?: return false
                Log.i(CONSOLE_LOG_TAG, message)
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val requestUrl = request?.url?.toString().orEmpty()
                resolveResource(requestUrl)?.let { return it }
                if (!NovelPluginImage.isSupported(requestUrl)) {
                    return super.shouldInterceptRequest(view, request)
                }
                val image = NovelPluginImageResolver.resolveBlocking(requestUrl)
                    ?: return super.shouldInterceptRequest(view, request)
                return WebResourceResponse(
                    image.mimeType,
                    null,
                    ByteArrayInputStream(image.bytes),
                )
            }
        }
    }

    override suspend fun open(
        document: NovelBookDocument,
        location: NovelBookLocation,
        flow: NovelBookEngineFlow,
    ) {
        val generation = generations.incrementAndGet()
        val ready = CompletableDeferred<NovelBookRendererReady>()
        activeGeneration = generation
        pendingReady?.cancel()
        pendingReady = ready

        val html = buildNovelBookEngineDocumentHtml(
            document = document,
            flow = flow,
            documentGeneration = generation,
            readerCss = readerCss(),
        )
        withContext(Dispatchers.Main.immediate) {
            webView.loadDataWithBaseURL(
                document.baseUrl?.takeIf { it.isNotBlank() } ?: FALLBACK_BASE_URL,
                html,
                HTML_MIME_TYPE,
                HTML_ENCODING,
                null,
            )
        }
        val rendererReady = withTimeout(operationTimeoutMillis) {
            ready.await()
        }
        if (generation != activeGeneration) return
        onDocumentMeasured(
            document.sectionIndex,
            document.chapterId,
            rendererReady.charCount,
        )
        if (generation != activeGeneration) return

        val restored = evaluatePageTurn(
            "window.__anBookEngine && window.__anBookEngine.goTo(${location.charOffset})",
        )
        if (generation != activeGeneration) return
        val moved = restored as? NovelBookPageTurnResult.Moved
            ?: error("Book renderer failed to restore section ${document.sectionIndex}")
        onRelocated(moved.charOffset)
    }

    override suspend fun next(): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.next()")

    override suspend fun previous(): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.previous()")

    override suspend fun relocate(): NovelBookPageTurnResult =
        evaluatePageTurn("window.__anBookEngine && window.__anBookEngine.relocate()")

    suspend fun close() {
        activeGeneration = generations.incrementAndGet()
        pendingReady?.cancel()
        pendingReady = null
        withContext(Dispatchers.Main.immediate) {
            webView.stopLoading()
            webView.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
            webView.loadUrl("about:blank")
        }
    }

    /** Synchronous lifecycle entry point for Compose disposal. */
    fun dispose() {
        if (disposed) return
        disposed = true
        val disposeGeneration = activeGeneration
        pendingReady?.cancel()
        pendingReady = null
        webView.post {
            webView.stopLoading()
            webView.evaluateJavascript(
                "window.__anBookEngine && window.__anBookEngine.relocate()",
            ) { rawResult ->
                if (disposeGeneration == activeGeneration) {
                    val moved = parseNovelBookPageTurnResult(rawResult) as? NovelBookPageTurnResult.Moved
                    if (moved != null) {
                        onRelocated(moved.charOffset)
                    }
                }
                activeGeneration = generations.incrementAndGet()
                webView.removeJavascriptInterface(NATIVE_BRIDGE_NAME)
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    private suspend fun evaluatePageTurn(script: String): NovelBookPageTurnResult {
        val rawResult = withTimeout(operationTimeoutMillis) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<String?> { continuation ->
                    webView.evaluateJavascript(script) { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                }
            }
        }
        return parseNovelBookPageTurnResult(rawResult)
            ?: error("Book renderer returned an invalid navigation result")
    }

    private inner class NativeBridge {
        @JavascriptInterface
        fun onReady(generation: Long, payload: String) {
            if (generation != activeGeneration) return
            val ready = parseNovelBookRendererReady(payload) ?: return
            pendingReady?.complete(ready)
        }

        @JavascriptInterface
        fun onRelocated(generation: Long, payload: String) {
            if (generation != activeGeneration) return
            val moved = parseNovelBookPageTurnResult(payload) as? NovelBookPageTurnResult.Moved ?: return
            webView.post {
                if (generation == activeGeneration) {
                    onRelocated(moved.charOffset)
                }
            }
        }

        /**
         * Reported by the scrolled document when the reader runs out of section: the engine turns
         * that into the adjacent spine section, which is what makes chapters continuous again.
         */
        @JavascriptInterface
        fun onBoundary(generation: Long, direction: String) {
            if (generation != activeGeneration) return
            val forward = when (direction) {
                "end" -> true
                "start" -> false
                else -> return
            }
            webView.post {
                if (generation == activeGeneration) {
                    onScrollBoundary(forward)
                }
            }
        }
    }

    companion object {
        private const val NATIVE_BRIDGE_NAME = "AnBookNative"
        private const val CONSOLE_LOG_TAG = "NovelBookWebView"
        private const val FALLBACK_BASE_URL = "https://localhost/"
        private const val HTML_MIME_TYPE = "text/html"
        private const val HTML_ENCODING = "UTF-8"
        private const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 15_000L
    }
}
