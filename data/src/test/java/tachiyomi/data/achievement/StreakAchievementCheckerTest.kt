package tachiyomi.data.achievement

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.activity.database.ActivityDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Execution(ExecutionMode.CONCURRENT)
class StreakAchievementCheckerTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: ActivityDatabase
    private lateinit var streakChecker: StreakAchievementChecker

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        tachiyomi.db.activity.ActivityDatabase.Schema.create(driver)
        database = ActivityDatabase(driver)
        streakChecker = StreakAchievementChecker(database)
    }

    @AfterEach
    fun teardown() {
        driver.close()
    }

    @Test
    fun `initial streak is zero`() = runTest {
        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 0
    }

    @Test
    fun `streak is one after logging activity today`() = runTest {
        database.activityLogQueries.incrementChapters(
            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1
    }

    @Test
    fun `streak counts consecutive days`() = runTest {
        val today = LocalDate.now()

        // Log activity for today and past 2 days
        repeat(3) { dayOffset ->
            val date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            database.activityLogQueries.incrementChapters(
                date = date,
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 3
    }

    @Test
    fun `streak breaks on missing day`() = runTest {
        val today = LocalDate.now()

        // Log activity for today and 2 days ago (skipping yesterday)
        database.activityLogQueries.incrementChapters(
            date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        database.activityLogQueries.incrementChapters(
            date = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1 // Only today counts
    }

    @Test
    fun `streak continues even without activity today yet`() = runTest {
        val today = LocalDate.now()

        // Log activity for yesterday and day before
        database.activityLogQueries.incrementChapters(
            date = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        database.activityLogQueries.incrementChapters(
            date = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
            level = 1,
            count = 1,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 2 // Yesterday and day before (today doesn't break streak)
    }

    @Test
    fun `mixed chapter and episode activity counts towards streak`() = runTest {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        // Log both chapter and episode activity
        database.activityLogQueries.incrementChapters(
            date = today,
            level = 1,
            count = 5,
            last_updated = System.currentTimeMillis(),
        )
        database.activityLogQueries.incrementEpisodes(
            date = today,
            level = 1,
            count = 3,
            last_updated = System.currentTimeMillis(),
        )

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 1
    }

    @Test
    fun `streak resets after gap`() = runTest {
        val today = LocalDate.now()

        // Create a 5-day streak
        repeat(5) { dayOffset ->
            database.activityLogQueries.incrementChapters(
                date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE),
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val initialStreak = streakChecker.getCurrentStreak()
        initialStreak shouldBe 5

        // Now add a gap by removing activity from yesterday
        database.activityLogQueries.deleteActivityLog(
            today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
        )

        val newStreak = streakChecker.getCurrentStreak()
        newStreak shouldBe 1 // Only today counts now (yesterday was removed, streak broken)
    }

    @Test
    fun `long streak is calculated correctly`() = runTest {
        val today = LocalDate.now()

        // Create a 30-day streak
        repeat(30) { dayOffset ->
            database.activityLogQueries.incrementChapters(
                date = today.minusDays(dayOffset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE),
                level = 1,
                count = 1,
                last_updated = System.currentTimeMillis(),
            )
        }

        val streak = streakChecker.getCurrentStreak()
        streak shouldBe 30
    }
}
