package eu.kanade.presentation.easteregg.lattice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.easteregg.lattice.DIRS
import eu.kanade.domain.easteregg.lattice.LatticeBoard
import eu.kanade.domain.easteregg.lattice.LatticeCircuit
import eu.kanade.domain.easteregg.lattice.LatticeProtocolManager
import eu.kanade.domain.easteregg.lattice.LatticeSignalBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val SQRT3 = 1.7320508f

private enum class LatticePhase { LOADING, INTRO, ROUTING, SYNTHESIS, REWARDS }

/** What occupies the stage slot between header and footer. Crossfaded as one unit. */
private enum class LatticeStage { LOADING, BOARD, CORE }

/** Single source of truth for the terminal header. One beat replaces another, never overlaps. */
private enum class LatticeHeader { BREACH, ROUTE, LOCK, CORE }

/** Mechanical snap: slight overshoot past 60 degrees that settles back — a physical click. */
private val TileSpinEasing = CubicBezierEasing(0.34f, 1.30f, 0.64f, 1f)

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Axial hex distance between two cells. */
private fun hexDist(aq: Int, ar: Int, bq: Int, br: Int): Int {
    val dq = aq - bq
    val dr = ar - br
    return (abs(dq) + abs(dr) + abs(dq + dr)) / 2
}

/** Full-screen Grid: route the signal from the amber port to the core. */
@Composable
fun LatticeGridScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { LatticeProtocolManager.get(context) }
    val scope = rememberCoroutineScope()
    val reduced = rememberLatticeReducedMotion()
    val haptics = LocalHapticFeedback.current

    var board by remember { mutableStateOf<LatticeBoard?>(null) }
    var boardVersion by remember { mutableIntStateOf(0) }
    var phase by remember {
        mutableStateOf(
            if (manager.isSynthesisDone()) LatticePhase.SYNTHESIS else LatticePhase.LOADING,
        )
    }
    var synthesizing by remember { mutableStateOf(false) }
    var spinCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val spinProgress = remember { Animatable(0f) }
    var derezProgress by remember { mutableFloatStateOf(1f) }
    var synthBeat by remember { mutableIntStateOf(0) }
    var synthFailed by remember { mutableStateOf(false) }
    var ignitionProgress by remember { mutableFloatStateOf(0f) }
    // Triggers the cinematic LaunchedEffect without being cancelled by phase key changes.
    var payoffToken by remember { mutableIntStateOf(0) }
    // Crossfade from ignition copy into the dedicated rewards showcase.
    val rewardsReveal = remember { Animatable(0f) }
    // Fusion timeline: seams dissolve and the board fuses into one plate (0 = idle).
    val fuseProgress = remember { Animatable(0f) }
    // Signal re-propagation wave after each topology change (port -> outward).
    val powerReveal = remember { Animatable(0f) }

    // Initial open / intro only — never depends on phase (avoid cancelling cinematics).
    LaunchedEffect(Unit) {
        if (manager.isSynthesisDone() && phase == LatticePhase.SYNTHESIS) {
            board = manager.openBoard()
            derezProgress = 0f
            payoffToken++
            return@LaunchedEffect
        }
        val opened = manager.openBoard()
        if (opened == null) {
            onClose()
            return@LaunchedEffect
        }
        board = opened
        phase = LatticePhase.INTRO
        if (reduced) {
            derezProgress = 0f
            delay(350)
        } else {
            val steps = 28
            for (i in 0..steps) {
                derezProgress = 1f - i / steps.toFloat()
                delay(50)
            }
            derezProgress = 0f
        }
        if (phase == LatticePhase.INTRO) phase = LatticePhase.ROUTING
    }

    val activeBoard = board
    val circuit = remember(boardVersion, activeBoard) {
        activeBoard?.evaluate() ?: LatticeCircuit(false, false, emptySet(), emptySet())
    }

    // Power re-propagation wave: after every topology change the signal visibly re-flows
    // outward from the port instead of the whole board recoloring in a single frame.
    LaunchedEffect(phase, boardVersion) {
        if (phase != LatticePhase.ROUTING) return@LaunchedEffect
        if (reduced) {
            powerReveal.snapTo(1f)
        } else {
            powerReveal.snapTo(0f)
            powerReveal.animateTo(1f, tween(650, easing = LinearEasing))
        }
    }

    // Auto-synthesize when circuit closes.
    // IMPORTANT: do NOT put `phase` in the key set — assigning SYNTHESIS would cancel this
    // coroutine mid-flight and leave the user stuck on a dead "ARCHITECT" screen.
    LaunchedEffect(circuit.closed, boardVersion) {
        if (phase != LatticePhase.ROUTING || activeBoard == null) return@LaunchedEffect
        if (!circuit.closed || synthesizing) return@LaunchedEffect
        if (manager.isSynthesisDone()) {
            phase = LatticePhase.SYNTHESIS
            payoffToken++
            return@LaunchedEffect
        }
        synthesizing = true
        synthFailed = false
        // Beat 1 — the lock lands: hold on the finished circuit so it feels earned.
        delay(if (reduced) 360 else 750)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Beat 2 — fusion: seams dissolve into one plate while the forge runs in parallel.
        val fuseJob = launch {
            if (reduced) {
                fuseProgress.snapTo(1f)
            } else {
                fuseProgress.animateTo(1f, tween(2600, easing = FastOutSlowInEasing))
            }
        }
        var ok = false
        repeat(5) { attempt ->
            if (ok) return@repeat
            ok = manager.trySynthesize(activeBoard)
            if (!ok) delay(300L * (attempt + 1))
        }
        fuseJob.join()
        if (ok) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            phase = LatticePhase.SYNTHESIS
            payoffToken++ // starts cinematic in a separate effect that is not cancelled
        } else {
            // Rollback beat — the plate un-fuses so the user can adjust the topology.
            fuseProgress.animateTo(0f, tween(if (reduced) 1 else 450))
            synthesizing = false
            synthFailed = true
        }
    }

    // High-end core ignition + text beats, then hand off to rewards screen.
    // Keyed ONLY by payoffToken — never by `phase` (that cancelled the cinematic before).
    LaunchedEffect(payoffToken) {
        if (payoffToken == 0) return@LaunchedEffect
        if (phase != LatticePhase.SYNTHESIS && phase != LatticePhase.REWARDS) {
            phase = LatticePhase.SYNTHESIS
        }
        synthBeat = 0
        ignitionProgress = 0f
        rewardsReveal.snapTo(0f)
        val steps = if (reduced) 24 else 96
        for (i in 0..steps) {
            ignitionProgress = i / steps.toFloat()
            delay(if (reduced) 36 else 90)
        }
        ignitionProgress = 1f
        synthBeat = 1
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(if (reduced) 1400 else 2800)
        synthBeat = 2
        delay(if (reduced) 1200 else 2400)
        LatticeSignalBus.consumeSynthesis()
        phase = LatticePhase.REWARDS
        if (reduced) {
            rewardsReveal.snapTo(1f)
        } else {
            rewardsReveal.animateTo(
                1f,
                tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            )
        }
    }

    // Header beats replace each other — they never stack or overlap.
    val headerStage = when {
        phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS -> LatticeHeader.CORE
        phase == LatticePhase.ROUTING && synthesizing && !synthFailed -> LatticeHeader.LOCK
        phase == LatticePhase.ROUTING -> LatticeHeader.ROUTE
        else -> LatticeHeader.BREACH
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LatticeColors.Void)
                .latticeGridFloor()
                .then(
                    if (phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS) {
                        Modifier
                    } else {
                        Modifier.latticeLightCycles()
                    },
                )
                .latticeTerminalFrame(),
        ) {
            if (phase == LatticePhase.INTRO && derezProgress > 0.01f && !reduced) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .latticeDerez(progress = derezProgress),
                )
            }

            // Full-screen ignition FX under UI copy
            if (phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .latticeCoreIgnition(progress = ignitionProgress)
                        .then(
                            if (phase == LatticePhase.REWARDS) {
                                Modifier.alpha(1f - rewardsReveal.value * 0.55f)
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            // Grid / synthesis stage (fades out as rewards reveal)
            AnimatedVisibility(
                visible = phase != LatticePhase.REWARDS || rewardsReveal.value < 0.98f,
                enter = fadeIn(tween(800)),
                exit = fadeOut(tween(1400)),
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (phase == LatticePhase.REWARDS) 1f - rewardsReveal.value else 1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(36.dp))
                    Crossfade(
                        targetState = headerStage,
                        animationSpec = tween(if (reduced) 1 else 700),
                        label = "latticeHeaderLine1",
                    ) { stage ->
                        Text(
                            text = when (stage) {
                                LatticeHeader.BREACH -> stringResource(AYMR.strings.lattice_breach_line1)
                                LatticeHeader.ROUTE -> stringResource(AYMR.strings.lattice_routing_line1)
                                LatticeHeader.LOCK -> stringResource(AYMR.strings.lattice_circuit_locked)
                                LatticeHeader.CORE -> stringResource(AYMR.strings.lattice_core_online)
                            },
                            color = LatticeColors.Signal,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            letterSpacing = 4.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LatticeTitleEnergyBar(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(10.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Crossfade(
                        targetState = headerStage,
                        animationSpec = tween(if (reduced) 1 else 700),
                        label = "latticeHeaderLine2",
                    ) { stage ->
                        Text(
                            text = when (stage) {
                                LatticeHeader.BREACH -> ""
                                LatticeHeader.ROUTE -> stringResource(AYMR.strings.lattice_breach_line2)
                                LatticeHeader.LOCK -> stringResource(AYMR.strings.lattice_synthesis_line1)
                                LatticeHeader.CORE -> stringResource(AYMR.strings.lattice_synthesis_line1)
                            },
                            color = LatticeColors.TextPrimary.copy(alpha = 0.75f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.weight(1f))

                    val stage = when {
                        phase == LatticePhase.LOADING || activeBoard == null -> LatticeStage.LOADING
                        phase == LatticePhase.SYNTHESIS || phase == LatticePhase.REWARDS -> LatticeStage.CORE
                        else -> LatticeStage.BOARD
                    }
                    Crossfade(
                        targetState = stage,
                        animationSpec = tween(if (reduced) 1 else 1000),
                        label = "latticeStage",
                    ) { st ->
                        when (st) {
                            LatticeStage.LOADING -> Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = LatticeColors.Signal,
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            LatticeStage.CORE -> Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(220.dp)
                                        .latticeCoreGlow(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Pulsing core disc
                                    Box(
                                        Modifier
                                            .size((48 + ignitionProgress * 36).dp)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        LatticeColors.TextPrimary.copy(alpha = 0.95f),
                                                        LatticeColors.Signal.copy(alpha = 0.55f),
                                                        LatticeColors.Void.copy(alpha = 0f),
                                                    ),
                                                ),
                                            ),
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = stringResource(AYMR.strings.lattice_synthesis_line2),
                                    color = LatticeColors.TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 1.sp,
                                )
                                AnimatedVisibility(
                                    visible = synthBeat >= 1,
                                    enter = fadeIn(tween(900)),
                                    exit = fadeOut(tween(700)),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = stringResource(AYMR.strings.lattice_synthesis_reward),
                                            color = LatticeColors.Signal.copy(alpha = 0.95f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 1.sp,
                                        )
                                    }
                                }
                                AnimatedVisibility(
                                    visible = synthBeat >= 2,
                                    enter = fadeIn(tween(900)),
                                    exit = fadeOut(tween(700)),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(AYMR.strings.lattice_synthesis_continue),
                                            color = LatticeColors.TextPrimary.copy(alpha = 0.55f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            LatticeStage.BOARD -> Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (activeBoard != null) {
                                    LatticeBoardCanvas(
                                        board = activeBoard,
                                        circuit = circuit,
                                        enabled = phase == LatticePhase.ROUTING && !synthesizing,
                                        spinCell = spinCell,
                                        spinProgress = spinProgress.value,
                                        revealProgress = powerReveal.value,
                                        fuseProgress = fuseProgress.value,
                                        animateFlow = circuit.closed && !reduced,
                                    ) { q, r ->
                                        if (spinCell != null) return@LatticeBoardCanvas
                                        scope.launch {
                                            spinCell = q to r
                                            spinProgress.snapTo(0f)
                                            if (!reduced) {
                                                spinProgress.animateTo(
                                                    1f,
                                                    tween(420, easing = TileSpinEasing),
                                                )
                                            }
                                            activeBoard.rotate(q, r)
                                            manager.persistRotations(activeBoard)
                                            boardVersion++
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            spinCell = null
                                            spinProgress.snapTo(0f)
                                        }
                                    }
                                }
                                AnimatedVisibility(
                                    visible = synthFailed && circuit.closed,
                                    enter = fadeIn(tween(500)),
                                    exit = fadeOut(tween(400)),
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(AYMR.strings.lattice_synth_failed),
                                            color = LatticeColors.Alert.copy(alpha = 0.9f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.5.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(32.dp))
                }
            }

            // Dedicated Tron rewards showcase (visual previews only)
            if (phase == LatticePhase.REWARDS) {
                LatticeRewardsScreen(
                    onClose = onClose,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(rewardsReveal.value),
                )
            }

            if (phase != LatticePhase.REWARDS) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(AYMR.strings.lattice_overlay_close),
                        tint = LatticeColors.SignalDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun LatticeBoardCanvas(
    board: LatticeBoard,
    circuit: LatticeCircuit,
    enabled: Boolean,
    spinCell: Pair<Int, Int>?,
    spinProgress: Float,
    revealProgress: Float,
    fuseProgress: Float,
    animateFlow: Boolean,
    onTap: (Int, Int) -> Unit,
) {
    val a11y = stringResource(AYMR.strings.lattice_board_a11y)
    val flowPhase: Float = if (animateFlow) {
        val transition = rememberInfiniteTransition(label = "latticeFlow")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "flow",
        )
        p
    } else {
        0f
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .aspectRatio(1f)
            .semantics { contentDescription = a11y }
            .pointerInput(enabled, board) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val s = size.width / ((board.radius * 2 + 1) * 1.9f)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    var best: Pair<Int, Int>? = null
                    var bestDist = s * s
                    board.cells.keys.forEach { (q, r) ->
                        val px = cx + s * 1.5f * q
                        val py = cy + s * SQRT3 * (r + q / 2f)
                        val dx = offset.x - px
                        val dy = offset.y - py
                        val d = dx * dx + dy * dy
                        if (d < bestDist) {
                            bestDist = d
                            best = q to r
                        }
                    }
                    best?.let { (q, r) -> onTap(q, r) }
                }
            },
    ) {
        val s = size.width / ((board.radius * 2 + 1) * 1.9f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun center(q: Int, r: Int) = Offset(cx + s * 1.5f * q, cy + s * SQRT3 * (r + q / 2f))

        val portQ = board.port.q
        val portR = board.port.r
        val maxDist = (
            board.cells.keys.maxOfOrNull { (q, r) -> hexDist(q, r, portQ, portR) } ?: 1
            ).coerceAtLeast(1)

        // Fusion timeline (all 0 while idle): pulse -> seams dissolve -> unified plate -> contraction.
        val pulsePos = seg(fuseProgress, 0.02f, 0.40f) * (maxDist + 1.6f)
        val seamAlpha = 1f - seg(fuseProgress, 0.35f, 0.72f)
        val heat = seg(fuseProgress, 0.30f, 0.80f)
        val unified = seg(fuseProgress, 0.55f, 0.90f)
        val contraction = 1f - 0.08f * seg(fuseProgress, 0.68f, 1f)

        scale(contraction, contraction, Offset(cx, cy)) {
            drawCircle(
                LatticeColors.Signal.copy(alpha = 0.12f + 0.08f * flowPhase),
                radius = s * 0.85f,
                center = Offset(cx, cy),
            )
            drawCircle(LatticeColors.TextPrimary, radius = s * 0.16f, center = Offset(cx, cy))
            if (circuit.coreReached) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(LatticeColors.Signal.copy(alpha = 0.55f), LatticeColors.Signal.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = s * 1.1f,
                    ),
                    radius = s * 1.1f,
                    center = Offset(cx, cy),
                )
            }

            fun drawCell(key: Pair<Int, Int>, spinning: Boolean) {
                val cell = board.cells.getValue(key)
                val (q, r) = key
                val c = center(q, r)
                val dist = hexDist(q, r, portQ, portR)
                // Re-propagation: cells light up in port -> outward order, not all at once.
                val lit = (revealProgress * (maxDist + 1.5f) - dist).coerceIn(0f, 1f)
                val powered = key in circuit.reached
                val isStub = key in circuit.stubs
                val baseColor = when {
                    isStub -> lerp(LatticeColors.SignalDim, LatticeColors.Alert, lit)
                    powered -> lerp(LatticeColors.SignalDim, LatticeColors.Signal, lit)
                    else -> LatticeColors.SignalDim
                }
                // During fusion the powered traces heat up toward white.
                val color = if (heat > 0f && powered) {
                    lerp(baseColor, LatticeColors.TextPrimary, heat * 0.85f)
                } else {
                    baseColor
                }
                val p = if (spinning) spinProgress else 0f
                val liftT = sin(Math.PI.toFloat() * p.coerceIn(0f, 1f))
                val flare = if (fuseProgress > 0.01f) {
                    (1f - abs(dist - pulsePos) / 1.4f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                // The whole tile (hex shell, studs, traces, socket) turns as ONE rigid piece.
                rotate(degrees = 60f * p, pivot = c) {
                    scale(1f + 0.07f * liftT, 1f + 0.07f * liftT, c) {
                        if (spinning && liftT > 0.01f) {
                            // Lift glow: the tile visually detaches from the plate while turning.
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        LatticeColors.Signal.copy(alpha = 0.28f * liftT),
                                        LatticeColors.Void.copy(alpha = 0f),
                                    ),
                                    center = c,
                                    radius = s * 1.25f,
                                ),
                                radius = s * 1.25f,
                                center = c,
                            )
                        }
                        val hex = Path()
                        for (i in 0..5) {
                            val a = (Math.PI / 3 * i).toFloat()
                            val pnt = Offset(c.x + s * 0.92f * cos(a), c.y + s * 0.92f * sin(a))
                            if (i == 0) hex.moveTo(pnt.x, pnt.y) else hex.lineTo(pnt.x, pnt.y)
                        }
                        hex.close()
                        drawPath(hex, LatticeColors.Panel.copy(alpha = 0.92f))
                        drawPath(
                            hex,
                            brush = Brush.radialGradient(
                                listOf(
                                    color.copy(alpha = 0.18f + 0.25f * flare),
                                    LatticeColors.Void.copy(alpha = 0.5f * seamAlpha),
                                ),
                                center = c,
                                radius = s * 0.9f,
                            ),
                        )
                        if (flare > 0.01f) {
                            drawPath(hex, LatticeColors.TextPrimary.copy(alpha = 0.28f * flare))
                        }
                        // Seams fade out while the board fuses into one solid plate.
                        drawPath(
                            hex,
                            color.copy(alpha = (0.75f + 0.25f * liftT) * seamAlpha),
                            style = Stroke(width = 2.4f + 1.6f * liftT),
                        )
                        drawPath(hex, color.copy(alpha = 0.2f * seamAlpha), style = Stroke(width = 5.5f))
                        // Vertex studs: orientation cue so the hexagon itself visibly turns.
                        for (i in 0..5) {
                            val a = (Math.PI / 3 * i).toFloat()
                            drawCircle(
                                color.copy(alpha = (0.5f + 0.3f * liftT) * seamAlpha),
                                radius = s * 0.045f,
                                center = Offset(c.x + s * 0.78f * cos(a), c.y + s * 0.78f * sin(a)),
                            )
                        }
                        cell.connectorDirs().forEach { d ->
                            val delta = center(q + DIRS[d][0], r + DIRS[d][1]) - c
                            val end = c + delta * 0.52f
                            drawLine(color.copy(alpha = 0.25f), c, end, strokeWidth = s * 0.22f, cap = StrokeCap.Round)
                            drawLine(color, c, end, strokeWidth = s * 0.11f, cap = StrokeCap.Round)
                            // Flow heads never render on a turning tile — it moves as one rigid piece.
                            if (powered && !spinning) {
                                val headAlpha = lit * (1f - unified)
                                if (headAlpha > 0.01f) {
                                    val head = c + delta * (0.12f + 0.4f * if (animateFlow) flowPhase else 0.5f)
                                    drawCircle(
                                        LatticeColors.TextPrimary.copy(alpha = 0.9f * headAlpha),
                                        radius = s * 0.055f,
                                        center = head,
                                    )
                                    drawCircle(color.copy(alpha = 0.4f * headAlpha), radius = s * 0.1f, center = head)
                                }
                            }
                        }
                        drawCircle(LatticeColors.Void, radius = s * 0.14f, center = c)
                        drawCircle(color, radius = s * 0.09f, center = c)
                    }
                }
            }

            // Resting tiles first; the turning tile renders above its neighbours.
            board.cells.keys.forEach { key -> if (key != spinCell) drawCell(key, spinning = false) }
            spinCell?.let { key -> if (key in board.cells) drawCell(key, spinning = true) }

            // Unified plate: one silhouette replaces the seam grid as fusion completes.
            if (unified > 0.01f) {
                val plate = Path()
                val pr = s * (board.radius * 1.5f + 1.05f)
                for (i in 0..5) {
                    val a = (Math.PI / 3 * i).toFloat()
                    val pnt = Offset(cx + pr * cos(a), cy + pr * sin(a))
                    if (i == 0) plate.moveTo(pnt.x, pnt.y) else plate.lineTo(pnt.x, pnt.y)
                }
                plate.close()
                drawPath(
                    plate,
                    brush = Brush.radialGradient(
                        listOf(LatticeColors.Signal.copy(alpha = 0.10f * unified), LatticeColors.Signal.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = pr,
                    ),
                )
                drawPath(plate, LatticeColors.Signal.copy(alpha = 0.6f * unified), style = Stroke(width = 3f))
            }

            // Core bloom rises at the very end of fusion and hands off to the ignition stage.
            val bloom = seg(fuseProgress, 0.62f, 1f)
            if (bloom > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            LatticeColors.TextPrimary.copy(alpha = 0.85f * bloom),
                            LatticeColors.Signal.copy(alpha = 0.45f * bloom),
                            LatticeColors.Void.copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = s * (0.5f + 2.4f * bloom),
                    ),
                    radius = s * (0.5f + 2.4f * bloom),
                    center = Offset(cx, cy),
                )
            }

            // Amber service port (fades as the plate unifies — the signal now lives inside)
            val pc = center(board.port.q, board.port.r)
            val pd = center(board.port.q + DIRS[board.port.dir][0], board.port.r + DIRS[board.port.dir][1]) - pc
            val portAlpha = 1f - 0.7f * unified
            drawCircle(LatticeColors.Service.copy(alpha = portAlpha), radius = s * 0.16f, center = pc + pd * 0.5f)
            drawCircle(LatticeColors.Service.copy(alpha = 0.35f * portAlpha), radius = s * 0.28f, center = pc + pd * 0.5f)
        }
    }
}
