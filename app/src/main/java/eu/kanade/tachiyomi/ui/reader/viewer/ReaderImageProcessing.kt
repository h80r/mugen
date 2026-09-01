package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import okio.BufferedSource
import tachiyomi.core.common.util.system.ImageUtil

/**
 * The dual-page image knobs the [processReaderImage] pipeline needs, so it does not depend on
 * `PagerConfig` (which is `PagerViewer`-typed). Values come from the viewer's own config.
 */
data class ReaderImageProcessingConfig(
    val dualPageSplit: Boolean,
    val dualPageInvert: Boolean,
    val dualPageRotateToFit: Boolean,
    val dualPageRotateToFitInvert: Boolean,
    val joinDoublePages: Boolean,
)

/**
 * The image-processing pipeline lifted verbatim from `PagerPageHolder`: wide-page detection with a
 * regroup trigger, `dualPageRotateToFit` rotation, and `dualPageSplit` half-splitting (including the
 * `InsertPage` second-half path). Shared by the pager and the manga curl viewer so both apply the
 * exact same rules.
 *
 * @param page the page being decoded; an [InsertPage] is the split's synthetic second half.
 * @param imageSource the raw decoded stream.
 * @param isL2R left-to-right reading direction (drives which half [InsertPage] vs the base page keep).
 * @param onWideDetected fired the first time [page] is found to be a wide image, so the caller can
 *   flip `page.isWide` and trigger a regroup when `joinDoublePages` is on.
 * @param onRequestSplit fired when a non-insert wide page must be split — the caller inserts an
 *   [InsertPage] for the second half into its item list.
 */
fun processReaderImage(
    config: ReaderImageProcessingConfig,
    page: ReaderPage,
    imageSource: BufferedSource,
    isL2R: Boolean,
    onWideDetected: () -> Unit,
    onRequestSplit: () -> Unit,
): BufferedSource {
    val isDoublePage = ImageUtil.isWideImage(imageSource)
    if (isDoublePage && !page.isWide) {
        page.isWide = true
        onWideDetected()
    }

    if (config.dualPageRotateToFit) {
        return if (isDoublePage) {
            val rotation = if (config.dualPageRotateToFitInvert) -90f else 90f
            ImageUtil.rotateImage(imageSource, rotation)
        } else {
            imageSource
        }
    }

    if (!config.dualPageSplit) {
        return imageSource
    }

    if (page is InsertPage) {
        return splitReaderImageInHalf(config, page, imageSource, isL2R)
    }

    if (!isDoublePage) {
        return imageSource
    }

    onRequestSplit()

    return splitReaderImageInHalf(config, page, imageSource, isL2R)
}

private fun splitReaderImageInHalf(
    config: ReaderImageProcessingConfig,
    page: ReaderPage,
    imageSource: BufferedSource,
    isL2R: Boolean,
): BufferedSource {
    var side = when {
        isL2R && page is InsertPage -> ImageUtil.Side.RIGHT
        !isL2R && page is InsertPage -> ImageUtil.Side.LEFT
        isL2R && page !is InsertPage -> ImageUtil.Side.LEFT
        !isL2R && page !is InsertPage -> ImageUtil.Side.RIGHT
        else -> error("We should choose a side!")
    }

    if (config.dualPageInvert) {
        side = when (side) {
            ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
            ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
        }
    }

    return ImageUtil.splitInHalf(imageSource, side)
}
