package eu.kanade.presentation.reader.novel

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.Selection
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.data.coil.NovelReaderRefererImage
import eu.kanade.tachiyomi.source.novel.NovelPluginImage
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextAnchor
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRenderer
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextRendererActions
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextSelection
import eu.kanade.tachiyomi.ui.reader.novel.SelectedTextAction
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt
import eu.kanade.tachiyomi.ui.reader.novel.setting.TextAlign as ReaderTextAlign

internal const val MENU_ID_DICTIONARY = 0x9001
internal const val MENU_ID_TRANSLATION = 0x9002
internal const val MENU_ID_COPY = 0x9003
internal const val MENU_ID_SELECT_SENTENCE = 0x9004
internal const val MENU_ID_SHARE = 0x9005
internal const val MENU_ID_SELECT_PARAGRAPH = 0x9006

/**
 * Extra stillness required after the long-press timer fires before a selection is actually
 * promoted. See [NovelPageReaderTextView]'s selectionPromotionConfirmationRunnable for why.
 */
private const val SELECTION_PROMOTION_CONFIRMATION_DELAY_MILLIS = 90L

internal data class NovelPageReaderContentLayout(
    val textPadding: PaddingValues,
)

internal fun resolveNovelPageReaderContentLayout(
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    horizontalMargin: Int,
): NovelPageReaderContentLayout {
    return NovelPageReaderContentLayout(
        textPadding = PaddingValues(
            top = contentPadding + statusBarTopPadding,
            bottom = contentPadding,
            start = horizontalMargin.dp,
            end = horizontalMargin.dp,
        ),
    )
}

internal fun resolveNovelPageReaderBookBottomInset(
    density: Density,
    fontSize: Int,
    lineHeight: Float,
): Dp {
    return with(density) {
        val oneLineInset = fontSize.sp.toDp() * lineHeight.coerceAtLeast(1f)
        val conservativeInset = oneLineInset * 1.25f
        if (conservativeInset.value > 24.dp.value) conservativeInset else 24.dp
    }
}

internal fun resolveNovelPageReaderPageFitSafetyInset(
    density: Density,
    fontSize: Int,
    lineHeight: Float,
): Dp {
    return with(density) {
        val scaledFontInset = fontSize.sp.toDp() * 0.5f
        val lineHeightInset = fontSize.sp.toDp() * (lineHeight.coerceAtLeast(1f) - 1f) * 1.0f
        val resolvedInset = maxOf(8.dp, scaledFontInset, lineHeightInset)
        resolvedInset
    }
}

internal data class NovelPageReaderSpanSpec(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeThrough: Boolean = false,
    val foregroundColor: Color? = null,
    val backgroundColor: Color? = null,
    val leadingMarginPx: Int? = null,
)

internal fun buildNovelPageReaderSpanSpecs(
    text: AnnotatedString,
    firstLineIndentPx: Int? = null,
): List<NovelPageReaderSpanSpec> {
    val specs = mutableListOf<NovelPageReaderSpanSpec>()
    text.spanStyles.forEach { range ->
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        if (start >= end) return@forEach

        val style = range.item
        specs += NovelPageReaderSpanSpec(
            start = start,
            end = end,
            bold = style.fontWeight?.weight?.let { it >= FontWeight.SemiBold.weight } == true,
            italic = style.fontStyle == FontStyle.Italic,
            underline = style.textDecoration?.contains(
                androidx.compose.ui.text.style.TextDecoration.Underline,
            ) == true,
            strikeThrough = style.textDecoration?.contains(
                androidx.compose.ui.text.style.TextDecoration.LineThrough,
            ) == true,
            foregroundColor = style.color.takeIf { it != Color.Unspecified },
            backgroundColor = style.background.takeIf { it != Color.Unspecified },
        )
    }

    val indentPx = firstLineIndentPx?.takeIf { it > 0 }
    if (indentPx != null && text.isNotEmpty()) {
        specs += NovelPageReaderSpanSpec(
            start = 0,
            end = text.length,
            leadingMarginPx = indentPx,
        )
    }

    return specs
}

internal fun buildNovelPageReaderSpannableText(
    text: AnnotatedString,
    firstLineIndentPx: Int? = null,
    forcedTypefaceStyle: Int = Typeface.NORMAL,
    onUrlClick: ((String) -> Unit)? = null,
): SpannableStringBuilder {
    val spannable = SpannableStringBuilder(text.text)
    buildNovelPageReaderSpanSpecs(
        text = text,
        firstLineIndentPx = firstLineIndentPx,
    ).forEach { spec ->
        if (spec.leadingMarginPx != null) {
            spannable.setSpan(
                LeadingMarginSpan.Standard(spec.leadingMarginPx, 0),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            return@forEach
        }

        val fontStyle = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        if (fontStyle != Typeface.NORMAL) {
            spannable.setSpan(
                StyleSpan(fontStyle),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (spec.underline) {
            spannable.setSpan(
                UnderlineSpan(),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (spec.strikeThrough) {
            spannable.setSpan(
                StrikethroughSpan(),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        spec.foregroundColor?.let { color ->
            spannable.setSpan(
                ForegroundColorSpan(color.toArgb()),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        spec.backgroundColor?.let { color ->
            spannable.setSpan(
                BackgroundColorSpan(color.toArgb()),
                spec.start,
                spec.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    text.getStringAnnotations(
        tag = "URL",
        start = 0,
        end = text.length,
    ).forEach { annotation ->
        val start = annotation.start.coerceIn(0, text.length)
        val end = annotation.end.coerceIn(start, text.length)
        if (start >= end) return@forEach
        spannable.setSpan(
            NovelPageReaderUrlClickableSpan(
                url = annotation.item,
                onUrlClick = onUrlClick,
            ),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    if (forcedTypefaceStyle != Typeface.NORMAL && text.text.isNotEmpty()) {
        spannable.setSpan(
            StyleSpan(forcedTypefaceStyle),
            0,
            text.text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return spannable
}

internal fun resolveNovelPageReaderRenderedTextSignature(
    text: AnnotatedString,
    firstLineIndentPx: Int?,
    forcedTypefaceStyle: Int,
): Int {
    var result = text.hashCode()
    result = 31 * result + (firstLineIndentPx ?: 0)
    result = 31 * result + forcedTypefaceStyle
    return result
}

private class NovelPageReaderUrlClickableSpan(
    private val url: String,
    private val onUrlClick: ((String) -> Unit)?,
) : ClickableSpan() {
    override fun onClick(widget: android.view.View) {
        onUrlClick?.invoke(url)
    }

    override fun updateDrawState(ds: TextPaint) {
    }
}

internal data class NovelPageReaderTextViewMetrics(
    val textSizePx: Float,
    val lineSpacingMultiplier: Float,
    val typeface: Typeface?,
    val textAlign: ReaderTextAlign,
    val shadow: Shadow?,
)

internal fun resolveNovelPageReaderTextViewMetrics(
    textSizePx: Float,
    lineSpacingMultiplier: Float,
    typeface: Typeface?,
    textAlign: ReaderTextAlign,
    shadow: Shadow?,
): NovelPageReaderTextViewMetrics {
    return NovelPageReaderTextViewMetrics(
        textSizePx = textSizePx,
        lineSpacingMultiplier = lineSpacingMultiplier,
        typeface = typeface,
        textAlign = textAlign,
        shadow = shadow,
    )
}

internal fun applyNovelPageReaderTextViewMetrics(
    textView: TextView,
    metrics: NovelPageReaderTextViewMetrics,
) {
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.textSizePx)
    textView.setLineSpacing(0f, metrics.lineSpacingMultiplier)
    textView.typeface = metrics.typeface
    textView.gravity = resolveNovelPageReaderTextGravity(metrics.textAlign)
    metrics.shadow?.let { resolvedShadow ->
        textView.setShadowLayer(
            resolvedShadow.blurRadius,
            resolvedShadow.offset.x,
            resolvedShadow.offset.y,
            resolvedShadow.color.toArgb(),
        )
    } ?: textView.setShadowLayer(0f, 0f, 0f, Color.Transparent.toArgb())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        textView.justificationMode = if (metrics.textAlign == ReaderTextAlign.JUSTIFY) {
            Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            Layout.JUSTIFICATION_MODE_NONE
        }
    }
}

private fun combineTypefaceStyles(
    first: Int,
    second: Int,
): Int {
    val bold = first == Typeface.BOLD ||
        first == Typeface.BOLD_ITALIC ||
        second == Typeface.BOLD ||
        second == Typeface.BOLD_ITALIC
    val italic = first == Typeface.ITALIC ||
        first == Typeface.BOLD_ITALIC ||
        second == Typeface.ITALIC ||
        second == Typeface.BOLD_ITALIC
    return when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
}

private fun resolveNovelPageReaderFirstLineIndentPx(
    firstLineIndentEm: Float?,
    textSizePx: Float,
): Int? {
    return firstLineIndentEm
        ?.takeIf { it > 0f }
        ?.let { (textSizePx.coerceAtLeast(1f) * it).roundToInt().coerceAtLeast(1) }
}

private fun resolveNovelPageReaderTextGravity(
    textAlign: ReaderTextAlign,
): Int {
    return when (textAlign) {
        ReaderTextAlign.CENTER -> Gravity.CENTER_HORIZONTAL
        ReaderTextAlign.RIGHT -> Gravity.END
        ReaderTextAlign.JUSTIFY,
        ReaderTextAlign.LEFT,
        ReaderTextAlign.SOURCE,
        -> Gravity.START
    }
}

internal class NovelPageReaderTextView constructor(
    context: android.content.Context,
    private val selectionRenderer: NovelSelectedTextRenderer,
    private val selectionSessionIdProvider: () -> Long,
    private val onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit,
    private val selectionCoordinator: NovelPageReaderSelectionCoordinator? = null,
    private val selectionBlockOrder: Int = 0,
    private var onPlainTap: ((Float, Float, Float, Float) -> Unit)?,
    private var onSelectionGestureActiveChanged: ((Boolean) -> Unit)?,
    private val touchHandlingEnabled: Boolean,
    selectionInteractionEnabled: Boolean,
) : TextView(context) {
    private enum class SelectionHandle { START, END }

    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val longPressTimeoutMillis = ViewConfiguration.getLongPressTimeout().toLong()
    private var gestureStartUptimeMillis = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var latestX = 0f
    private var latestY = 0f
    private var selectionPromotionScheduled = false
    private var selectionPromotionConfirmationScheduled = false
    private var selectionPromotedByLongPress = false
    private var selectionGestureActive = false
    private var longPressSelectionStart = -1
    private var longPressSelectionEnd = -1
    private var dismissOnlyGesture = false
    private var draggedSelectionHandle: SelectionHandle? = null
    private var selectionInteractionEnabled = selectionInteractionEnabled
    var renderedTextSignature: Int? = null
    private var appliedSelectionClearRequestToken: Int? = null
    private var appliedSelectionExpandRequestToken = 0
    private val selectionPromotionRunnable = Runnable {
        selectionPromotionScheduled = false
        if (
            selectionInteractionEnabled &&
            NovelReaderSelectionGestureArbiter.shouldPromoteSelectionCandidate(
                elapsedMillis = SystemClock.uptimeMillis() - gestureStartUptimeMillis,
                movedDistancePx = currentGestureDistancePx(),
                touchSlopPx = touchSlopPx,
                longPressTimeoutMillis = longPressTimeoutMillis,
            )
        ) {
            // Don't commit yet: a finger that was still right up to this instant may resume a
            // scroll drag in the next few ms (e.g. a natural pause before dragging, common when
            // starting to scroll). Committing here would let that drag get consumed as "extend
            // the long-press selection" instead of reaching LazyColumn as a scroll. Re-check
            // stillness after a short confirmation beat; only a finger that is STILL still after
            // the beat gets treated as a deliberate long-press.
            selectionPromotionConfirmationScheduled = true
            postDelayed(selectionPromotionConfirmationRunnable, SELECTION_PROMOTION_CONFIRMATION_DELAY_MILLIS)
        }
    }
    private val selectionPromotionConfirmationRunnable = Runnable {
        selectionPromotionConfirmationScheduled = false
        if (selectionInteractionEnabled && currentGestureDistancePx() <= touchSlopPx) {
            selectionPromotedByLongPress = promoteSelectionFromGesture()
            if (selectionPromotedByLongPress) {
                setSelectionGestureActive(true)
            }
        }
    }

    private var localSelection: NovelSelectedTextSelection? = null
    private var selectionActionMode: ActionMode? = null
    private val selectionBoundsInView = RectF()
    private var isExecutingAction = false
    private var applyingCoordinatedSelection = false
    private var drawSelectionStartHandle = true
    private var drawSelectionEndHandle = true

    var selectionHighlightColor: Int = android.graphics.Color.TRANSPARENT
    var selectionHandleColor: Int = android.graphics.Color.TRANSPARENT
    private val selectionHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val selectionHandlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    var isDictionaryEnabled = false
    var isTranslationEnabled = false
    private var isDetaching = false

    override fun onDetachedFromWindow() {
        isDetaching = true
        selectionCoordinator?.unregister(this)
        finishSelectionActionMode()
        clearSelectionPromotion()
        if (selectionGestureActive) {
            selectionGestureActive = false
            onSelectionGestureActiveChanged?.invoke(false)
        }
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
            return
        }
        super.clearFocus()
    }

    override fun focusSearch(direction: Int): View? {
        if (isDetachingFromCompose() || isLayoutRequested) {
            return null
        }
        return super.focusSearch(direction)
    }

    init {
        updateSelectionInteractionEnabled(selectionInteractionEnabled)
        isClickable = false
        setupCustomSelectionActionModeCallback()
        selectionCoordinator?.register(this)
    }

    private fun setupCustomSelectionActionModeCallback() {
        customSelectionActionModeCallback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (menu == null) return true
                // Standard system-style actions first, then the reader-specific ones.
                menu.add(Menu.NONE, MENU_ID_COPY, 10, context.getString(MR.strings.copy.resourceId))
                menu.add(
                    Menu.NONE,
                    MENU_ID_SELECT_SENTENCE,
                    20,
                    context.getString(AYMR.strings.novel_reader_text_selection_action_select_sentence.resourceId),
                )
                menu.add(
                    Menu.NONE,
                    MENU_ID_SELECT_PARAGRAPH,
                    30,
                    context.getString(AYMR.strings.novel_reader_text_selection_action_select_paragraph.resourceId),
                )
                menu.add(Menu.NONE, MENU_ID_SHARE, 40, context.getString(MR.strings.action_share.resourceId))
                val menuOrder = 100
                if (isDictionaryEnabled) {
                    menu.add(
                        Menu.NONE,
                        MENU_ID_DICTIONARY,
                        menuOrder,
                        context.getString(AYMR.strings.novel_reader_text_selection_action_dictionary.resourceId),
                    )
                }
                if (isTranslationEnabled) {
                    menu.add(
                        Menu.NONE,
                        MENU_ID_TRANSLATION,
                        menuOrder + 1,
                        context.getString(AYMR.strings.novel_reader_text_selection_action_translate.resourceId),
                    )
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item == null) return false
                val selection = localSelection
                when (item.itemId) {
                    MENU_ID_COPY -> {
                        val selectedText = selection?.text ?: return false
                        copyToClipboard(selectedText)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_SELECT_SENTENCE -> {
                        selectSentence()
                        return true
                    }
                    MENU_ID_SELECT_PARAGRAPH -> {
                        selectParagraph()
                        return true
                    }
                    MENU_ID_SHARE -> {
                        val selectedText = selection?.text ?: return false
                        shareSelectedText(selectedText)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_DICTIONARY -> {
                        if (selection == null) return false
                        isExecutingAction = true
                        val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.DICTIONARY)
                        onSelectedTextSelectionChanged(selectionWithAction)
                        mode?.finish()
                        return true
                    }
                    MENU_ID_TRANSLATION -> {
                        if (selection == null) return false
                        isExecutingAction = true
                        val selectionWithAction = selection.copy(triggerAction = SelectedTextAction.TRANSLATION)
                        onSelectedTextSelectionChanged(selectionWithAction)
                        mode?.finish()
                        return true
                    }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                // A floating action mode can consume the outside tap before the TextView gets
                // its ACTION_UP. Clear here as the authoritative dismissal path. Actions that
                // need to keep the selection alive (dictionary/translation) set this guard
                // before finishing the mode.
                if (!isExecutingAction) {
                    clearSelection()
                }
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                if (view !== this@NovelPageReaderTextView || layout == null || selectionBoundsInView.isEmpty) {
                    super.onGetContentRect(mode, view, outRect)
                    return
                }
                outRect.set(
                    selectionBoundsInView.left.roundToInt(),
                    selectionBoundsInView.top.roundToInt(),
                    selectionBoundsInView.right.roundToInt(),
                    selectionBoundsInView.bottom.roundToInt(),
                )
            }
        }
    }

    fun updateSelectionInteractionEnabled(enabled: Boolean) {
        selectionInteractionEnabled = enabled && touchHandlingEnabled
        setTextIsSelectable(selectionInteractionEnabled)
        // setTextIsSelectable() re-enables focusability, so this interop view can become the
        // focused Android view. Compose detaches interop views while composition changes are
        // still being applied, and ViewGroup.removeViewInLayout() then makes the framework search
        // the hierarchy for a new focus target. That search re-enters Compose layout and crashes
        // with "Cannot start a writer when another writer is pending". Selection here is driven by
        // Selection.setSelection(), so Android focus is not needed.
        isFocusable = false
        isFocusableInTouchMode = false
        isLongClickable = selectionInteractionEnabled
        if (!selectionInteractionEnabled) {
            clearSelectionPromotion()
            selectionPromotedByLongPress = false
            longPressSelectionStart = -1
            longPressSelectionEnd = -1
            clearSelection()
            finishSelectionActionMode()
        }
    }

    /**
     * Applies a hoisted "clear selection" request (e.g. from the reader's own back handler). The
     * token only needs to change value to trigger a clear; the caller does not need to know
     * whether this particular view instance is the one currently holding the selection.
     */
    fun applySelectionClearRequestToken(token: Int) {
        if (appliedSelectionClearRequestToken == token) return
        appliedSelectionClearRequestToken = token
        if (hasActiveSelection()) {
            clearSelection()
            finishSelectionActionMode()
        }
    }

    /** Applies a hoisted expand request to the coordinator owner exactly once. */
    fun applySelectionExpandRequestToken(token: Int) {
        if (appliedSelectionExpandRequestToken == token) return
        appliedSelectionExpandRequestToken = token
        if (hasActiveSelection() && (selectionCoordinator == null || selectionCoordinator.isOwner(this))) {
            expandSelectionForConsole()
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (!selectionInteractionEnabled) return
        super.onSelectionChanged(selStart, selEnd)
        if (!applyingCoordinatedSelection) {
            publishSelection(selStart, selEnd)
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val spannable = text as? Spannable
        val currentLayout = layout
        val start = spannable?.let(Selection::getSelectionStart) ?: -1
        val end = spannable?.let(Selection::getSelectionEnd) ?: -1
        val hasSelection = currentLayout != null && start >= 0 && end >= 0 && start != end
        if (hasSelection) {
            val normalizedStart = minOf(start, end)
            val normalizedEnd = maxOf(start, end)
            val selectionPath = Path()
            currentLayout.getSelectionPath(normalizedStart, normalizedEnd, selectionPath)
            selectionHighlightPaint.color = selectionHighlightColor
            canvas.save()
            canvas.translate((totalPaddingLeft - scrollX).toFloat(), (totalPaddingTop - scrollY).toFloat())
            canvas.drawPath(selectionPath, selectionHighlightPaint)
            canvas.restore()
        }

        super.onDraw(canvas)

        if (hasSelection) {
            val normalizedStart = minOf(start, end)
            val normalizedEnd = maxOf(start, end)
            val startLine = currentLayout.getLineForOffset(normalizedStart)
            val endLine = currentLayout.getLineForOffset(normalizedEnd)
            val handleRadius = resources.displayMetrics.density * 5f
            val contentLeft = (totalPaddingLeft - scrollX).toFloat()
            selectionHandlePaint.color = selectionHandleColor
            if (drawSelectionStartHandle) {
                canvas.drawCircle(
                    contentLeft + currentLayout.getPrimaryHorizontal(normalizedStart),
                    selectionHandleY(currentLayout, startLine),
                    handleRadius,
                    selectionHandlePaint,
                )
            }
            if (drawSelectionEndHandle) {
                canvas.drawCircle(
                    contentLeft + currentLayout.getPrimaryHorizontal(normalizedEnd),
                    selectionHandleY(currentLayout, endLine),
                    handleRadius,
                    selectionHandlePaint,
                )
            }
        }
    }

    /**
     * Attach handles to the actual text paint's glyph descent. Layout's line descent/bottom also
     * includes configured line spacing, which differs between paragraph blocks and makes pins
     * drift vertically even when their text looks aligned.
     */
    private fun selectionHandleY(currentLayout: Layout, line: Int): Float {
        return totalPaddingTop - scrollY +
            currentLayout.getLineBaseline(line) + currentLayout.paint.descent() +
            resources.displayMetrics.density * 4f
    }

    private fun selectionHandlePoint(handle: SelectionHandle): android.graphics.PointF? {
        val spannable = text as? Spannable ?: return null
        val currentLayout = layout ?: return null
        val rawStart = Selection.getSelectionStart(spannable)
        val rawEnd = Selection.getSelectionEnd(spannable)
        if (rawStart < 0 || rawEnd < 0 || rawStart == rawEnd) return null
        val offset = when (handle) {
            SelectionHandle.START -> minOf(rawStart, rawEnd)
            SelectionHandle.END -> maxOf(rawStart, rawEnd)
        }
        val line = currentLayout.getLineForOffset(offset)
        return android.graphics.PointF(
            totalPaddingLeft - scrollX + currentLayout.getPrimaryHorizontal(offset),
            selectionHandleY(currentLayout, line),
        )
    }

    private fun resolveSelectionHandleAt(x: Float, y: Float): SelectionHandle? {
        if (!hasActiveSelection()) return null
        val hitRadius = resources.displayMetrics.density * 24f
        return SelectionHandle.entries.mapNotNull { handle ->
            if (handle == SelectionHandle.START && !drawSelectionStartHandle) return@mapNotNull null
            if (handle == SelectionHandle.END && !drawSelectionEndHandle) return@mapNotNull null
            val point = selectionHandlePoint(handle) ?: return@mapNotNull null
            val distance = hypot((x - point.x).toDouble(), (y - point.y).toDouble()).toFloat()
            if (distance <= hitRadius) handle to distance else null
        }.minByOrNull { (_, distance) -> distance }?.first
    }

    private fun updateSelectionHandle(handle: SelectionHandle, x: Float, y: Float) {
        val spannable = text as? Spannable ?: return
        val offset = resolveTextOffsetAt(x, y) ?: return
        val currentStart = minOf(Selection.getSelectionStart(spannable), Selection.getSelectionEnd(spannable))
        val currentEnd = maxOf(Selection.getSelectionStart(spannable), Selection.getSelectionEnd(spannable))
        val (start, end) = when (handle) {
            SelectionHandle.START -> offset.coerceAtMost(currentEnd - 1) to currentEnd
            SelectionHandle.END -> currentStart to offset.coerceAtLeast(currentStart + 1)
        }
        applySelectionRange(start, end)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchHandlingEnabled) {
            return false
        }

        // Classify the initial touch before handing it to TextView's native selection handling.
        // Otherwise the native handler may consume/cancel the stream and the outside-dismiss
        // cleanup below becomes timing-dependent.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureStartUptimeMillis = event.eventTime
            gestureStartX = event.x
            gestureStartY = event.y
            latestX = event.x
            latestY = event.y
            selectionPromotedByLongPress = false
            dismissOnlyGesture = false
            draggedSelectionHandle = resolveSelectionHandleAt(event.x, event.y)
            if (draggedSelectionHandle != null) {
                val coordinatedDrag = selectionCoordinator?.beginHandleDrag(
                    view = this,
                    movingStart = draggedSelectionHandle == SelectionHandle.START,
                ) ?: false
                if (!coordinatedDrag) {
                    draggedSelectionHandle = null
                }
                setSelectionGestureActive(true)
                return true
            }
            if (selectionInteractionEnabled) {
                if (selectionCoordinator?.hasActiveSelection() == true ||
                    (hasActiveSelection() && !selectionBoundsInView.contains(event.x, event.y))) {
                    // Clear immediately. The action-mode window or a parent gesture detector may
                    // consume the remainder of this tap, so waiting for ACTION_UP is unreliable.
                    dismissOnlyGesture = true
                    clearSelection()
                    finishSelectionActionMode()
                    setSelectionGestureActive(true)
                    return true
                }
                clearSelection()
                finishSelectionActionMode()
                scheduleSelectionPromotion()
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            latestX = event.x
            latestY = event.y
            if (selectionCoordinator?.updateActiveHandleDrag(this, event.x, event.y) == true) {
                return true
            }
            draggedSelectionHandle?.let { handle ->
                if (
                    selectionCoordinator?.updateHandleDrag(
                        view = this,
                        movingStart = handle == SelectionHandle.START,
                        localX = event.x,
                        localY = event.y,
                    ) == true
                ) {
                    return true
                }
                updateSelectionHandle(handle, event.x, event.y)
                return true
            }
            if (selectionPromotedByLongPress) {
                updateLongPressSelection(event.x, event.y)
                return true
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && dismissOnlyGesture) {
            dismissOnlyGesture = false
            setSelectionGestureActive(false)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL && dismissOnlyGesture) {
            dismissOnlyGesture = false
            setSelectionGestureActive(false)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && draggedSelectionHandle != null) {
            draggedSelectionHandle = null
            selectionCoordinator?.endHandleDrag()
            setSelectionGestureActive(false)
            selectionActionMode?.invalidateContentRect()
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && selectionCoordinator?.hasActiveHandleDrag() == true) {
            selectionCoordinator.endHandleDrag()
            setSelectionGestureActive(false)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && selectionPromotedByLongPress) {
            selectionPromotedByLongPress = false
            setSelectionGestureActive(false)
            selectionActionMode?.invalidateContentRect()
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL && draggedSelectionHandle != null) {
            draggedSelectionHandle = null
            selectionCoordinator?.endHandleDrag()
            setSelectionGestureActive(false)
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_CANCEL && selectionCoordinator?.hasActiveHandleDrag() == true) {
            selectionCoordinator.endHandleDrag()
            setSelectionGestureActive(false)
            return true
        }

        val handledBySuper = super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // ACTION_DOWN is classified before the native handler above.
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectionInteractionEnabled && currentGestureDistancePx() > touchSlopPx) {
                    clearSelectionPromotion()
                }
            }
            MotionEvent.ACTION_UP -> {
                latestX = event.x
                latestY = event.y
                if (selectionInteractionEnabled) {
                    clearSelectionPromotion()
                }
                val plainTapEligible = NovelReaderSelectionGestureArbiter.shouldHandlePlainTap(
                    elapsedMillis = event.eventTime - gestureStartUptimeMillis,
                    movedDistancePx = currentGestureDistancePx(),
                    touchSlopPx = touchSlopPx,
                    longPressTimeoutMillis = longPressTimeoutMillis,
                )
                val hasActiveSelection = hasActiveSelection()
                if (plainTapEligible && !hasActiveSelection) {
                    val clickableSpan = resolveClickableSpanAt(event)
                    if (clickableSpan != null) {
                        clickableSpan.onClick(this)
                        return true
                    }
                    val windowOffset = IntArray(2)
                    getLocationInWindow(windowOffset)
                    onPlainTap?.invoke(
                        windowOffset[0] + event.x,
                        windowOffset[1] + event.y,
                        rootView.width.toFloat().coerceAtLeast(1f),
                        rootView.height.toFloat().coerceAtLeast(1f),
                    )
                    return true
                }
                return handledBySuper
            }
            MotionEvent.ACTION_CANCEL -> {
                if (selectionInteractionEnabled) {
                    clearSelection()
                    clearSelectionPromotion()
                }
                selectionPromotedByLongPress = false
                longPressSelectionStart = -1
                longPressSelectionEnd = -1
                dismissOnlyGesture = false
                setSelectionGestureActive(false)
                draggedSelectionHandle = null
                return handledBySuper
            }
        }
        return handledBySuper
    }

    fun selectWordAt(x: Float, y: Float): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(x, y) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        return true
    }

    /** The Compose Aurora console owns selection actions; retain only native spans and handles. */
    private fun startSelectionActionMode() {
        // Deliberately empty: starting an ActionMode would show Android's floating toolbar.
    }

    private fun finishSelectionActionMode() {
        selectionActionMode?.finish()
        selectionActionMode = null
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    private fun shareSelectedText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, null)
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun selectSentence() {
        val spannable = text as? Spannable ?: return
        val currentText = spannable.toString()
        if (currentText.isBlank()) return
        val anchor = Selection.getSelectionStart(spannable).coerceIn(0, currentText.length - 1)
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault()).apply { setText(currentText) }
        val start = if (iterator.isBoundary(anchor)) anchor else iterator.preceding(anchor).coerceAtLeast(0)
        val end = iterator.following(anchor).takeIf { it != BreakIterator.DONE } ?: currentText.length
        applySelectionRange(start, end)
    }

    private fun selectParagraph() {
        val spannable = text as? Spannable ?: return
        if (spannable.length == 0) return
        applySelectionRange(0, spannable.length)
    }

    internal fun clearSelectionForConsole() {
        if (selectionCoordinator?.clearSelection(this) != true) {
            clearSelection()
        }
    }

    internal fun expandSelectionForConsole() {
        if (selectionCoordinator != null) {
            selectionCoordinator.expandSelection()
        } else {
            expandLocalSelectionForConsole()
        }
    }

    internal fun expandLocalSelectionForConsole() {
        val spannable = text as? Spannable ?: return
        val currentText = spannable.toString()
        if (currentText.isBlank()) return
        val selectionStart = Selection.getSelectionStart(spannable)
        val selectionEnd = Selection.getSelectionEnd(spannable)
        if (selectionStart < 0 || selectionEnd <= selectionStart) return
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault()).apply { setText(currentText) }
        val sentenceStart = if (iterator.isBoundary(selectionStart)) {
            selectionStart
        } else {
            iterator.preceding(selectionStart).coerceAtLeast(0)
        }
        val sentenceEnd = iterator.following(selectionStart).takeIf { it != BreakIterator.DONE } ?: currentText.length
        if (selectionStart >= sentenceStart && selectionEnd <= sentenceEnd &&
            (selectionStart != sentenceStart || selectionEnd != sentenceEnd)
        ) {
            applySelectionRange(sentenceStart, sentenceEnd)
        } else {
            selectParagraph()
        }
    }

    private fun applySelectionRange(start: Int, end: Int) {
        val spannable = text as? Spannable ?: return
        Selection.setSelection(spannable, start, end)
        publishSelection(start, end)
        selectionActionMode?.invalidateContentRect()
        invalidate()
    }

    /**
     * After a long-press selects the initial word, keep the original word as the anchor and use
     * the same finger's drag to extend the selection in either direction. This mirrors native
     * text selection while preserving curl gestures for ordinary (non-long-press) drags.
     */
    private fun updateLongPressSelection(x: Float, y: Float) {
        if (selectionCoordinator?.extendSelection(this, x, y) == true) return
        val offset = resolveTextOffsetAt(x, y) ?: return
        if (longPressSelectionStart < 0 || longPressSelectionEnd <= longPressSelectionStart) return
        when {
            offset < longPressSelectionStart -> applySelectionRange(offset, longPressSelectionEnd)
            offset > longPressSelectionEnd -> applySelectionRange(longPressSelectionStart, offset)
        }
    }

    private fun scheduleSelectionPromotion() {
        clearSelectionPromotion()
        selectionPromotionScheduled = true
        // Deliberately does NOT call setSelectionGestureActive(true) yet: this runs on every
        // ACTION_DOWN, including ones that turn out to be a scroll/swipe/tap. Toggling
        // userScrollEnabled this early races LazyColumn's own gesture detector — if Compose
        // recomposes with scrolling disabled before this same pointer's first ACTION_MOVE, the
        // scrollable pointerInput never arms for it; if it then re-enables once slop is exceeded
        // (see clearSelectionPromotion below) and recomposes again, the pointerInput restart
        // misses the pointer's continuation entirely, since a fresh coroutine only observes
        // events from its own start. Net effect without this guard: the gesture is claimed by
        // neither scroll nor selection. Only flip the flag once promotion actually succeeds
        // (selectionPromotionRunnable) or a handle drag begins, where the pointer is already
        // confirmed to belong to selection, not scrolling.
        postDelayed(selectionPromotionRunnable, longPressTimeoutMillis)
    }

    private fun clearSelectionPromotion() {
        if (selectionPromotionConfirmationScheduled) {
            removeCallbacks(selectionPromotionConfirmationRunnable)
            selectionPromotionConfirmationScheduled = false
        }
        if (!selectionPromotionScheduled) return
        removeCallbacks(selectionPromotionRunnable)
        selectionPromotionScheduled = false
    }

    private fun setSelectionGestureActive(active: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(active)
        if (selectionGestureActive == active) return
        selectionGestureActive = active
        onSelectionGestureActiveChanged?.invoke(active)
    }

    private fun publishSelection(selStart: Int, selEnd: Int) {
        selectionBoundsInView.setEmpty()
        val currentText = text?.toString().orEmpty()
        if (selStart < 0 || selEnd < 0 || selStart == selEnd || currentText.isBlank()) {
            clearPublishedSelection()
            return
        }

        val layout = layout ?: run {
            clearPublishedSelection()
            return
        }
        val start = minOf(selStart, selEnd).coerceIn(0, currentText.length)
        val end = maxOf(selStart, selEnd).coerceIn(start, currentText.length)
        if (start >= end) {
            clearPublishedSelection()
            return
        }

        val selectionPath = Path()
        layout.getSelectionPath(start, end, selectionPath)
        val bounds = RectF()
        selectionPath.computeBounds(bounds, true)
        if (bounds.isEmpty) {
            clearPublishedSelection()
            return
        }
        selectionBoundsInView.set(bounds)

        val selectedText = currentText.substring(start, end)
        if (selectedText.isBlank()) {
            clearPublishedSelection()
            return
        }

        val locationOnScreen = IntArray(2)
        getLocationOnScreen(locationOnScreen)
        localSelection = NovelSelectedTextSelection(
            sessionId = selectionSessionIdProvider(),
            renderer = selectionRenderer,
            text = selectedText,
            anchor = NovelSelectedTextAnchor(
                leftPx = (locationOnScreen[0] + bounds.left).roundToInt(),
                topPx = (locationOnScreen[1] + bounds.top).roundToInt(),
                rightPx = (locationOnScreen[0] + bounds.right).roundToInt(),
                bottomPx = (locationOnScreen[1] + bounds.bottom).roundToInt(),
            ),
        )
        if (!applyingCoordinatedSelection) {
            onSelectedTextSelectionChanged(localSelection)
        }
        isExecutingAction = false
        invalidate()
    }

    /**
     * Only the TextView that published a selection may clear the hoisted selection state. Curl
     * keeps several sibling page views mounted, and their ordinary collapsed selections must not
     * dismiss a selection owned by the visible page.
     */
    private fun clearPublishedSelection() {
        val hadPublishedSelection = localSelection != null
        localSelection = null
        if (hadPublishedSelection && !isExecutingAction && !applyingCoordinatedSelection) {
            onSelectedTextSelectionChanged(null)
        }
        isExecutingAction = false
    }

    private fun hasActiveSelection(): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        return start >= 0 && end >= 0 && start != end
    }

    private fun clearSelection() {
        if (!applyingCoordinatedSelection && selectionCoordinator?.clearSelection(this) == true) {
            return
        }
        selectionBoundsInView.setEmpty()
        longPressSelectionStart = -1
        longPressSelectionEnd = -1
        val spannable = text as? Spannable
        val hadSelection = localSelection != null ||
            (spannable != null && Selection.getSelectionStart(spannable) != Selection.getSelectionEnd(spannable))
        // Clear local ownership before Selection.removeSelection() dispatches onSelectionChanged,
        // preventing the callback from firing twice for the same owner.
        localSelection = null
        spannable?.let(Selection::removeSelection)
        if (hadSelection && !isExecutingAction && !applyingCoordinatedSelection) {
            onSelectedTextSelectionChanged(null)
        }
        isExecutingAction = false
        invalidate()
    }

    internal fun applySelectionFromCoordinator(start: Int, end: Int) {
        val spannable = text as? Spannable ?: return
        applyingCoordinatedSelection = true
        try {
            Selection.setSelection(spannable, start.coerceIn(0, spannable.length), end.coerceIn(0, spannable.length))
            publishSelection(start, end)
        } finally {
            applyingCoordinatedSelection = false
        }
    }

    internal fun setCoordinatedHandleVisibility(showStart: Boolean, showEnd: Boolean) {
        drawSelectionStartHandle = showStart
        drawSelectionEndHandle = showEnd
        invalidate()
    }

    internal fun clearSelectionFromCoordinator() {
        applyingCoordinatedSelection = true
        try {
            clearSelection()
        } finally {
            applyingCoordinatedSelection = false
        }
        drawSelectionStartHandle = true
        drawSelectionEndHandle = true
    }

    internal fun resolveTextOffsetAtScreen(screenX: Float, screenY: Float): Int? {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return resolveTextOffsetAt(screenX - location[0], screenY - location[1])
    }

    internal fun selectionText(): String = text?.toString().orEmpty()

    internal fun selectionBoundsOnScreen(): RectF? {
        if (selectionBoundsInView.isEmpty) return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return RectF(selectionBoundsInView).apply { offset(location[0].toFloat(), location[1].toFloat()) }
    }

    internal fun publishCoordinatedSelection(text: String, bounds: RectF) {
        if (text.isBlank()) return
        localSelection = NovelSelectedTextSelection(
            sessionId = selectionSessionIdProvider(),
            renderer = selectionRenderer,
            text = text,
            anchor = NovelSelectedTextAnchor(
                leftPx = bounds.left.roundToInt(),
                topPx = bounds.top.roundToInt(),
                rightPx = bounds.right.roundToInt(),
                bottomPx = bounds.bottom.roundToInt(),
            ),
        )
        onSelectedTextSelectionChanged(localSelection)
    }

    internal fun notifyCoordinatedSelectionCleared() {
        localSelection = null
        onSelectedTextSelectionChanged(null)
    }

    internal fun finishSelectionActionModeFromCoordinator() {
        finishSelectionActionMode()
    }

    internal fun selectionOrder(): Int = selectionBlockOrder

    private fun currentGestureDistancePx(): Float {
        return hypot((latestX - gestureStartX).toDouble(), (latestY - gestureStartY).toDouble()).toFloat()
    }

    private fun promoteSelectionFromGesture(): Boolean {
        if (!selectionInteractionEnabled) return false
        val spannable = text as? Spannable ?: return false
        val selectionRange = resolveWordSelectionRangeAt(latestX, latestY) ?: return false
        val selectionStart = selectionRange.first
        val selectionEnd = selectionRange.last + 1
        longPressSelectionStart = selectionStart
        longPressSelectionEnd = selectionEnd
        Selection.setSelection(spannable, selectionStart, selectionEnd)
        publishSelection(selectionStart, selectionEnd)
        selectionCoordinator?.beginSelection(this, selectionStart, selectionEnd)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        startSelectionActionMode()
        return true
    }

    private fun resolveWordSelectionRangeAt(
        x: Float,
        y: Float,
    ): IntRange? {
        val currentText = text?.toString().orEmpty()
        if (currentText.isBlank()) return null
        val offset = resolveTextOffsetAt(x, y) ?: return null
        if (offset >= currentText.length) return null

        val wordIterator = BreakIterator.getWordInstance(Locale.getDefault()).apply {
            setText(currentText)
        }
        val safeOffset = offset.coerceIn(0, currentText.length)
        val start = wordIterator.preceding(safeOffset)
        val end = wordIterator.following(safeOffset)
        if (start < 0 || end < 0 || start >= end) return null
        val selectionText = currentText.substring(start, end)
        if (selectionText.isBlank()) return null
        return start until end
    }

    private fun resolveTextOffsetAt(x: Float, y: Float): Int? {
        val currentLayout = layout ?: return null
        val contentX = x - totalPaddingLeft + scrollX
        val contentY = y - totalPaddingTop + scrollY
        if (contentX < 0f || contentY < 0f || contentX > currentLayout.width || contentY > currentLayout.height) {
            return null
        }
        val line = currentLayout.getLineForVertical(contentY.roundToInt())
        val lineStart = currentLayout.getLineStart(line)
        val lineEnd = currentLayout.getLineEnd(line)
        if (lineStart >= lineEnd) return null
        return currentLayout.getOffsetForHorizontal(line, contentX)
            .coerceIn(lineStart, lineEnd)
            .coerceIn(0, text?.length ?: 0)
    }

    fun updatePlainTapHandler(handler: ((Float, Float, Float, Float) -> Unit)?) {
        onPlainTap = handler
    }

    fun updateSelectionGestureActiveChangedHandler(handler: ((Boolean) -> Unit)?) {
        onSelectionGestureActiveChanged = handler
    }

    private fun resolveClickableSpanAt(event: MotionEvent): ClickableSpan? {
        val currentLayout = layout ?: return null
        val spannable = text as? Spanned ?: return null
        if (spannable.length == 0) return null
        val x = event.x - totalPaddingLeft + scrollX
        val y = event.y - totalPaddingTop + scrollY
        if (x < 0 || y < 0 || x > currentLayout.width || y > currentLayout.height) {
            return null
        }
        val line = currentLayout.getLineForVertical(y.roundToInt())
        val offset = currentLayout.getOffsetForHorizontal(line, x).coerceIn(0, spannable.length - 1)
        return spannable.getSpans(offset, offset + 1, ClickableSpan::class.java).lastOrNull()
    }
}

/** Coordinates a single page's independently-rendered text blocks as one selection surface. */
internal class NovelPageReaderSelectionCoordinator {
    private val views = linkedSetOf<NovelPageReaderTextView>()
    private var owner: NovelPageReaderTextView? = null
    private var anchorStart = -1
    private var anchorEnd = -1
    private var globalStartView: NovelPageReaderTextView? = null
    private var globalStartOffset = -1
    private var globalEndView: NovelPageReaderTextView? = null
    private var globalEndOffset = -1
    private var draggingGlobalStart: Boolean? = null

    fun register(view: NovelPageReaderTextView) {
        views += view
    }

    /**
     * Drops [view] from the shared surface. In a `LazyColumn`, this fires whenever a block
     * scrolls out of the composed window, not just when its selection is dismissed — a block
     * that currently holds a selection endpoint or sits inside the active range can leave
     * composition mid-gesture. Rather than leave a stale reference in [globalStartView]/
     * [globalEndView] (which the next drag/extend call would read and act on with a
     * detached view) or silently open a gap in [views] that [applyRange] would treat as
     * adjacency, collapse the whole coordinated selection whenever the departing view was
     * part of it.
     */
    fun unregister(view: NovelPageReaderTextView) {
        views -= view
        if (owner === view || globalStartView === view || globalEndView === view) {
            val previousOwner = owner
            owner = null
            anchorStart = -1
            anchorEnd = -1
            globalStartView = null
            globalStartOffset = -1
            globalEndView = null
            globalEndOffset = -1
            draggingGlobalStart = null
            views.forEach(NovelPageReaderTextView::clearSelectionFromCoordinator)
            previousOwner?.let {
                it.finishSelectionActionModeFromCoordinator()
                it.notifyCoordinatedSelectionCleared()
            }
        }
    }

    fun beginSelection(view: NovelPageReaderTextView, start: Int, end: Int) {
        owner = view
        anchorStart = minOf(start, end)
        anchorEnd = maxOf(start, end)
        // A new word selection starts in one TextView, but its two visible handles are already
        // global endpoints. Initialise them here so either handle can cross into another block
        // on its very first adjustment.
        globalStartView = view
        globalStartOffset = anchorStart
        globalEndView = view
        globalEndOffset = anchorEnd
    }

    fun hasActiveSelection(): Boolean = owner != null

    fun extendSelection(source: NovelPageReaderTextView, localX: Float, localY: Float): Boolean {
        if (owner !== source || anchorStart < 0 || anchorEnd <= anchorStart) return false
        val sourceLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        val target = views.firstOrNull { view ->
            val rect = Rect()
            view.getGlobalVisibleRect(rect) && rect.contains(
                (sourceLocation[0] + localX).roundToInt(),
                (sourceLocation[1] + localY).roundToInt(),
            )
        } ?: return false
        val offset = target.resolveTextOffsetAtScreen(
            sourceLocation[0] + localX,
            sourceLocation[1] + localY,
        ) ?: return false
        applyRange(source, target, offset)
        return true
    }

    fun beginHandleDrag(view: NovelPageReaderTextView, movingStart: Boolean): Boolean {
        draggingGlobalStart = null
        if (movingStart && view !== globalStartView) return false
        if (!movingStart && view !== globalEndView) return false
        draggingGlobalStart = movingStart
        return true
    }

    fun endHandleDrag() {
        draggingGlobalStart = null
    }

    fun hasActiveHandleDrag(): Boolean = draggingGlobalStart != null

    fun updateActiveHandleDrag(
        view: NovelPageReaderTextView,
        localX: Float,
        localY: Float,
    ): Boolean {
        val movingStart = draggingGlobalStart ?: return false
        return updateHandleDrag(view, movingStart, localX, localY)
    }

    fun updateHandleDrag(
        view: NovelPageReaderTextView,
        movingStart: Boolean,
        localX: Float,
        localY: Float,
    ): Boolean {
        val activeOwner = owner ?: return false
        // Android keeps sending this gesture's MOVE events to the TextView that received DOWN.
        // Once the handle crosses into another paragraph that is no longer the same view as the
        // global endpoint, so do not reject the continuing stream based on the endpoint's view.
        val effectiveMovingStart = draggingGlobalStart ?: movingStart
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val screenX = location[0] + localX
        val screenY = location[1] + localY
        val target = views.firstOrNull { candidate ->
            val rect = Rect()
            candidate.getGlobalVisibleRect(rect) && rect.contains(screenX.roundToInt(), screenY.roundToInt())
        } ?: return false
        val offset = target.resolveTextOffsetAtScreen(screenX, screenY) ?: return false
        val fixedView = (if (effectiveMovingStart) globalEndView else globalStartView) ?: return false
        val fixedOffset = if (effectiveMovingStart) globalEndOffset else globalStartOffset
        if (fixedOffset < 0) return false
        applyExplicitRange(
            activeOwner = activeOwner,
            firstView = if (effectiveMovingStart) target else fixedView,
            firstOffset = if (effectiveMovingStart) offset else fixedOffset,
            secondView = if (effectiveMovingStart) fixedView else target,
            secondOffset = if (effectiveMovingStart) fixedOffset else offset,
        )
        // Crossing the stationary endpoint swaps the semantic roles while the same finger keeps
        // moving. A view alone is not enough to determine this: both endpoints may belong to the
        // same paragraph/TextView. Compare the resolved text offset too, otherwise an ordinary
        // end-handle move is mistakenly reclassified as the start handle on its second MOVE.
        draggingGlobalStart = target === globalStartView && offset == globalStartOffset
        return true
    }

    fun clearSelection(requester: NovelPageReaderTextView): Boolean {
        if (owner == null) return false
        val previousOwner = owner ?: return false
        owner = null
        anchorStart = -1
        anchorEnd = -1
        globalStartView = null
        globalStartOffset = -1
        globalEndView = null
        globalEndOffset = -1
        draggingGlobalStart = null
        views.forEach(NovelPageReaderTextView::clearSelectionFromCoordinator)
        previousOwner.finishSelectionActionModeFromCoordinator()
        previousOwner.notifyCoordinatedSelectionCleared()
        return true
    }

    fun expandSelection() {
        val activeOwner = owner ?: return
        val startView = globalStartView ?: activeOwner
        val endView = globalEndView ?: activeOwner
        if (startView === endView) {
            activeOwner.expandLocalSelectionForConsole()
            return
        }
        // A multi-block selection is already at paragraph granularity. Expand every touched
        // block, preserving both global endpoints rather than collapsing back to the owner.
        applyExplicitRange(
            activeOwner = activeOwner,
            firstView = startView,
            firstOffset = 0,
            secondView = endView,
            secondOffset = endView.selectionText().length,
        )
    }

    fun isOwner(view: NovelPageReaderTextView): Boolean = owner === view

    private fun applyExplicitRange(
        activeOwner: NovelPageReaderTextView,
        firstView: NovelPageReaderTextView,
        firstOffset: Int,
        secondView: NovelPageReaderTextView,
        secondOffset: Int,
    ) {
        val ordered = views.sortedBy(NovelPageReaderTextView::selectionOrder)
        var startView = firstView
        var startOffset = firstOffset
        var endView = secondView
        var endOffset = secondOffset
        val firstIndex = ordered.indexOf(firstView)
        val secondIndex = ordered.indexOf(secondView)
        if (firstIndex > secondIndex || (firstIndex == secondIndex && firstOffset > secondOffset)) {
            startView = secondView
            startOffset = secondOffset
            endView = firstView
            endOffset = firstOffset
        }
        if (startView === endView && startOffset == endOffset) return
        anchorStart = startOffset
        anchorEnd = (startOffset + 1).coerceAtMost(startView.selectionText().length)
        applyRange(startView, endView, endOffset)
        owner = activeOwner
        publishCurrentRange(activeOwner)
    }

    private fun publishCurrentRange(activeOwner: NovelPageReaderTextView) {
        val ordered = views.sortedBy(NovelPageReaderTextView::selectionOrder)
        val selectedText = ordered.mapNotNull { view ->
            val spannable = view.text as? Spannable ?: return@mapNotNull null
            val start = Selection.getSelectionStart(spannable)
            val end = Selection.getSelectionEnd(spannable)
            if (start < 0 || end <= start) null else view.selectionText().substring(start, end)
        }.joinToString("\n")
        val bounds = ordered.mapNotNull(NovelPageReaderTextView::selectionBoundsOnScreen).fold(RectF()) { union, next ->
            if (union.isEmpty) RectF(next) else union.apply { union(next) }
        }
        if (!bounds.isEmpty) activeOwner.publishCoordinatedSelection(selectedText, bounds)
    }

    private fun applyRange(
        source: NovelPageReaderTextView,
        target: NovelPageReaderTextView,
        targetOffset: Int,
    ) {
        val orderedViews = views.sortedBy(NovelPageReaderTextView::selectionOrder)
        val sourceIndex = orderedViews.indexOf(source)
        val targetIndex = orderedViews.indexOf(target)
        if (sourceIndex < 0 || targetIndex < 0) return

        val ranges = linkedMapOf<NovelPageReaderTextView, Pair<Int, Int>>()
        if (sourceIndex == targetIndex) {
            when {
                targetOffset < anchorStart -> ranges[source] = targetOffset to anchorEnd
                targetOffset > anchorEnd -> ranges[source] = anchorStart to targetOffset
                else -> ranges[source] = anchorStart to anchorEnd
            }
        } else if (targetIndex > sourceIndex) {
            ranges[source] = anchorStart to source.selectionText().length
            for (index in sourceIndex + 1 until targetIndex) {
                val view = orderedViews[index]
                ranges[view] = 0 to view.selectionText().length
            }
            ranges[target] = 0 to targetOffset
        } else {
            ranges[source] = 0 to anchorEnd
            for (index in targetIndex + 1 until sourceIndex) {
                val view = orderedViews[index]
                ranges[view] = 0 to view.selectionText().length
            }
            ranges[target] = targetOffset to target.selectionText().length
        }

        views.forEach { view ->
            val range = ranges[view]
            if (range == null || range.first >= range.second) {
                view.clearSelectionFromCoordinator()
            } else {
                view.applySelectionFromCoordinator(range.first, range.second)
            }
        }
        val selectionStartView = if (targetIndex < sourceIndex) target else source
        val selectionEndView = if (targetIndex > sourceIndex) target else source
        globalStartView = selectionStartView
        globalStartOffset = ranges[selectionStartView]?.first ?: -1
        globalEndView = selectionEndView
        globalEndOffset = ranges[selectionEndView]?.second ?: -1
        ranges.keys.forEach { view ->
            view.setCoordinatedHandleVisibility(
                showStart = view === selectionStartView,
                showEnd = view === selectionEndView,
            )
        }

        val selectedText = orderedViews.mapNotNull { view ->
            val range = ranges[view] ?: return@mapNotNull null
            view.selectionText().substring(range.first.coerceIn(0, view.selectionText().length), range.second.coerceIn(0, view.selectionText().length))
                .takeIf(String::isNotBlank)
        }.joinToString(separator = "\n")
        val bounds = ranges.keys.mapNotNull(NovelPageReaderTextView::selectionBoundsOnScreen)
            .fold(RectF()) { union, next ->
                if (union.isEmpty) RectF(next) else union.apply { union(next) }
            }
        if (!bounds.isEmpty) {
            source.publishCoordinatedSelection(selectedText, bounds)
        }
    }
}

@Composable
internal fun NovelPageReaderPageContent(
    contentPage: NovelPageContentPage,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    pageSurfaceColor: Color? = null,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    chapterTitleTextColor: Color,
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    contentPadding: Dp,
    statusBarTopPadding: Dp,
    backgroundTexture: NovelReaderBackgroundTexture,
    nativeTextureStrengthPercent: Int,
    ttsHighlightState: NovelReaderTtsHighlightState? = null,
    ttsHighlightColor: Color = Color.Transparent,
    selectionRenderer: NovelSelectedTextRenderer = NovelSelectedTextRenderer.PAGE_READER,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onSelectionRendererActionsChanged: (NovelSelectedTextRendererActions) -> Unit = {},
    selectionClearRequestToken: Int = 0,
    selectionExpandRequestToken: Int = 0,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val selectionCoordinator = remember(contentPage.pageIndex) { NovelPageReaderSelectionCoordinator() }
    val contentLayout = resolveNovelPageReaderContentLayout(
        contentPadding = contentPadding,
        statusBarTopPadding = statusBarTopPadding,
        horizontalMargin = readerSettings.margin,
    )
    val bookBottomInset = resolveNovelPageReaderBookBottomInset(
        density = density,
        fontSize = readerSettings.fontSize,
        lineHeight = readerSettings.lineHeight,
    )
    val preserveSourceAlign = readerSettings.preserveSourceTextAlignInNative

    // Handle short taps on the empty page area (below the text, in margins, around images)
    // so they reach the same onPlainTap dispatch as taps on text/image blocks. Child
    // handlers (TextView, image block) consume their own events, so this never double-fires.
    val pageTapModifier = if (touchHandlingEnabled) {
        Modifier.pointerInput(onPlainTap) {
            detectTapGestures(
                onTap = { offset ->
                    onPlainTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier.fillMaxSize().then(pageTapModifier),
        contentAlignment = Alignment.TopStart,
    ) {
        if (pageSurfaceColor != Color.Transparent) {
            NovelPageSurfaceBackground(
                backgroundTexture = backgroundTexture,
                nativeTextureStrengthPercent = nativeTextureStrengthPercent,
                surfaceColor = pageSurfaceColor,
            )
        }
        val imageBlock = contentPage.blocks.singleOrNull() as? NovelPageContentBlock.Image
        if (imageBlock != null) {
            NovelPageReaderImageBlock(
                imageUrl = imageBlock.imageUrl,
                contentDescription = imageBlock.contentDescription,
                contentLayout = contentLayout,
                bookBottomInset = bookBottomInset,
                fullPage = true,
                onPlainTap = onPlainTap,
                onImageLongClick = onImageLongClick,
                touchHandlingEnabled = touchHandlingEnabled,
            )
            return@Box
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentLayout.textPadding)
                .padding(bottom = bookBottomInset),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                contentPage.blocks.forEachIndexed { index, block ->
                    key(index, block) {
                        if (block.spacingBeforePx > 0) {
                            Spacer(
                                modifier = Modifier.height(
                                    with(density) { block.spacingBeforePx.toDp() },
                                ),
                            )
                        }
                        when (block) {
                            is NovelPageContentBlock.Plain -> {
                                val baseText = if (readerSettings.bionicReading) {
                                    toBionicText(block.text)
                                } else {
                                    AnnotatedString(block.text)
                                }
                                NovelPageReaderTextBlock(
                                    text = applyNovelReaderTtsHighlight(
                                        text = baseText,
                                        blockText = block.text,
                                        sourceBlockIndex = block.sourceBlockIndex,
                                        pageIndex = contentPage.pageIndex,
                                        pageBlockTextStart = block.sourceTextStart,
                                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                                        highlightState = ttsHighlightState,
                                        highlightColor = ttsHighlightColor,
                                    ),
                                    isChapterTitle = block.isChapterTitle,
                                    firstLineIndentEm = block.firstLineIndentEm,
                                    readerSettings = readerSettings,
                                    textColor = textColor,
                                    textBackground = textBackground,
                                    textAlign = resolveNativeReaderTextAlign(
                                        globalTextAlign = readerSettings.textAlign,
                                        preserveSourceTextAlignInNative = preserveSourceAlign,
                                        sourceTextAlign = null,
                                    ),
                                    textTypeface = textTypeface,
                                    chapterTitleTypeface = chapterTitleTypeface,
                                    chapterTitleTextColor = if (block.isChapterTitle) {
                                        Color.Black
                                    } else {
                                        chapterTitleTextColor
                                    },
                                    textShadowEnabled = readerSettings.textShadow,
                                    textShadowColor = readerSettings.textShadowColor,
                                    textShadowBlur = readerSettings.textShadowBlur,
                                    textShadowX = readerSettings.textShadowX,
                                    textShadowY = readerSettings.textShadowY,
                                    baseTypefaceStyle = if (block.isChapterTitle) {
                                        Typeface.BOLD
                                    } else {
                                        Typeface.NORMAL
                                    },
                                    selectionRenderer = selectionRenderer,
                                    selectionSessionIdProvider = selectionSessionIdProvider,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                    onSelectionRendererActionsChanged = onSelectionRendererActionsChanged,
                                    selectionCoordinator = selectionCoordinator,
                                    selectionBlockOrder = index,
                                    selectionClearRequestToken = selectionClearRequestToken,
                                    selectionExpandRequestToken = selectionExpandRequestToken,
                                    onPlainTap = onPlainTap,
                                    touchHandlingEnabled = touchHandlingEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            is NovelPageContentBlock.Rich -> {
                                NovelPageReaderTextBlock(
                                    text = applyNovelReaderTtsHighlight(
                                        text = block.text,
                                        blockText = block.text.text,
                                        sourceBlockIndex = block.sourceBlockIndex,
                                        pageIndex = contentPage.pageIndex,
                                        pageBlockTextStart = block.sourceTextStart,
                                        pageBlockTextEndExclusive = block.sourceTextEndExclusive,
                                        highlightState = ttsHighlightState,
                                        highlightColor = ttsHighlightColor,
                                    ),
                                    isChapterTitle = block.isChapterTitle,
                                    firstLineIndentEm = block.firstLineIndentEm,
                                    readerSettings = readerSettings,
                                    textColor = textColor,
                                    textBackground = textBackground,
                                    textAlign = resolveNativeReaderTextAlign(
                                        globalTextAlign = readerSettings.textAlign,
                                        preserveSourceTextAlignInNative = preserveSourceAlign,
                                        sourceTextAlign = block.sourceTextAlign,
                                    ),
                                    textTypeface = textTypeface,
                                    chapterTitleTypeface = chapterTitleTypeface,
                                    chapterTitleTextColor = if (block.isChapterTitle) {
                                        Color.Black
                                    } else {
                                        chapterTitleTextColor
                                    },
                                    textShadowEnabled = readerSettings.textShadow,
                                    textShadowColor = readerSettings.textShadowColor,
                                    textShadowBlur = readerSettings.textShadowBlur,
                                    textShadowX = readerSettings.textShadowX,
                                    textShadowY = readerSettings.textShadowY,
                                    baseTypefaceStyle = if (block.isChapterTitle) {
                                        Typeface.BOLD
                                    } else {
                                        Typeface.NORMAL
                                    },
                                    selectionRenderer = selectionRenderer,
                                    selectionSessionIdProvider = selectionSessionIdProvider,
                                    onSelectedTextSelectionChanged = onSelectedTextSelectionChanged,
                                    onSelectionRendererActionsChanged = onSelectionRendererActionsChanged,
                                    selectionCoordinator = selectionCoordinator,
                                    selectionBlockOrder = index,
                                    selectionClearRequestToken = selectionClearRequestToken,
                                    selectionExpandRequestToken = selectionExpandRequestToken,
                                    onPlainTap = onPlainTap,
                                    touchHandlingEnabled = touchHandlingEnabled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            is NovelPageContentBlock.Image -> {
                                NovelPageReaderImageBlock(
                                    imageUrl = block.imageUrl,
                                    contentDescription = block.contentDescription,
                                    contentLayout = contentLayout,
                                    bookBottomInset = bookBottomInset,
                                    fullPage = false,
                                    onPlainTap = onPlainTap,
                                    onImageLongClick = onImageLongClick,
                                    touchHandlingEnabled = touchHandlingEnabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelPageReaderImageBlock(
    imageUrl: String,
    contentDescription: String?,
    contentLayout: NovelPageReaderContentLayout,
    bookBottomInset: Dp,
    fullPage: Boolean,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onImageLongClick: ((String) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
) {
    val referer = LocalNovelReaderReferer.current
    val imageModel = if (NovelPluginImage.isSupported(imageUrl)) {
        NovelPluginImage(imageUrl)
    } else if (referer != null) {
        NovelReaderRefererImage(
            url = imageUrl,
            referer = referer,
        )
    } else {
        imageUrl
    }

    val gestureModifier = if (touchHandlingEnabled) {
        Modifier.pointerInput(imageUrl, onPlainTap, onImageLongClick) {
            detectTapGestures(
                onTap = { offset ->
                    onPlainTap?.invoke(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                },
                onLongPress = {
                    onImageLongClick?.invoke(imageUrl)
                },
            )
        }
    } else {
        Modifier
    }

    if (fullPage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentLayout.textPadding)
                .padding(bottom = bookBottomInset)
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Inside,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            )
        }
    }
}

@Composable
internal fun NovelPageReaderTextBlock(
    text: AnnotatedString,
    isChapterTitle: Boolean,
    firstLineIndentEm: Float?,
    readerSettings: NovelReaderSettings,
    textColor: Color,
    textBackground: Color,
    textAlign: ReaderTextAlign,
    textTypeface: Typeface?,
    chapterTitleTypeface: Typeface?,
    chapterTitleTextColor: Color,
    textShadowEnabled: Boolean,
    textShadowColor: String,
    textShadowBlur: Float,
    textShadowX: Float,
    textShadowY: Float,
    fontSizeMultiplier: Float = if (isChapterTitle) PAGE_READER_CHAPTER_TITLE_FONT_SIZE_MULTIPLIER else 1f,
    lineHeightMultiplier: Float = if (isChapterTitle) PAGE_READER_CHAPTER_TITLE_LINE_HEIGHT_MULTIPLIER else 1f,
    textColorOverride: Color? = null,
    baseTypefaceStyle: Int = Typeface.NORMAL,
    selectionRenderer: NovelSelectedTextRenderer? = null,
    selectionSessionIdProvider: () -> Long = { 0L },
    onSelectedTextSelectionChanged: (NovelSelectedTextSelection?) -> Unit = {},
    onSelectionRendererActionsChanged: (NovelSelectedTextRendererActions) -> Unit = {},
    selectionCoordinator: NovelPageReaderSelectionCoordinator? = null,
    selectionBlockOrder: Int = 0,
    selectionClearRequestToken: Int = 0,
    selectionExpandRequestToken: Int = 0,
    onPlainTap: ((Float, Float, Float, Float) -> Unit)? = null,
    onSelectionGestureActiveChanged: ((Boolean) -> Unit)? = null,
    touchHandlingEnabled: Boolean = true,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val blockFontSizeMultiplier = fontSizeMultiplier
    val blockLineHeightMultiplier = lineHeightMultiplier
    val blockTextSizePx = with(density) {
        (readerSettings.fontSize * blockFontSizeMultiplier).sp.toPx()
    }
    val blockLineSpacingMultiplier = readerSettings.lineHeight * blockLineHeightMultiplier
    val blockFirstLineIndentPx = resolveNovelPageReaderFirstLineIndentPx(
        firstLineIndentEm = firstLineIndentEm,
        textSizePx = blockTextSizePx,
    )
    val blockTypeface = if (isChapterTitle) {
        chapterTitleTypeface ?: textTypeface
    } else {
        textTypeface
    }
    val blockTextColor = textColorOverride ?: if (isChapterTitle) chapterTitleTextColor else textColor
    val blockTextShadow = resolveReaderTextShadow(
        textShadowEnabled = textShadowEnabled,
        textShadowColor = textShadowColor,
        textShadowBlur = textShadowBlur,
        textShadowX = textShadowX,
        textShadowY = textShadowY,
        textColor = blockTextColor,
        backgroundColor = textBackground,
    )
    val blockTextViewMetrics = resolveNovelPageReaderTextViewMetrics(
        textSizePx = blockTextSizePx,
        lineSpacingMultiplier = blockLineSpacingMultiplier,
        typeface = blockTypeface,
        textAlign = textAlign,
        shadow = blockTextShadow,
    )
    val selectionInteractionEnabled =
        touchHandlingEnabled &&
            (
                readerSettings.textSelectionEnabled ||
                    readerSettings.selectedTextTranslationEnabled ||
                    readerSettings.novelDictionaryEnabled
                )
    // The TextView stays non-focusable to avoid Compose interop focus crashes, so draw selection
    // feedback explicitly using the reader theme's primary color.
    val blockSelectionHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f).toArgb()
    val blockSelectionHandleColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            lateinit var textView: NovelPageReaderTextView
            textView = NovelPageReaderTextView(
                context = context,
                selectionRenderer = selectionRenderer ?: NovelSelectedTextRenderer.PAGE_READER,
                selectionSessionIdProvider = selectionSessionIdProvider,
                onSelectedTextSelectionChanged = { selection ->
                    if (selection != null) {
                        onSelectionRendererActionsChanged(
                            NovelSelectedTextRendererActions(
                                clear = textView::clearSelectionForConsole,
                                expand = textView::expandSelectionForConsole,
                            ),
                        )
                    }
                    onSelectedTextSelectionChanged(selection)
                },
                selectionCoordinator = selectionCoordinator,
                selectionBlockOrder = selectionBlockOrder,
                onPlainTap = onPlainTap,
                onSelectionGestureActiveChanged = onSelectionGestureActiveChanged,
                touchHandlingEnabled = touchHandlingEnabled,
                selectionInteractionEnabled = selectionInteractionEnabled,
            ).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
                setPadding(0, 0, 0, glyphPadBottom)
                includeFontPadding = false
                setTextColor(blockTextColor.toArgb())
                // Selection is drawn explicitly in NovelPageReaderTextView.onDraw(). Keeping
                // TextView's native highlight transparent prevents the action-mode owner from
                // receiving a second, darker/lighter overlay than coordinated sibling blocks.
                setHighlightColor(android.graphics.Color.TRANSPARENT)
                selectionHighlightColor = blockSelectionHighlightColor
                selectionHandleColor = blockSelectionHandleColor
                applyNovelPageReaderTextViewMetrics(textView = this, metrics = blockTextViewMetrics)
            }
            textView
        },
        update = { textView ->
            textView.isDictionaryEnabled = readerSettings.novelDictionaryEnabled
            textView.isTranslationEnabled = readerSettings.selectedTextTranslationEnabled
            textView.updatePlainTapHandler(onPlainTap)
            textView.updateSelectionGestureActiveChangedHandler(onSelectionGestureActiveChanged)
            textView.updateSelectionInteractionEnabled(selectionInteractionEnabled)
            textView.setHighlightColor(android.graphics.Color.TRANSPARENT)
            textView.selectionHighlightColor = blockSelectionHighlightColor
            textView.selectionHandleColor = blockSelectionHandleColor
            textView.applySelectionClearRequestToken(selectionClearRequestToken)
            textView.applySelectionExpandRequestToken(selectionExpandRequestToken)
            val glyphPadBottom = resolvePageReaderGlyphOverflowPaddingPx(blockTextSizePx)
            textView.setPadding(textView.paddingLeft, textView.paddingTop, textView.paddingRight, glyphPadBottom)
            applyNovelPageReaderTextViewMetrics(textView = textView, metrics = blockTextViewMetrics)
            val forcedTypefaceStyle = combineTypefaceStyles(
                first = baseTypefaceStyle,
                second = resolveForcedReaderTypefaceStyle(
                    forceBoldText = readerSettings.forceBoldText,
                    forceItalicText = readerSettings.forceItalicText,
                ),
            )
            val renderedTextSignature = resolveNovelPageReaderRenderedTextSignature(
                text = text,
                firstLineIndentPx = blockFirstLineIndentPx,
                forcedTypefaceStyle = forcedTypefaceStyle,
            )
            if (textView.renderedTextSignature != renderedTextSignature) {
                textView.text = buildNovelPageReaderSpannableText(
                    text = text,
                    firstLineIndentPx = blockFirstLineIndentPx,
                    forcedTypefaceStyle = forcedTypefaceStyle,
                    onUrlClick = onUrlClick,
                )
                textView.renderedTextSignature = renderedTextSignature
            }
            textView.setTextColor(blockTextColor.toArgb())
        },
        // Dropping a focused interop view makes the framework search the hierarchy for a new focus
        // target, which can re-enter Compose layout while changes are still being applied. Giving
        // up focus first keeps that removal side-effect free.
        onRelease = { textView ->
            textView.clearFocus()
        },
    )
}
