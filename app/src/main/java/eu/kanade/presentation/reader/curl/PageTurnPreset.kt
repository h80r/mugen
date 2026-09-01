package eu.kanade.presentation.reader.curl

/**
 * The four content-agnostic knobs a page-turn renderer needs from a resolved style preset:
 * how long a tap-driven turn animates, how far the leaf bows mid-turn, and the alphas of the
 * drop shadow and the back-of-page tint.
 *
 * Which style maps to which values, and how user speed/intensity/shadow preferences tune them,
 * is reader-specific and stays out of this package — the novel reader resolves it from
 * `NovelPageTransitionStyle`, the manga curl viewer from its own defaults.
 */
data class PageTurnPreset(
    val animationDurationMillis: Int,
    val curlAmount: Float,
    val shadowAlpha: Float,
    val backPageAlpha: Float,
)
