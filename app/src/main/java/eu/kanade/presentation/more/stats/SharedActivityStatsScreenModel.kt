package eu.kanade.presentation.more.stats

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.data.activity.ActivityLogRepository
import tachiyomi.domain.activity.model.MonthActivityStats
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class SharedActivityStatsScreenModel(
    private val activityLogRepository: ActivityLogRepository = Injekt.get(),
) : ScreenModel {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    val state: StateFlow<SharedActivityStatsState> = combine(
        currentStreakFlow(),
        monthStatsFlow(YearMonth.now()),
        monthStatsFlow(YearMonth.now().minusMonths(1)),
        yearlyStatsFlow(),
    ) { streak, currentMonth, previousMonth, yearlyStats ->
        SharedActivityStatsState(
            currentStreak = streak,
            currentMonthStats = currentMonth,
            previousMonthStats = previousMonth,
            yearlyStats = yearlyStats,
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), SharedActivityStatsState())

    private fun currentStreakFlow() = run {
        val today = LocalDate.now()
        val startDate = today.minusDays(364)
        activityLogRepository
            .getActivityForDateRange(startDate.format(dateFormatter), today.format(dateFormatter))
            .map { rows ->
                val activeDates = rows.filter { it.level > 0 }.map { it.date }.toSet()
                var streak = 0
                var cursor = today
                while (activeDates.contains(cursor.format(dateFormatter))) {
                    streak++
                    cursor = cursor.minusDays(1)
                }
                streak
            }
    }

    private fun monthStatsFlow(month: YearMonth) = run {
        val startDate = month.atDay(1).format(dateFormatter)
        val endDate = month.atEndOfMonth().format(dateFormatter)
        activityLogRepository.getActivityStats(startDate, endDate).map { stats ->
            MonthActivityStats(
                chaptersRead = stats?.total_chapters?.toInt() ?: 0,
                episodesWatched = stats?.total_episodes?.toInt() ?: 0,
                timeInAppMinutes = ((stats?.total_duration ?: 0L) / 60000).toInt(),
            )
        }
    }

    private fun yearlyStatsFlow() = run {
        val currentMonth = YearMonth.now()
        val months = (11 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        val monthFlows = months.map { month ->
            activityLogRepository.getMonthActivity(month.format(yearMonthFormatter)).map { rows ->
                month to MonthActivityStats(
                    chaptersRead = rows.sumOf { it.chapters_read }.toInt(),
                    episodesWatched = rows.sumOf { it.episodes_watched }.toInt(),
                    timeInAppMinutes = (rows.sumOf { it.duration_ms } / 60000).toInt(),
                )
            }
        }
        combine(monthFlows) { it.toList() }
    }
}

data class SharedActivityStatsState(
    val currentStreak: Int = 0,
    val currentMonthStats: MonthActivityStats = MonthActivityStats(0, 0, 0),
    val previousMonthStats: MonthActivityStats = MonthActivityStats(0, 0, 0),
    val yearlyStats: List<Pair<YearMonth, MonthActivityStats>> = emptyList(),
)
