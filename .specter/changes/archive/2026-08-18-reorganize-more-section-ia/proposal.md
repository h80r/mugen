# Proposal: Reorganize More Section Information Architecture

## Intent
The More tab and its Settings sub-screens accumulated 10 destinations reachable by more than one navigation path — five of them (Data & Storage, Player Settings, Manga Reader Settings, Novel Reader Settings, About) purely redundant, appearing both as standalone More-tab entries and inside the Settings list pointing at the exact same screen. Achievements and Treasury (gamification screens, not real preferences) are duplicated between the More tab and the Settings root list. Two Settings screens — Novel Reader Settings (9 groups, 90+ items) and Advanced Settings (8 heterogeneous groups) — are overloaded single screens with no internal structure. This change redesigns the navigation so every destination is reachable by exactly one path (except one deliberately-kept contextual shortcut), and so the two overloaded screens are split into focused tabs.

Three alternative groupings were prototyped and presented visually; the user selected **"by usage domain"**: instead of organizing the Settings root by screen name (12 flat categories), group by *when* a feature is used — Reading (manga + novel + player, since all three configure how you consume content), Library & Data (library behavior + downloads + storage/backup, since all three govern what's in your collection and where it lives), Appearance (unchanged), Connections (tracking + browse/sources, since both configure external services/content sources), System (security + advanced + about, the rarely-touched administrative tail).

Every one of the 19 pre-existing destination screens (10 Settings screens + 10 Player Settings sub-screens, cross-checked against the canonical list in `SettingsSearchScreen.kt:485-509`, plus the ~250 individual preference items inventoried across them) was audited against this new structure: none become unreachable. The tracking shortcut on manga/anime/novel detail screens is kept as a deliberate second path (contextual convenience, not an accidental duplicate) — the user explicitly chose to preserve it.

## Scope

### New More tab root
```
Mais
├─ Somente baixados / Incógnito (toggles, unchanged)
├─ Categorias
├─ Estatísticas
├─ Conquistas          (moved out of Settings root — lives only here now)
├─ Tesouraria           (moved out of Settings root — lives only here now)
├─ Fila de downloads
├─ Erros de atualização da biblioteca
├─ Configurações        (see restructured tree below)
└─ Ajuda
```
The five redundant direct entries (Dados e armazenamento, Configurações do player, Leitor de mangá, Leitor de novel, Sobre) are removed from the More tab root; each remains reachable exclusively through Configurações.

### New Settings root — 5 domains instead of 12 categories
```
Configurações
├─ Leitura
│  ├─ Mangá             = SettingsReaderScreen, no internal change
│  ├─ Novel              = SettingsNovelReaderScreen, split into 5 tabs (see below)
│  └─ Vídeo              = PlayerSettingsMainScreen (10 sub-screens), now nested under Leitura instead of a sibling root entry
│
├─ Biblioteca & Dados
│  ├─ Biblioteca         = SettingsLibraryScreen, no internal change
│  ├─ Downloads          = SettingsDownloadScreen, no internal change
│  └─ Dados e armazenamento = SettingsDataScreen, no internal change
│
├─ Aparência             = SettingsAppearanceScreen, unchanged
│
├─ Conexões
│  ├─ Rastreamento       = SettingsTrackingScreen (+ contextual shortcut from title screens, kept)
│  └─ Fontes & Navegar   = SettingsBrowseScreen (+ 3 Extension Store screens)
│
└─ Sistema
   ├─ Segurança          = SettingsSecurityScreen, no internal change
   ├─ Avançado           = SettingsAdvancedScreen, split into 3 tabs (see below)
   └─ Sobre              = AboutScreen, no internal change
```

### SettingsNovelReaderScreen split (5 tabs)
- **Texto**: display/typography group + font family/import + theme settings group
- **Tradução**: Gemini/AI translation group + Google Translate group + dictionary group
- **Navegação**: navigation group (gestures, auto-scroll, paging, tap zones)
- **Acessibilidade & TTS**: accessibility group + TTS group
- **Avançado**: custom CSS/JS group + E-Ink refresh group

### SettingsAdvancedScreen split (3 tabs)
- **Sistema**: background activity, incognito policy, haptic feedback, network groups
- **Dados e cache**: data group (clear databases), reader cache/decode settings, data saver group
- **Depuração**: crash logs, verbose logging, debug info nav, extensions group

## Correctness invariant (this change's contract)
Every destination screen reachable from the More tab today remains reachable by **exactly one** path after this change, with one deliberate exception: the tracking shortcut from manga/anime/novel detail screens, which the user chose to keep as a legitimate contextual second path. No preference item, toggle, sub-screen, or navigable destination is deleted — only regrouped. This was audited item-by-item against the canonical screen list in `SettingsSearchScreen.kt:485-509` (10 Settings screens + 10 Player Settings sub-screens) and the full content inventory of every screen (~250 individual items) gathered before this proposal was drafted.

## Out of scope
- Debug-only / easter-egg entries (Lattice Grid, Reset Aurora Heart, Force Lattice Breach, Glitch Rift, the About-screen logo tap sequence, etc.) — the user explicitly chose to leave these as-is, not isolate them further
- The internal content of screens other than Novel Reader and Advanced (Appearance, Library, Downloads, Tracking, Browse, Data, Security, Player, About) — these only move position in the tree, their contents are untouched
- `SettingsSearchScreen`'s indexing/matching logic — only its screen list and breadcrumb construction need updating for the new hierarchy, the search algorithm itself doesn't change
- This change depends on `remove-dead-standard-more-ui` having been merged first, so there is only one live More-tab UI (`MoreScreenAurora`) to restructure instead of two

## Approach
Two independent-ish workstreams: (1) build the four new domain-grouping screens (Leitura, Biblioteca & Dados, Conexões, Sistema) as simple nav-card lists pointing at existing screens, with no new business logic; (2) split `SettingsNovelReaderScreen` and `SettingsAdvancedScreen` into internal tabs, following the existing tabbed-screen pattern already used by `StorageTab`/`AnimeStorageTab`/etc. Then rewire `SettingsMainScreen`/`SettingsNavigationItems` to the new 5-domain tree, remove the five duplicate direct entries and the two gamification entries from `MoreScreenAurora.kt`, and update `SettingsSearchScreen`'s `settingScreens`/`playerSettingScreens` lists and breadcrumb logic to match the new hierarchy. Finish with a manual walk of all 19 destinations from the More tab, confirming single-path reachability against the audit performed during planning.
