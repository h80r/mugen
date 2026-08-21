# Proposal: Fix Novel Reader Behavior Settings Group

## Intent
Six related issues live in the novel reader's behavior and selection surfaces: an unnecessary chevron button, a settings drawer that's hard to read against busy content, a clock/battery display that only appears when it's redundant with the system status bar, text selection that's silently broken in every reading mode, translation/dictionary defaults that ignore the app's UI language, and a cramped system context menu plus eager lookup drawer that make selection actions difficult to discover and control. These changes refine existing reader behavior without adding a new screen or reading mode.

## Scope
- Remove the chevron-up button from the novel reader's bottom bar (confirmed functional — scrolls to top of chapter — but judged unnecessary by the user).
- Add a backdrop blur behind the novel reader's settings drawer so background content isn't sharply readable through it.
- Move the clock/battery display from the tap-reveal chip to the always-visible persistent progress line.
- Fix text selection, which is broken under both the page-turn (Curl/Book) renderer and continuous scroll mode (the default), via two independent fixes.
- Default the selection-translation and dictionary target languages to the app's current UI language instead of a hardcoded Russian default.
- Replace the selected-text system context menu with a reusable Aurora bottom action console across native and WebView renderers.
- Enable selected-text translation and dictionary lookup by default when their preferences have never been explicitly set.
- Open translation/definition results only after the matching action is requested, using the same blurred Aurora sheet chrome as reader settings and progressively revealing the alternate lookup mode.
- Out of scope: any new preferences, new reading modes, or changes to reading-mode selection itself.

## Approach
Keep the existing targeted fixes, then build the selection UI on top of the completed shared-selection coordinator. Extract reusable Aurora navigation/sheet chrome instead of visually duplicating the home navigation and reader settings drawer, route selection actions through a renderer-neutral command contract, and make lookup-sheet visibility depend on an explicit dictionary/translation request rather than selection presence alone. See `design.md` for the interaction and reuse decisions.
