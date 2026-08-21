# Delta for Theming & Aurora UI

## ADDED Requirements

### Requirement: Home hub welcome empty state uses media-appropriate wording
The system SHALL show reading-appropriate wording ("read"/"ler") on the Home hub empty-state welcome card for the manga and novel sections, and watching-appropriate wording ("watch"/"assistir") only for the anime section.

#### Scenario: Manga and Novel empty state does not say "watch"
- GIVEN a user has no manga (or no novel) in their library
- WHEN the Home > Manga (or Home > Novel) empty-state welcome card renders
- THEN its title and subtitle use reading-appropriate wording, not "Comece a assistir" / "Start watching"

#### Scenario: Anime empty state keeps watching wording
- GIVEN a user has no anime in their library
- WHEN the Home > Anime empty-state welcome card renders
- THEN its title and subtitle retain the existing watching-appropriate wording, unaffected by the manga/novel change

### Requirement: Quick source fallback label reads as "browse sources"
The system SHALL label the Home hub's quick-source fallback button (shown when no specific last-used source is available) with wording that reads as "open/browse sources," not "open-source software."

#### Scenario: pt-BR label does not read as source code
- GIVEN the app language is Portuguese (Brazil) and a Home hub quick-source button has no specific source name to show
- WHEN the fallback label renders
- THEN it reads "Abrir fontes" (or equivalent "browse sources" phrasing), not "Código aberto"
