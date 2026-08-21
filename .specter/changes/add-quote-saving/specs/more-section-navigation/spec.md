# Delta for More Section Navigation

## ADDED Requirements

### Requirement: Citações is reachable from the More tab
The system SHALL present "Citações" as a direct navigation entry in the More tab, opening the saved-quotes list screen.

#### Scenario: Citações is reachable directly from the More tab
- GIVEN the user opens the More tab
- WHEN they look for the saved-quotes list
- THEN "Citações" is listed as a direct More-tab entry, opening the quotes list screen

<!--
Note: the main spec's "More tab top-level entries" requirement still lists Conquistas and
Tesouraria as live entries, but both were removed by prior archived changes
(remove-treasury-screen, remove-achievements-database) — the main spec's entry count/list is
already stale independent of this change. Fixing that drift is out of scope here; this delta
only adds Citações without re-asserting a full corrected count, to avoid overwriting the
existing (partially inaccurate) text with an equally unverified one. Worth a dedicated spec-sync
pass in the future.
-->
