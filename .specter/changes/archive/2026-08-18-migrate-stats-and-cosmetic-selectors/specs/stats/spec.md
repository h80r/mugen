# Stats Specification Delta

## ADDED Requirements

### Requirement: Activity data isolation
The system SHALL persist activity-log data (daily reading/watching/app-open counts) in a dedicated `ActivityDatabase` sqldelight sourceSet, independent of any achievement-related schema.
Source: `data/src/main/sqldelightactivity/tachiyomi/data/activity/activity_log.sq`.

#### Scenario: Activity data survives achievement system removal
- GIVEN the achievement system and its database are removed from the app
- WHEN the Stats screen queries streak, monthly comparison, or yearly activity data
- THEN the query succeeds against `ActivityDatabase`, unaffected by the achievement system's removal

### Requirement: Reading/watching streak on Stats screen
The system SHALL display the user's current consecutive-day reading/watching streak on the Stats screen, computed by walking backward from today where an inactive "today" does not break an existing streak, capped at 365 days.
Source: ported from `AchievementScreenModel.calculateCurrentStreak` (`AchievementScreenModel.kt:209-224`).

#### Scenario: Streak displays without requiring achievements
- GIVEN a user has read chapters on 5 consecutive days including today
- WHEN they open the Stats screen
- THEN a streak of 5 is shown, with no dependency on any achievement being unlocked or the achievement system being present

### Requirement: Month-over-month activity comparison on Stats screen
The system SHALL display a comparison between the current month's and previous month's activity: chapters read, episodes watched, and time spent in the app.
Source: relocated from the Achievements screen's stats comparison section.

#### Scenario: Comparison omits achievement-specific metrics
- GIVEN the Achievements-screen version of this comparison also showed an "achievements unlocked" delta
- WHEN the comparison is rebuilt on the Stats screen
- THEN only chapters read, episodes watched, and time in app are shown — the achievements-unlocked metric is dropped, not ported

### Requirement: Yearly activity calendar on Stats screen
The system SHALL display a calendar/heatmap view of activity across the current year, sourced from `ActivityDatabase`.
Source: relocated from the Achievements screen's yearly activity view.

#### Scenario: Calendar reflects activity level per day
- GIVEN a user was active on some days of the year and inactive on others
- WHEN they view the yearly activity calendar on the Stats screen
- THEN each day's cell reflects that day's recorded activity level from `activity_log`
