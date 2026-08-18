package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupDayActivity
import eu.kanade.tachiyomi.data.backup.models.BackupStats
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ActivityStatsRestorer(
    private val activityDataRepository: ActivityDataRepository = Injekt.get(),
) {

    /**
     * Restore activity and statistics data from backup
     * Note: Stats are recalculated automatically from restored history/chapters
     */
    suspend fun restoreActivityAndStats(
        backupActivityLog: List<BackupDayActivity>,
        backupStats: BackupStats? = null,
    ) {
        if (backupActivityLog.isEmpty() && backupStats == null) {
            return
        }

        try {
            restoreActivityLog(backupActivityLog)
            restoreStats(backupStats)
            logcat { "[BACKUP] Activity and stats data restored successfully" }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring activity and stats data" }
        }
    }

    /**
     * Restore activity log
     * Strategy: Merge - accumulate values for each day
     * Supports both legacy format (date/level/type only) and new format (detailed metrics)
     */
    private suspend fun restoreActivityLog(backupActivityLog: List<BackupDayActivity>) {
        if (backupActivityLog.isEmpty()) return

        try {
            var recordsRestored = 0
            var recordsFailed = 0

            backupActivityLog.forEach { backupActivity ->
                try {
                    val params = backupActivity.toDatabaseParams()

                    // Use upsertActivityData which handles merging/accumulation
                    activityDataRepository.upsertActivityData(
                        date = params.date,
                        chaptersRead = params.chaptersRead,
                        episodesWatched = params.episodesWatched,
                        appOpens = params.appOpens,
                        achievementsUnlocked = params.achievementsUnlocked,
                        durationMs = params.durationMs,
                    )

                    recordsRestored++
                } catch (e: Exception) {
                    logcat(throwable = e) { "[BACKUP] Error restoring activity for date ${backupActivity.date}" }
                    recordsFailed++
                }
            }

            logcat {
                "[BACKUP] Activity log restored: $recordsRestored records restored, $recordsFailed failed"
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error restoring activity log" }
        }
    }

    /**
     * Restore stats
     * Note: Stats are recalculated automatically when history and chapters are restored.
     * This method logs the backed-up stats for reference.
     */
    private suspend fun restoreStats(backupStats: BackupStats?) {
        if (backupStats == null) return

        try {
            // Stats are aggregate data calculated from:
            // - Library manga/anime counts
            // - Chapter/Episode read counts
            // - History read duration
            // - Download counts
            // - Tracker scores
            //
            // These are automatically recalculated when:
            // 1. Manga/Anime are restored (library counts)
            // 2. Chapters/Episodes are restored (total counts)
            // 3. History is restored (read counts, duration)
            // 4. Downloads are restored (download counts)
            // 5. Tracks are restored (scores)

            logcat {
                "[BACKUP] Stats backed up: " +
                    "${backupStats.mangaLibraryCount} manga, " +
                    "${backupStats.animeLibraryCount} anime, " +
                    "${backupStats.chaptersReadCount} chapters read, " +
                    "${backupStats.episodesWatchedCount} episodes watched"
            }
        } catch (e: Exception) {
            logcat(throwable = e) { "[BACKUP] Error logging stats" }
        }
    }
}
