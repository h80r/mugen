# Design: Migrating Legacy Tab Screens to TabbedScreenAurora

## Key finding: no new overload needed

Initial assumption (before reading `TabbedScreenAurora`'s source) was that a lighter "no search bar" variant would need to be added, since History — the only current consumer — always has search. Reading the implementation shows this isn't true:

- `TabbedScreenAurora` and the legacy `TabbedScreen` both consume the same `TabContent` data class (`TabbedScreen.kt:137-146`), including its `searchEnabled: Boolean = false` field.
- In both components, the search icon/field only renders when `currentTab?.searchEnabled == true` (`TabbedScreenAurora.kt:548`, mirrored by `TabbedScreen.kt:60,81`) — it is a **per-tab** flag, not a screen-level "does this screen support search" switch.
- `navigateUp` is likewise read identically from `tabs.getOrNull(currentPage)?.navigateUp` in both components (`TabbedScreenAurora.kt:278`, `TabbedScreen.kt:91` via `tab.navigateUp`).

Since none of `SettingsAdvancedScreen`, `SettingsNovelReaderScreen`, or `StorageTab`'s tab definitions set `searchEnabled = true` (confirmed — they're settings/storage groups, not searchable lists), swapping the call from `TabbedScreen(...)` to `TabbedScreenAurora(...)` with the same `titleRes`/`tabs`/`state` arguments is a like-for-like replacement. No new component variant, no `TabContent` schema change.

## Per-screen migration notes

- **`SettingsAdvancedScreen.kt:164`** and **`SettingsNovelReaderScreen.kt:277`**: both already build their `tabs: ImmutableList<TabContent>` via a local `Tab` enum → `TabContent` mapping function (`AdvancedSettingsTab`/`NovelReaderSettingsTab`), with `navigateUp = handleBack ?: { navigator.pop() }` already wired per tab (confirmed correct fallback pattern, unlike `HelpScreen`'s bug in a separate change). Only the `TabbedScreen(...)` → `TabbedScreenAurora(...)` call itself needs to change; the tab-content-building logic is unaffected.
- **`StorageTab.kt:52`**: this is a Voyager `Tab` (bottom-nav-style destination), not a `Screen` pushed onto a stack — so it likely has no back button today (bottom-nav tabs typically don't). Confirm during implementation whether `TabContent.navigateUp` is `null` here (expected) and whether `TabbedScreenAurora`'s header renders acceptably with no back arrow (it should — `navigateUp != null` already gates the back-arrow `AuroraTopBarIconButton` in both components).

## Visual differences to verify after migration

`TabbedScreenAurora` adds chrome beyond what `TabbedScreen` has: `AuroraTabRow`/`AuroraTabHeader` styling, tab-switch bounce/edge animations, and optional `showCompactHeader`/`userName`/`userAvatar` header content (unused by History's simple case, and not needed here either — leave those params at their defaults). The three migrated screens should end up visually matching History's tab row (pill/underline styling, spacing, colors) with no functional regressions to their existing tab content, actions, or badge counts.
