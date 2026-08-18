# Theming/Aurora UI Specification Delta

## REMOVED Requirements

### Requirement: Treasury unlockables gallery
**Reason**: Treasury is deleted entirely. Cosmetic selection is now handled by the Cosméticos sub-screen under Settings > Appearance (see `migrate-stats-and-cosmetic-selectors`'s spec delta, "Cosmetic selection in Settings Appearance"), and there is no remaining lock/progress state to gallery-display since every cosmetic is unconditionally unlocked.
**Migration**: None needed for end users — every selector Treasury exposed has an equivalent in the Cosméticos screen, already shipped and verified before this change. `shouldShowTreasury` and `debugBypassTreasuryLocks` are removed as dead code with no replacement needed.
