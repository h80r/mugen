package eu.kanade.presentation.reader.novel.curl

// Vendored from io.github.oleksandrbalan:pagecurl 1.5.1 (Apache 2.0), github.com/oleksandrbalan/pagecurl.

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param externalBackContentLayers When non-null, the back of a flap draws from these externally-supplied layers
 * instead of capturing this instance's own neighbouring `content(Int)` page. Use this when the physical back of a
 * page lives in a different [PageCurl] instance entirely (e.g. a two-page spread, where the back of the right
 * column's page is the left column's page of the next spread). [ExternalBackContentLayers.forward] backs the
 * forward flap (curling `current` away, normally revealing `current + 1`); [ExternalBackContentLayers.backward]
 * backs the backward flap (curling `current - 1` back into place, normally revealing `current`).
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
public fun PageCurl(
    count: Int,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    externalBackContentLayers: ExternalBackContentLayers? = null,
    // Set when this PageCurl is placed inside a horizontally mirrored layer, so an externally supplied back-page
    // layer is not flipped a second time.
    onMirroredSurface: Boolean = false,
    content: @Composable (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier) {
        state.setup(count, constraints)

        val updatedCurrent by rememberUpdatedState(state.current)
        val internalState by rememberUpdatedState(state.internalState ?: return@BoxWithConstraints)

        val updatedConfig by rememberUpdatedState(config)

        val dragGestureModifier = when (val interaction = updatedConfig.dragInteraction) {
            is PageCurlConfig.GestureDragInteraction ->
                Modifier
                    .dragGesture(
                        dragInteraction = interaction,
                        state = internalState,
                        enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                        enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                        scope = scope,
                        onChange = { state.current = updatedCurrent + it },
                    )

            is PageCurlConfig.StartEndDragInteraction ->
                Modifier
                    .dragStartEnd(
                        dragInteraction = interaction,
                        state = internalState,
                        enabledForward = updatedConfig.dragForwardEnabled && updatedCurrent < state.max - 1,
                        enabledBackward = updatedConfig.dragBackwardEnabled && updatedCurrent > 0,
                        scope = scope,
                        onChange = { state.current = updatedCurrent + it },
                    )
        }

        Box(
            Modifier
                .then(dragGestureModifier)
                .tapGesture(
                    config = updatedConfig,
                    scope = scope,
                    onTapForward = state::next,
                    onTapBackward = state::prev,
                ),
        ) {
            // Wrap in key to synchronize state updates
            key(updatedCurrent, internalState.forward.value, internalState.backward.value) {
                // When independentBackPageEnabled and no externalBackContentLayers were supplied, the back of a
                // flap shows this instance's own neighbouring page:
                //  - the forward flap curls page `current` away and should reveal `current + 1` underneath, so its
                //    back is painted with a layer of `current + 1`'s content.
                //  - the backward flap curls page `current - 1` back into place and should reveal `current`
                //    underneath, so its back is painted with a layer of `current`'s content.
                // Each layer is recorded once, during that page's own normal draw pass (see selfContentLayer on
                // drawCurl / the plain recording Box below), so no page is composed or drawn an extra time.
                //
                // externalBackContentLayers instead lets the caller wire in a layer recorded by a *different*
                // PageCurl instance entirely (see the doc on the parameter) — used when this instance only ever
                // shows half of a physical page sequence (e.g. one column of a two-page spread), so the true back
                // of its page lives in the sibling instance, not in this instance's own content(Int) lambda.
                val nextPageLayer = if (externalBackContentLayers != null) {
                    externalBackContentLayers.forward
                } else if (updatedConfig.independentBackPageEnabled && updatedCurrent + 1 < state.max) {
                    rememberGraphicsLayer()
                } else {
                    null
                }
                val currentPageLayer = if (externalBackContentLayers != null) {
                    externalBackContentLayers.backward
                } else if (updatedConfig.independentBackPageEnabled && updatedCurrent > 0) {
                    rememberGraphicsLayer()
                } else {
                    null
                }
                val capturesOwnLayers = externalBackContentLayers == null

                if (updatedCurrent + 1 < state.max) {
                    if (capturesOwnLayers && nextPageLayer != null) {
                        Box(
                            Modifier.drawWithContent {
                                nextPageLayer.record { this@drawWithContent.drawContent() }
                                drawContent()
                            },
                        ) {
                            content(updatedCurrent + 1)
                        }
                    } else {
                        content(updatedCurrent + 1)
                    }
                }

                if (updatedCurrent < state.max) {
                    val forward = internalState.forward.value
                    Box(
                        Modifier.drawCurl(
                            config = updatedConfig,
                            posA = forward.top,
                            posB = forward.bottom,
                            backContentLayer = nextPageLayer,
                            selfContentLayer = if (capturesOwnLayers) currentPageLayer else null,
                            backContentLayerOnMirroredSurface = onMirroredSurface,
                        ),
                    ) {
                        content(updatedCurrent)
                    }
                }

                if (updatedCurrent > 0) {
                    val backward = internalState.backward.value
                    Box(
                        Modifier.drawCurl(
                            config = updatedConfig,
                            posA = backward.top,
                            posB = backward.bottom,
                            backContentLayer = currentPageLayer,
                            backContentLayerOnMirroredSurface = onMirroredSurface,
                        ),
                    ) {
                        content(updatedCurrent - 1)
                    }

                    // Page `current - 1` is composed only in the flap above, and whenever the backward fold is
                    // parked at the left edge — which it always is for a caller that drives both directions
                    // through the forward mechanism, as the two-page spread renderer does with
                    // dragBackwardEnabled false on both columns — drawCurl draws nothing at all. The page is
                    // composed but never drawn, so any recording that rides on its own draw pass (such as
                    // NovelPageTurnSnapshotRenderer's GraphicsLayer) never runs, and another instance asking for
                    // that page's layer as its back-of-page gets an instantiated-but-never-recorded 0x0 layer that
                    // paints as a blank flap. Only one turn direction ever showed this, because the pages the
                    // other direction needs happen to live in slots that do get drawn.
                    //
                    // Composing it a second time at alpha 0 gives that page a draw pass of its own, so whatever
                    // recording its content sets up runs every frame. Nothing reaches the screen, and unlike
                    // recording it through drawCurl's selfContentLayer this doesn't nest a record{} call inside
                    // the one the page's own content already makes into the same layer — which throws
                    // "Recording currently in progress - missing #endRecording() call?".
                    if (externalBackContentLayers != null) {
                        Box(Modifier.graphicsLayer(alpha = 0f)) {
                            content(updatedCurrent - 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param count The count of pages.
 * @param key The lambda to provide stable key for each item. Useful when adding and removing items before current page.
 * @param modifier The modifier for this composable.
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param config The configuration for PageCurl.
 * @param externalBackContentLayers See the overload above for details.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
public fun PageCurl(
    count: Int,
    key: (Int) -> Any,
    modifier: Modifier = Modifier,
    state: PageCurlState = rememberPageCurlState(),
    config: PageCurlConfig = rememberPageCurlConfig(),
    externalBackContentLayers: ExternalBackContentLayers? = null,
    // Set when this PageCurl is placed inside a horizontally mirrored layer, so an externally supplied back-page
    // layer is not flipped a second time.
    onMirroredSurface: Boolean = false,
    content: @Composable (Int) -> Unit,
) {
    var lastKey by remember(state.current) { mutableStateOf(if (count > 0) key(state.current) else null) }

    remember(count) {
        val newKey = if (count > 0) key(state.current) else null
        if (newKey != lastKey) {
            val index = List(count, key).indexOf(lastKey).coerceIn(0, count - 1)
            lastKey = newKey
            state.current = index
        }
        count
    }

    PageCurl(
        count = count,
        state = state,
        config = config,
        externalBackContentLayers = externalBackContentLayers,
        onMirroredSurface = onMirroredSurface,
        content = content,
        modifier = modifier,
    )
}

/**
 * Shows the pages which may be turned by drag or tap gestures.
 *
 * @param state The state of the PageCurl. Use this to programmatically change the current page or observe changes.
 * @param modifier The modifier for this composable.
 * @param content The content lambda to provide the page composable. Receives the page number.
 */
@ExperimentalPageCurlApi
@Composable
@Deprecated("Specify 'max' as 'count' in PageCurl composable.")
public fun PageCurl(
    state: PageCurlState,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    PageCurl(
        count = state.max,
        state = state,
        modifier = modifier,
        content = content,
    )
}

/**
 * Externally-supplied [GraphicsLayer]s to paint on the back of a [PageCurl]'s flaps, in place of layers this
 * instance would otherwise capture from its own [PageCurl] `content(Int)` lambda. See the `externalBackContentLayers`
 * parameter on [PageCurl] for when and why to use this.
 *
 * @param forward Layer painted on the back of the forward flap (curling `current` away).
 * @param backward Layer painted on the back of the backward flap (curling `current - 1` back into place).
 */
@ExperimentalPageCurlApi
public data class ExternalBackContentLayers(
    val forward: GraphicsLayer?,
    val backward: GraphicsLayer?,
)
