# More Section Navigation Specification

## Requirements

### Requirement: More tab top-level entries
The system SHALL present exactly nine top-level entries in the More tab: two behavior toggles (Downloaded only, Incognito mode), five direct navigation items (Categorias, Estatísticas, Conquistas, Tesouraria, Fila de downloads, Erros de atualização da biblioteca), one entry point into Configurações, and Ajuda — with no other settings or preference destination duplicated directly at this level.
Source: `app/src/main/java/eu/kanade/presentation/more/MoreScreenAurora.kt`.

#### Scenario: Gamification entries live only in the More tab
- GIVEN the user opens the More tab
- WHEN they look for Conquistas or Tesouraria
- THEN both are listed as direct More-tab entries, and neither appears anywhere inside Configurações

#### Scenario: Screens with a Settings home are not duplicated at the More-tab root
- GIVEN Dados e armazenamento, Configurações do player, Leitor de mangá (Settings), Leitor de novel (Settings), and Sobre each have a home inside Configurações
- WHEN the user views the More tab root
- THEN none of these five appear as standalone More-tab entries — each is reachable only by first opening Configurações

### Requirement: Settings grouped by usage domain
The system SHALL organize the Settings root into five domain groups — Leitura, Biblioteca & Dados, Aparência, Conexões, Sistema — each a navigable screen listing its member screens, replacing the prior flat list of twelve screen-named categories.
Source: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsMainScreen.kt`, `SettingsNavigationItems.kt`.

#### Scenario: Leitura groups all content-consumption settings
- GIVEN the user opens Configurações → Leitura
- WHEN the screen renders
- THEN it lists exactly three entries — Mangá, Novel, Vídeo — with Vídeo navigating to the existing Player Settings screen hierarchy

#### Scenario: Biblioteca & Dados groups collection-management settings
- GIVEN the user opens Configurações → Biblioteca & Dados
- WHEN the screen renders
- THEN it lists exactly three entries — Biblioteca, Downloads, Dados e armazenamento

#### Scenario: Aparência remains a direct Settings-root entry
- GIVEN the user opens Configurações
- WHEN the screen renders
- THEN Aparência is listed as its own top-level entry, not nested under any domain group

#### Scenario: Conexões groups external-service settings
- GIVEN the user opens Configurações → Conexões
- WHEN the screen renders
- THEN it lists exactly two entries — Rastreamento, Fontes & Navegar

#### Scenario: Sistema groups administrative settings
- GIVEN the user opens Configurações → Sistema
- WHEN the screen renders
- THEN it lists exactly three entries — Segurança, Avançado, Sobre

### Requirement: Novel Reader settings split into tabs
The system SHALL present Novel Reader settings as five tabs — Texto, Tradução, Navegação, Acessibilidade & TTS, Avançado — instead of a single scrolling screen with nine groups.
Source: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsNovelReaderScreen.kt`.

#### Scenario: Texto tab contains display and theming settings
- GIVEN the user opens Configurações → Leitura → Novel → Texto
- WHEN the tab renders
- THEN it contains the display/typography group, font family/import controls, and the theme settings group

#### Scenario: Tradução tab contains all translation-related settings
- GIVEN the user opens Configurações → Leitura → Novel → Tradução
- WHEN the tab renders
- THEN it contains the Gemini/AI translation group, the Google Translate group, and the dictionary group

#### Scenario: Every item from the original nine groups has a home in exactly one tab
- GIVEN the pre-existing Novel Reader settings screen had nine groups
- WHEN the five-tab split is complete
- THEN every individual preference item from those groups is present in exactly one of the five new tabs, with none omitted

### Requirement: Advanced settings split into tabs
The system SHALL present Advanced settings as three tabs — Sistema, Dados e cache, Depuração — instead of a single scrolling screen with eight heterogeneous groups.
Source: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt`.

#### Scenario: Sistema tab contains OS-level integration settings
- GIVEN the user opens Configurações → Sistema → Avançado → Sistema
- WHEN the tab renders
- THEN it contains the background activity group, incognito policy group, haptic feedback group, and network group

#### Scenario: Dados e cache tab contains storage-affecting settings
- GIVEN the user opens Configurações → Sistema → Avançado → Dados e cache
- WHEN the tab renders
- THEN it contains the data group, reader cache/decode settings, and the data saver group

#### Scenario: Depuração tab contains diagnostic settings
- GIVEN the user opens Configurações → Sistema → Avançado → Depuração
- WHEN the tab renders
- THEN it contains crash log dumping, verbose logging, the debug info navigation entry, and the extensions group

### Requirement: Tracking shortcut remains contextually accessible
The system SHALL continue to offer a direct shortcut to Rastreamento settings from the manga, anime, and novel detail screens, in addition to its home under Configurações → Conexões, as a deliberate exception to the single-path rule.
Source: `MangaScreen.kt`, `AnimeScreen.kt`, `NovelScreen.kt`, `SettingsTrackingScreen.kt`.

#### Scenario: Tracking is reachable both contextually and via Settings
- GIVEN a user is viewing a manga's detail screen
- WHEN they tap the tracking button
- THEN they reach the same `SettingsTrackingScreen` that Configurações → Conexões → Rastreamento leads to

### Requirement: No orphaned destinations
The system SHALL preserve reachability for every destination screen that was accessible from the More tab prior to this reorganization.
Source: navigation audit against `SettingsSearchScreen.kt`.

#### Scenario: Every pre-existing Settings screen has exactly one home
- GIVEN the pre-existing Settings and Player Settings screens
- WHEN the new five-domain structure is in place
- THEN each screen is reachable by exactly one navigation path from the More tab, excluding the deliberate tracking shortcut

#### Scenario: Every non-Settings destination retains its path
- GIVEN Categorias, Estatísticas, Conquistas, Tesouraria, Fila de downloads, Erros de atualização, and Ajuda were reachable from the More tab
- WHEN the new structure ships
- THEN all remain reachable directly from the More tab root
