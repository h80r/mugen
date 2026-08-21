# Tasks

## 1. Migrate SettingsAdvancedScreen
- [ ] 1.1 Swap `TabbedScreen(...)` for `TabbedScreenAurora(...)` at `SettingsAdvancedScreen.kt:164`, passing the same `titleRes`/`tabs`/`state` arguments
- [ ] 1.2 Manually verify: Mais > Configurações > Sistema > Avançado shows Aurora-styled tabs (Sistema/Dados e cache/Depuração), back button and every group's content unchanged

## 2. Migrate SettingsNovelReaderScreen
- [ ] 2.1 Swap `TabbedScreen(...)` for `TabbedScreenAurora(...)` at `SettingsNovelReaderScreen.kt:277`, passing the same `titleRes`/`tabs`/`state` arguments
- [ ] 2.2 Manually verify: Mais > Configurações > Leitura > Novel shows Aurora-styled tabs (Texto/Tradução/Navegação/Acessibilidade & TTS/Avançado), back button and every group's content unchanged

## 3. Migrate StorageTab
- [ ] 3.1 Swap `TabbedScreen(...)` for `TabbedScreenAurora(...)` at `StorageTab.kt:52`, passing the same `titleRes`/`tabs`/`state` arguments
- [ ] 3.2 Confirm `TabContent.navigateUp` behavior is acceptable for this Voyager `Tab` destination (expected `null`, no back arrow, consistent with other bottom-nav-style tabs) — adjust if the screen is actually reached in a way that needs a back button
- [ ] 3.3 Manually verify: Mais > Configurações > Dados e Armazenamento > Armazenamento shows Aurora-styled tabs (Anime/Manga/Novel), content unchanged

## 4. Confirm no legacy call sites remain
- [ ] 4.1 Grep for `TabbedScreen(` (excluding `TabbedScreenAurora(`) across the codebase and confirm zero remaining call sites
- [ ] 4.2 Consider whether the now-unused legacy `TabbedScreen.kt` composable should be deleted (grep-confirm zero remaining references first) — delete if confirmed dead, otherwise leave a note for why it's kept
