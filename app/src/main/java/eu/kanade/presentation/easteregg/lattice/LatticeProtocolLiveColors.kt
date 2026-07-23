package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Living Lattice Protocol accents: slow cyan breath + amber tertiary glint.
 * Frequencies &lt; 0.4 Hz — safe for epilepsy guidelines.
 */
data class LatticeProtocolLiveColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

@Composable
fun rememberLatticeProtocolLiveColors(animated: Boolean = true): LatticeProtocolLiveColors {
    val reduced = rememberLatticeReducedMotion()
    val phase: State<Float> = if (animated && !reduced) {
        val transition = rememberInfiniteTransition(label = "latticeProtocolLive")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "latticeBreath",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }
    val t by phase
    val breath = (sin(t * 2f * PI.toFloat()) + 1f) / 2f
    val cyanCore = Color(0xFF5FE9FF)
    val cyanDeep = Color(0xFF2BB8D0)
    val amber = Color(0xFFFFB84D)
    val amberSoft = Color(0xFFE09A30)
    return LatticeProtocolLiveColors(
        primary = lerp(cyanDeep, cyanCore, 0.45f + 0.55f * breath),
        secondary = lerp(cyanDeep, cyanCore, 0.25f + 0.4f * (1f - breath)),
        tertiary = lerp(amberSoft, amber, 0.35f + 0.65f * breath),
    )
}
