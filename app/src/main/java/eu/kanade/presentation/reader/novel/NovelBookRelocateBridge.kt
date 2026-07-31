package eu.kanade.presentation.reader.novel

import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Push bridge that reports the book document's reading position to the reader.
 *
 * Book mode used to poll the document every 400 ms, which burned a JS evaluation, a JSON parse and a
 * progress write per tick: scrolling stuttered, the log filled up and the reader chrome could not be
 * opened because the JS thread was busy. The document now pushes a `relocate` event instead, coalesced
 * to at most one per animation frame (the same approach foliate/readest use), so the reader only does
 * work when the position actually changed.
 */
internal const val WEB_BOOK_RELOCATE_BRIDGE_NAME = "__an_book_relocate_bridge__"

/** Global installed by the bridge script, used to request an immediate relocate event. */
internal const val WEB_BOOK_RELOCATE_REQUEST_FUNCTION = "__anBookRelocate"

/**
 * Installs the relocate listeners in the book document.
 *
 * The payload is exactly the shape [buildBookSectionMetricsJavascript] returns, so the Kotlin side can
 * keep using [parseNovelBookDocumentMetrics]. Installation is idempotent: re-running the script after a
 * settings change or a re-render does not add duplicate listeners.
 */
internal fun buildBookRelocateBridgeJavascript(
    bridgeName: String = WEB_BOOK_RELOCATE_BRIDGE_NAME,
): String {
    return """
        (function() {
            const bridge = window['$bridgeName'];
            if (!bridge) return 'no-bridge';
            if (window.__anBookRelocateInstalled) return 'already-installed';
            window.__anBookRelocateInstalled = true;

            let pending = false;
            let lastPayload = '';

            const collect = function() {
                // The relocate bridge is the ONLY channel that reports the reading position, so it
                // must resolve the same scroll container the DOM commands write to. It used to read
                // `document.scrollingElement` (the `html` element) in both flows, but the paginated
                // flow makes `html` `overflow: hidden` and puts the horizontal column scroll on
                // `body`: `scrollLeft` was therefore always 0, the current section always resolved to
                // the first one, progress was never advanced or persisted, and the window never moved
                // past the first sections. `offsetTop`/`offsetHeight` had the same class of bug in the
                // scrolled flow: they are measured against the nearest positioned ancestor, not the
                // scroller, which is where the large resume offset came from.
                const paginated = $BOOK_PAGINATED_JS;
                const scroller = $BOOK_SCROLLER_JS;
                if (!scroller) {
                    return JSON.stringify({
                        scrollTop: 0,
                        viewportHeight: 0,
                        contentHeight: 0,
                        sections: [],
                    });
                }
                const scrollLeft = scroller.scrollLeft || 0;
                const scrollTop = scroller.scrollTop || 0;
                const nodes = document.querySelectorAll('section.$BOOK_SECTION_CLASS');
                const sections = Array.from(nodes).map(function(element) {
                    // Paginated flow pages horizontally, so the same fields carry the horizontal
                    // axis: the Kotlin side keeps one position model for both flows.
                    const rect = element.getBoundingClientRect();
                    return {
                        index: parseInt(element.getAttribute('data-an-section') || '-1', 10),
                        chapterId: element.getAttribute('data-an-chapter') || '',
                        top: Math.round(paginated ? (rect.left + scrollLeft) : (rect.top + scrollTop)),
                        height: Math.round(paginated ? rect.width : rect.height),
                        pruned: element.getAttribute('data-an-placeholder') === '1',
                    };
                });
                return JSON.stringify({
                    scrollTop: paginated ? scrollLeft : scrollTop,
                    viewportHeight: paginated ? (window.innerWidth || 0) : (window.innerHeight || 0),
                    contentHeight: paginated ? (scroller.scrollWidth || 0) : (scroller.scrollHeight || 0),
                    sections: sections,
                });
            };

            const commitRelocate = function() {
                pending = false;
                const payload = collect();
                if (payload === lastPayload) return;
                lastPayload = payload;
                bridge.onRelocate(payload);
            };

            const scheduleRelocate = function() {
                if (pending) return;
                pending = true;
                if (document.hidden || !window.requestAnimationFrame) {
                    commitRelocate();
                    return;
                }
                window.requestAnimationFrame(commitRelocate);
            };

            window.$WEB_BOOK_RELOCATE_REQUEST_FUNCTION = scheduleRelocate;
            document.addEventListener('scroll', scheduleRelocate, { passive: true, capture: true });
            window.addEventListener('scroll', scheduleRelocate, { passive: true });
            window.addEventListener('resize', scheduleRelocate, { passive: true });
            document.addEventListener('visibilitychange', scheduleRelocate, true);
            if (window.ResizeObserver && document.body) {
                if (window.__anBookRelocateObserver) {
                    window.__anBookRelocateObserver.disconnect();
                }
                const observer = new ResizeObserver(scheduleRelocate);
                observer.observe(document.body);
                window.__anBookRelocateObserver = observer;
            }
            scheduleRelocate();
            return 'ok';
        })();
    """.trimIndent()
}

/**
 * Asks the document for one relocate event, e.g. right after a section was appended, pruned or the
 * reader jumped to a new location. Cheap and safe to call before the script is installed.
 */
internal fun buildRequestBookRelocateJavascript(): String {
    return """
        (function() {
            const request = window.$WEB_BOOK_RELOCATE_REQUEST_FUNCTION;
            if (typeof request !== 'function') return 'not-installed';
            request();
            return 'ok';
        })();
    """.trimIndent()
}

internal class NovelBookRelocateBridge(
    private val view: WebView,
    private val onRelocate: (NovelBookDocumentMetrics) -> Unit,
) {
    @JavascriptInterface
    fun onRelocate(payloadJson: String) {
        val metrics = parseNovelBookDocumentMetrics(payloadJson) ?: return
        if (metrics.isEmpty) return
        // JavascriptInterface callbacks arrive on a WebView worker thread; reader state is main-thread.
        view.post { onRelocate(metrics) }
    }
}

/** (Re)registers the relocate bridge. Safe to call on every recomposition of the book document. */
internal fun WebView.registerBookRelocateBridge(
    onRelocate: (NovelBookDocumentMetrics) -> Unit,
) {
    runCatching { removeJavascriptInterface(WEB_BOOK_RELOCATE_BRIDGE_NAME) }
    addJavascriptInterface(
        NovelBookRelocateBridge(view = this, onRelocate = onRelocate),
        WEB_BOOK_RELOCATE_BRIDGE_NAME,
    )
}

/** Installs the relocate listeners in the currently loaded book document. */
internal fun WebView.installBookRelocateBridgeScript(onComplete: ((String?) -> Unit)? = null) {
    evaluateJavascript(buildBookRelocateBridgeJavascript(), onComplete)
}

/** Requests a single relocate event from the currently loaded book document. */
internal fun WebView.requestBookRelocate(onComplete: ((String?) -> Unit)? = null) {
    evaluateJavascript(buildRequestBookRelocateJavascript(), onComplete)
}
