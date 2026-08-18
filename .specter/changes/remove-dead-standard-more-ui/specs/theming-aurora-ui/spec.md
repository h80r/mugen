# Delta for Theming & Aurora UI

## REMOVED Requirements

### Requirement: Standard (non-Aurora) More screen variant
Reason: `isAuroraStyle` has defaulted to `true` for every entry in the `AppTheme` enum since commit `b00c794ab` (2026-01-12); no theme sets it to `false`, so the non-Aurora `MoreScreen` composable and its selection branch in `MoreTab` — along with ~15 structurally identical `if (theme.isAuroraStyle) { ... } else { ... }` branches across History, Category, Stats, Downloads, Library, and Reader screens — were unreachable in any shipped build. Removed as dead code rather than kept as a maintained alternative UI.
Migration: none — behavior is unchanged for all users, since these branches never executed.
