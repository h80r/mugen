package eu.kanade.presentation.reader.novel

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * The relocate bridge is the only channel that reports the book's reading position, so its script
 * has to resolve the same scroll container the DOM commands write to and measure sections against
 * that container. Reading `document.scrollingElement` broke paging (the paginated flow scrolls
 * `body`, so `scrollLeft` was always 0) and `offsetTop` broke resume (it is relative to the nearest
 * positioned ancestor, not the scroller).
 */
class NovelBookRelocateBridgeTest {

    @Test
    fun `relocate script resolves the flow aware scroll container`() {
        val script = buildBookRelocateBridgeJavascript()

        script shouldContain BOOK_PAGINATED_CLASS
        script shouldContain "scroller.scrollLeft"
        script shouldContain "scroller.scrollWidth"
        script shouldContain "document.body"
    }

    @Test
    fun `relocate script measures sections against the scroller in both flows`() {
        val script = buildBookRelocateBridgeJavascript()

        script shouldContain "getBoundingClientRect()"
        script shouldContain "rect.left + scrollLeft"
        script shouldContain "rect.top + scrollTop"
        script shouldNotContain "element.offsetTop"
        script shouldNotContain "element.offsetHeight"
    }

    @Test
    fun `relocate script stays idempotent and pushes through the bridge`() {
        val script = buildBookRelocateBridgeJavascript()

        script shouldContain WEB_BOOK_RELOCATE_BRIDGE_NAME
        script shouldContain WEB_BOOK_RELOCATE_REQUEST_FUNCTION
        script shouldContain "already-installed"
        script shouldContain "bridge.onRelocate(payload)"
    }
}
