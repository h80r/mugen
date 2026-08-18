# Proposal: Migrate Stats Data and Cosmetic Selectors Out of Achievements/Treasury

## Intent
The app's gamification layer (easter eggs, Achievements, Treasury) is being removed in four follow-up changes: `unlock-easter-egg-cosmetics-by-default`, `remove-easter-eggs`, `remove-achievements-system`, `remove-treasury-screen`. Two pieces of value currently live *inside* the systems being deleted and must be relocated first, so nothing breaks mid-sequence:

1. **Stats content.** The Achievements screen shows a reading/watching streak, a month-over-month activity comparison, and a yearly activity calendar — all backed by the `activity_log` table. These are genuinely useful, media-agnostic stats that belong on the Stats screen, not the Achievements screen. `activity_log` currently lives inside the `AchievementsDatabase` sqldelight sourceSet, which `remove-achievements-system` deletes wholesale — so the table's schema and data must move to their own home first.
2. **Cosmetic selection.** Treasury (`SettingsTreasuryScreen.kt`) is the only place today where a user picks which theme, aura, special background, tab customization, profile title, profile effect, avatar frame, or home badge is actually *applied*. `remove-treasury-screen` deletes this screen entirely, so equivalent selector UI must exist in Settings > Appearance before that happens.

This change adds both, changes nothing about what's currently unlocked or displayed, and is purely additive — Achievements and Treasury keep working exactly as before until the later changes remove them.

## Scope

### 1. New `ActivityDatabase` sqldelight sourceSet
- New minimal sqldelight sourceSet (sibling to `AchievementsDatabase`, declared in `data/build.gradle.kts`) containing only the `activity_log` table and its queries — a direct copy of the schema currently in `data/src/main/sqldelightachievements/tachiyomi/data/achievement/activity_log.sq`
- A one-time migration copies existing rows from the old `AchievementsDatabase.activity_log` table into the new database on first run after upgrade
- New repository (mirroring the existing `AchievementHandler`/`ActivityLog`-adjacent access pattern) exposed via DI for the Stats layer to consume
- The **old** `activity_log` table inside `AchievementsDatabase` is left untouched by this change — it still gets written to by existing achievement-tracking code until `remove-achievements-system` removes that code and the whole database

### 2. Stats screen additions
- Streak card: port `calculateCurrentStreak` (`AchievementScreenModel.kt:209-224`) to a Stats-layer screen model, backed by the new `ActivityDatabase` repository
- Month-over-month comparison: chapters read, episodes watched, time in app (the achievements-unlocked comparison metric is dropped, not ported)
- Yearly activity calendar/heatmap, reusing existing calendar-rendering approach from the achievement screen where applicable
- Rendered in a single new **"Geral"** tab on the Stats screen (first tab, before Anime/Manga/Novel) rather than duplicated into each media-specific tab — this content is media-agnostic and showing it three times added no value

### 3. Cosmetic selectors in Settings > Appearance
- New "Cosméticos" sub-screen/tab reachable from `SettingsAppearanceScreen.kt`, containing 7 of the 8 selector groups Treasury exposes today: Aura, Special Background, Tab Customization, Profile Titles, Profile Effects, Avatar Frames, Home Hub Rewards (`SettingsTreasuryScreen.kt:925-1035`)
- **Theme is intentionally excluded**: Settings > Appearance already has its own theme selector (`getThemeGroup` in `SettingsAppearanceScreen.kt`, bound to the same `UiPreferences.appTheme()`), so a second one in Cosméticos would be redundant
- Each selector binds to the same preferences Treasury already writes (`UiPreferences.enabledAuras()`, `specialBackgroundStyle()`, `showTabGlow()`/`showCelestialNavbar()`/`showCircuitNavbar()`; `UserProfilePreferences.nicknameEffect()`, `avatarFrameStyle()`, `homeBadgeStyle()`, `profileTitle()`) — no new preference keys, no new persistence
- Built by reusing Treasury's actual visual components (`TreasuryToggleSelector`/`TreasuryArtifactShard` for the preset-based groups, `TreasuryAuraSelector`/`TreasuryAuraChannel` for auras, `TreasurySectionStage` as the shared section wrapper) with their lock/unlock inputs neutralized — card grids with icons, previews, and descriptions, not a plain Settings list. Treasury's rendering code is untouched; only its call site gets a synthetic "everything unlocked" input.
- Grouped as its own sub-screen (not flattened into the main Appearance list) to avoid overloading that screen, consistent with `reorganize-more-section-ia`'s stated goal of using sub-screens/tabs to prevent information overload
- Lock/unlock state is intentionally not surfaced here — `unlock-easter-egg-cosmetics-by-default` (next change) makes every cosmetic unconditionally available, so this screen shows all options as selectable without gating logic

## Out of scope
- Removing anything from Achievements or Treasury — that happens in later changes
- Changing which cosmetics exist or their unlock state — that's `unlock-easter-egg-cosmetics-by-default`
- The Treasury-exclusive vault hero/orb/constellation backdrop chrome that surrounds the selector cards — not ported; only the per-option card components are reused
- A second theme selector in Cosméticos — Appearance's existing one is sufficient
- Deleting `AchievementsDatabase`'s `activity_log` table or the achievement-side writes to it — that happens in `remove-achievements-system`, after this change's copy migration has run

## Approach
Two independent workstreams that can proceed in parallel:
1. **Data workstream**: new sqldelight sourceSet + Gradle wiring + copy migration + repository + Stats screen model/UI wiring (see `design.md`)
2. **UI workstream**: new Appearance sub-screen wired to existing preferences, following existing `SearchableSettings`/`PreferenceItem` conventions already used throughout `SettingsAppearanceScreen.kt` and its siblings

Finish with manual verification: confirm Stats shows correct streak/comparison/calendar data matching what Achievements currently shows for the same account, and confirm every cosmetic selectable in the new Appearance sub-screen actually applies (visually verified) and matches Treasury's current selection state.
