# Delta for Quotes

## ADDED Requirements

### Requirement: Save a text selection as a quote while reading a novel
The system SHALL let the user save a selected text passage while reading a novel chapter, recording the passage text, its source chapter, and the time it was saved.
Source: `NovelSelectedTextActionConsole`, the Aurora bottom action console shown while a text selection is active — its `SHARE` action is replaced by `SAVE_QUOTE`.

#### Scenario: Saving a selection creates a quote
- GIVEN a user has selected a passage of text while reading a novel chapter
- WHEN they tap "Save quote" in the selection action console
- THEN a quote is persisted recording the selected text, the chapter it was read from, and the current timestamp

#### Scenario: Save-quote action replaces Share in the selection console
- GIVEN a user has an active text selection in the novel reader
- WHEN the selection action console appears
- THEN it shows "Save quote" in place of "Share", alongside Copy, Expand, Dictionary, and Translate

### Requirement: Browse saved quotes
The system SHALL provide a screen listing every saved quote, newest first, showing the quote text, its source (novel title, media type, chapter), and when it was saved.

#### Scenario: Quotes list shows source context
- GIVEN a user has saved at least one quote
- WHEN they open the quotes list
- THEN each entry shows the quote text, the source novel's title, its media type, the chapter it was read from, and the saved timestamp

#### Scenario: Quotes are ordered newest first
- GIVEN a user has saved multiple quotes at different times
- WHEN the quotes list renders
- THEN the most recently saved quote appears first

### Requirement: Delete a saved quote
The system SHALL let the user delete a saved quote from the quotes list.

#### Scenario: Deleting a quote removes it permanently
- GIVEN a user is viewing the quotes list
- WHEN they delete a quote
- THEN it no longer appears in the list and is removed from storage

### Requirement: Tapping a saved quote opens the reader near its original position
The system SHALL, when the user taps a saved quote, open the novel reader at the quote's source chapter and, when the quote's text can still be located in the chapter, at that text's position rather than the chapter's default open position.

#### Scenario: Tapping a quote seeks to its text
- GIVEN a user is viewing the quotes list
- WHEN they tap a quote whose source chapter still contains the saved text
- THEN the novel reader opens on that chapter, scrolled or seeked to the quote's text

#### Scenario: Tapping a quote falls back gracefully if the text can't be found
- GIVEN a user taps a quote whose source chapter no longer contains the saved text (e.g. the chapter was re-fetched and changed)
- WHEN the reader opens
- THEN it opens the chapter normally, using its regular default position, without an error

### Requirement: Quote scope is Novel only
The system SHALL restrict quote-saving to the novel reader; manga has no equivalent feature.

#### Scenario: No save-quote action while reading manga
- GIVEN a user is reading a manga chapter
- WHEN they look for a way to save a quote
- THEN no such action exists — quote-saving is only available in the novel reader
