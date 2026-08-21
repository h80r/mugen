# Delta for Quotes

## ADDED Requirements

### Requirement: Save a text selection as a quote while reading a novel
The system SHALL let the user save a selected text passage while reading a novel chapter, recording the passage text, its source chapter, and the time it was saved.
Source: `NovelPageReaderPageContent.kt`'s selection `ActionMode` menu, `SelectedTextAction.SAVE_QUOTE`.

#### Scenario: Saving a selection creates a quote
- GIVEN a user has selected a passage of text while reading a novel chapter
- WHEN they choose "Save quote" (or equivalent) from the selection menu
- THEN a quote is persisted recording the selected text, the chapter it was read from, and the current timestamp

#### Scenario: Save-quote action is always available when selection is enabled
- GIVEN text selection is enabled in the novel reader
- WHEN the user opens the selection context menu
- THEN "Save quote" is present regardless of whether dictionary or translation preferences are enabled

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

### Requirement: Quote scope is Novel only
The system SHALL restrict quote-saving to the novel reader; manga has no equivalent feature.

#### Scenario: No save-quote action while reading manga
- GIVEN a user is reading a manga chapter
- WHEN they look for a way to save a quote
- THEN no such action exists — quote-saving is only available in the novel reader
