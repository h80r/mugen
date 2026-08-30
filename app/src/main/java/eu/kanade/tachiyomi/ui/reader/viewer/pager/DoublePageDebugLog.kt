package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.util.system.isDebugBuildType
import logcat.LogPriority
import logcat.logcat

/**
 * Debug-only tracing for the "unir páginas duplas" (join double pages) reader feature.
 *
 * All four known bugs (navigation resolution and joined-spread rendering) are hard to observe
 * without a play-by-play of how pages are grouped, which adapter position a target page resolves
 * to, and how each joined half loads. These logs stay in the codebase but only emit on debug
 * builds so release users are unaffected. Everything is tagged "DoublePage" for one-filter access.
 */
internal inline fun doublePageLog(priority: LogPriority = LogPriority.DEBUG, message: () -> String) {
    if (!isDebugBuildType) return
    logcat(tag = "DoublePage", priority = priority, message = message)
}
