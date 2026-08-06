package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.table.GitHubTableMarkerProvider
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.AtxHeaderProvider
import org.intellij.markdown.parser.markerblocks.providers.BlockQuoteProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.CodeFenceProvider
import org.intellij.markdown.parser.markerblocks.providers.HorizontalRuleProvider
import org.intellij.markdown.parser.markerblocks.providers.ListMarkerProvider
import org.intellij.markdown.parser.markerblocks.providers.SetextHeaderProvider
import tachiyomi.presentation.core.components.material.padding

const val MARKDOWN_INLINE_IMAGE_TAG = "MARKDOWN_INLINE_IMAGE"

/**
 * Renders a Markdown description with clickable links (native [LinkAnnotation]s, so links stay
 * tappable inside a [SelectionContainer]) and theme-aware typography/colors.
 * Ported from Mihon's `MarkdownRender` (https://github.com/mihonapp/mihon).
 */
@Composable
fun MarkdownRender(
    content: String,
    modifier: Modifier = Modifier,
    flavour: MarkdownFlavourDescriptor = SimpleMarkdownFlavourDescriptor,
    annotator: MarkdownAnnotator = remember { markdownAnnotator() },
    loadImages: Boolean = false,
) {
    Markdown(
        markdownState = rememberMarkdownState(
            content = content,
            flavour = flavour,
            immediate = true,
        ),
        annotator = annotator,
        colors = getMarkdownColors(),
        typography = getMarkdownTypography(),
        padding = markdownPadding,
        components = markdownComponents,
        imageTransformer = NoOpImageTransformerImpl(),
        modifier = modifier,
    )
}

@Composable
@ReadOnlyComposable
private fun getMarkdownColors(): MarkdownColors {
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    return DefaultMarkdownColors(
        text = MaterialTheme.colorScheme.onSurface,
        codeBackground = codeBackground,
        inlineCodeBackground = codeBackground,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        tableBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
    )
}

@Composable
@ReadOnlyComposable
fun getMarkdownLinkStyle() = MaterialTheme.typography.bodyMedium.copy(
    color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.Bold,
)

@Composable
@ReadOnlyComposable
private fun getMarkdownTypography(): MarkdownTypography {
    val link = getMarkdownLinkStyle()
    return DefaultMarkdownTypography(
        h1 = MaterialTheme.typography.headlineMedium,
        h2 = MaterialTheme.typography.headlineSmall,
        h3 = MaterialTheme.typography.titleLarge,
        h4 = MaterialTheme.typography.titleMedium,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.bodyLarge,
        text = MaterialTheme.typography.bodyMedium,
        code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        inlineCode = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        quote = MaterialTheme.typography.bodyMedium.plus(SpanStyle(fontStyle = FontStyle.Italic)),
        paragraph = MaterialTheme.typography.bodyMedium,
        ordered = MaterialTheme.typography.bodyMedium,
        bullet = MaterialTheme.typography.bodyMedium,
        list = MaterialTheme.typography.bodyMedium,
        textLink = TextLinkStyles(style = link.toSpanStyle()),
        table = MaterialTheme.typography.bodyMedium,
    )
}

private val markdownPadding = object : MarkdownPadding {
    override val block: Dp = 2.dp
    override val blockQuote: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    override val blockQuoteBar: PaddingValues.Absolute = PaddingValues.Absolute(
        left = 4.dp,
        top = 2.dp,
        right = 4.dp,
        bottom = 2.dp,
    )
    override val blockQuoteText: PaddingValues = PaddingValues(vertical = 4.dp)
    override val codeBlock: PaddingValues = PaddingValues(8.dp)
    override val list: Dp = 0.dp
    override val listIndent: Dp = 8.dp
    override val listItemBottom: Dp = 0.dp
    override val listItemTop: Dp = 0.dp
}

private val markdownComponents = markdownComponents(
    horizontalRule = {
        MarkdownDivider(
            modifier = Modifier
                .padding(vertical = MaterialTheme.padding.extraSmall)
                .fillMaxWidth(),
        )
    },
    orderedList = { ol ->
        Column(modifier = Modifier.padding(start = MaterialTheme.padding.small)) {
            MarkdownOrderedList(
                content = ol.content,
                node = ol.node,
                style = ol.typography.ordered,
                depth = ol.listDepth,
                markerModifier = { Modifier.alignBy(FirstBaseline) },
                listModifier = { Modifier.alignBy(FirstBaseline) },
            )
        }
    },
    unorderedList = { ul ->
        val markers = listOf("•", "◦", "▸", "▹")

        CompositionLocalProvider(
            LocalBulletListHandler provides { _, _, _, _, _ -> "${markers[ul.listDepth % markers.size]} " },
        ) {
            Column(modifier = Modifier.padding(start = MaterialTheme.padding.small)) {
                MarkdownBulletList(
                    content = ul.content,
                    node = ul.node,
                    style = ul.typography.bullet,
                    markerModifier = { Modifier.alignBy(FirstBaseline) },
                    listModifier = { Modifier.alignBy(FirstBaseline) },
                )
            }
        }
    },
    table = { t ->
        MarkdownTable(
            content = t.content,
            node = t.node,
            style = t.typography.text,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                )
            },
            rowBlock = { content, header, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                )
            },
        )
    },
    custom = { type, model ->
        if (type in DISALLOWED_MARKDOWN_TYPES) {
            MarkdownText(
                content = model.content.substring(model.node.startOffset, model.node.endOffset),
                node = model.node,
                style = model.typography.text,
            )
        }
    },
)

/**
 * Custom annotator: renders images as a tappable link to the image URL (image loading is not wired
 * up yet) and keeps raw HTML blocks as plain text instead of letting the parser drop them.
 */
@Composable
fun descriptionAnnotator(loadImages: Boolean, linkStyle: SpanStyle): MarkdownAnnotator =
    remember(loadImages, linkStyle) {
        markdownAnnotator(
            config = markdownAnnotatorConfig(
                eolAsNewLine = true,
            ),
            annotate = { content, child ->
                if (!loadImages && child.type == MarkdownElementTypes.IMAGE) {
                    val inlineLink = child.findChildOfType(MarkdownElementTypes.INLINE_LINK)

                    val url = inlineLink?.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                        ?.getUnescapedTextInNode(content)
                        ?: inlineLink?.findChildOfType(MarkdownElementTypes.AUTOLINK)
                            ?.findChildOfType(MarkdownTokenTypes.AUTOLINK)
                            ?.getUnescapedTextInNode(content)
                        ?: return@markdownAnnotator false

                    val textNode = inlineLink?.findChildOfType(MarkdownElementTypes.LINK_TITLE)
                        ?: inlineLink?.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    val altText = textNode?.findChildOfType(MarkdownTokenTypes.TEXT)
                        ?.getUnescapedTextInNode(content)

                    withLink(LinkAnnotation.Url(url = url)) {
                        pushStyle(linkStyle)
                        append(altText?.takeIf { it.isNotBlank() } ?: url)
                        pop()
                    }

                    return@markdownAnnotator true
                }

                if (child.type in DISALLOWED_MARKDOWN_TYPES) {
                    append(content.substring(child.startOffset, child.endOffset))
                    return@markdownAnnotator true
                }

                false
            },
        )
    }

/**
 * Builds an [AnnotatedString] for a Markdown description (inline styles + clickable links).
 * Used where a plain [Text] with `maxLines`/`overflow` is required (Aurora info cards).
 *
 * Renders the full document tree (all paragraphs, not just the first one); block-level elements
 * like lists/headings are flattened to their text content.
 */
@Composable
fun markdownDescriptionAnnotated(
    content: String?,
    style: TextStyle,
    linkStyle: SpanStyle,
    loadImages: Boolean = false,
): AnnotatedString? {
    if (content.isNullOrBlank()) return null
    val uriHandler = LocalUriHandler.current
    val annotator = descriptionAnnotator(loadImages = loadImages, linkStyle = linkStyle)
    val settings = annotatorSettings(
        linkTextSpanStyle = TextLinkStyles(style = linkStyle),
        codeSpanStyle = style.copy(fontFamily = FontFamily.Monospace).toSpanStyle(),
        annotator = annotator,
        referenceLinkHandler = ReferenceLinkHandlerImpl(),
        uriHandler = uriHandler,
        linkInteractionListener = null,
    )
    return remember(content, style, linkStyle, annotator) {
        val parsedTree = MarkdownParser(SimpleMarkdownFlavourDescriptor).buildMarkdownTreeFromString(content)
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            parsedTree.children.forEach { child ->
                appendMarkdownTree(
                    content = content,
                    node = child,
                    annotatorSettings = settings,
                )
            }
            pop()
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownTree(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
) {
    when (node.type) {
        // Let the library render rich inline content (bold/italic/links/code/strikethrough).
        MarkdownElementTypes.PARAGRAPH -> {
            buildMarkdownAnnotatedString(
                content = content,
                node = node,
                annotatorSettings = annotatorSettings,
            )
        }
        // Flatten any other block-level element (headings, lists, block quotes, tables) to text.
        else -> node.children.forEach { child ->
            appendMarkdownTree(
                content = content,
                node = child,
                annotatorSettings = annotatorSettings,
            )
        }
    }
}

/**
 * GFM-based flavour: adds GFM autolinks (bare `https://`/`www.` URLs become tappable links) and
 * strikethrough on top of CommonMark, plus GitHub tables. Raw HTML blocks stay out by using a
 * CommonMark-style marker processor below.
 */
private object SimpleMarkdownFlavourDescriptor : GFMFlavourDescriptor() {
    override val markerProcessorFactory: MarkerProcessorFactory = SimpleMarkdownProcessFactory
}

private object SimpleMarkdownProcessFactory : MarkerProcessorFactory {
    override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> {
        return SimpleMarkdownMarkerProcessor(productionHolder, CommonMarkdownConstraints.BASE)
    }
}

/**
 * Like `CommonMarkFlavour`, but with html blocks and reference links removed and
 * table support added
 */
private class SimpleMarkdownMarkerProcessor(
    productionHolder: ProductionHolder,
    constraints: MarkdownConstraints,
) : CommonMarkMarkerProcessor(productionHolder, constraints) {
    private val markerBlockProviders = listOf(
        CodeBlockProvider(),
        HorizontalRuleProvider(),
        CodeFenceProvider(),
        SetextHeaderProvider(),
        BlockQuoteProvider(),
        ListMarkerProvider(),
        AtxHeaderProvider(),
        GitHubTableMarkerProvider(),
    )

    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> {
        return markerBlockProviders
    }
}

private val DISALLOWED_MARKDOWN_TYPES = arrayOf(MarkdownTokenTypes.Companion.HTML_TAG)
