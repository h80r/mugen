package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeAlign
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlock
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeBlockKind
import eu.kanade.tachiyomi.data.book.novel.NovelBookNativeSegment

/**
 * Maps the on-disk block DTO to the renderer model.
 *
 * Keeping the two models apart is what allows the renderer to evolve (new styling, bionic reading,
 * TTS highlighting) without breaking books that were compiled by an older version: an unknown field
 * is ignored by the codec and an unmapped one simply falls back to the renderer default.
 */
internal fun NovelBookNativeBlock.toRichContentBlock(): NovelRichContentBlock? = when (kind) {
    NovelBookNativeBlockKind.RULE -> NovelRichContentBlock.HorizontalRule
    NovelBookNativeBlockKind.IMAGE -> imageUrl?.let { url ->
        NovelRichContentBlock.Image(url = url, alt = imageAlt)
    }
    NovelBookNativeBlockKind.HEADING -> NovelRichContentBlock.Heading(
        level = level.coerceIn(1, 6),
        segments = segments.map { it.toRichSegment() },
        textAlign = align.toRichAlign(),
    )
    NovelBookNativeBlockKind.QUOTE -> NovelRichContentBlock.BlockQuote(
        segments = segments.map { it.toRichSegment() },
        textAlign = align.toRichAlign(),
    )
    NovelBookNativeBlockKind.PARAGRAPH -> NovelRichContentBlock.Paragraph(
        segments = segments.map { it.toRichSegment() },
        textAlign = align.toRichAlign(),
        firstLineIndentEm = indentEm,
    )
}

/**
 * Maps a window of stored blocks to renderer blocks.
 *
 * [includeChapterHeadings] only controls rendering. The heading block itself always exists in the
 * stored stream because its characters are part of the book offset domain: hiding it here can never
 * shift a saved reading position.
 */
internal fun List<NovelBookNativeBlock>.toRichContentBlocks(
    includeChapterHeadings: Boolean = true,
): List<NovelRichContentBlock> = asSequence()
    .filter { includeChapterHeadings || !it.isChapterHeading }
    .mapNotNull { it.toRichContentBlock() }
    .toList()

private fun NovelBookNativeSegment.toRichSegment(): NovelRichTextSegment = NovelRichTextSegment(
    text = t,
    style = NovelRichTextStyle(
        bold = b,
        italic = i,
        underline = u,
        strikeThrough = s,
        colorCss = color,
        backgroundColorCss = background,
    ),
    linkUrl = href,
)

private fun NovelBookNativeAlign?.toRichAlign(): NovelRichBlockTextAlign? = when (this) {
    null -> null
    NovelBookNativeAlign.LEFT -> NovelRichBlockTextAlign.LEFT
    NovelBookNativeAlign.CENTER -> NovelRichBlockTextAlign.CENTER
    NovelBookNativeAlign.JUSTIFY -> NovelRichBlockTextAlign.JUSTIFY
    NovelBookNativeAlign.RIGHT -> NovelRichBlockTextAlign.RIGHT
}
