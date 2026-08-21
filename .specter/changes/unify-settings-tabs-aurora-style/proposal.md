# Proposal: Migrate Remaining Legacy-Tab Screens to Aurora Tab Styling

## Intent
Mais > Histórico uses the app's bespoke Aurora-themed tab component (`TabbedScreenAurora`), but three other tabbed screens — Mais > Configurações > Sistema > Avançado, Mais > Configurações > Leitura > Novel, and Mais > Configurações > Dados e Armazenamento > Armazenamento — instead use the older, generic `TabbedScreen` component with plain Material3 tab styling. A full codebase audit confirms these are the only three call sites of the legacy component, so this is a complete, bounded fix rather than a partial one.

## Scope
- Migrate `SettingsAdvancedScreen.kt`, `SettingsNovelReaderScreen.kt`, and `StorageTab.kt` from `TabbedScreen` to `TabbedScreenAurora`.
- Out of scope: the 6 non-tabbed screens found using raw Material3 `Scaffold`/`TopAppBar` instead of `SettingsScaffold` (tracked separately in the backlog as `audit-legacy-non-aurora-screens`) — a distinct, larger-scoped problem not related to tabs.

## Approach
`TabbedScreenAurora` already supports a no-search mode: its search UI is gated per-tab by `TabContent.searchEnabled`, which defaults to `false` — the same flag `TabbedScreen` already uses. No new overload is needed; each of the three screens' `TabbedScreen(...)` call is swapped directly for `TabbedScreenAurora(...)` with equivalent parameters, matching History's back-button handling and header layout. See `design.md` for confirmation of this API-compatibility finding and any per-screen wiring differences (e.g. `StorageTab` being a Voyager `Tab`, not a `Screen`).
