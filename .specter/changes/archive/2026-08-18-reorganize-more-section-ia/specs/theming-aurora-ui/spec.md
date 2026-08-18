# Delta for Theming & Aurora UI

## MODIFIED Requirements

### Requirement: Treasury unlockables gallery
The system SHALL show a gallery of unlocked cosmetic rewards (themes, auras, presets, nicknames, avatar frames, home badges, special backgrounds), visible from the More tab only if the build is DEBUG or the user has unlocked at least one reward. Treasury no longer appears inside the Settings root list — it is reachable exclusively as a direct More-tab entry.
Source: `SettingsTreasuryScreen.kt`, `shouldShowTreasury`, `UnlockableManager`, `MoreScreenAurora.kt`.

#### Scenario: Treasury entry hidden until first unlock
- GIVEN a release-build user has never unlocked any reward
- WHEN they view the More tab
- THEN the Treasury entry is not shown

#### Scenario: Debug builds always show Treasury
- GIVEN the app is a DEBUG build
- WHEN the More tab is viewed, regardless of unlock state
- THEN the Treasury entry is shown

#### Scenario: Debug preview bypasses locks without granting rewards
- GIVEN `debugBypassTreasuryLocks` is enabled in a DEBUG build
- WHEN the Treasury screen renders
- THEN locked rewards are shown for preview via a hardcoded preview set, without actually adding them to the user's granted-unlockables state

#### Scenario: Treasury is not listed inside Settings
- GIVEN a user opens Configurações
- WHEN they browse its five domain groups
- THEN Treasury does not appear in any of them — its only entry point is the More tab root
