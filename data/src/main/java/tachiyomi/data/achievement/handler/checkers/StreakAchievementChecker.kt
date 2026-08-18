package tachiyomi.data.achievement.handler.checkers

import tachiyomi.data.activity.Activity_log
import tachiyomi.data.activity.database.ActivityDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Calculates the current consecutive-day reading/watching streak from
 * [ActivityDatabase]'s activity log.
 */

class StreakAchievementChecker(
    private val database: ActivityDatabase,
) {

    companion object {
        /** Maximum lookback window when computing the streak (prevents unbounded scans). */
        private const val MAX_STREAK_DAYS = 365

        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }

    /**
     * Calculate the current streak of consecutive days with activity.
     * Does not break streak if there's no activity yet today.
     */
    suspend fun getCurrentStreak(): Int {
        var streak = 0
        var checkDate = LocalDate.now()
        var checkedToday = false

        // Check up to MAX_STREAK_DAYS back
        repeat(MAX_STREAK_DAYS) {
            val activity = getActivityForDate(checkDate)

            when {
                // First iteration (today): no activity yet is OK, check yesterday
                !checkedToday && activity == null -> {
                    checkedToday = true
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // First iteration (today): has activity, count it and continue
                !checkedToday && hasActivity(activity) -> {
                    checkedToday = true
                    streak++
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // First iteration (today): no activity log at all, check yesterday
                !checkedToday -> {
                    checkedToday = true
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // Subsequent iterations: need activity to continue streak
                hasActivity(activity) -> {
                    streak++
                    checkDate = checkDate.minusDays(1)
                    return@repeat
                }
                // No activity on this day, streak broken
                else -> return streak
            }
        }

        return streak
    }

    /**
     * Get the activity record for a specific date.
     */
    private suspend fun getActivityForDate(date: LocalDate): ActivityLog? {
        val dateStr = date.format(DATE_FORMATTER)
        val record: Activity_log? = database.activityLogQueries
            .getActivityForDate(dateStr)
            .executeAsOneOrNull()

        return if (record != null) {
            ActivityLog(
                date = dateStr,
                chapterCount = record.chapters_read,
                episodeCount = record.episodes_watched,
                lastUpdated = record.last_updated,
            )
        } else {
            null
        }
    }

    /**
     * Check if an activity log contains any activity.
     */
    private fun hasActivity(activity: ActivityLog?): Boolean {
        return activity != null && (activity.chapterCount > 0 || activity.episodeCount > 0)
    }

    /**
     * Data class representing an activity log entry.
     */
    private data class ActivityLog(
        val date: String,
        val chapterCount: Long,
        val episodeCount: Long,
        val lastUpdated: Long,
    )
}
