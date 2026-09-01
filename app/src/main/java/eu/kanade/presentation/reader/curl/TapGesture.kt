package eu.kanade.presentation.reader.curl

// Vendored from io.github.oleksandrbalan:pagecurl 1.5.1 (Apache 2.0), github.com/oleksandrbalan/pagecurl.

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ExperimentalPageCurlApi
internal fun Modifier.tapGesture(
    config: PageCurlConfig,
    scope: CoroutineScope,
    onTapForward: suspend () -> Unit,
    onTapBackward: suspend () -> Unit,
): Modifier = pointerInput(config) {
    val tapInteraction = config.tapInteraction as? PageCurlConfig.TargetTapInteraction ?: return@pointerInput

    awaitEachGesture {
        // Do NOT consume the down or the up: the manga curl viewer keeps a live
        // ReaderPageImageView behind the curl, and a pinch / pan / double-tap that starts here must
        // still reach it. A plain single tap resolves nav below without consuming, so the image's
        // own single-tap listener (a no-op in the curl viewer) sees it too and a double-tap still
        // reaches the image's double-tap-to-zoom.
        val down = awaitFirstDown(requireUnconsumed = false)
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture

        if ((down.position - up.position).getDistance() > viewConfiguration.touchSlop) {
            return@awaitEachGesture
        }
        if (currentEvent.changes.count { it.pressed } > 0) {
            // Another finger is still down — this is part of a multi-touch gesture, not a tap.
            return@awaitEachGesture
        }

        if (config.tapCustomEnabled && config.onCustomTap(this, size, up.position)) {
            return@awaitEachGesture
        }

        if (config.tapForwardEnabled && tapInteraction.forward.target.multiply(size).contains(up.position)) {
            scope.launch {
                onTapForward()
            }
            return@awaitEachGesture
        }

        if (config.tapBackwardEnabled && tapInteraction.backward.target.multiply(size).contains(up.position)) {
            scope.launch {
                onTapBackward()
            }
            return@awaitEachGesture
        }
    }
}
