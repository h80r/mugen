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

### Requirement: Cosmetic selection in Settings Appearance
The system SHALL provide a cosmetic selector sub-screen reachable from `SettingsAppearanceScreen.kt`, exposing aura, special background, tab customization, profile title, profile effect, avatar frame, and home badge selection, bound to the same preferences Treasury's selectors write. Theme is intentionally excluded, since Appearance already provides its own theme selector.
Source: new "Cosméticos" sub-screen, `UiPreferences.enabledAuras()/specialBackgroundStyle()/showTabGlow()/showCelestialNavbar()/showCircuitNavbar()`, `UserProfilePreferences.nicknameEffect()/avatarFrameStyle()/homeBadgeStyle()/profileTitle()`.

#### Scenario: Selectors render as visual cards, not plain list dialogs
- GIVEN a user opens the Cosméticos sub-screen
- WHEN any of the 7 selector groups renders
- THEN it presents as a card grid with icon, preview accent, and description per option — reusing Treasury's own selector components (`TreasuryToggleSelector`/`TreasuryArtifactShard`, `TreasuryAuraSelector`/`TreasuryAuraChannel`) rather than a plain text list

#### Scenario: Selecting a cosmetic in Settings applies it immediately
- GIVEN a user opens the Cosméticos sub-screen under Settings > Appearance
- WHEN they select a different aura
- THEN `UiPreferences.enabledAuras()` is updated and the change is reflected wherever auras render, identical in effect to selecting it via Treasury

#### Scenario: All options are shown regardless of unlock state
- GIVEN the Cosméticos sub-screen renders its 7 selector groups
- WHEN a cosmetic option is displayed
- THEN it is shown as selectable without a lock/unlock check, unlike Treasury's gated presentation

#### Scenario: A fixed identity preview reflects avatar, effect, badge, and title
- GIVEN a user opens the Cosméticos sub-screen
- WHEN they scroll through the selector groups
- THEN a pinned identity preview card (avatar frame, styled nickname with home-hub badge, profile title) stays fixed above the scrolling content and updates live as those selections change
