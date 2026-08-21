# Delta for Library

## ADDED Requirements

### Requirement: Novel library search accepts spaces
The system SHALL preserve spaces typed into the novel library search field while the user is typing, consistent with the manga library search field.

#### Scenario: Trailing space is not stripped while typing
- GIVEN a user is typing a multi-word query into Títulos > Novel > Busca
- WHEN they type a space after a word
- THEN the space remains in the search field, allowing them to continue typing the next word
