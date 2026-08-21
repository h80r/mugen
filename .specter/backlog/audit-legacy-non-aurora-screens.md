# Auditoria de telas com visual legado (não-Aurora)

**Captured:** 2026-08-19
**Source:** exploration (durante o planejamento de `unify-settings-tabs-aurora-style`)

## Idea
Durante a auditoria de telas com abas fora do padrão Aurora (que originou a change `unify-settings-tabs-aurora-style`), uma varredura mais ampla encontrou 6 telas que usam `androidx.compose.material3.Scaffold`/`TopAppBar` diretamente, em vez do `SettingsScaffold` ou qualquer equivalente Aurora usado pelo resto do app. É o mesmo cheiro de "não usa o wrapper do app" do problema das abas, mas um escopo maior e não relacionado a abas — vale uma investigação/change dedicada no futuro.

## Notes
- Telas identificadas (arquivo:linha da chamada a `Scaffold`/`TopAppBar`):
  - `app/src/main/java/eu/kanade/tachiyomi/ui/entries/novel/OmniBuilderScreen.kt:71,73`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/entries/suggestions/EntrySuggestionsScreen.kt:326,328`
  - `app/src/main/java/eu/kanade/tachiyomi/ui/reader/novel/dictionary/NovelDictionaryHistoryScreen.kt:414,417`
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/anixart/AnixartImportScreen.kt:119`
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/anilist/AnilistImportScreen.kt:107`
  - `app/src/main/java/eu/kanade/presentation/more/settings/screen/shikimori/ShikimoriImportScreen.kt:114`
- Duas outras ocorrências apareceram na busca (`TrackerWebViewLoginActivity.kt`, `SubtitleSettingsPanel.kt`) mas são plausivelmente exceções legítimas — uma Activity de login via WebView e um painel overlay do player — merecem uma segunda checagem antes de incluir na lista real.
- Ao promover: confirmar se cada tela é de fato uma tela "normal" de navegação (não um fluxo modal/import que intencionalmente foge do padrão) antes de decidir migrar para `SettingsScaffold`/Aurora.
