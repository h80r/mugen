# Easter Eggs Specification Delta

## REMOVED Requirements

### Requirement: Aurora Heart riddle-chain trigger
**Reason**: Aurora Heart is deleted entirely; its reward cosmetics are permanently unlocked by default (see `unlock-easter-egg-cosmetics-by-default`), so the quest mechanism has no remaining purpose.
**Migration**: None needed — no user-facing state depends on the trigger surviving. Cosmetic rewards remain available via `UnlockableManager.isDefaultUnlockable()`.

### Requirement: Aurora Heart vault versioning
**Reason**: The vault and its versioning/migration logic are deleted alongside the rest of Aurora Heart.
**Migration**: None — vault state is no longer read or written.

### Requirement: Aurora Heart living theme
**Reason**: The device-tilt-reactive material was gated on unlocked payload data produced only by completing Aurora Heart; with the quest gone, this rendering path is removed. The underlying AURORA_PRIME theme itself is unaffected and keeps rendering with its static fallback.
**Migration**: None — the static-theme fallback (already the behavior for any payload missing `themeMaterial`) becomes the only path.

### Requirement: Lattice Resonance carrier latching
**Reason**: Lattice Resonance is deleted entirely, including the three-carrier latch mechanism.
**Migration**: None needed — reward cosmetics remain available via `UnlockableManager.isDefaultUnlockable()`.

### Requirement: Lattice Resonance breach persistence
**Reason**: The breach-overlay mechanism is deleted alongside the rest of Lattice Resonance.
**Migration**: None.

### Requirement: Lattice Resonance two-stage encryption
**Reason**: Both encryption stages (board layout, rewards) are deleted alongside the rest of Lattice Resonance.
**Migration**: None.

### Requirement: Lattice Resonance version migration
**Reason**: The vault and its versioning logic are deleted alongside the rest of Lattice Resonance.
**Migration**: None — vault state is no longer read or written.

### Requirement: Debug-only overrides
**Reason**: "Force Lattice breach" and "Reset Lattice Resonance" existed only to test the Lattice Resonance quest mechanism, which is deleted entirely. Void Broadcast's equivalent debug entry ("Glitch Rift") and Aurora Heart's ("Reset Aurora Heart") are removed for the same reason, though this repository's baseline spec never separately documented them as a requirement.
**Migration**: None — debug-only entries, no user-facing state affected.
