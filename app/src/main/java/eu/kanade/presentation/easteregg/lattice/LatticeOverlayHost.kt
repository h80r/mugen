package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.easteregg.lattice.LatticeProtocolManager
import eu.kanade.domain.easteregg.lattice.LatticeSignalBus
import kotlinx.coroutines.delay
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Global overlay host mounted in MainActivity.
 * Terminal flashes (whisper / latch / residual) + Grid on shell breach.
 */
@Composable
fun LatticeOverlayHost() {
    val context = LocalContext.current
    val manager = remember { LatticeProtocolManager.get(context) }
    val breach by LatticeSignalBus.breach.collectAsState()
    val latchTick by LatticeSignalBus.latchEvents.collectAsState()
    val whisper by LatticeSignalBus.whisper.collectAsState()

    var showGrid by remember { mutableStateOf(false) }
    LaunchedEffect(breach) {
        if (breach) {
            LatticeSignalBus.consumeBreach()
            showGrid = true
        }
    }

    var flashText by remember { mutableStateOf<String?>(null) }
    val latchLabel = stringResource(AYMR.strings.lattice_carrier_signal)
    val residualLabel = stringResource(AYMR.strings.lattice_residual)
    val whisperLabel = stringResource(AYMR.strings.lattice_frame_whisper)

    LaunchedEffect(latchTick) {
        if (latchTick != 0L) {
            flashText = latchLabel
            delay(2000)
            if (flashText == latchLabel) flashText = null
        }
    }

    LaunchedEffect(whisper) {
        val line = whisper
        if (line != null) {
            LatticeSignalBus.consumeWhisper()
            flashText = whisperLabel
            delay(3200)
            if (flashText == whisperLabel) flashText = null
        }
    }

    var residual by remember { mutableStateOf(manager.shouldShowResidual()) }
    LaunchedEffect(residual) {
        if (residual) {
            flashText = residualLabel
            delay(5500)
            manager.markResidualShown()
            residual = false
            if (flashText == residualLabel) flashText = null
        }
    }

    AnimatedVisibility(
        visible = flashText != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        flashText?.let { LatticeTerminalFlash(it) }
    }
    if (showGrid) {
        LatticeGridScreen(
            onClose = {
                showGrid = false
                // Re-arm residual flash after a successful unlock cinematic.
                residual = manager.shouldShowResidual()
            },
        )
    }
}

@Composable
private fun LatticeTerminalFlash(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = text,
            color = LatticeColors.Signal,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            modifier = Modifier
                .background(LatticeColors.Void.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                .border(1.dp, LatticeColors.SignalDim, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
