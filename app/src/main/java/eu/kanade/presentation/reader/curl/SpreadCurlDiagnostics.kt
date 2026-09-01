package eu.kanade.presentation.reader.curl

import android.util.Log

/**
 * Temporary instrumentation for the landscape double-page curl.
 *
 * Two symptoms are under investigation:
 *  1. the halves drift apart into a wide black band the moment a fold starts, and
 *  2. a manual drag folds the opposite way from a tap at the same place, with edge zones that do
 *     not match where the fold actually starts.
 *
 * Everything logs under a single tag so a session can be pulled with
 * `adb logcat -s SpreadCurl:D`, and every line is prefixed with a short phase name so the ordering
 * within one gesture is readable. Delete this file, and its call sites, once both are fixed.
 */
internal object SpreadCurlDiagnostics {

    const val TAG = "SpreadCurl"

    /** Flip to false to silence the whole instrumentation without unpicking the call sites. */
    var enabled: Boolean = true

    /**
     * Per-frame draw/layout sampling would emit thousands of identical lines. Each throttled key
     * only logs when its payload actually changes, so a run of frames collapses to the transitions
     * that matter — which is what the layout questions here are about.
     */
    private val lastByKey = HashMap<String, String>()

    /** A monotonically increasing id per gesture, so a drag's lines can be read as one group. */
    private var gestureSeq: Int = 0

    // android.util.Log rather than the app's `logcat` extension: that extension derives its tag from
    // the calling class, so these lines would scatter across a dozen tags instead of the single one
    // this investigation needs to filter on.
    fun log(phase: String, message: String) {
        if (!enabled) return
        Log.d(TAG, "[$phase] $message")
    }

    /** Logs only when [message] differs from the last one seen for [key]. */
    fun logChanged(key: String, phase: String, message: String) {
        if (!enabled) return
        if (lastByKey.put(key, message) == message) return
        Log.d(TAG, "[$phase] $message")
    }

    /** Starts a new gesture group and returns its id. */
    fun nextGesture(): Int = ++gestureSeq

    /** Drops the change-tracking state, e.g. when a chapter is re-listed. */
    fun reset(reason: String) {
        lastByKey.clear()
        log("reset", "reason=$reason")
    }

    /** Compact float formatting: the logs are read as columns, so keep them narrow. */
    fun f(value: Float): String = String.format("%.1f", value)

    fun f2(value: Float): String = String.format("%.3f", value)
}
