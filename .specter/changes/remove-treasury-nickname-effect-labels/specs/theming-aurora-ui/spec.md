# Delta for Theming & Aurora UI

## ADDED Requirements

### Requirement: Nickname effect labels are fully localized
The system SHALL source every `NicknameEffectPreset` display label from a string resource, with no hardcoded English literals and no references to the removed Treasury feature.

#### Scenario: All effect labels resolve through string resources
- GIVEN a user opens the nickname "Efeito" picker on Home
- WHEN any of the 17 effect options renders its label
- THEN the label is sourced via `stringResource(...)`, none as a hardcoded Kotlin string literal

#### Scenario: No label references Treasury
- GIVEN the app language is Portuguese (Brazil)
- WHEN the effect picker renders labels for the effects formerly gated behind Treasury (Aurora Crown, Glitch Rune, Cipher Sigil, Trinity Prism, Shadow Crown, Rank Sigils)
- THEN none of their labels contain the word "Treasury" or "Tesouraria"
