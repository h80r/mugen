package eu.kanade.presentation.reader.curl

// Vendored from io.github.oleksandrbalan:pagecurl 1.5.1 (Apache 2.0), github.com/oleksandrbalan/pagecurl.

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import eu.kanade.presentation.reader.curl.PageCurlConfig.DragInteraction.PointerBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ExperimentalPageCurlApi
internal fun Modifier.dragGesture(
    dragInteraction: PageCurlConfig.GestureDragInteraction,
    state: PageCurlState.InternalState,
    enabledForward: Boolean,
    enabledBackward: Boolean,
    scope: CoroutineScope,
    onChange: (Int) -> Unit,
): Modifier = this.composed {
    val isEnabledForward = rememberUpdatedState(enabledForward)
    val isEnabledBackward = rememberUpdatedState(enabledBackward)

    pointerInput(state) {
        val forwardTargetRect by lazy { dragInteraction.forward.target.multiply(size) }
        val backwardTargetRect by lazy { dragInteraction.backward.target.multiply(size) }

        val forwardConfig = DragConfig(
            edge = state.forward,
            start = state.rightEdge,
            end = state.leftEdge,
            isEnabled = { isEnabledForward.value },
            isDragSucceed = { start, end -> end.x < start.x },
            onChange = { onChange(+1) },
        )
        val backwardConfig = DragConfig(
            edge = state.backward,
            start = state.leftEdge,
            end = state.rightEdge,
            isEnabled = { isEnabledBackward.value },
            isDragSucceed = { start, end -> end.x > start.x },
            onChange = { onChange(-1) },
        )

        detectCurlGestures(
            scope = scope,
            newEdgeCreator = when (dragInteraction.pointerBehavior) {
                PointerBehavior.Default -> NewEdgeCreator.Default()
                PointerBehavior.PageEdge -> NewEdgeCreator.PageEdge()
            },
            // Both directions disabled = the page is zoomed; let the drag reach the image behind.
            bailNow = { !isEnabledForward.value && !isEnabledBackward.value },
            getConfig = { start, end ->
                val config = if (forwardTargetRect.contains(start) && end.x < start.x) {
                    forwardConfig
                } else if (backwardTargetRect.contains(start) && end.x > start.x) {
                    backwardConfig
                } else {
                    null
                }

                if (config != null) {
                    scope.launch {
                        state.animateJob?.cancel()
                        state.reset()
                    }
                }

                config
            },
        )
    }
}
