package tachiyomi.data.activity

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.activity.database.ActivityDatabase

class ActivityLogRepository(
    private val database: ActivityDatabase,
) {

    fun getActivityForDateRange(startDate: String, endDate: String): Flow<List<Activity_log>> {
        return database.activityLogQueries
            .getActivityForDateRange(startDate, endDate)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getMonthActivity(yearMonth: String): Flow<List<Activity_log>> {
        return database.activityLogQueries
            .getMonthActivity(yearMonth)
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun getActivityStats(startDate: String, endDate: String): Flow<GetActivityStats?> {
        return database.activityLogQueries
            .getActivityStats(startDate, endDate)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
    }

    fun getActivityForDate(date: String): Flow<Activity_log?> {
        return database.activityLogQueries
            .getActivityForDate(date)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
    }
}
