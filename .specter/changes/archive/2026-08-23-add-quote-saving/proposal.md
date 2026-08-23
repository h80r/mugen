# Proposal: Save and Browse Novel Quotes

## Intent
Readers want to save a passage of text while reading a novel and revisit it later with its source context (which work, which chapter, when it was saved). No such feature exists today. This is scoped to Novel only — manga's image-based reading has no equivalent native text selection, so quotes don't apply there.

## Scope
- Let the user save a text selection made while reading a novel as a "quote."
- Provide a new top-level entry in the Mais tab ("Citações") that lists all saved quotes, each showing the quote text, source (novel title, chapter), and when it was saved.
- Out of scope: manga quotes, editing/annotating a saved quote, sharing/exporting quotes, quote search/filtering (a plain chronological list is sufficient for v1), and any change to how text selection itself is enabled — this feature assumes selection already works (depends on `improve-novel-reader-behavior-settings`, which fixes text selection across all reading modes).

## Approach
Add a new `novel_quotes` SQLDelight table (mirroring the existing `novel_history` pattern: FK'd to `novel_chapters`, one `.sq` file with named queries), a domain model + repository + interactors following the same layering as `history/novel`, a save action wired into the novel reader's existing text-selection context menu, and a new list screen reachable from a new Mais-tab entry. See `design.md` for the data model and save-action UI decisions.
