# Proposal: Localize Remaining Treasury-Named Nickname Effect Labels

## Intent
The nickname "Efeito" picker on Home shows 6 unlockable effect names hardcoded as English Kotlin string literals with a `" (Treasury)"` suffix (e.g. "Aurora Crown (Treasury)"), instead of using `stringResource(...)` like every other entry in the same picker. Treasury was already removed as a feature, so these labels are both untranslated and reference a system that no longer exists.

## Scope
- Localize the 6 hardcoded `NicknameEffectPreset` labels in `HomeHubTab.kt` to use string resources, matching the pattern already used by every sibling entry (including the 7th Treasury-origin entry, `GlitchRuneRed`, which already does this correctly).
- Drop the "(Treasury)" parenthetical from the new strings entirely — Treasury no longer exists as a concept, so there's nothing left to reference.
- Out of scope: renaming the underlying `NicknameEffectPreset` enum keys/values, changing which effects exist, or touching `GlitchRuneRed`'s existing "(Carmesim)" qualifier (a color name, not a system reference).

## Approach
Add 6 new string keys (`aurora_nickname_effect_aurora_crown`, `_glitch_rune`, `_cipher`, `_trinity_prism`, `_shadow_crown`, `_rank_sigils`) to base + pt-rBR `strings.xml`, following the existing `aurora_nickname_effect_*` naming convention, then replace the 6 hardcoded literals in `NicknameEffectPreset.label()` (`HomeHubTab.kt:1298-1304`) with `stringResource(...)` calls.
