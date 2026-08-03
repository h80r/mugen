package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Lattice Protocol theme (hidden reward of the Lattice Resonance).
 * Circuit-etched void with cyan signal traces and amber service accents.
 */
internal object LatticeProtocolColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF5FE9FF),
        onPrimary = Color(0xFF00323B),
        primaryContainer = Color(0xFF004A57),
        onPrimaryContainer = Color(0xFFAEF3FF),
        inversePrimary = Color(0xFF006879),
        secondary = Color(0xFF5FE9FF),
        onSecondary = Color(0xFF00323B),
        secondaryContainer = Color(0xFF0A2A38),
        onSecondaryContainer = Color(0xFFAEF3FF),
        tertiary = Color(0xFFFFB84D),
        onTertiary = Color(0xFF442B00),
        tertiaryContainer = Color(0xFF624000),
        onTertiaryContainer = Color(0xFFFFDDB0),
        background = Color(0xFF050608),
        onBackground = Color(0xFFF4FEFF),
        surface = Color(0xFF050608),
        onSurface = Color(0xFFF4FEFF),
        surfaceVariant = Color(0xFF0A1220),
        onSurfaceVariant = Color(0xFFBFC8D9),
        surfaceTint = Color(0xFF5FE9FF),
        inverseSurface = Color(0xFFE1E3E4),
        inverseOnSurface = Color(0xFF1A1C1D),
        outline = Color(0xFF3A4A5C),
        outlineVariant = Color(0xFF243244),
        error = Color(0xFFFF3B4E),
        onError = Color(0xFF690007),
        errorContainer = Color(0xFF93000F),
        onErrorContainer = Color(0xFFFFDAD8),
        surfaceContainerLowest = Color(0xFF030405),
        surfaceContainerLow = Color(0xFF070A0F),
        surfaceContainer = Color(0xFF0A1220),
        surfaceContainerHigh = Color(0xFF101A2A),
        surfaceContainerHighest = Color(0xFF162234),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF006879),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFAEF3FF),
        onPrimaryContainer = Color(0xFF001F26),
        inversePrimary = Color(0xFF5FE9FF),
        secondary = Color(0xFF006879),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE7EC),
        onSecondaryContainer = Color(0xFF051F24),
        tertiary = Color(0xFF825500),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDDB0),
        onTertiaryContainer = Color(0xFF291800),
        background = Color(0xFFF7FAFC),
        onBackground = Color(0xFF171C1E),
        surface = Color(0xFFF7FAFC),
        onSurface = Color(0xFF171C1E),
        surfaceVariant = Color(0xFFDBE4E8),
        onSurfaceVariant = Color(0xFF3F484C),
        surfaceTint = Color(0xFF006879),
        inverseSurface = Color(0xFF2C3134),
        inverseOnSurface = Color(0xFFEDF1F3),
        outline = Color(0xFF6F797D),
        outlineVariant = Color(0xFFBFC8CC),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F4F6),
        surfaceContainer = Color(0xFFEBEEF1),
        surfaceContainerHigh = Color(0xFFE5E9EB),
        surfaceContainerHighest = Color(0xFFDFE3E6),
    )
}
