# Theming & Aurora UI Specification

## Requirements

### Requirement: Theme catalog with hidden reward-only themes
The system SHALL define an `AppTheme` catalog where each entry has `isAuroraStyle` and `isHidden` flags; hidden themes are reward-only and excluded from the normal theme picker.
Source: `app/.../domain/ui/model/AppTheme.kt`.

#### Scenario: Hidden themes do not appear in the normal picker
- GIVEN a theme like `AURORA_PRIME` or `LATTICE_PROTOCOL` has `isHidden = true`
- WHEN the user opens the standard theme picker
- THEN it is not listed, since it is only reachable via its unlock mechanism

#### Scenario: Deprecated legacy themes remain for migration only
- GIVEN legacy entries `DARK_BLUE`, `HOT_PINK`, `BLUE` have `titleRes = null`
- WHEN the theme picker renders
- THEN they are not shown as selectable options, existing only so previously-persisted preference values continue to resolve without crashing

### Requirement: Independent Aurora-vs-Classic display toggles
The system SHALL provide three independent Aurora-vs-Classic toggles — home recent-card style, home hero CTA style, and title-card CTA style — each stored separately and each defaulting to Aurora.
Source: `UserProfilePreferences.homeHubRecentCardMode()/homeHeroCtaMode()/auroraTitleHeroCtaMode()`, `SettingsAppearanceScreen.kt`.

#### Scenario: Toggles are independently configurable
- GIVEN a user sets Home recent-card style to Classic
- WHEN they check the title-card CTA style setting
- THEN it remains at its own independently-configured value (default Aurora), unaffected by the recent-card change

#### Scenario: Classic hero CTA is opaque, Aurora hero CTA is glass
- GIVEN the home hero CTA mode is set to Classic
- WHEN the hero button renders
- THEN it uses a fully opaque solid/gradient fill with no glow, versus Aurora mode's translucent glass surface with gradient glow/highlight

#### Scenario: Title-card CTA label shadow depends on mode
- GIVEN the title-card CTA mode is Aurora
- WHEN the button label renders
- THEN it gets a drop shadow (`resolveAuroraCtaLabelShadowSpec(enabled = true)`), which is absent in Classic mode

### Requirement: Media section visibility
The system SHALL let users independently hide the anime, manga, or novel sections on the Home hub, defaulting to all shown.

#### Scenario: Hiding a section removes it from Home only
- GIVEN `showNovelSection` is disabled
- WHEN the Home hub renders
- THEN the novel section is omitted, while the Library/Browse screens still allow novel access (this toggle governs Home visibility, not feature availability)

### Requirement: Home greeting engine
The system SHALL select a deterministic-but-varied greeting using a seed derived from 2-hour time buckets plus hour/day/streak/etc., avoiding immediate repetition of the last two scenarios shown.
Source: `GreetingProvider.kt`.

#### Scenario: Same time bucket yields the same greeting seed
- GIVEN the user reopens the app twice within the same 2-hour time bucket with no other state change
- WHEN the greeting is computed both times
- THEN the same deterministic seed is derived, so the greeting selection is stable rather than random each launch

#### Scenario: Milestone greetings are prioritized in a fixed order
- GIVEN a user qualifies for both a streak milestone and a library-size milestone
- WHEN a milestone-category greeting is selected
- THEN streak is checked before achievements, episodes watched, and library size, in that priority order

#### Scenario: Recent scenarios are not immediately repeated
- GIVEN the last two greeting scenarios shown were "weekend" and "time_of_day"
- WHEN a new greeting is selected
- THEN those two scenario categories are excluded from selection this time

#### Scenario: Cached greeting is discarded if the time bucket has changed
- GIVEN a cached greeting was generated for the "morning" time-of-day bucket
- WHEN the app reopens in the "evening" bucket
- THEN `getInitialGreeting()` does not show the stale cached greeting, falling back to a generic "welcome back" string until the real greeting recomputes

### Requirement: Upcoming calendar
The system SHALL provide a per-media (anime/manga/novel) upcoming-releases calendar reached from the Updates tab, grouping entries by expected next-update date.
Source: `mihon.feature.upcoming.{anime,manga,novel}`.

#### Scenario: Calendar groups entries by day
- GIVEN multiple library entries share the same expected next-update date
- WHEN the upcoming calendar renders
- THEN they are grouped under one day-header, with a day-to-count map available for a calendar-style indicator view

### Requirement: Treasury unlockables gallery
The system SHALL show a gallery of unlocked cosmetic rewards (themes, auras, presets, nicknames, avatar frames, home badges, special backgrounds), visible from Settings only if the build is DEBUG or the user has unlocked at least one reward.
Source: `SettingsTreasuryScreen.kt`, `shouldShowTreasury`, `UnlockableManager`.

#### Scenario: Treasury entry hidden until first unlock
- GIVEN a release-build user has never unlocked any reward
- WHEN they view Settings
- THEN the Treasury entry is not shown

#### Scenario: Debug builds always show Treasury
- GIVEN the app is a DEBUG build
- WHEN Settings is viewed, regardless of unlock state
- THEN the Treasury entry is shown

#### Scenario: Debug preview bypasses locks without granting rewards
- GIVEN `debugBypassTreasuryLocks` is enabled in a DEBUG build
- WHEN the Treasury screen renders
- THEN locked rewards are shown for preview via a hardcoded preview set, without actually adding them to the user's granted-unlockables state
