# Proposal: Fix Missing Back Button on Ajuda (Help) Screen

## Intent
Mais > Ajuda has no back button in its app bar. `HelpScreen.kt` reads the nullable `LocalBackPress.current` and, when it's null, passes `null` onward instead of falling back to `navigator.pop()` — the one screen in the codebase that doesn't apply the standard fallback every comparable screen uses. Since the screen is always reached via `navigator.push(HelpScreen)`, there is always a way back that just isn't being used.

## Scope
- Give `HelpScreen` a working back button in every navigation context it can be reached from.
- Out of scope: any other Help screen content or layout changes.

## Approach
Apply the same fallback pattern already used elsewhere (`resolveSearchableSettingsBackPress()` in `SearchableSettings.kt:53-58`, or the inline `handleBack ?: { navigator.pop() }` pattern used by `SettingsAdvancedScreen.kt`/`SettingsNovelReaderScreen.kt`) to `HelpScreen.kt:29,43`.
