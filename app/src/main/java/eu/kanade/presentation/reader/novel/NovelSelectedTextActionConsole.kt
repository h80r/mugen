package eu.kanade.presentation.reader.novel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import eu.kanade.presentation.components.AuroraBottomBar
import eu.kanade.presentation.components.AuroraBottomBarItem
import eu.kanade.presentation.components.auroraCelestialBar
import eu.kanade.presentation.components.auroraCelestialHalo
import eu.kanade.presentation.components.latticeCircuitBar
import eu.kanade.presentation.components.rememberAuroraCelestialNavbarUnlocked
import eu.kanade.presentation.components.rememberLatticeCircuitNavbarUnlocked
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.reader.novel.NovelSelectedTextConsoleAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/** Aurora action console shown by the reader while a text selection is active. */
@Composable
internal fun NovelSelectedTextActionConsole(
    dictionaryEnabled: Boolean,
    translationEnabled: Boolean,
    onAction: (NovelSelectedTextConsoleAction) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val actions = NovelSelectedTextConsoleAction.entries.filter { action ->
        when (action) {
            NovelSelectedTextConsoleAction.DICTIONARY -> dictionaryEnabled
            NovelSelectedTextConsoleAction.TRANSLATE -> translationEnabled
            else -> true
        }
    }
    val colors = AuroraTheme.colorsForCurrentTheme()
    var selectedAction by remember { mutableStateOf<NovelSelectedTextConsoleAction?>(null) }
    val scope = rememberCoroutineScope()
    val appHaptics = LocalAppHaptics.current
    val celestialEnabled = !colors.isEInk && rememberAuroraCelestialNavbarUnlocked()
    val circuitEnabled = !colors.isEInk && rememberLatticeCircuitNavbarUnlocked()
    val selectedIndex = actions.indexOf(selectedAction)
    val celestialDecoration = if (celestialEnabled) {
        Modifier.auroraCelestialBar(
            accent = colors.accent,
            accentVariant = colors.accentVariant,
            isDark = colors.isDark,
            selectedIndex = selectedIndex,
            tabCount = actions.size,
        )
    } else {
        Modifier
    }
    val decorationModifier = if (circuitEnabled) celestialDecoration.latticeCircuitBar() else celestialDecoration

    AuroraBottomBar(
        hazeState = hazeState,
        modifier = modifier,
        decorationModifier = decorationModifier,
    ) {
        // Keep the same equal-width item layout as the home navigation hub. The former
        // intrinsic-width, scrollable row made this transient hub read as a separate UI.
        actions.forEach { action ->
            AuroraBottomBarItem(
                label = action.label(),
                selected = selectedAction == action,
                role = Role.Button,
                onClick = {
                    if (selectedAction == null) {
                        selectedAction = action
                        appHaptics.tap()
                        // The reader's Android TextView can receive this same pointer event. Run
                        // the command now, while its native selection is still intact; delaying
                        // it for the visual feedback turns Expand into a no-op.
                        onAction(action)
                        scope.launch {
                            if (action == NovelSelectedTextConsoleAction.EXPAND) {
                                // Expanding keeps the selection in place, unlike actions that
                                // dismiss the console. Return its visual state to neutral after
                                // the same short feedback window used by the home hub.
                                delay(540)
                                selectedAction = null
                            }
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = action.icon(),
                        contentDescription = action.label(),
                    )
                },
                selectedIconModifier = {
                    it.auroraCelestialHalo(
                        accent = colors.accent,
                        accentVariant = colors.accentVariant,
                        isDark = colors.isDark,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        enabled = celestialEnabled,
                    )
                },
            )
        }
    }
}

@Composable
private fun NovelSelectedTextConsoleAction.label(): String = when (this) {
    NovelSelectedTextConsoleAction.COPY -> stringResource(MR.strings.copy)
    NovelSelectedTextConsoleAction.SHARE -> stringResource(MR.strings.action_share)
    NovelSelectedTextConsoleAction.EXPAND -> stringResource(AYMR.strings.novel_reader_text_selection_action_expand)
    NovelSelectedTextConsoleAction.DICTIONARY -> stringResource(
        AYMR.strings.novel_reader_text_selection_action_dictionary,
    )
    NovelSelectedTextConsoleAction.TRANSLATE -> stringResource(
        AYMR.strings.novel_reader_text_selection_action_translate,
    )
}

private fun NovelSelectedTextConsoleAction.icon() = when (this) {
    NovelSelectedTextConsoleAction.COPY -> Icons.Default.ContentCopy
    NovelSelectedTextConsoleAction.SHARE -> Icons.Default.Share
    NovelSelectedTextConsoleAction.EXPAND -> Icons.Default.MoreHoriz
    NovelSelectedTextConsoleAction.DICTIONARY -> Icons.Default.Search
    NovelSelectedTextConsoleAction.TRANSLATE -> Icons.Default.Translate
}
