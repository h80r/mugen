package eu.kanade.presentation.reader.novel.curl

// Vendored from io.github.oleksandrbalan:pagecurl 1.5.1 (Apache 2.0), github.com/oleksandrbalan/pagecurl.

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.lang.Float.max
import kotlin.math.atan2

@ExperimentalPageCurlApi
internal fun Modifier.drawCurl(
    config: PageCurlConfig,
    posA: Offset,
    posB: Offset,
    // Pre-recorded layer of the neighbouring page's content. When non-null and
    // config.independentBackPageEnabled is true, the back of the flap draws this layer instead of a mirrored,
    // tinted copy of this node's own content.
    backContentLayer: GraphicsLayer? = null,
    // When non-null, this node's own pristine (unclipped, unmirrored) content is recorded into this layer every
    // draw pass, so another flap elsewhere can use it as its backContentLayer without a second composition pass.
    selfContentLayer: GraphicsLayer? = null,
    // Set when this whole PageCurl sits inside a horizontally mirrored layer. The back-page layer needs exactly
    // one horizontal flip to read the right way round; on a mirrored surface that flip already comes from the
    // surface itself, so this suppresses the one drawCurl would otherwise apply.
    backContentLayerOnMirroredSurface: Boolean = false,
): Modifier = drawWithCache {
    // Fast-check if curl is in left most position (gesture is fully completed)
    // In such case do not bother and draw nothing
    if (posA == size.toRect().topLeft && posB == size.toRect().bottomLeft) {
        return@drawWithCache drawNothing(selfContentLayer)
    }

    // Fast-check if curl is in right most position (gesture is not yet started)
    // In such case do not bother and draw the full content
    if (posA == size.toRect().topRight && posB == size.toRect().bottomRight) {
        return@drawWithCache drawOnlyContent(selfContentLayer)
    }

    // Find the intersection of the curl line ([posA, posB]) and top and bottom sides, so that we may clip and mirror
    // content correctly
    val topIntersection = lineLineIntersection(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        posA,
        posB,
    )
    val bottomIntersection = lineLineIntersection(
        Offset(0f, size.height),
        Offset(size.width, size.height),
        posA,
        posB,
    )

    // Should not really happen, but in case there is not intersection (curl line is horizontal), just draw the full
    // content instead
    if (topIntersection == null || bottomIntersection == null) {
        return@drawWithCache drawOnlyContent(selfContentLayer)
    }

    // Limit x coordinates of both intersections to be at least 0, so that page do not look like teared from the book
    val topCurlOffset = Offset(max(0f, topIntersection.x), topIntersection.y)
    val bottomCurlOffset = Offset(max(0f, bottomIntersection.x), bottomIntersection.y)

    // That is the easy part, prepare a lambda to draw the content clipped by the curl line
    val drawClippedContent = prepareClippedContent(topCurlOffset, bottomCurlOffset)
    // That is the tricky part, prepare a lambda to draw the back-page with the shadow
    val drawCurl = prepareCurl(
        config,
        topCurlOffset,
        bottomCurlOffset,
        backContentLayer,
        backContentLayerOnMirroredSurface,
    )

    onDrawWithContent {
        recordSelfContentLayer(selfContentLayer)
        drawClippedContent()
        drawCurl()
    }
}

/**
 * Records this node's pristine (unclipped) content into [selfContentLayer], if supplied. No-op otherwise.
 */
private fun ContentDrawScope.recordSelfContentLayer(selfContentLayer: GraphicsLayer?) {
    if (selfContentLayer == null) return
    selfContentLayer.record {
        this@recordSelfContentLayer.drawContent()
    }
}

/**
 * The simple method to draw the whole unmodified content.
 */
private fun CacheDrawScope.drawOnlyContent(selfContentLayer: GraphicsLayer? = null): DrawResult =
    onDrawWithContent {
        recordSelfContentLayer(selfContentLayer)
        drawContent()
    }

/**
 * The simple method to draw nothing.
 */
private fun CacheDrawScope.drawNothing(selfContentLayer: GraphicsLayer? = null): DrawResult =
    onDrawWithContent {
        recordSelfContentLayer(selfContentLayer)
        /* Empty */
    }

@ExperimentalPageCurlApi
private fun CacheDrawScope.prepareClippedContent(
    topCurlOffset: Offset,
    bottomCurlOffset: Offset,
): ContentDrawScope.() -> Unit {
    // Make a quadrilateral from the left side to the intersection points
    val path = Path()
    path.lineTo(topCurlOffset.x, topCurlOffset.y)
    path.lineTo(bottomCurlOffset.x, bottomCurlOffset.y)
    path.lineTo(0f, size.height)
    return result@{
        // Draw a content clipped by the constructed path
        clipPath(path) {
            this@result.drawContent()
        }
    }
}

@ExperimentalPageCurlApi
private fun CacheDrawScope.prepareCurl(
    config: PageCurlConfig,
    topCurlOffset: Offset,
    bottomCurlOffset: Offset,
    backContentLayer: GraphicsLayer?,
    backContentLayerOnMirroredSurface: Boolean = false,
): ContentDrawScope.() -> Unit {
    // Build a quadrilateral of the part of the page which should be mirrored as the back-page
    // In all cases polygon should have 4 points, even when back-page is only a small "corner" (with 3 points) due to
    // the shadow rendering, otherwise it will create a visual artifact when switching between 3 and 4 points polygon
    val polygon = Polygon(
        sequence {
            // Find the intersection of the curl line and right side
            // If intersection is found adds to the polygon points list
            suspend fun SequenceScope<Offset>.yieldEndSideInterception() {
                val offset = lineLineIntersection(
                    topCurlOffset,
                    bottomCurlOffset,
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                ) ?: return
                yield(offset)
                yield(offset)
            }

            // In case top intersection lays in the bounds of the page curl, take 2 points from the top side, otherwise
            // take the interception with a right side
            if (topCurlOffset.x < size.width) {
                yield(topCurlOffset)
                yield(Offset(size.width, topCurlOffset.y))
            } else {
                yieldEndSideInterception()
            }

            // In case bottom intersection lays in the bounds of the page curl, take 2 points from the bottom side,
            // otherwise take the interception with a right side
            if (bottomCurlOffset.x < size.width) {
                yield(Offset(size.width, size.height))
                yield(bottomCurlOffset)
            } else {
                yieldEndSideInterception()
            }
        }.toList(),
    )

    // Calculate the angle in radians between X axis and the curl line, this is used to rotate mirrored content to the
    // right position of the curled back-page
    val lineVector = topCurlOffset - bottomCurlOffset
    val angle = Math.PI.toFloat() - atan2(lineVector.y, lineVector.x) * 2

    // Prepare a lambda to draw the shadow of the back-page
    val drawShadow = prepareShadow(config, polygon, angle)
    val externalBackContentLayer = backContentLayer.takeIf { config.independentBackPageEnabled }

    return result@{
        withTransform({
            // Mirror in X axis: this positions the clip/shadow geometry on the correct side of the fold line
            // (always needed) and, for the classic replay of this node's own content, also mirrors that content to
            // simulate seeing the current page's ink through the back of the thin paper.
            scale(-1f, 1f, pivot = bottomCurlOffset)
            // Rotate the drawing according to the curl line
            rotateRad(angle, pivot = bottomCurlOffset)
        }) {
            // Draw shadow first
            this@result.drawShadow()

            // And finally draw the back-page: either the neighbouring page's own content (when
            // independentBackPageEnabled and a layer was supplied), or the classic mirrored, tinted copy of this
            // node's own content.
            clipPath(polygon.toPath()) {
                if (externalBackContentLayer != null) {
                    // Paper is opaque. The recorded layer carries only what the page composable painted, and the
                    // reader's backgrounds can be fully transparent (OLED/dark themes let the window colour show
                    // through), so without an opaque base the page underneath reads straight through the flap and
                    // the two texts blend together — the "transparent curl" this looked like on device.
                    drawRect(config.backPageColor)

                    // Drawn *inside* the fold's mirror+rotate, exactly like the classic drawContent() branch below,
                    // so the back-page's pixels move with the paper instead of sliding around independently of it.
                    //
                    // The fold's scale(-1f, 1f) is what makes a flap's reverse side read as paper, and it must stay.
                    // But this layer holds a *different*, already right-way-round page rather than a see-through
                    // replay of this node, so its text alone has to be flipped back. Mirroring about the node's own
                    // horizontal centre (a value fixed for the whole gesture) does exactly that while leaving the
                    // fold's geometry untouched — mirroring about bottomCurlOffset instead would re-introduce the
                    // per-frame pivot movement that made the text drift across the page.
                    //
                    // On a mirrored surface that flip is already provided by the surface itself, so applying it
                    // here too would be the second one and the back page would read backwards — which it did, on
                    // that column only. There, the layer is drawn as-is.
                    if (backContentLayerOnMirroredSurface) {
                        drawLayer(externalBackContentLayer)
                    } else {
                        withTransform({ scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
                            drawLayer(externalBackContentLayer)
                        }
                    }
                } else {
                    this@result.drawContent()

                    val overlayAlpha = 1f - config.backPageContentAlpha
                    drawRect(config.backPageColor.copy(alpha = overlayAlpha))
                }
            }
        }
    }
}

@ExperimentalPageCurlApi
private fun CacheDrawScope.prepareShadow(
    config: PageCurlConfig,
    polygon: Polygon,
    angle: Float,
): ContentDrawScope.() -> Unit {
    // Quick exit if no shadow is requested
    if (config.shadowAlpha == 0f || config.shadowRadius == 0.dp) {
        return { /* No shadow is requested */ }
    }

    // Prepare shadow parameters
    val radius = config.shadowRadius.toPx()
    val shadowColor = config.shadowColor.copy(alpha = config.shadowAlpha).toArgb()
    val transparent = config.shadowColor.copy(alpha = 0f).toArgb()
    val shadowOffset = Offset(-config.shadowOffset.x.toPx(), config.shadowOffset.y.toPx())
        .rotate(2 * Math.PI.toFloat() - angle)

    // Prepare shadow paint with a shadow layer
    val paint = Paint().apply {
        val frameworkPaint = asFrameworkPaint()
        frameworkPaint.color = transparent
        frameworkPaint.setShadowLayer(
            config.shadowRadius.toPx(),
            shadowOffset.x,
            shadowOffset.y,
            shadowColor,
        )
    }

    // Hardware acceleration supports setShadowLayer() only on API 28 and above, thus to support previous API versions
    // draw a shadow to the bitmap instead
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        prepareShadowApi28(radius, paint, polygon)
    } else {
        prepareShadowImage(radius, paint, polygon)
    }
}

private fun prepareShadowApi28(
    radius: Float,
    paint: Paint,
    polygon: Polygon,
): ContentDrawScope.() -> Unit = {
    drawIntoCanvas {
        it.nativeCanvas.drawPath(
            polygon
                .offset(radius).toPath()
                .asAndroidPath(),
            paint.asFrameworkPaint(),
        )
    }
}

private fun CacheDrawScope.prepareShadowImage(
    radius: Float,
    paint: Paint,
    polygon: Polygon,
): ContentDrawScope.() -> Unit {
    // Increase the size a little bit so that shadow is not clipped
    val bitmap = Bitmap.createBitmap(
        (size.width + radius * 4).toInt(),
        (size.height + radius * 4).toInt(),
        Bitmap.Config.ARGB_8888,
    )
    Canvas(bitmap).apply {
        drawPath(
            polygon
                // As bitmap size is increased we should translate the polygon so that shadow remains in center
                .translate(Offset(2 * radius, 2 * radius))
                .offset(radius).toPath()
                .asAndroidPath(),
            paint.asFrameworkPaint(),
        )
    }

    return {
        drawIntoCanvas {
            // As bitmap size is increased we should shift the drawing so that shadow remains in center
            it.nativeCanvas.drawBitmap(bitmap, -2 * radius, -2 * radius, null)
        }
    }
}
