# Tasks

## 1. Fix back button fallback
- [ ] 1.1 In `app/src/main/java/eu/kanade/presentation/more/settings/screen/HelpScreen.kt:29,43`, replace `if (handleBack != null) handleBack::invoke else null` with a fallback to `navigator.pop()` (reuse `resolveSearchableSettingsBackPress()` from `SearchableSettings.kt:53-58` if it fits, otherwise an inline `handleBack ?: { navigator.pop() }` using `LocalNavigator.currentOrThrow`)
- [ ] 1.2 Manually verify: navigate to Mais > Ajuda and confirm the back button appears and works
