package eu.kanade.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.presentation.core.components.material.NavigationBar

/**
 * Reusable Aurora glass shell for bottom navigation and transient action consoles.
 * Decorations that depend on the caller (such as celestial or circuit effects) can
 * be supplied through [modifier] without duplicating the glass treatment.
 */
@Composable
fun AuroraBottomBar(
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    decorationModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = AuroraTheme.colorsForCurrentTheme()
    val shape = CircleShape
    val baseModifier = Modifier
        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
        .padding(horizontal = 12.dp, vertical = 10.dp)
    val glassModifier = if (colors.isDark) {
        baseModifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.12f),
                spotColor = Color.White.copy(alpha = 0.08f),
            )
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.White.copy(alpha = 0.18f),
                spotColor = Color.White.copy(alpha = 0.12f),
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = colors.background,
                            tint = HazeTint(colors.surface.copy(alpha = 0.65f)),
                            blurRadius = 24.dp,
                            noiseFactor = 0.12f,
                        ),
                    )
                } else {
                    Modifier.background(colors.surface.copy(alpha = 0.65f))
                },
            )
            .border(
                BorderStroke(width = 1.dp, brush = auroraMenuRimLightBrush(colors)),
                shape = shape,
            )
    } else {
        baseModifier
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = colors.background,
                            tint = HazeTint(colors.surface.copy(alpha = 0.65f)),
                            blurRadius = 24.dp,
                            noiseFactor = 0.12f,
                        ),
                    )
                } else {
                    Modifier.background(colors.surface.copy(alpha = 0.65f))
                },
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.20f)),
                    ),
                ),
                shape = shape,
            )
    }

    NavigationBar(
        // Positioning modifiers must wrap the glass shell. Appending them after the shell would
        // move only the navigation content while leaving the clipped surface behind.
        modifier = modifier.then(glassModifier).then(decorationModifier),
        containerColor = Color.Transparent,
        contentColor = colors.textPrimary,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 8.dp),
        content = content,
    )
}

/** A selectable Aurora bottom-bar entry shared by persistent navigation and action consoles. */
@Composable
fun RowScope.AuroraBottomBarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Tab,
    fillAvailableWidth: Boolean = true,
    selectedIconModifier: @Composable (Modifier) -> Modifier = { it },
) {
    val colors = AuroraTheme.colorsForCurrentTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val iconColor = if (selected) colors.accent else colors.textSecondary.copy(alpha = if (colors.isDark) 0.72f else 0.78f)
    val labelColor = if (selected) colors.accent else colors.textSecondary.copy(alpha = if (colors.isDark) 0.82f else 0.88f)
    val iconShape = RoundedCornerShape(999.dp)
    val iconBackgroundBrush = if (selected) {
        Brush.verticalGradient(
            listOf(
                if (colors.isDark) colors.accent.copy(alpha = 0.28f) else colors.accent.copy(alpha = 0.18f),
                if (colors.isDark) colors.accentVariant.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.78f),
            ),
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .then(if (fillAvailableWidth) Modifier.weight(1f) else Modifier)
            .padding(horizontal = 1.dp)
            .padding(top = 8.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (selected) {
                            selectedIconModifier(Modifier)
                                .background(iconBackgroundBrush!!, iconShape)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (colors.isDark) Color.White.copy(alpha = 0.12f) else colors.accent.copy(alpha = 0.16f),
                                    ),
                                    iconShape,
                                )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
            Text(
                text = label,
                color = labelColor.copy(alpha = if (enabled) 1f else 0.45f),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.92f,
                ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
