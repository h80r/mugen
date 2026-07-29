package eu.kanade.presentation.reader.novel.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A FrameLayout container that guards against Jetpack Compose re-entrancy focus crashes
 * ("Cannot start a writer when another writer is pending") during view detachment or
 * seamless layout updates in LazyLayout / SubcomposeLayout.
 *
 * When an AndroidView is detached by Compose, Android's ViewGroup.removeViewInLayout()
 * automatically clears focus on focused child views, triggering a rootViewRequestFocus()
 * call that re-enters Compose layout (measureAndLayout) while SlotTable writer is open.
 *
 * This container intercepts focus search and focus clearing requests from its children
 * during detachment, preventing focus requests from bubbling up to AndroidComposeView.
 */
open class SeamlessFocusGuardLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var isDetaching = false

    override fun onDetachedFromWindow() {
        isDetaching = true
        super.onDetachedFromWindow()
        isDetaching = false
    }

    private fun isDetachingFromCompose(): Boolean {
        if (isDetaching) return true
        val p = parent as? View ?: return true
        return p.parent == null
    }

    override fun clearFocus() {
        if (isDetachingFromCompose() || isLayoutRequested) {
            try {
                focusedChild?.clearFocus()
            } catch (_: Throwable) {}
            return
        }
        super.clearFocus()
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isDetachingFromCompose() || isLayoutRequested) {
            return null
        }
        return super.focusSearch(focused, direction)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        if (isDetachingFromCompose() || isLayoutRequested) {
            return
        }
        super.requestChildFocus(child, focused)
    }
}
