package eu.kanade.presentation.reader.novel

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.reader.settings.auroraRimColor
import eu.kanade.presentation.theme.AuroraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** Shared Aurora glass chrome for reader sheets, including progressive window effects. */
@Composable
internal fun NovelReaderAuroraSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val aurora = AuroraTheme.colors
    val baseScheme = MaterialTheme.colorScheme
    var sheetReveal by remember { mutableFloatStateOf(0f) }
    val sheetContainer = remember(aurora.isDark, aurora.isEInk) {
        when {
            aurora.isEInk -> baseScheme.surfaceContainerHigh
            aurora.isDark -> Color.Black.copy(alpha = 0.70f)
            else -> Color.White.copy(alpha = 0.88f)
        }
    }
    val auroraScheme = remember(baseScheme, aurora) {
        baseScheme.copy(
            primary = aurora.accent,
            onPrimary = if (aurora.isDark) aurora.background else Color.White,
            surfaceContainerHigh = sheetContainer,
            surfaceContainerHighest = sheetContainer,
            secondaryContainer = aurora.accent.copy(alpha = 0.22f),
            onSecondaryContainer = aurora.accent,
        )
    }
    val sheetShape = MaterialTheme.shapes.extraLarge.copy(
        bottomStart = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
    )
    val supportsBlurBehind = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !aurora.isEInk

    MaterialTheme(
        colorScheme = auroraScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        AdaptiveSheet(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.border(1.dp, auroraRimColor(), sheetShape),
            containerColor = sheetContainer,
            scrimAlpha = 0f,
            applyStatusBarsPadding = false,
            onRevealChange = { sheetReveal = it },
        ) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            val revealState = rememberUpdatedState(sheetReveal)
            DisposableEffect(window, supportsBlurBehind) {
                val sheetWindow = window
                if (sheetWindow != null) {
                    sheetWindow.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    sheetWindow.setDimAmount(0f)
                    if (supportsBlurBehind) {
                        sheetWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        sheetWindow.attributes = sheetWindow.attributes.apply { blurBehindRadius = 0 }
                    }
                }
                onDispose {
                    if (sheetWindow != null && supportsBlurBehind) {
                        sheetWindow.attributes = sheetWindow.attributes.apply { blurBehindRadius = 0 }
                        sheetWindow.setDimAmount(0f)
                        sheetWindow.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }
            LaunchedEffect(window, supportsBlurBehind) {
                val sheetWindow = window ?: return@LaunchedEffect
                snapshotFlow { revealState.value.coerceIn(0f, 1f) }
                    .map { reveal -> (reveal * 20f).roundToInt().coerceIn(0, 20) }
                    .distinctUntilChanged()
                    .collect { step ->
                        applyNovelReaderAuroraSheetWindowFx(
                            window = sheetWindow,
                            reveal = step / 20f,
                            supportsBlurBehind = supportsBlurBehind,
                        )
                    }
            }
            content()
        }
    }
}

private fun applyNovelReaderAuroraSheetWindowFx(
    window: Window,
    reveal: Float,
    supportsBlurBehind: Boolean,
) {
    val glass = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    if (supportsBlurBehind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = if (glass <= 0.02f) 0 else (28f * glass).roundToInt().coerceIn(1, 32)
        val attrs = window.attributes
        if (attrs.blurBehindRadius != radius) {
            window.attributes = attrs.apply { blurBehindRadius = radius }
        }
        window.setDimAmount(0.18f * glass)
    } else {
        window.setDimAmount(0.26f * glass)
    }
}
