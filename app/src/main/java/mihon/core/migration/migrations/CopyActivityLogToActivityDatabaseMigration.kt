package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.achievement.database.AchievementsDatabase
import tachiyomi.data.activity.database.ActivityDatabase

/**
 * One-time copy of `activity_log` rows out of `AchievementsDatabase` into the new,
 * standalone `ActivityDatabase` ahead of the achievements system's removal.
 *
 * Upserts by primary key (`date`), so re-running is safe and idempotent; the
 * completion flag just avoids doing the work again on every launch.
 */
class CopyActivityLogToActivityDatabaseMigration : Migration {
    override val version = Migration.ALWAYS

    override suspend operator fun invoke(migrationContext: MigrationContext): Boolean {
        if (migrationContext.dryrun) return true
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        val preference = preferenceStore.getBoolean("activity_log_migrated_to_activity_db_v1", false)
        if (preference.get()) return true

        val achievementsDatabase = migrationContext.get<AchievementsDatabase>() ?: return false
        val activityDatabase = migrationContext.get<ActivityDatabase>() ?: return false

        val rows = achievementsDatabase.activityLogQueries.selectAllActivityLog().executeAsList()
        rows.forEach { row ->
            activityDatabase.activityLogQueries.upsertActivity(
                date = row.date,
                level = row.level,
                type = row.type,
                chapters_read = row.chapters_read,
                episodes_watched = row.episodes_watched,
                app_opens = row.app_opens,
                achievements_unlocked = row.achievements_unlocked,
                duration_ms = row.duration_ms,
                last_updated = row.last_updated,
            )
        }

        preference.set(true)
        return true
    }
}
