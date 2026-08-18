# Stats Specification

## Requirements

### Requirement: Activity data isolation
The system SHALL persist activity-log data (daily reading/watching/app-open counts) in a dedicated `ActivityDatabase` sqldelight sourceSet, independent of any achievement-related schema.
Source: `data/src/main/sqldelightactivity/tachiyomi/data/activity/activity_log.sq`.

#### Scenario: Activity data survives achievement system removal
- GIVEN the achievement system and its database are removed from the app
- WHEN the Stats screen queries streak, monthly comparison, or yearly activity data
- THEN the query succeeds against `ActivityDatabase`, unaffected by the achievement system's removal

### Requirement: Reading/watching streak on the Stats screen's Geral tab
The system SHALL display the user's current consecutive-day reading/watching streak on the Stats screen's Geral tab, computed by walking backward from today and stopping at the first inactive day (an inactive "today" yields a streak of 0), capped at 365 days.
Source: ported from `AchievementScreenModel.calculateCurrentStreak` (`AchievementScreenModel.kt:209-224`), `SharedActivityStatsScreenModel.currentStreakFlow`.

#### Scenario: Streak displays without requiring achievements
- GIVEN a user has read chapters on 5 consecutive days including today
- WHEN they open the Stats screen's Geral tab
- THEN a streak of 5 is shown, with no dependency on any achievement being unlocked or the achievement system being present

### Requirement: Month-over-month activity comparison on the Stats screen's Geral tab
The system SHALL display a comparison between the current month's and previous month's activity: chapters read, episodes watched, and time spent in the app.
Source: relocated from the Achievements screen's stats comparison section.

#### Scenario: Comparison omits achievement-specific metrics
- GIVEN the Achievements-screen version of this comparison also showed an "achievements unlocked" delta
- WHEN the comparison is rebuilt on the Stats screen
- THEN only chapters read, episodes watched, and time in app are shown — the achievements-unlocked metric is dropped, not ported

### Requirement: Yearly activity bar chart on the Stats screen's Geral tab
The system SHALL display a 12-month bar chart of activity across the current year (paged in two 6-month halves), sourced from `ActivityDatabase`, reusing the achievement screen's existing bar-chart rendering approach.
Source: relocated from the Achievements screen's yearly activity view (`AchievementActivityGraph.kt`), `SharedYearlyActivityGraph`.

#### Scenario: Each bar reflects that month's total activity
- GIVEN a user was active across several months of the year
- WHEN they view the yearly activity chart on the Stats screen
- THEN each month's bar height reflects that month's summed chapters-read and episodes-watched, with a tooltip showing the full breakdown on long-press

### Requirement: Media-agnostic Stats content lives in a single Geral tab
The system SHALL render the streak, month-over-month comparison, and yearly activity chart exactly once, in a dedicated "Geral" tab that is first in the Stats screen's tab order — not duplicated into the Anime, Manga, or Novel tabs, since the content is media-agnostic.
Source: `StatsTab.kt` (`StatsContentTab.GENERAL`), `generalStatsTab()`, `GeneralStatsContent.kt`.

#### Scenario: Media tabs show only their own content
- GIVEN a user opens the Anime, Manga, or Novel tab on the Stats screen
- WHEN the tab renders
- THEN it shows only that medium's own stats (library counts, episode/chapter progress, trackers) — the streak/comparison/yearly-activity block does not appear there
