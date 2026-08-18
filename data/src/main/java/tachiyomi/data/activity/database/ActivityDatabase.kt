package tachiyomi.data.activity.database

import app.cash.sqldelight.db.SqlDriver
import tachiyomi.db.activity.ActivityDatabase as SqlDelightActivityDatabase

class ActivityDatabase(
    private val driver: SqlDriver,
) {

    val activityLogQueries
        get() = database.activity_logQueries

    companion object {
        const val NAME = "activity.db"
        const val VERSION = 1L
    }

    private val database = SqlDelightActivityDatabase(driver)
}
