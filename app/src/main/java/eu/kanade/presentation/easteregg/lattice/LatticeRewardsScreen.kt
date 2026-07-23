package eu.kanade.presentation.easteregg.lattice

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tadami.aurora.R
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private const val REVEAL_MS = 4200

private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

private fun easeOut(x: Float): Float {
    val i = 1f - x
    return 1f - i * i * i
}

/**
 * Post-synthesis Tron showcase: visual-only reward previews.
 * No equip/toggle controls — treasures stay in the Treasury.
 */
@Composable
fun LatticeRewardsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLatticeReducedMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) {
            anim.snapTo(1f)
        } else {
            anim.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
        }
    }
    val t = anim.value

    val titleIn = easeOut(seg(t, 0.05f, 0.28f))
    val subtitleIn = easeOut(seg(t, 0.18f, 0.40f))
    val card0In = easeOut(seg(t, 0.30f, 0.55f))
    val card1In = easeOut(seg(t, 0.45f, 0.70f))
    val noteIn = easeOut(seg(t, 0.62f, 0.82f))
    val buttonIn = easeOut(seg(t, 0.78f, 0.96f))

    val pulse = if (reduced) {
        1f
    } else {
        val infinite = rememberInfiniteTransition(label = "latticeRewardsPulse")
        val p by infinite.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        p
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LatticeColors.Void)
            .latticeGridFloor()
            .latticeTerminalFrame(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(AYMR.strings.lattice_rewards_kicker),
                color = LatticeColors.Service.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = (8f - 4f * titleIn).sp,
                modifier = Modifier.alpha(titleIn),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(AYMR.strings.lattice_rewards_title),
                color = LatticeColors.Signal,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (6f - 4f * titleIn).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn
                    scaleX = 0.94f + 0.06f * titleIn
                    scaleY = 0.94f + 0.06f * titleIn
                },
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(8.dp)
                    .alpha(titleIn)
                    .graphicsLayer { scaleX = pulse },
            ) {
                LatticeTitleEnergyBar(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(AYMR.strings.lattice_rewards_subtitle),
                color = LatticeColors.TextPrimary.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .alpha(subtitleIn),
            )

            Spacer(Modifier.height(28.dp))

            LatticeRewardPreviewCard(
                imageRes = R.drawable.ic_reward_card_lattice_theme,
                title = stringResource(AYMR.strings.theme_lattice_protocol),
                description = stringResource(AYMR.strings.lattice_rewards_theme_desc),
                kindLabel = stringResource(AYMR.strings.lattice_rewards_kind_theme),
                progress = card0In,
                accent = LatticeColors.Signal,
            )
            Spacer(Modifier.height(16.dp))
            LatticeRewardPreviewCard(
                imageRes = R.drawable.ic_reward_card_lattice_circuit,
                title = stringResource(AYMR.strings.lattice_rewards_circuit_title),
                description = stringResource(AYMR.strings.lattice_rewards_circuit_desc),
                kindLabel = stringResource(AYMR.strings.lattice_rewards_kind_navbar),
                progress = card1In,
                accent = LatticeColors.Service,
            )

            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(AYMR.strings.lattice_rewards_preview_only),
                color = LatticeColors.SignalDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(noteIn),
            )

            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .alpha(buttonIn)
                    .border(1.dp, LatticeColors.Signal.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(AYMR.strings.lattice_accept),
                    color = LatticeColors.Signal,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun LatticeRewardPreviewCard(
    @DrawableRes imageRes: Int,
    title: String,
    description: String,
    kindLabel: String,
    progress: Float,
    accent: androidx.compose.ui.graphics.Color,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 28f
                scaleX = 0.96f + 0.04f * progress
                scaleY = 0.96f + 0.04f * progress
            }
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        LatticeColors.Panel.copy(alpha = 0.96f),
                        LatticeColors.Void.copy(alpha = 0.88f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .background(LatticeColors.Void),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = kindLabel,
                color = accent.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = LatticeColors.TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = LatticeColors.TextPrimary.copy(alpha = 0.68f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(AYMR.strings.lattice_rewards_preview_badge),
                color = LatticeColors.Signal.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
            )
        }
    }
}
