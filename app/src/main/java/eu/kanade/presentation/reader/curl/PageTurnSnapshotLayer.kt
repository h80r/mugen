package eu.kanade.presentation.reader.curl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Draws [content] once and replays it through a [GraphicsLayer], so a curl renderer can compose the same page
 * subtree several times per animation frame (once live, once or twice as flap backs, once in the alpha-0 pass)
 * without paying for the real draw each time.
 *
 * This is the content-agnostic core of the novel reader's snapshot renderer — the branch that records straight
 * into a layer and never touches a bitmap cache. The novel-specific bitmap cache (keyed on ~40 text-rendering
 * preferences) stays on the novel side; the manga curl viewer never needs it because its heavy page is a live
 * `SubsamplingScaleImageView`, not a captured bitmap.
 *
 * When [externalGraphicsLayer] is supplied, the recording goes into that layer instead of an internally
 * remembered one, so a caller can read back the exact layer this composable draws with — e.g. to reuse it as
 * another composable's back-of-page content — without wrapping it in a second, separately-recorded layer.
 */
@Composable
fun PageTurnSnapshotLayer(
    modifier: Modifier = Modifier,
    externalGraphicsLayer: GraphicsLayer? = null,
    content: @Composable () -> Unit,
) {
    val ownGraphicsLayer = rememberGraphicsLayer()
    val graphicsLayer = externalGraphicsLayer ?: ownGraphicsLayer
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                // GraphicsLayer.clip defaults to false: without it, anything this content draws
                // outside the layer's own recorded bounds is never cut off. That matters here because
                // externalGraphicsLayer can be a cache entry reused across differently-sized/positioned
                // callers over time (e.g. the spread back-content-layer cache) — clip=true guarantees
                // drawLayer() below never bleeds this page's pixels past its own bounds.
                graphicsLayer.clip = true
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
    ) {
        content()
    }
}
