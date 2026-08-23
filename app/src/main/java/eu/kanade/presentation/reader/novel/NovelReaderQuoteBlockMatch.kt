package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.ui.reader.novel.tts.buildNormalizedTextWithIndexMap

/** Block a saved quote's text was found in, for scrolling the reader to it on open. */
internal data class NovelReaderQuoteBlockMatch(val blockIndex: Int)

/**
 * Finds the first block whose text contains [quoteText], used to seek the reader to a saved
 * quote on open instead of the chapter's normal resume position.
 *
 * [blocks] pairs each block's own index (list position for native scroll, `sourceBlockIndex` for
 * page reader — callers supply whichever index space their renderer scrolls by) with its text.
 *
 * Tries a direct substring match first, then falls back to a whitespace-normalized match (the
 * same normalization [eu.kanade.tachiyomi.ui.reader.novel.tts.NovelPageReaderTtsPaging] uses to
 * line up TTS utterances against block text) so minor whitespace differences between the saved
 * quote and the chapter's current text don't prevent a match.
 */
internal fun findQuoteTextBlockMatch(
    blocks: List<Pair<Int, String>>,
    quoteText: String,
): NovelReaderQuoteBlockMatch? {
    val trimmedQuote = quoteText.trim()
    if (trimmedQuote.isBlank()) return null

    val normalizedQuote = trimmedQuote.replace(Regex("\\s+"), " ")

    blocks.forEach { (blockIndex, blockText) ->
        if (blockText.contains(trimmedQuote)) {
            return NovelReaderQuoteBlockMatch(blockIndex)
        }
        val normalizedBlock = buildNormalizedTextWithIndexMap(blockText)
        if (normalizedBlock.text.contains(normalizedQuote)) {
            return NovelReaderQuoteBlockMatch(blockIndex)
        }
    }
    return null
}
