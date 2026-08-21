# Tasks

## 1. Remove orphaned achievement-notifications preference
- [ ] 1.1 Remove the `SwitchPreference` block for `showAchievementNotifications` from `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt:262-266`
- [ ] 1.2 Remove `UiPreferences.showAchievementNotifications()` from `app/src/main/java/eu/kanade/domain/ui/UiPreferences.kt:129`
- [ ] 1.3 Remove `pref_show_achievement_notifications`/`pref_show_achievement_notifications_summary` string keys from base and every locale's strings.xml (confirmed present in base, pt-rBR, ar, ru at minimum — grep for the key across all locale files)
- [ ] 1.4 Confirm no leftover preference-store key orphaned in a migration file; if `PreferenceStore.getBoolean("show_achievement_notifications", ...)` needs an explicit removal/cleanup in a migration, add it — otherwise the stored value simply becomes unread, which is acceptable

## 2. Delete orphaned Achievement domain model
- [ ] 2.1 Grep-confirm zero references to `Achievement`, `AchievementCondition`, `AchievementReward` from `core/common/src/main/java/tachiyomi/core/achievement/Achievement.kt` anywhere outside the file itself
- [ ] 2.2 Delete `core/common/src/main/java/tachiyomi/core/achievement/Achievement.kt` (and the now-empty `achievement` package directory if nothing else lives there)

## 3. Delete dead ActivityDataRepository method
- [ ] 3.1 Grep-confirm zero call sites for `recordAchievementUnlock()` anywhere outside its own interface/impl declaration
- [ ] 3.2 Remove `recordAchievementUnlock()` from the `ActivityDataRepository` interface and its implementation (impl around line 156)
- [ ] 3.3 Leave the `achievementsUnlocked`/`achievements_unlocked` field/column in place (schema migration for a single always-zero column is out of scope — noted as intentionally deferred)

## 4. Prune orphaned achievement locale strings
- [ ] 4.1 Build the list of live `achievement_*` keys still referenced in Kotlin (`achievement_stat_*`, `achievement_comparison_title`, `achievement_days_unit`, `achievement_hours*`/`achievement_minutes*`, `achievement_period_*`, `achievement_year_activity_title`, `achievement_no_activity`, `achievement_activity_bar_a11y`, and any others found by a fresh grep for `AYMR.strings.achievement_` / `MR.strings.achievement_`)
- [ ] 4.2 Remove every `achievement_*` key NOT in that live list from base + all locale strings.xml files (expect roughly the per-achievement title/description/flavor-text keys around `i18n/.../pt-rBR/strings.xml:852-996,1612,1619-1632` and their base/other-locale counterparts)
- [ ] 4.3 Build the app (or run the moko-resources string-check task if one exists) to confirm no live code references a key that was just removed

## 5. Update backup-restore spec
- [ ] 5.1 Spec delta already written (`specs/backup-restore/spec.md`) removing the stale "Manual backup triggers achievement tracking" requirement — no code change needed here since `AchievementEvent` doesn't exist; this task is just confirming the delta is accurate before archive merges it
