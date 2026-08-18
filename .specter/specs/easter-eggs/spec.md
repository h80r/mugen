# Easter Eggs Specification

Documents the *mechanism* of two hidden unlock systems. No puzzle answers, riddles, or reward payloads exist in this repository — scenario/vault source files are gitignored — and none are recorded here.

## Requirements

### Requirement: Aurora Heart riddle-chain trigger
The system SHALL reveal a hint for the Aurora Heart quest when an episode/chapter is marked seen/read during a soft "veil thin" local-time window, and SHALL accept phrase submissions only from completed (not per-keystroke) search queries.
Source: `eu.kanade.domain.easteregg.aurora`, `AuroraHeartManager`, `AuroraNight.isVeilThin()` (02:45–04:15 local), `AuroraQuest.kt`.

#### Scenario: Hint reveals only inside the veil-thin window
- GIVEN the local time is 03:00 (inside the 02:45–04:15 window)
- WHEN the user marks a chapter as read
- THEN `AuroraHeartManager.revealHint()` is triggered

#### Scenario: Hint does not reveal outside the window
- GIVEN the local time is 15:00
- WHEN the user marks a chapter as read
- THEN no hint reveal occurs, though the night-action counter logic is unaffected by time of day for other purposes

#### Scenario: Phrase submissions are rate-limited
- GIVEN a phrase is submitted from a completed search query
- WHEN another phrase is submitted less than 250ms later
- THEN the second attempt is rate-limited via mutex, since PBKDF2 verification is deliberately expensive

#### Scenario: Repeated night actions emit an ambient whisper once
- GIVEN the user has triggered `registerNightAction()` on 3 separate nights (device lifetime count)
- WHEN the third such night's action registers
- THEN a one-time ambient "whisper" is emitted via `AuroraEchoBus`, and not repeated on subsequent nights

### Requirement: Aurora Heart vault versioning
The system SHALL compare a deterministic vault version against stored progress on each init, wiping quest progress on mismatch so scenario regeneration never requires a manual app-data clear.
Source: `AuroraVaultData.VERSION`, `AuroraHeartManager.migrateIfNeeded()`.

#### Scenario: Version mismatch soft-resets progress
- GIVEN the stored quest version differs from the current `AuroraVaultData.VERSION`
- WHEN the manager initializes
- THEN all quest progress/state keys are wiped and the stored version is updated to match — no manual data clear needed

#### Scenario: Stale unlocked payload without living-theme material is migrated
- GIVEN a previously unlocked final payload lacks `themeMaterial` (from an older forge run)
- WHEN the manager initializes
- THEN that payload is detected as stale and soft-migrated (wiped) so a newer forge run's living-theme material can populate correctly

### Requirement: Aurora Heart living theme
The system SHALL render a device-tilt-reactive "living metal" material on hero surfaces only when the unlocked final payload includes a `themeMaterial` block with `style: "aurora-metal"`.

#### Scenario: Missing or wrong style yields a static theme
- GIVEN a payload's `themeMaterial.style` is absent or not exactly `"aurora-metal"`
- WHEN the AURORA_PRIME theme renders
- THEN the client ignores the material and falls back to a static theme, no tilt-reactive shader applied

### Requirement: Lattice Resonance carrier latching
The system SHALL require three carriers (anime player, manga reader, novel reader) to each be long-press-latched to a per-carrier threshold before the encrypted board can open, gated by a session-eligibility check.
Source: `eu.kanade.domain.easteregg.lattice`, `LatticeCarrier`, `LatticeVaultData.THRESHOLDS`.

#### Scenario: Carriers are invisible before session eligibility
- GIVEN the app has been started fewer than 20 times and no carrier has ever been latched
- WHEN the user looks for a carrier long-press hotspot
- THEN it is not interactive, since `sessionEligible()` requires ≥20 starts plus either an existing latch or a pseudo-random "permeable start" gate

#### Scenario: Holds are cooldown-limited
- GIVEN a carrier is being long-pressed
- WHEN holds occur less than 400ms apart
- THEN only one increment per 400ms window is counted, preventing spam-latching

#### Scenario: All three carriers latched triggers board opening
- GIVEN anime, manga, and novel carriers have each reached their threshold
- WHEN the last carrier latches
- THEN `openBoard()` derives a canonical string from the three carrier counts and attempts to decrypt Stage A (the board layout)

### Requirement: Lattice Resonance breach persistence
The system SHALL persist a pending "breach" flag across process death so the board-opening overlay reliably surfaces even if the triggering screen was covering the main activity, and SHALL unconditionally re-arm breach on app start if carriers are fully latched but synthesis is not complete.

#### Scenario: Breach survives app restart before being shown
- GIVEN all carriers latch while the reader screen covers the main activity, and the app is killed before the overlay shows
- WHEN the app restarts
- THEN the pending breach flag persists and the overlay is shown, cleared only via `markBreachOpened()`

### Requirement: Lattice Resonance two-stage encryption
The system SHALL require a first decryption (Stage A, board layout) keyed on carrier latch counts, and a second, independent decryption (Stage B, rewards) keyed on the user's solved board topology — with Stage B having no shared rate limit with Stage A.

#### Scenario: Solving the board does not require re-latching carriers
- GIVEN Stage A (the board) is already open
- WHEN the user solves the topology puzzle
- THEN `trySynthesize(board)` attempts Stage B using only the board's topology canonical string, independent of the original carrier-count derivation

#### Scenario: Immediate re-solve attempt after breach is not falsely blocked
- GIVEN the board was just opened via breach
- WHEN the user immediately attempts to solve it
- THEN Stage B synthesis is not subject to Stage A's rate limit, avoiding a prior bug where a near-immediate re-solve right after breach silently failed

### Requirement: Lattice Resonance version migration
The system SHALL wipe carrier counters and board topology on a vault version mismatch while deliberately preserving synthesis-done, residual-shown, whisper-shown, and start-count state.

#### Scenario: Regenerated scenario preserves completion state
- GIVEN a user already completed Lattice Resonance (`synth_done = true`) and the scenario is regenerated with a new vault version
- WHEN the app next initializes
- THEN carrier counters and board topology are cleared, but `synth_done` and related shown-flags are preserved — the user is not asked to re-solve a puzzle they already completed

### Requirement: Debug-only overrides
The system SHALL expose "Force Lattice breach" and "Reset Lattice Resonance" actions only in DEBUG builds.

#### Scenario: Debug actions are absent in release builds
- GIVEN the app is a release (non-DEBUG) build
- WHEN the user opens the "More" screen
- THEN no "Force Lattice breach" or "Reset Lattice Resonance" entries are present

### Requirement: Achievement reward cosmetics are unconditionally unlocked
The system SHALL treat every cosmetic reward ID granted by any achievement in `achievements.json` — including but not limited to Aurora Heart, Lattice Resonance, and Void Broadcast — as unconditionally unlocked, via `UnlockableManager.isDefaultUnlockable()`, regardless of whether the associated achievement/quest was ever completed.
Source: `UnlockableManager.isDefaultUnlockable()` (`data/src/main/java/tachiyomi/data/achievement/UnlockableManager.kt:243`).

#### Scenario: Reward available without completing the quest
- GIVEN a user has never triggered or progressed a given achievement (easter egg or regular)
- WHEN `UnlockableManager.isUnlockableAvailable()` (or `isThemeAvailable()`/`isBadgeAvailable()`/`isDisplayPreferenceAvailable()`) is checked for one of that achievement's reward IDs
- THEN it returns true, identical to how default-prefixed unlockables already behave

#### Scenario: Previously-completed users see no change
- GIVEN a user already completed an achievement and its rewards are marked unlocked in preferences
- WHEN the reward availability is checked after this change ships
- THEN the reward is still available — `isDefaultUnlockable()` short-circuits to true before the stored unlock-state prefs are even consulted, so no state migration is needed
