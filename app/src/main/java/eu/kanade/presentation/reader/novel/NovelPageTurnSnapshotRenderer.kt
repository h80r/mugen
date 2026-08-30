package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun NovelPageTurnSnapshotRenderer(
    snapshotKey: NovelPageTurnSnapshotKey,
    snapshotCache: NovelPageTurnSnapshotCache<ImageBitmap>,
    preferCachedBitmap: Boolean = true,
    modifier: Modifier = Modifier,
    // When supplied, this layer is used (and recorded into) instead of an internally-remembered one, so a caller
    // can read the exact same layer this renderer draws with — e.g. to reuse it as another composable's back-of-page
    // content — without a second, separately-recorded GraphicsLayer wrapping this one's draw output.
    externalGraphicsLayer: GraphicsLayer? = null,
    content: @Composable () -> Unit,
) {
    if (!preferCachedBitmap) {
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
        return
    }

    val graphicsLayer = rememberGraphicsLayer()
    var bitmap by remember(snapshotKey) {
        mutableStateOf(snapshotCache[snapshotKey])
    }

    bitmap?.let { cachedBitmap ->
        Image(
            bitmap = cachedBitmap,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                graphicsLayer.record {
                    this@drawWithContent.drawContent()
                }
                drawLayer(graphicsLayer)
            },
    ) {
        content()
    }

    LaunchedEffect(snapshotKey) {
        withFrameNanos { }
        runCatching { graphicsLayer.toImageBitmap() }
            .onSuccess { captured ->
                bitmap = captured
                snapshotCache.put(snapshotKey, captured)
            }
    }
}
