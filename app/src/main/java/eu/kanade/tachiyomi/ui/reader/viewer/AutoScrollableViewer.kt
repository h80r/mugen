package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * Capability marker for a [Viewer] that supports automatic page/scroll advancement, so the
 * reader activity can drive auto-scroll without a per-implementation `when`. Implemented by the
 * pager, webtoon and manga-curl viewers; the method signatures already match across them.
 */
interface AutoScrollableViewer {
    fun startAutoScroll(speed: Int? = null)
    fun stopAutoScroll()
    fun setAutoScrollCooldown(delayMs: Long)
}
