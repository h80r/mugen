# Theming/Aurora UI Specification Delta

## ADDED Requirements

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
- GIVEN the Cosméticos sub-screen renders its 8 selector groups
- WHEN a cosmetic option is displayed
- THEN it is shown as selectable without a lock/unlock check, unlike Treasury's gated presentation
