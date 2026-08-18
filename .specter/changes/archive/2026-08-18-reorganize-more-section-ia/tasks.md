# Tasks

## 1. Build the four domain-grouping screens
- [x] 1.1 Create `SettingsReadingScreen` ("Leitura") — nav-card list with three entries: Mangá → `SettingsReaderScreen`, Novel → `SettingsNovelReaderScreen`, Vídeo → `PlayerSettingsMainScreen`
- [x] 1.2 Create `SettingsLibraryDataScreen` ("Biblioteca & Dados") — nav-card list with three entries: Biblioteca → `SettingsLibraryScreen`, Downloads → `SettingsDownloadScreen`, Dados e armazenamento → `SettingsDataScreen`
- [x] 1.3 Create `SettingsConnectionsScreen` ("Conexões") — nav-card list with two entries: Rastreamento → `SettingsTrackingScreen`, Fontes & Navegar → `SettingsBrowseScreen`
- [x] 1.4 Create `SettingsSystemScreen` ("Sistema") — nav-card list with three entries: Segurança → `SettingsSecurityScreen`, Avançado → `SettingsAdvancedScreen` (post-split), Sobre → `AboutScreen`

## 2. Split SettingsNovelReaderScreen into 5 tabs
**Branch:** novel-reader-settings-split
- [x] 2.1 Extract a tabbed screen shell for Novel Reader settings (reuse the existing tabbed-screen pattern from `StorageTab`/`AnimeStorageTab`)
- [x] 2.2 Texto tab: display/typography group, font family/import controls, theme settings group
- [x] 2.3 Tradução tab: Gemini/AI translation group, Google Translate group, dictionary group
- [x] 2.4 Navegação tab: navigation group (gestures, auto-scroll, paging, tap zones)
- [x] 2.5 Acessibilidade & TTS tab: accessibility group, TTS group
- [x] 2.6 Avançado tab: custom CSS/JS group, E-Ink refresh group
- [x] 2.7 Verify every preference item from the original nine groups is present in exactly one of the five tabs (cross-check against the full content inventory)

## 3. Split SettingsAdvancedScreen into 3 tabs
**Branch:** advanced-settings-split
- [x] 3.1 Extract a tabbed screen shell for Advanced settings
- [x] 3.2 Sistema tab: background activity group, incognito policy group, haptic feedback group, network group
- [x] 3.3 Dados e cache tab: data group (clear databases), reader cache/decode settings, data saver group
- [x] 3.4 Depuração tab: crash log dumping, verbose logging, debug info nav entry, extensions group
- [x] 3.5 Verify every preference item from the original eight groups is present in exactly one of the three tabs

## 4. Rewire the Settings root
- [x] 4.1 Update `SettingsMainScreen`/`SettingsNavigationItems` to list the five domain groups (Leitura, Biblioteca & Dados, Aparência, Conexões, Sistema) instead of the twelve flat categories
- [x] 4.2 Remove the standalone "Player" entry from the Settings root now that it's nested under Leitura → Vídeo

## 5. Remove duplicate entries from the More tab
- [x] 5.1 In `MoreScreenAurora.kt`, remove the five direct entries that duplicate a Settings destination: Dados e armazenamento, Configurações do player, Leitor de mangá, Leitor de novel, Sobre
- [x] 5.2 In `MoreScreenAurora.kt`, remove Conquistas and Tesouraria from wherever they were also listed inside the Settings root (per the `SettingsNavigationItems` update in 4.1, they should no longer be there — this task is the cross-check)

## 6. Update SettingsSearchScreen
- [x] 6.1.1 Add tab-selectable routes and reusable per-tab preference inventories for Novel Reader and Advanced settings
- [x] 6.1.2 Update `settingScreens`/`playerSettingScreens` lists to reflect the new hierarchy (domain screens plus their children)
- [x] 6.2 Update breadcrumb construction (`getLocalizedBreadcrumb` call sites) so search results show the new domain path (e.g. "Configurações > Leitura > Novel > Tradução")

## 7. Verification
- [x] 7.1 Manually walk all 19 pre-existing destinations from the More tab, confirming each is reachable by exactly one path (tracking shortcut excluded per the kept-exception)
- [x] 7.2 Confirm Conquistas and Tesouraria are reachable only from the More tab root, not from within Configurações
- [x] 7.3 Confirm the five removed More-tab entries (Dados e armazenamento, Player, Leitor de mangá, Leitor de novel, Sobre) are still reachable via Configurações
- [x] 7.4 Run `SettingsSearchScreen` and confirm searching for a known deep item (e.g. a Novel Reader TTS setting) surfaces the correct new breadcrumb and navigates correctly
- [x] 7.5 Full project build and existing test suite pass
