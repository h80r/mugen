# Delta for More Section Navigation

## ADDED Requirements

### Requirement: Tabbed screens use consistent Aurora tab styling
The system SHALL render every tabbed screen reachable from the More tab using the same Aurora-styled tab component (`TabbedScreenAurora`) that History uses, rather than mixing in the older generic tab component with plain Material3 styling.

#### Scenario: Novel Reader settings tabs match History's styling
- GIVEN the user opens Configurações → Leitura → Novel
- WHEN the five tabs (Texto, Tradução, Navegação, Acessibilidade & TTS, Avançado) render
- THEN they use the same Aurora tab row/header styling as Mais → Histórico, not the plain Material3 `PrimaryTabRow`

#### Scenario: Advanced settings tabs match History's styling
- GIVEN the user opens Configurações → Sistema → Avançado
- WHEN the three tabs (Sistema, Dados e cache, Depuração) render
- THEN they use the same Aurora tab row/header styling as Mais → Histórico, not the plain Material3 `PrimaryTabRow`

#### Scenario: Storage tabs match History's styling
- GIVEN the user opens Configurações → Dados e Armazenamento → Armazenamento
- WHEN the three tabs (Anime, Manga, Novel) render
- THEN they use the same Aurora tab row/header styling as Mais → Histórico, not the plain Material3 `PrimaryTabRow`

#### Scenario: No remaining screen uses the legacy tab component
- GIVEN a developer greps the codebase for calls to the legacy `TabbedScreen(` component
- WHEN the migration is complete
- THEN no call sites remain — every tabbed screen uses `TabbedScreenAurora`
