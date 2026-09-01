package eu.kanade.presentation.reader.curl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalGraphicsContext

/**
 * Shares [GraphicsLayer]s between the two independent [PageCurl]
 * instances of a two-page spread, keyed by real (flat, 0-indexed) content page index.
 *
 * The physical back of a spread page always lives across the spine, in the *other* column's own [PageCurl]
 * instance (see the spread page-turn renderer's doc comment): the back of real page N is real page N+1 when N sits
 * on the right/forward-turning column, or N-1 when N sits on the left/backward-turning column. Since each column
 * only ever composes its own half of the spread, a column cannot capture a layer for content it never draws — this
 * cache lets the *other* column's own normal draw pass leave a layer behind for it to pick up.
 *
 * Layers are created and owned by this cache itself (via [GraphicsContext.createGraphicsLayer], not
 * `rememberGraphicsLayer()`), and deliberately NOT `remember`-scoped to whichever composable first asks for one.
 * [PageCurl] wraps its `content(Int)` calls in a `key(...)` block keyed on
 * animation progress, so every drag/snap tears down and rebuilds that subtree's composition state — a layer
 * `remember`-owned by a call site inside that subtree would be torn down and orphaned the moment its owning scope
 * is disposed, leaving the cache holding a dead reference that draws nothing. Owning creation here, in a scope tied
 * to the spread page-turn renderer itself (which never gets torn down by page turns), keeps each real-page-index's
 * layer alive across any number of sibling-column recompositions.
 *
 * Backed by a [SnapshotStateMap] (not a plain `MutableMap`) so that a read of one key inside composition
 * subscribes to *that key specifically*: when the sibling column later writes a layer for a real page index this
 * column already read as a miss (null), that read recomposes automatically instead of staying stale until some
 * unrelated recomposition happens to re-run it. This matters because reads and writes for the same key routinely
 * land in different frames — the column revealing a page as its neighbour's back-of-page often composes before the
 * column that owns that page has had a chance to register its layer.
 *
 * Layers are requested lazily and are meant to be transient: a real page index only has a live layer while some
 * column is actively displaying it as front content (typically the current spread and its immediate neighbours,
 * since [PageCurl] only composes `current - 1`, `current`, `current + 1`). A miss (the neighbouring column hasn't
 * composed that page yet, or never will, e.g. the spread hasn't been visited) simply yields `null`, which the
 * curl renderer already treats as "fall back to the solid tinted back page" — never a crash or blank flap.
 */
internal class SpreadCurlBackContentLayerCache(private val graphicsContext: GraphicsContext) {
    private val layers = mutableStateMapOf<Int, GraphicsLayer>()

    fun get(realPageIndex: Int): GraphicsLayer? = layers[realPageIndex]

    fun getOrCreate(realPageIndex: Int): GraphicsLayer {
        return layers.getOrPut(realPageIndex) { graphicsContext.createGraphicsLayer() }
    }

    fun releaseAll() {
        layers.values.forEach { graphicsContext.releaseGraphicsLayer(it) }
        layers.clear()
    }
}

@Composable
internal fun rememberSpreadCurlBackContentLayerCache(): SpreadCurlBackContentLayerCache {
    val graphicsContext = LocalGraphicsContext.current
    val cache = remember(graphicsContext) { SpreadCurlBackContentLayerCache(graphicsContext) }
    DisposableEffect(cache) {
        onDispose { cache.releaseAll() }
    }
    return cache
}

/**
 * Registers (creating if needed) a layer for [realPageIndex] in [cache]. The returned [GraphicsLayer] is owned by
 * [cache], not by the calling composable's own composition scope — safe to call from inside a subtree that gets
 * torn down and rebuilt on every page turn (see the class doc on [SpreadCurlBackContentLayerCache]).
 */
internal fun registeredSpreadCurlBackContentLayer(
    cache: SpreadCurlBackContentLayerCache,
    realPageIndex: Int,
): GraphicsLayer {
    return cache.getOrCreate(realPageIndex)
}
