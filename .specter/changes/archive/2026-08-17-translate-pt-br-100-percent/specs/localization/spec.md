# Delta for Localization

## ADDED Requirements

### Requirement: Full pt-BR string parity
The system SHALL provide a Brazilian Portuguese (`pt-rBR`) translation for every translatable string key defined in the `base` locale of the `i18n` and `i18n-aniyomi` moko-resources modules, excluding keys whose value is inherently non-translatable (e.g. external URLs).
Source: `i18n/src/commonMain/moko-resources/{base,pt-rBR}/strings.xml`, `i18n-aniyomi/src/commonMain/moko-resources/{base,pt-rBR}/strings.xml`.

#### Scenario: No English fallback for pt-BR users
- GIVEN the device/app language is set to Portuguese (Brazil)
- WHEN any screen in the app renders text sourced from a moko-resources string key
- THEN the text appears in Brazilian Portuguese, never falling back to the English `base` value

#### Scenario: Non-translatable technical strings are exempt
- GIVEN a string key's value is an external URL (e.g. `novel_reader_ai_translator_api_url_openrouter`)
- WHEN checking pt-BR translation completeness
- THEN that key is excluded from the parity requirement and may remain identical across locales

### Requirement: pt-BR translation style consistency
New pt-rBR translations SHALL match the existing established style: informal register (using "você", not "tu"), natural Brazilian Portuguese phrasing rather than literal or European Portuguese translation, and exact preservation of format placeholders (`%s`, `%1$s`, etc.) present in the corresponding `base` string.

#### Scenario: Placeholder count matches source
- GIVEN a base string contains format placeholders
- WHEN its pt-rBR translation is written
- THEN the translation contains the same placeholders, in a grammatically valid order for Portuguese

### Requirement: User-facing text sourced from string resources
All user-facing text rendered in Compose UI SHALL be sourced from a moko-resources string key rather than a hardcoded Kotlin string literal, so it participates in translation. This excludes the intentionally separate bilingual EN/RU system used by the Aurora/Lattice easter-egg modules (`AuroraLocalization.translate()`), debug/preview-only composables, and non-UI technical strings (URLs, log messages, animation transition labels).

#### Scenario: New UI text is added as a string resource
- GIVEN a developer adds new user-facing text to a Compose screen
- WHEN the text is not part of the Aurora/Lattice bilingual system
- THEN it is added as a key in `base/strings.xml` (with a corresponding `pt-rBR` translation) and referenced via `stringResource`/`MR.strings`, not written as a literal
