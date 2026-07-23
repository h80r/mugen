package eu.kanade.presentation.easteregg.lattice

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** True when the user disabled animations system-wide (a11y). */
@Composable
fun rememberLatticeReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Slow 0..1 pulse. Default period 2.4s (~0.42 Hz), far below the dangerous 3-30 Hz strobe band.
 * With reduced motion returns a constant 0.5.
 */
@Composable
fun latticePulse(periodMs: Int = 2400): State<Float> {
    if (rememberLatticeReducedMotion()) {
        return remember { mutableFloatStateOf(0.5f) }
    }
    val transition = rememberInfiniteTransition(label = "latticePulse")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "latticePulseValue",
    )
}
