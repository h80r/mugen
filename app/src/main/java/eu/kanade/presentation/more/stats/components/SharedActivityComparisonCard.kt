package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.achievement.components.achievementTimeStrings
import eu.kanade.presentation.achievement.components.formatAchievementTimeMinutes
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.activity.model.MonthActivityStats
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Media-agnostic version of `AchievementStatsComparison`, dropping the
 * achievements-unlocked metric (Stats has no notion of achievements).
 */
@Composable
fun SharedActivityComparisonCard(
    currentMonth: MonthActivityStats,
    previousMonth: MonthActivityStats,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val timeStrings = achievementTimeStrings()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(MR.strings.achievement_comparison_title).uppercase(),
            color = colors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.surface.copy(alpha = 0.5f),
                                colors.surface.copy(alpha = 0.3f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(size.width / 3f, 0f),
                                end = Offset(size.width / 3f, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.06f),
                                start = Offset(size.width * 2f / 3f, 0f),
                                end = Offset(size.width * 2f / 3f, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        .padding(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatItem(
                            label = stringResource(MR.strings.achievement_stat_chapters_read),
                            currentValue = currentMonth.chaptersRead,
                            previousValue = previousMonth.chaptersRead,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            label = stringResource(MR.strings.achievement_stat_episodes_watched),
                            currentValue = currentMonth.episodesWatched,
                            previousValue = previousMonth.episodesWatched,
                            modifier = Modifier.weight(1f),
                        )
                        StatItem(
                            label = stringResource(MR.strings.achievement_stat_app_time),
                            currentValue = currentMonth.timeInAppMinutes,
                            previousValue = previousMonth.timeInAppMinutes,
                            isTimeValue = true,
                            timeStrings = timeStrings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    currentValue: Int,
    previousValue: Int,
    isTimeValue: Boolean = false,
    timeStrings: eu.kanade.presentation.achievement.components.AchievementTimeStrings? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    val percentageChange = if (previousValue > 0) {
        ((currentValue - previousValue).toFloat() / previousValue * 100).toInt()
    } else if (currentValue > 0) {
        100
    } else {
        0
    }

    val isIncrease = currentValue >= previousValue
    val changeColor = if (isIncrease) colors.success else colors.error

    val valueString = if (isTimeValue) {
        val strings = requireNotNull(timeStrings) {
            "timeStrings must be provided for time-based stats"
        }
        val hours = currentValue / 60
        val minutes = currentValue % 60
        formatAchievementTimeMinutes(
            currentValue,
            hoursMinutesText = stringResource(strings.hoursMinutes, hours, minutes),
            hoursText = stringResource(strings.hours, hours),
            minutesText = stringResource(strings.minutes, minutes),
        )
    } else {
        currentValue.toString()
    }

    Column(
        modifier = modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary.copy(alpha = 0.4f),
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = valueString,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )

            if (previousValue > 0 || currentValue > 0) {
                val prefix = if (isIncrease) "+" else "-"
                Text(
                    text = "$prefix${kotlin.math.abs(percentageChange)}%",
                    color = changeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(changeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
