# Verification: remove-treasury-screen

## Verdict
PASS

## CRITICAL
None.

## WARNING
None.

## SUGGESTION
- [completeness] `label_treasury` and other now-orphaned `treasury_*` translation strings were left in `strings.xml` — out of this change's Scope (only Kotlin symbols were listed), but worth a follow-up unused-resource cleanup pass across all locales.
- [completeness] `visibleUnlockablesForTreasuryPreview()` in `DebugUnlockablePreview.kt` lost its only real call site when `AppThemePreferenceWidget.kt` was simplified in task 3.3, leaving it used only by its own test. Explicitly out of scope per the proposal ("Out of scope: Any further change to `UnlockableManager`'s core availability methods beyond removing the now-dead debug-bypass checks"), but a candidate for a future cleanup change.
- [coherence] `isTreasury()` on `NicknameEffectPreset` (`HomeHubTab.kt`/`NicknameEffects.kt`) is an unrelated naming convention for a category of nickname effects, not a reference to the deleted screen — confirmed out of scope and left untouched.

## Notes
- Full debug build (`:app:assembleDebug`) succeeds; `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` are clean.
- Unit test suite: 3156/3159 pass. The 3 failures (`EntryRatingCacheTest`, `NovelReaderCacheCoordinatorTest`, `NovelReaderScreenModelTest`) are in rating-cache and novel-reader-cache code untouched by this change — pre-existing/unrelated.
- Manual verification on a physical device (task 5.4/5.5) confirmed: Treasury is unreachable from every entry point (More tab, Settings root, deep link); all 7 active Cosméticos selector groups (auras, backgrounds, tab customization, profile titles, nickname effects, avatar frames, home badges) apply correctly and reflect live in the identity preview card and app-wide UI; Stats screen (streak/comparison/yearly activity) renders correctly; no crashes or logcat errors throughout the session.
